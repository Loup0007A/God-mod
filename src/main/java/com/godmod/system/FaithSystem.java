package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Tier;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Conversion des fidèles par le PvP, chute des dieux tués par des mortels, choix du trône.
 */
public final class FaithSystem {
    /** Un demi-dieu ne peut contrôler que quelques fidèles. */
    public static final int DEMIGOD_MAX_FOLLOWERS = 3;
    public static final long THRONE_CHOICE_MS = 5L * 60 * 1000;

    private FaithSystem() {
    }

    /** Point d'entrée : un joueur en a tué un autre (hors tournoi). */
    public static void onPlayerKill(ServerPlayer killerPlayer, ServerPlayer victimPlayer) {
        MinecraftServer server = killerPlayer.level().getServer();
        PlayerProfile killer = GodData.get(killerPlayer);
        PlayerProfile victim = GodData.get(victimPlayer);
        killer.kills++;
        victim.deaths++;
        GodData.dirty();
        PowerSystem.addEssence(killerPlayer, 25, "victoire");
        com.godmod.progress.QuestSystem.progress(killerPlayer, java.util.List.of("pvp:kill"), 1);
        GodData.log("PVP : " + killer.name + " (" + killer.tier.label + ") a tué " + victim.name + " (" + victim.tier.label + ")");

        // 1) Rébellion en cours entre ces deux joueurs ?
        if (RebellionSystem.onKill(server, killer, victim)) return;

        // 2) Guerre entre dieux ?
        if (WarSystem.atWar(killer, victim)) {
            PowerSystem.addEssence(killerPlayer, 25, "guerre");
            if (WarSystem.onKill(server, killer, victim)) return;
        }

        // 3) Un fidèle qui tue son propre dieu hors rébellion : sacrilège, sans effet.
        if (GodData.isAbove(victim.uuid, killer)) {
            PowerSystem.removeEssence(killer, 0.10);
            Msg.err(killerPlayer, "Sacrilège ! Tuer votre propre dieu hors d'une rébellion ne vous libère pas (−10 % d'Essence). Utilisez /rebellion.");
            return;
        }

        // 4) Un non-dieu terrasse un dieu : le dieu perd son statut.
        if (victim.tier.isGod() && !killer.tier.isGod()) {
            dethrone(server, victim, killer, "terrassé par le mortel " + killer.name);
            killer.godsSlain++;
            PowerSystem.addEssence(killerPlayer, 500, "déicide");
            offerThrone(killerPlayer, killer, victim);
            return;
        }

        // 5) Conversion : un dieu (ou demi-dieu) plus puissant soumet sa victime.
        tryConvert(killerPlayer, killer, victimPlayer, victim);
    }

    private static void tryConvert(ServerPlayer killerPlayer, PlayerProfile killer, ServerPlayer victimPlayer, PlayerProfile victim) {
        if (!killer.tier.atLeast(Tier.DEMI_DIEU)) return;
        if (killer.uuid.equals(victim.master)) return;
        if (GodData.isAbove(victim.uuid, killer)) return;
        if (PowerSystem.powerScore(killer) <= PowerSystem.powerScore(victim)) {
            Msg.info(killerPlayer, victim.name + " est trop puissant pour être converti.");
            return;
        }
        if (killer.tier == Tier.DEMI_DIEU && GodData.followersOf(killer.uuid).size() >= DEMIGOD_MAX_FOLLOWERS) {
            Msg.info(killerPlayer, "Un demi-dieu ne peut avoir que " + DEMIGOD_MAX_FOLLOWERS + " fidèles. Devenez dieu pour en avoir plus.");
            return;
        }
        PlayerProfile oldMaster = GodData.find(victim.master);
        victim.master = killer.uuid;
        GodData.dirty();
        GodData.log("CONVERSION : " + victim.name + " devient fidèle de " + killer.name
                + (oldMaster != null ? " (quitte " + oldMaster.name + ")" : ""));
        PowerSystem.addEssence(killerPlayer, 60, "conversion");
        Msg.ok(killerPlayer, victim.name + " est désormais votre fidèle.");
        Msg.send(victimPlayer, Msg.text("Vous êtes désormais fidèle de " + killer.name
                + ". 10 % de vos gains lui reviennent. Vous pourrez vous rebeller avec /rebellion.", ChatFormatting.GOLD));
        ServerPlayer om = oldMaster == null ? null : PowerSystem.online(killerPlayer.level().getServer(), oldMaster.uuid);
        if (om != null) Msg.err(om, victim.name + " vous a été arraché par " + killer.name + ".");
        PowerSystem.checkPromotion(killerPlayer, killer);
    }

    /** Fait chuter un dieu : il redevient mortel et perd la moitié de son Essence. Ses fidèles restent en suspens. */
    public static void dethrone(MinecraftServer server, PlayerProfile god, PlayerProfile cause, String why) {
        PowerSystem.removeEssence(god, 0.5);
        god.master = null;
        PowerSystem.setTier(server, god, Tier.MORTEL, why);
        GodData.log("CHUTE : " + god.name + " " + why);
    }

    /** Propose au vainqueur de devenir dieu et d'asservir le vaincu (5 minutes pour choisir). */
    public static void offerThrone(ServerPlayer winnerPlayer, PlayerProfile winner, PlayerProfile fallen) {
        winner.pendingThroneVictim = fallen.uuid;
        winner.pendingThroneUntil = System.currentTimeMillis() + THRONE_CHOICE_MS;
        GodData.dirty();
        Msg.broadcast(winnerPlayer.level().getServer(), "☠ " + winner.name + " a terrassé le dieu " + fallen.name + " !", ChatFormatting.DARK_RED);
        Msg.send(winnerPlayer, Msg.text("Le trône de " + fallen.name + " est à vous. ", ChatFormatting.GOLD)
                .append(Msg.button("Devenir dieu et l'asservir", "/dieu choix trone",
                        "Vous devenez Dieu ; " + fallen.name + " et ses fidèles vous sont soumis", ChatFormatting.GOLD))
                .append(" ")
                .append(Msg.button("Refuser", "/dieu choix refuser",
                        "Vous restez libre ; ses fidèles sont libérés", ChatFormatting.GRAY))
                .append(Msg.text("  (5 min)", ChatFormatting.DARK_GRAY)));
    }

    /** Résout le choix du trône. */
    public static String resolveThrone(ServerPlayer player, boolean accept) {
        PlayerProfile p = GodData.get(player);
        if (p.pendingThroneVictim == null || System.currentTimeMillis() > p.pendingThroneUntil) {
            p.pendingThroneVictim = null;
            return "Aucun trône ne vous attend.";
        }
        PlayerProfile fallen = GodData.find(p.pendingThroneVictim);
        p.pendingThroneVictim = null;
        GodData.dirty();
        if (fallen == null) return "Ce dieu n'existe plus.";
        MinecraftServer server = player.level().getServer();
        List<PlayerProfile> followers = GodData.followersOf(fallen.uuid);
        if (accept) {
            p.master = null;
            for (PlayerProfile f : followers) {
                if (!f.uuid.equals(p.uuid)) f.master = p.uuid;
            }
            fallen.master = p.uuid;
            if (!p.tier.isGod()) PowerSystem.setTier(server, p, Tier.DIEU, "s'empare du trône et devient");
            GodData.log("TRÔNE : " + p.name + " devient dieu et asservit " + fallen.name + " (" + followers.size() + " fidèles)");
            Msg.broadcast(server, p.name + " règne désormais sur l'ancien panthéon de " + fallen.name + ".", ChatFormatting.GOLD);
        } else {
            release(server, fallen);
            PowerSystem.addEssence(player, 200, "humilité");
            Msg.ok(player, "Vous refusez le trône. Les fidèles de " + fallen.name + " sont libérés.");
        }
        GodData.dirty();
        return null;
    }

    /** Libère tous les fidèles directs d'un joueur. */
    public static void release(MinecraftServer server, PlayerProfile god) {
        for (PlayerProfile f : GodData.followersOf(god.uuid)) {
            f.master = null;
            ServerPlayer sp = PowerSystem.online(server, f.uuid);
            if (sp != null) Msg.ok(sp, "Vous êtes libre : " + god.name + " n'a plus d'emprise sur vous.");
        }
        GodData.dirty();
    }

    /** Vérifie les choix de trône expirés (appelé chaque minute). */
    public static void tickExpired(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (PlayerProfile p : GodData.state().players.values()) {
            if (p.pendingThroneVictim != null && now > p.pendingThroneUntil) {
                PlayerProfile fallen = GodData.find(p.pendingThroneVictim);
                p.pendingThroneVictim = null;
                if (fallen != null && !fallen.tier.isGod()) release(server, fallen);
                GodData.dirty();
            }
        }
    }

    public static String releaseFollower(ServerPlayer god, PlayerProfile follower) {
        PlayerProfile p = GodData.get(god);
        if (!p.uuid.equals(follower.master)) return follower.name + " n'est pas votre fidèle direct.";
        follower.master = null;
        GodData.dirty();
        ServerPlayer sp = PowerSystem.online(god.level().getServer(), follower.uuid);
        if (sp != null) Msg.ok(sp, p.name + " vous a libéré de son emprise.");
        GodData.log("LIBÉRATION : " + p.name + " libère " + follower.name);
        return null;
    }
}
