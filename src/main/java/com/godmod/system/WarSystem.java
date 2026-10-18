package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Guerres entre dieux. Pendant une guerre (30 min), les deux camps peuvent saccager
 * le monde divin adverse, et le dieu vaincu devient le vassal du vainqueur avec tout son panthéon.
 */
public final class WarSystem {
    public static final double DECLARE_COST = 200;
    public static final long DURATION_MS = 30L * 60 * 1000;

    public static final class War {
        public final String attacker;
        public final String defender;
        public final long endsAt;

        War(String attacker, String defender) {
            this.attacker = attacker;
            this.defender = defender;
            this.endsAt = System.currentTimeMillis() + DURATION_MS;
        }
    }

    private static final List<War> WARS = new ArrayList<>();

    private WarSystem() {
    }

    public static List<War> wars() {
        return WARS;
    }

    /** Le dieu au sommet de la hiérarchie d'un joueur (lui-même s'il est libre). */
    public static String topOf(PlayerProfile p) {
        PlayerProfile cur = p;
        for (int i = 0; i < 32 && cur.master != null; i++) {
            PlayerProfile up = GodData.find(cur.master);
            if (up == null) break;
            cur = up;
        }
        return cur.uuid;
    }

    /** Dieu de faction impliqué dans une guerre (le joueur lui-même ou un de ses seigneurs), ou null. */
    public static War warOf(PlayerProfile p) {
        for (War w : WARS) {
            if (inFaction(p, w.attacker) || inFaction(p, w.defender)) return w;
        }
        return null;
    }

    public static boolean inFaction(PlayerProfile p, String godUuid) {
        return p.uuid.equals(godUuid) || GodData.isAbove(godUuid, p);
    }

    /** Vrai si les deux joueurs sont dans des camps opposés d'une guerre en cours. */
    public static boolean atWar(PlayerProfile a, PlayerProfile b) {
        for (War w : WARS) {
            if (inFaction(a, w.attacker) && inFaction(b, w.defender)) return true;
            if (inFaction(a, w.defender) && inFaction(b, w.attacker)) return true;
        }
        return false;
    }

    public static String declare(ServerPlayer player, PlayerProfile target) {
        PlayerProfile p = GodData.get(player);
        if (!p.tier.isGod()) return "Seul un dieu peut déclarer une guerre.";
        if (!target.tier.isGod()) return target.name + " n'est pas un dieu.";
        if (target.uuid.equals(p.uuid)) return "Vous ne pouvez pas vous déclarer la guerre.";
        if (GodData.isAbove(p.uuid, target) || GodData.isAbove(target.uuid, p))
            return "Impossible : vous êtes dans la même hiérarchie. Utilisez la rébellion.";
        if (warOf(p) != null) return "Votre panthéon est déjà en guerre.";
        if (warOf(target) != null) return target.name + " est déjà en guerre.";
        if (!PowerSystem.spendFaith(player, DECLARE_COST)) return "";
        WARS.add(new War(p.uuid, target.uuid));
        GodData.log("GUERRE : " + p.name + " déclare la guerre à " + target.name);
        Msg.broadcast(player.level().getServer(), "⚔ " + p.name + " déclare la guerre à " + target.name
                + " ! Pendant 30 minutes, les mondes divins des deux camps peuvent être saccagés.", ChatFormatting.DARK_RED);
        return null;
    }

    /**
     * Un membre d'un camp en a tué un autre. Si la victime est un des deux dieux de faction,
     * la guerre se termine : le perdant devient vassal du vainqueur.
     */
    public static boolean onKill(MinecraftServer server, PlayerProfile killer, PlayerProfile victim) {
        Iterator<War> it = WARS.iterator();
        while (it.hasNext()) {
            War w = it.next();
            String winnerGod = null, loserGod = null;
            if (victim.uuid.equals(w.defender) && inFaction(killer, w.attacker)) {
                winnerGod = w.attacker;
                loserGod = w.defender;
            } else if (victim.uuid.equals(w.attacker) && inFaction(killer, w.defender)) {
                winnerGod = w.defender;
                loserGod = w.attacker;
            }
            if (winnerGod != null) {
                PlayerProfile win = GodData.find(winnerGod), lose = GodData.find(loserGod);
                it.remove();
                if (win != null && lose != null) {
                    lose.master = win.uuid;
                    PowerSystem.removeEssence(lose, 0.25);
                    long loot = lose.money / 3;
                    lose.money -= loot;
                    win.money += loot;
                    GodData.dirty();
                    GodData.log("GUERRE gagnée par " + win.name + " contre " + lose.name);
                    Msg.broadcast(server, "👑 " + win.name + " remporte la guerre ! " + lose.name
                            + " et tout son panthéon lui sont désormais soumis.", ChatFormatting.GOLD);
                }
                return true;
            }
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<War> it = WARS.iterator();
        while (it.hasNext()) {
            War w = it.next();
            if (now >= w.endsAt) {
                it.remove();
                PlayerProfile a = GodData.find(w.attacker), d = GodData.find(w.defender);
                Msg.broadcast(server, "La guerre entre " + (a == null ? "?" : a.name) + " et "
                        + (d == null ? "?" : d.name) + " s'achève sans vainqueur.", ChatFormatting.GRAY);
            }
        }
    }
}
