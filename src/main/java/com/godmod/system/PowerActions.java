package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Tier;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Pouvoirs divins activés depuis l'interface ou les commandes. */
public final class PowerActions {

    public enum Power {
        BENIR("Bénédiction", Tier.DEMI_DIEU, 60, "Régénération II et Force I pour vos fidèles connectés"),
        CHATIER("Châtiment", Tier.DEMI_DIEU, 25, "Frappe un fidèle de la foudre"),
        CONVOQUER("Convocation", Tier.DIEU, 50, "Téléporte tous vos fidèles connectés auprès de vous"),
        TEMPS("Maîtrise du temps", Tier.DIEU_SUPREME, 250, "Change l'heure ou la météo du monde principal"),
        ESPACE("Maîtrise de l'espace", Tier.DIEU_SUPREME, 150, "Se téléporter auprès de n'importe quel joueur");

        public final String label;
        public final Tier required;
        public final double cost;
        public final String description;

        Power(String label, Tier required, double cost, String description) {
            this.label = label;
            this.required = required;
            this.cost = cost;
            this.description = description;
        }
    }

    private PowerActions() {
    }

    private static boolean check(ServerPlayer player, Power power) {
        PlayerProfile p = GodData.get(player);
        if (!p.tier.atLeast(power.required)) {
            Msg.err(player, power.label + " nécessite le palier " + power.required.label + ".");
            return false;
        }
        return PowerSystem.spendFaith(player, power.cost);
    }

    private static List<ServerPlayer> onlineFollowers(ServerPlayer god) {
        MinecraftServer server = god.level().getServer();
        List<ServerPlayer> list = new ArrayList<>();
        for (PlayerProfile f : GodData.followersOf(god.getUUID().toString())) {
            ServerPlayer sp = PowerSystem.online(server, f.uuid);
            if (sp != null) list.add(sp);
        }
        return list;
    }

    public static void bless(ServerPlayer god) {
        List<ServerPlayer> followers = onlineFollowers(god);
        if (followers.isEmpty()) {
            Msg.err(god, "Aucun fidèle connecté.");
            return;
        }
        if (!check(god, Power.BENIR)) return;
        for (ServerPlayer f : followers) {
            f.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
            f.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1200, 0));
            ((ServerLevel) f.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, f.getX(), f.getY() + 1, f.getZ(), 30, 0.5, 1, 0.5, 0.2);
            Msg.ok(f, god.getName().getString() + " vous accorde sa bénédiction.");
        }
        Msg.ok(god, followers.size() + " fidèle(s) béni(s).");
    }

    public static void smite(ServerPlayer god, PlayerProfile target) {
        if (!god.getUUID().toString().equals(target.master) && !GodData.isAbove(god.getUUID().toString(), target)) {
            Msg.err(god, target.name + " n'est pas sous votre emprise.");
            return;
        }
        ServerPlayer tp = PowerSystem.online(god.level().getServer(), target.uuid);
        if (tp == null) {
            Msg.err(god, target.name + " n'est pas connecté.");
            return;
        }
        if (!check(god, Power.CHATIER)) return;
        ServerLevel level = (ServerLevel) tp.level();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(tp.getX(), tp.getY(), tp.getZ());
            bolt.setCause(god);
            level.addFreshEntity(bolt);
        }
        Msg.err(tp, "Votre dieu vous châtie !");
        GodData.log("CHÂTIMENT : " + god.getName().getString() + " frappe " + target.name);
    }

    public static void summonAll(ServerPlayer god) {
        List<ServerPlayer> followers = onlineFollowers(god);
        if (followers.isEmpty()) {
            Msg.err(god, "Aucun fidèle connecté.");
            return;
        }
        if (!check(god, Power.CONVOQUER)) return;
        for (ServerPlayer f : followers) summonOne(god, f);
        Msg.ok(god, followers.size() + " fidèle(s) convoqué(s).");
    }

    public static void summonOne(ServerPlayer god, ServerPlayer f) {
        Teleport.to(f, (ServerLevel) god.level(), god.getX(), god.getY(), god.getZ());
        f.level().playSound((Player) null, f.getX(), f.getY(), f.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        Msg.info(f, "Vous êtes convoqué par " + god.getName().getString() + ".");
    }

    public static void time(ServerPlayer god, String mode) {
        ServerLevel ow = god.level().getServer().overworld();
        if (!List.of("jour", "nuit", "pluie", "orage", "clair").contains(mode)) {
            Msg.err(god, "Mode inconnu : jour, nuit, pluie, orage ou clair.");
            return;
        }
        if (!check(god, Power.TEMPS)) return;
        long day = ow.getDayTime() - Math.floorMod(ow.getDayTime(), 24000L);
        switch (mode) {
            case "jour" -> ow.setDayTime(day + 24000L + 1000L);
            case "nuit" -> ow.setDayTime(day + 13000L);
            case "pluie" -> ow.setWeatherParameters(0, 6000, true, false);
            case "orage" -> ow.setWeatherParameters(0, 6000, true, true);
            default -> ow.setWeatherParameters(12000, 0, false, false);
        }
        Msg.broadcast(god.level().getServer(), god.getName().getString() + " plie le temps à sa volonté (" + mode + ").", ChatFormatting.LIGHT_PURPLE);
        GodData.log("TEMPS : " + god.getName().getString() + " -> " + mode);
    }

    public static void space(ServerPlayer god, ServerPlayer target) {
        if (target == god) return;
        if (!check(god, Power.ESPACE)) return;
        Teleport.to(god, (ServerLevel) target.level(), target.getX(), target.getY(), target.getZ());
        god.level().playSound((Player) null, god.getX(), god.getY(), god.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 0.5F);
        Msg.ok(god, "Vous traversez l'espace jusqu'à " + target.getName().getString() + ".");
    }
}
