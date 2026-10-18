package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.data.Stat;
import com.godmod.data.Tier;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Essence, paliers, caractéristiques, Foi et économie de base.
 */
public final class PowerSystem {
    private static final Identifier MOD_FORCE = GodMod.id("force");
    private static final Identifier MOD_VITALITE = GodMod.id("vitalite");
    private static final Identifier MOD_AGILITE = GodMod.id("agilite");

    private PowerSystem() {
    }

    // ------------------------------------------------------------------ Essence & paliers

    public static long threshold(Tier t) {
        return Math.round(t.essence * GodData.settings().thresholdMultiplier);
    }

    /** Ajoute de l'Essence (avec multiplicateurs) puis vérifie la montée de palier. */
    public static void addEssence(ServerPlayer player, long amount, String reason) {
        PlayerProfile p = GodData.get(player);
        double mult = GodData.settings().essenceMultiplier;
        if (GodData.settings().payEnabled && System.currentTimeMillis() < p.boostUntil) mult *= 2;
        if (p.isOathbreaker()) mult *= 0.5;
        mult *= com.godmod.progress.EventSystem.essenceMultiplier();
        long gain = Math.max(1, Math.round(amount * mult));
        p.essence += gain;
        GodData.dirty();
        if (gain >= 20) {
            player.displayClientMessage(Msg.text("+" + gain + " Essence (" + reason + ")", ChatFormatting.LIGHT_PURPLE), true);
        }
        checkPromotion(player, p);
    }

    public static void removeEssence(PlayerProfile p, double fraction) {
        p.essence = Math.max(0, Math.round(p.essence * (1 - fraction)));
        GodData.dirty();
    }

    /** Monte automatiquement de palier si les conditions sont remplies (jamais de descente ici). */
    public static void checkPromotion(ServerPlayer player, PlayerProfile p) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Tier next = p.tier.next();
            if (next == null) break;
            int followers = next == Tier.DIEU_SUPREME ? GodData.totalFollowers(p.uuid) : GodData.followersOf(p.uuid).size();
            if (p.essence >= threshold(next) && followers >= next.followers) {
                setTier(player.level().getServer(), p, next, "a atteint le palier");
                changed = true;
            }
        }
        applyAttributes(player);
    }

    /** Change le palier d'un joueur et l'annonce. */
    public static void setTier(MinecraftServer server, PlayerProfile p, Tier tier, String why) {
        Tier old = p.tier;
        p.tier = tier;
        GodData.dirty();
        if (old == tier) return;
        GodData.log(p.name + " : " + old.label + " -> " + tier.label + " (" + why + ")");
        if (tier.ordinal() > old.ordinal()) {
            Msg.broadcast(server, p.name + " " + why + " " + tier.label + " !", tier.color);
            ServerPlayer sp = online(server, p.uuid);
            if (sp != null) {
                sp.level().playSound((Player) null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.6F);
                Msg.ok(sp, "Nouveau palier : " + tier.label + ". Vous gagnez 3 points de caractéristique.");
                if (tier == Tier.DIEU && p.specialization == com.godmod.data.Specialization.AUCUNE) {
                    Msg.send(sp, Msg.text("Choisissez votre domaine divin : ", ChatFormatting.GOLD)
                            .append(Msg.button("Océan", "/dieu specialisation ocean", "Trident des Marées", ChatFormatting.BLUE)).append(" ")
                            .append(Msg.button("Ciel", "/dieu specialisation ciel", "Sceptre de Foudre", ChatFormatting.YELLOW)).append(" ")
                            .append(Msg.button("Cosmos", "/dieu specialisation cosmos", "Orbe du Néant", ChatFormatting.DARK_PURPLE)));
                }
            }
        } else {
            Msg.broadcast(server, p.name + " est déchu : " + old.label + " → " + tier.label + ".", ChatFormatting.RED);
        }
        ServerPlayer sp = online(server, p.uuid);
        if (sp != null) {
            if (tier.isGod()) DivineWorld.grant(sp, p);
            applyAttributes(sp);
            Ranks.refresh(sp);
        } else if (tier.isGod()) {
            DivineWorld.ensurePlot(p);
        }
    }

    // ------------------------------------------------------------------ Caractéristiques

    public static int totalStatPoints(PlayerProfile p) {
        double mult = Math.max(0.05, GodData.settings().thresholdMultiplier);
        return (int) (p.essence / (200 * mult)) + 3 * p.tier.ordinal() + 2;
    }

    public static int spentStatPoints(PlayerProfile p) {
        int n = 0;
        for (Stat s : Stat.values()) n += p.stat(s);
        return n;
    }

    public static int freeStatPoints(PlayerProfile p) {
        return totalStatPoints(p) - spentStatPoints(p);
    }

    /** Dépense un point dans une caractéristique. Renvoie un message d'erreur ou null. */
    public static String upgradeStat(ServerPlayer player, Stat stat) {
        PlayerProfile p = GodData.get(player);
        if (freeStatPoints(p) <= 0) return "Aucun point disponible. Gagnez de l'Essence pour en obtenir.";
        if (p.stat(stat) >= p.tier.statCap) return "Plafond atteint pour votre palier (" + p.tier.statCap + "). Montez de palier.";
        p.setStat(stat, p.stat(stat) + 1);
        GodData.dirty();
        applyAttributes(player);
        return null;
    }

    /** Si des points ont été perdus (pénalités), retire des niveaux aux caractéristiques les plus hautes. */
    private static void clampStats(PlayerProfile p) {
        int guard = 0;
        while (freeStatPoints(p) < 0 && guard++ < 500) {
            Stat best = Stat.FORCE;
            for (Stat s : Stat.values()) if (p.stat(s) > p.stat(best)) best = s;
            if (p.stat(best) == 0) break;
            p.setStat(best, p.stat(best) - 1);
        }
        for (Stat s : Stat.values()) {
            if (p.stat(s) > p.tier.statCap) p.setStat(s, p.tier.statCap);
        }
    }

    /** Applique les bonus d'attributs (force, vie, vitesse) correspondant au profil. */
    public static void applyAttributes(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        clampStats(p);
        double penalty = p.isOathbreaker() ? 0.5 : 1.0;
        int t = p.tier.ordinal();

        setModifier(player, Attributes.ATTACK_DAMAGE, MOD_FORCE,
                (0.5 * p.stat(Stat.FORCE) + 1.0 * t) * penalty, AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.MAX_HEALTH, MOD_VITALITE,
                (1.0 * p.stat(Stat.VITALITE) + 2.0 * t) * penalty, AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.MOVEMENT_SPEED, MOD_AGILITE,
                (0.02 * p.stat(Stat.AGILITE) + 0.01 * t) * penalty, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        if (p.faith > p.maxFaith()) p.faith = p.maxFaith();
    }

    private static void setModifier(ServerPlayer player, Holder<Attribute> attr, Identifier id, double value, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (value != 0) inst.addTransientModifier(new AttributeModifier(id, value, op));
    }

    // ------------------------------------------------------------------ Foi

    public static boolean spendFaith(ServerPlayer player, double cost) {
        PlayerProfile p = GodData.get(player);
        if (p.faith < cost) {
            Msg.err(player, "Pas assez de Foi (" + (int) p.faith + "/" + (int) cost + ").");
            return false;
        }
        p.faith -= cost;
        GodData.dirty();
        return true;
    }

    /** Appelé toutes les 5 secondes pour chaque joueur connecté. */
    public static void periodic(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (p.tier.atLeast(Tier.EVEILLE)) {
            int direct = GodData.followersOf(p.uuid).size();
            double regen = 1 + 0.25 * p.stat(Stat.ESPRIT) + 0.5 * direct + p.tier.ordinal();
            p.faith = Math.min(p.maxFaith(), p.faith + regen);
        }
        p.lastSeen = System.currentTimeMillis();
        GodData.dirty();
    }

    /** Appelé chaque minute. */
    public static void perMinute(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        addEssence(player, 2, "temps de jeu");
        com.godmod.progress.QuestSystem.progress(player, java.util.List.of("play:minute"), 1);
        // Revenu passif : 2 pièces par minute.
        earnMoney(player.level().getServer(), p, 2);
    }

    // ------------------------------------------------------------------ Argent

    /** Gain d'argent avec la dîme : 10 % reviennent au dieu du joueur. */
    public static void earnMoney(MinecraftServer server, PlayerProfile p, long amount) {
        if (amount <= 0) return;
        PlayerProfile master = GodData.find(p.master);
        if (master != null) {
            long tithe = Math.max(0, amount / 10);
            master.money += tithe;
            amount -= tithe;
        }
        p.money += amount;
        com.godmod.progress.StatsSystem.add(p, com.godmod.progress.StatsSystem.EARNED, amount);
        GodData.dirty();
    }

    // ------------------------------------------------------------------ Utilitaires

    public static ServerPlayer online(MinecraftServer server, String uuid) {
        if (server == null || uuid == null) return null;
        try {
            return server.getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Score de puissance, utilisé pour savoir qui peut contrôler qui et pour le classement. */
    public static long powerScore(PlayerProfile p) {
        long s = p.essence;
        s += 2000L * p.tier.ordinal();
        s += 150L * GodData.totalFollowers(p.uuid);
        for (Stat st : Stat.values()) s += 40L * p.stat(st);
        return s;
    }

    public static Settings settings() {
        return GodData.settings();
    }
}
