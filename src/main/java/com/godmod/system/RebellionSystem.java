package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rébellion des fidèles contre leur dieu.
 * <ul>
 *   <li>Un fidèle déclare la rébellion (délai de 3 jours par joueur entre deux rébellions).</li>
 *   <li>60 s pour que d'autres fidèles du même dieu la rejoignent.</li>
 *   <li>10 min de combat : si un rebelle tue le dieu, celui-ci chute et le meneur peut prendre le trône.</li>
 *   <li>Échec (rebelles tués ou temps écoulé) : perte d'Essence, d'argent et statut de Parjure.</li>
 * </ul>
 */
public final class RebellionSystem {
    public static final long JOIN_MS = 60_000;
    public static final long FIGHT_MS = 10L * 60 * 1000;
    public static final long OATHBREAKER_MS = 3L * 24 * 3600 * 1000;

    public static final class Rebellion {
        public final String god;
        public final String leader;
        public final Set<String> rebels = new LinkedHashSet<>();
        public boolean fighting = false;
        public long phaseEnd;

        Rebellion(String god, String leader) {
            this.god = god;
            this.leader = leader;
            this.rebels.add(leader);
            this.phaseEnd = System.currentTimeMillis() + JOIN_MS;
        }
    }

    /** Une rébellion au plus par dieu. Clé = UUID du dieu. */
    private static final Map<String, Rebellion> ACTIVE = new HashMap<>();

    private RebellionSystem() {
    }

    public static Rebellion against(String god) {
        return ACTIVE.get(god);
    }

    public static Rebellion involving(String uuid) {
        for (Rebellion r : ACTIVE.values()) {
            if (r.god.equals(uuid) || r.rebels.contains(uuid)) return r;
        }
        return null;
    }

    public static long cooldownLeft(PlayerProfile p) {
        return Math.max(0, p.rebellionCooldownUntil - System.currentTimeMillis());
    }

    private static void setCooldown(PlayerProfile p) {
        p.rebellionCooldownUntil = System.currentTimeMillis() + GodData.settings().rebellionCooldownHours * 3600_000L;
        GodData.dirty();
    }

    public static String declare(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        MinecraftServer server = player.level().getServer();
        if (p.master == null) return "Vous n'avez aucun dieu contre qui vous rebeller.";
        if (cooldownLeft(p) > 0) return "Vous devez attendre encore " + Msg.time(cooldownLeft(p)) + " avant une nouvelle rébellion.";
        if (involving(p.uuid) != null) return "Vous participez déjà à une rébellion.";
        PlayerProfile god = GodData.find(p.master);
        if (god == null) return "Votre dieu est introuvable.";
        if (ACTIVE.containsKey(god.uuid)) return "Une rébellion contre " + god.name + " est déjà en cours : /rebellion rejoindre";
        ServerPlayer godPlayer = PowerSystem.online(server, god.uuid);
        if (godPlayer == null) return god.name + " n'est pas connecté. S'il est inactif depuis longtemps, utilisez /trone reclamer " + god.name;

        Rebellion r = new Rebellion(god.uuid, p.uuid);
        ACTIVE.put(god.uuid, r);
        setCooldown(p);
        GodData.log("RÉBELLION : " + p.name + " se soulève contre " + god.name);

        Msg.err(godPlayer, "⚠ " + p.name + " mène une rébellion contre vous ! Le combat commence dans 60 secondes.");
        for (PlayerProfile f : GodData.followersOf(god.uuid)) {
            ServerPlayer fp = PowerSystem.online(server, f.uuid);
            if (fp == null || f.uuid.equals(p.uuid)) continue;
            Msg.send(fp, Msg.text(p.name + " se rebelle contre " + god.name + " ! ", ChatFormatting.RED)
                    .append(Msg.button("Rejoindre la rébellion", "/rebellion rejoindre",
                            "Risqué : en cas d'échec vous perdez Essence, argent et devenez Parjure", ChatFormatting.DARK_RED)));
        }
        Msg.ok(player, "Rébellion déclarée ! 60 s pour rallier d'autres fidèles, puis 10 min pour abattre " + god.name + ".");
        return null;
    }

    public static String join(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (p.master == null) return "Vous n'avez pas de dieu.";
        Rebellion r = ACTIVE.get(p.master);
        if (r == null) return "Aucune rébellion contre votre dieu.";
        if (r.fighting) return "Trop tard, le combat a commencé.";
        if (r.rebels.contains(p.uuid)) return "Vous êtes déjà dans la rébellion.";
        if (cooldownLeft(p) > 0) return "Vous devez attendre encore " + Msg.time(cooldownLeft(p)) + ".";
        r.rebels.add(p.uuid);
        setCooldown(p);
        Msg.ok(player, "Vous rejoignez la rébellion (" + r.rebels.size() + " rebelles).");
        return null;
    }

    /** Gestion d'un meurtre impliquant une rébellion. Renvoie vrai si l'événement est consommé. */
    public static boolean onKill(MinecraftServer server, PlayerProfile killer, PlayerProfile victim) {
        // Un rebelle abat le dieu : succès.
        Rebellion r = ACTIVE.get(victim.uuid);
        if (r != null && r.fighting && r.rebels.contains(killer.uuid)) {
            ACTIVE.remove(victim.uuid);
            success(server, r, killer);
            return true;
        }
        // Le dieu (ou n'importe qui) abat un rebelle : il est éliminé.
        Rebellion r2 = involving(victim.uuid);
        if (r2 != null && r2.fighting && r2.rebels.contains(victim.uuid)) {
            r2.rebels.remove(victim.uuid);
            punish(server, r2, victim);
            if (r2.rebels.isEmpty()) {
                ACTIVE.remove(r2.god);
                PlayerProfile god = GodData.find(r2.god);
                Msg.broadcast(server, "La rébellion contre " + (god == null ? "?" : god.name) + " a été écrasée.", ChatFormatting.DARK_RED);
            }
            return true;
        }
        return false;
    }

    /** Un rebelle mort autrement qu'en PvP (chute, monstre...) est éliminé. */
    public static void onRebelDied(MinecraftServer server, PlayerProfile victim) {
        Rebellion r = involving(victim.uuid);
        if (r != null && r.fighting && r.rebels.remove(victim.uuid)) {
            punish(server, r, victim);
            if (r.rebels.isEmpty()) ACTIVE.remove(r.god);
        }
    }

    private static void success(MinecraftServer server, Rebellion r, PlayerProfile slayer) {
        PlayerProfile god = GodData.find(r.god);
        PlayerProfile leader = GodData.find(r.leader);
        if (god == null) return;
        for (String uuid : r.rebels) {
            PlayerProfile reb = GodData.find(uuid);
            if (reb != null) reb.master = null;
        }
        Msg.broadcast(server, "🔥 La rébellion menée par " + (leader == null ? "?" : leader.name) + " a renversé " + god.name + " !", ChatFormatting.GOLD);
        FaithSystem.dethrone(server, god, slayer, "renversé par une rébellion");
        PlayerProfile heir = leader != null ? leader : slayer;
        ServerPlayer heirPlayer = PowerSystem.online(server, heir.uuid);
        if (heirPlayer != null) {
            PowerSystem.addEssence(heirPlayer, 400, "rébellion victorieuse");
            FaithSystem.offerThrone(heirPlayer, heir, god);
        }
        for (String uuid : r.rebels) {
            ServerPlayer sp = PowerSystem.online(server, uuid);
            if (sp != null && !uuid.equals(heir.uuid)) PowerSystem.addEssence(sp, 150, "rébellion victorieuse");
        }
        GodData.log("RÉBELLION RÉUSSIE contre " + god.name + " (meneur " + heir.name + ")");
    }

    private static void punish(MinecraftServer server, Rebellion r, PlayerProfile rebel) {
        PowerSystem.removeEssence(rebel, 0.30);
        long fine = rebel.money / 4;
        rebel.money -= fine;
        PlayerProfile god = GodData.find(r.god);
        if (god != null) {
            god.money += fine;
            god.essence += 100;
        }
        rebel.oathbreakerUntil = System.currentTimeMillis() + OATHBREAKER_MS;
        GodData.dirty();
        GodData.log("RÉBELLION ÉCHOUÉE : " + rebel.name + " puni (amende " + fine + ")");
        ServerPlayer sp = PowerSystem.online(server, rebel.uuid);
        if (sp != null) {
            Msg.err(sp, "Votre rébellion a échoué : −30 % d'Essence, −" + fine + " pièces et statut de Parjure pendant 3 jours (caractéristiques réduites de moitié).");
            PowerSystem.applyAttributes(sp);
        }
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Rebellion r : ACTIVE.values().toArray(new Rebellion[0])) {
            PlayerProfile god = GodData.find(r.god);
            ServerPlayer godPlayer = PowerSystem.online(server, r.god);
            if (!r.fighting && now >= r.phaseEnd) {
                r.fighting = true;
                r.phaseEnd = now + FIGHT_MS;
                Msg.broadcast(server, "⚔ La rébellion contre " + (god == null ? "?" : god.name) + " commence ! "
                        + r.rebels.size() + " rebelle(s), 10 minutes.", ChatFormatting.RED);
            } else if (r.fighting && now >= r.phaseEnd) {
                ACTIVE.remove(r.god);
                for (String uuid : r.rebels) {
                    PlayerProfile reb = GodData.find(uuid);
                    if (reb != null) punish(server, r, reb);
                }
                Msg.broadcast(server, "Le temps est écoulé : " + (god == null ? "?" : god.name) + " a tenu bon face à la rébellion.", ChatFormatting.GOLD);
                continue;
            }
            // Pendant le combat, tous les participants brillent pour se retrouver.
            if (r.fighting) {
                if (godPlayer != null) godPlayer.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
                for (String uuid : r.rebels) {
                    ServerPlayer sp = PowerSystem.online(server, uuid);
                    if (sp != null) sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
                }
            }
        }
    }

    /** Le dieu se déconnecte pendant le combat : il fuit, la rébellion réussit sans tuer. */
    public static void onLogout(MinecraftServer server, PlayerProfile p) {
        Rebellion r = ACTIVE.get(p.uuid);
        if (r != null && r.fighting) {
            ACTIVE.remove(p.uuid);
            PlayerProfile leader = GodData.find(r.leader);
            Msg.broadcast(server, p.name + " a fui sa propre rébellion !", ChatFormatting.RED);
            success(server, r, leader != null ? leader : p);
        }
    }
}
