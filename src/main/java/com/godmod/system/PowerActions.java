package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Stat;
import com.godmod.data.Tier;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pouvoirs divins : chaque pouvoir coûte de la Foi, a un temps de recharge, et se lance depuis
 * le Codex (accroupi + clic droit), le menu /dieu ou la commande /pouvoir &lt;nom&gt;.
 * Leur puissance augmente avec l'Esprit et le palier.
 */
public final class PowerActions {

    public enum Power {
        BENIR("Bénédiction", Tier.EVEILLE, 35, 30, Items.GLISTERING_MELON_SLICE,
                "Soigne et renforce vous et vos fidèles proches (Régénération, Force, Absorption)"),
        AILES("Ailes célestes", Tier.EVEILLE, 30, 20, Items.FEATHER,
                "Bond prodigieux vers l'avant, puis chute ralentie et vitesse"),
        FOUDRE("Foudre divine", Tier.DEMI_DIEU, 30, 8, Items.LIGHTNING_ROD,
                "Abat la foudre là où vous regardez (jusqu'à 60 blocs)"),
        CHATIER("Châtiment", Tier.DEMI_DIEU, 25, 10, Items.BLAZE_ROD,
                "Frappe un de vos fidèles insolents"),
        MIRACLE("Miracle", Tier.DEMI_DIEU, 45, 45, Items.BONE_MEAL,
                "Fait pousser les plantes, nourrit et soigne tout autour de vous"),
        CONVOQUER("Convocation", Tier.DIEU, 50, 60, Items.ENDER_PEARL,
                "Téléporte tous vos fidèles connectés auprès de vous"),
        AURA("Aura de domination", Tier.DIEU, 70, 40, Items.NETHER_STAR,
                "Onde de choc : repousse et affaiblit les ennemis, protège vos alliés"),
        METEORES("Pluie de météores", Tier.DIEU, 120, 90, Items.FIRE_CHARGE,
                "Une pluie de météores s'abat sur la zone visée (sans détruire de blocs)"),
        JUGEMENT("Jugement", Tier.DIEU_SUPREME, 180, 120, Items.TRIDENT,
                "Foudroie et marque un joueur visé : dégâts massifs et lumière divine"),
        TEMPS("Maîtrise du temps", Tier.DIEU_SUPREME, 250, 300, Items.CLOCK,
                "Change l'heure ou la météo du monde"),
        ESPACE("Maîtrise de l'espace", Tier.DIEU_SUPREME, 150, 60, Items.ENDER_EYE,
                "Se téléporter auprès de n'importe quel joueur");

        public final String label;
        public final Tier required;
        public final double cost;
        public final int cooldownSeconds;
        public final net.minecraft.world.item.Item icon;
        public final String description;

        Power(String label, Tier required, double cost, int cooldownSeconds, net.minecraft.world.item.Item icon, String description) {
            this.label = label;
            this.required = required;
            this.cost = cost;
            this.cooldownSeconds = cooldownSeconds;
            this.icon = icon;
            this.description = description;
        }

        public static Power parse(String s) {
            for (Power p : values()) if (p.name().equalsIgnoreCase(s) || p.label.equalsIgnoreCase(s)) return p;
            return null;
        }
    }

    private static final Map<UUID, Map<Power, Long>> COOLDOWNS = new HashMap<>();

    private PowerActions() {
    }

    public static long cooldownLeft(ServerPlayer player, Power power) {
        Map<Power, Long> m = COOLDOWNS.get(player.getUUID());
        if (m == null) return 0;
        return Math.max(0, m.getOrDefault(power, 0L) - System.currentTimeMillis());
    }

    /** Puissance : 1 + Esprit et palier. */
    private static double power(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        return 1.0 + 0.06 * p.stat(Stat.ESPRIT) + 0.2 * p.tier.ordinal();
    }

    private static boolean check(ServerPlayer player, Power power) {
        PlayerProfile p = GodData.get(player);
        if (!p.tier.atLeast(power.required)) {
            Msg.err(player, power.label + " nécessite le palier " + power.required.label + ".");
            return false;
        }
        long cd = cooldownLeft(player, power);
        if (cd > 0) {
            Msg.err(player, power.label + " sera prêt dans " + Msg.time(cd) + ".");
            return false;
        }
        if (Hub.isSpawn(player.level()) && power != Power.BENIR && power != Power.ESPACE && power != Power.CONVOQUER && power != Power.TEMPS) {
            Msg.err(player, "Ce pouvoir ne peut pas être utilisé dans le Sanctuaire.");
            return false;
        }
        if (!PowerSystem.spendFaith(player, power.cost)) return false;
        double reduce = Math.min(0.5, 0.015 * p.stat(Stat.ESPRIT));
        COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(power, System.currentTimeMillis() + Math.round(power.cooldownSeconds * 1000 * (1 - reduce)));
        return true;
    }

    /** Lance un pouvoir qui ne demande pas de cible particulière. */
    public static void cast(ServerPlayer player, Power power) {
        switch (power) {
            case BENIR -> bless(player);
            case AILES -> wings(player);
            case FOUDRE -> lightning(player);
            case MIRACLE -> miracle(player);
            case CONVOQUER -> summonAll(player);
            case AURA -> aura(player);
            case METEORES -> meteors(player);
            case JUGEMENT -> judgement(player);
            case CHATIER -> Msg.info(player, "Choisissez le fidèle à châtier dans le menu Fidèles (clic droit) ou /dieu pouvoir chatier <joueur>.");
            case TEMPS -> Msg.info(player, "Utilisez le menu Maîtrise du temps ou /dieu pouvoir temps <jour|nuit|pluie|orage|clair>.");
            case ESPACE -> Msg.info(player, "Utilisez le menu Maîtrise de l'espace ou /dieu pouvoir espace <joueur>.");
        }
    }

    private static void announce(ServerPlayer player, Power power) {
        player.displayClientMessage(Component.literal("✦ " + power.label + " ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
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

    private static boolean isAlly(ServerPlayer caster, Entity target) {
        if (target == caster) return true;
        if (!(target instanceof Player tp)) return false;
        PlayerProfile a = GodData.get(caster), b = GodData.get(tp);
        return GodData.isAbove(a.uuid, b) || GodData.isAbove(b.uuid, a) || (a.master != null && a.master.equals(b.master));
    }

    private static Vec3 aim(ServerPlayer player, double dist) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(dist));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    // ------------------------------------------------------------------ Bénédiction

    public static void bless(ServerPlayer god) {
        if (!check(god, Power.BENIR)) return;
        announce(god, Power.BENIR);
        double pw = power(god);
        ServerLevel l = (ServerLevel) god.level();
        List<ServerPlayer> targets = new ArrayList<>();
        targets.add(god);
        for (ServerPlayer f : onlineFollowers(god)) if (f.level() == l && f.distanceTo(god) < 24) targets.add(f);
        for (ServerPlayer t : targets) {
            t.heal((float) (6 * pw));
            t.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) (200 * pw), 1));
            t.addEffect(new MobEffectInstance(MobEffects.STRENGTH, (int) (600 * pw), 0));
            t.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, (int) (600 * pw), 1));
            l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, t.getX(), t.getY() + 1, t.getZ(), 40, 0.5, 1, 0.5, 0.3);
            if (t != god) Msg.ok(t, god.getName().getString() + " vous accorde sa bénédiction.");
        }
        for (int i = 0; i < 3; i++) Fx.ring(l, ParticleTypes.END_ROD, god.getX(), god.getY() + 0.2 + i * 0.6, god.getZ(), 2.5 + i, 40);
        l.playSound((Player) null, god.getX(), god.getY(), god.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        Msg.ok(god, targets.size() + " âme(s) bénie(s).");
    }

    // ------------------------------------------------------------------ Ailes célestes

    private static void wings(ServerPlayer god) {
        if (!check(god, Power.AILES)) return;
        announce(god, Power.AILES);
        double pw = power(god);
        Vec3 look = god.getLookAngle();
        god.setDeltaMovement(look.x * 2.2 * pw, Math.max(0.9, look.y * 1.5 + 0.9), look.z * 2.2 * pw);
        god.hurtMarked = true;
        god.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
        god.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1));
        ServerLevel l = (ServerLevel) god.level();
        l.sendParticles(ParticleTypes.CLOUD, god.getX(), god.getY(), god.getZ(), 40, 0.6, 0.2, 0.6, 0.15);
        l.playSound((Player) null, god.getX(), god.getY(), god.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    // ------------------------------------------------------------------ Foudre divine

    private static void lightning(ServerPlayer god) {
        Vec3 target = aim(god, 60);
        if (target == null) {
            Msg.err(god, "Visez un point à moins de 60 blocs.");
            return;
        }
        if (!check(god, Power.FOUDRE)) return;
        announce(god, Power.FOUDRE);
        ServerLevel l = (ServerLevel) god.level();
        int bolts = 1 + (int) Math.floor(power(god));
        for (int i = 0; i < bolts; i++) {
            double ox = i == 0 ? 0 : (l.random.nextDouble() - 0.5) * 6, oz = i == 0 ? 0 : (l.random.nextDouble() - 0.5) * 6;
            strike(god, l, target.x + ox, target.y, target.z + oz);
        }
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 1, target.z, 80, 2, 2, 2, 0.4);
    }

    private static void strike(ServerPlayer god, ServerLevel l, double x, double y, double z) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(x, y, z);
        bolt.setCause(god);
        l.addFreshEntity(bolt);
    }

    // ------------------------------------------------------------------ Châtiment

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
        if (Hub.isSpawn(tp.level())) {
            Msg.err(god, target.name + " est à l'abri dans le Sanctuaire.");
            return;
        }
        if (!check(god, Power.CHATIER)) return;
        ServerLevel level = (ServerLevel) tp.level();
        strike(god, level, tp.getX(), tp.getY(), tp.getZ());
        tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
        tp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
        Msg.err(tp, "Votre dieu vous châtie !");
        GodData.log("CHÂTIMENT : " + god.getName().getString() + " frappe " + target.name);
    }

    // ------------------------------------------------------------------ Miracle

    private static void miracle(ServerPlayer god) {
        if (!check(god, Power.MIRACLE)) return;
        announce(god, Power.MIRACLE);
        ServerLevel l = (ServerLevel) god.level();
        BlockPos c = god.blockPosition();
        int r = 6 + (int) Math.round(power(god) * 2);
        int grown = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -2; dy <= 2; dy++) {
            if (dx * dx + dz * dz > r * r) continue;
            BlockPos pos = c.offset(dx, dy, dz);
            if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), l, pos)) {
                grown++;
                if (grown % 3 == 0) l.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            }
        }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(r), e -> e instanceof Animal || isAlly(god, e))) {
            e.heal(8);
            if (e instanceof ServerPlayer sp) sp.getFoodData().eat(8, 1.0F);
        }
        Fx.ring(l, ParticleTypes.HAPPY_VILLAGER, god.getX(), god.getY() + 0.3, god.getZ(), r, 60);
        l.playSound((Player) null, god.getX(), god.getY(), god.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.8F);
        Msg.ok(god, "Miracle ! " + grown + " plante(s) ont poussé.");
    }

    // ------------------------------------------------------------------ Convocation

    public static void summonAll(ServerPlayer god) {
        List<ServerPlayer> followers = onlineFollowers(god);
        if (followers.isEmpty()) {
            Msg.err(god, "Aucun fidèle connecté.");
            return;
        }
        if (!check(god, Power.CONVOQUER)) return;
        announce(god, Power.CONVOQUER);
        for (ServerPlayer f : followers) summonOne(god, f);
        Msg.ok(god, followers.size() + " fidèle(s) convoqué(s).");
    }

    public static void summonOne(ServerPlayer god, ServerPlayer f) {
        Teleport.to(f, (ServerLevel) god.level(), god.getX(), god.getY(), god.getZ());
        ((ServerLevel) f.level()).sendParticles(ParticleTypes.PORTAL, f.getX(), f.getY() + 1, f.getZ(), 60, 0.5, 1, 0.5, 0.5);
        f.level().playSound((Player) null, f.getX(), f.getY(), f.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        Msg.info(f, "Vous êtes convoqué par " + god.getName().getString() + ".");
    }

    // ------------------------------------------------------------------ Aura de domination

    private static void aura(ServerPlayer god) {
        if (!check(god, Power.AURA)) return;
        announce(god, Power.AURA);
        ServerLevel l = (ServerLevel) god.level();
        double pw = power(god);
        double r = 10 * pw;
        int hit = 0;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, god.getBoundingBox().inflate(r), e -> e != god && e.isAlive())) {
            if (isAlly(god, e)) {
                e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 0));
                continue;
            }
            Vec3 push = e.position().subtract(god.position()).normalize().scale(1.6 * pw);
            e.setDeltaMovement(push.x, 0.6, push.z);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 1));
            e.hurtServer(l, l.damageSources().playerAttack(god), (float) (4 * pw));
            hit++;
        }
        god.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 1));
        for (int i = 1; i <= 4; i++) Fx.ring(l, ParticleTypes.SONIC_BOOM, god.getX(), god.getY() + 1, god.getZ(), i * r / 4, 24);
        l.playSound((Player) null, god.getX(), god.getY(), god.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.0F);
        Msg.ok(god, "Votre aura frappe " + hit + " ennemi(s).");
    }

    // ------------------------------------------------------------------ Pluie de météores

    private record Meteor(ServerLevel level, ServerPlayer god, double x, double z, double y, int delay, float power) {
    }

    private static final List<Meteor> METEORS = new ArrayList<>();

    private static void meteors(ServerPlayer god) {
        Vec3 target = aim(god, 64);
        if (target == null) {
            Msg.err(god, "Visez le sol à moins de 64 blocs.");
            return;
        }
        if (!check(god, Power.METEORES)) return;
        announce(god, Power.METEORES);
        ServerLevel l = (ServerLevel) god.level();
        int count = 6 + (int) Math.round(power(god) * 3);
        for (int i = 0; i < count; i++) {
            double x = target.x + (l.random.nextDouble() - 0.5) * 14, z = target.z + (l.random.nextDouble() - 0.5) * 14;
            METEORS.add(new Meteor(l, god, x, z, target.y, i * 6 + l.random.nextInt(4), (float) (2.0 + power(god) * 0.5)));
        }
        l.playSound((Player) null, target.x, target.y, target.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    /** Fait tomber les météores en attente (appelé chaque tick). */
    public static void tick(MinecraftServer server) {
        if (METEORS.isEmpty()) return;
        List<Meteor> next = new ArrayList<>();
        Iterator<Meteor> it = METEORS.iterator();
        while (it.hasNext()) {
            Meteor m = it.next();
            if (m.delay() > 0) {
                next.add(new Meteor(m.level(), m.god(), m.x(), m.z(), m.y(), m.delay() - 1, m.power()));
                if (m.delay() < 10) {
                    double h = m.y() + m.delay() * 3;
                    m.level().sendParticles(ParticleTypes.FLAME, m.x(), h, m.z(), 12, 0.3, 0.3, 0.3, 0.02);
                    m.level().sendParticles(ParticleTypes.LARGE_SMOKE, m.x(), h + 1, m.z(), 6, 0.3, 0.3, 0.3, 0.01);
                }
                continue;
            }
            m.level().explode(m.god(), m.x(), m.y(), m.z(), m.power(), false, Level.ExplosionInteraction.NONE);
            m.level().sendParticles(ParticleTypes.LAVA, m.x(), m.y() + 0.5, m.z(), 20, 1, 0.5, 1, 0.2);
        }
        METEORS.clear();
        METEORS.addAll(next);
    }

    // ------------------------------------------------------------------ Jugement

    private static void judgement(ServerPlayer god) {
        ServerPlayer target = null;
        double best = 40;
        Vec3 eye = god.getEyePosition(), look = god.getLookAngle();
        for (ServerPlayer other : god.level().getServer().getPlayerList().getPlayers()) {
            if (other == god || other.level() != god.level()) continue;
            Vec3 to = other.getEyePosition().subtract(eye);
            double d = to.length();
            if (d > 40 || to.normalize().dot(look) < 0.97) continue;
            if (d < best) {
                best = d;
                target = other;
            }
        }
        if (target == null) {
            Msg.err(god, "Regardez un joueur à moins de 40 blocs.");
            return;
        }
        if (isAlly(god, target)) {
            Msg.err(god, "Vous ne pouvez pas juger un allié.");
            return;
        }
        if (!check(god, Power.JUGEMENT)) return;
        announce(god, Power.JUGEMENT);
        ServerLevel l = (ServerLevel) target.level();
        for (int i = 0; i < 3; i++) strike(god, l, target.getX(), target.getY(), target.getZ());
        target.hurtServer(l, l.damageSources().playerAttack(god), (float) (8 * power(god)));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
        Fx.ring(l, ParticleTypes.END_ROD, target.getX(), target.getY() + 0.1, target.getZ(), 3, 40);
        Msg.broadcast(god.level().getServer(), "⚡ " + god.getName().getString() + " prononce son Jugement sur " + target.getName().getString() + " !", ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ Temps & espace

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
        ((ServerLevel) god.level()).sendParticles(ParticleTypes.REVERSE_PORTAL, god.getX(), god.getY() + 1, god.getZ(), 80, 0.5, 1, 0.5, 0.3);
        Teleport.to(god, (ServerLevel) target.level(), target.getX(), target.getY(), target.getZ());
        god.level().playSound((Player) null, god.getX(), god.getY(), god.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 0.5F);
        Msg.ok(god, "Vous traversez l'espace jusqu'à " + target.getName().getString() + ".");
    }

    public static void onLogout(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }
}
