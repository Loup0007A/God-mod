package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Specialization;
import com.godmod.data.Stat;
import com.godmod.data.Tier;
import com.godmod.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Effets des artefacts divins : vague, foudre, trou noir. */
public final class ArtifactEffects {
    public static final double WAVE_COST = 30, LIGHTNING_COST = 40, VOID_COST = 60;

    private static final class Vortex {
        final ServerLevel level;
        final Vec3 center;
        final ServerPlayer owner;
        final double power;
        int ticks;

        Vortex(ServerLevel level, Vec3 center, ServerPlayer owner, double power, int ticks) {
            this.level = level;
            this.center = center;
            this.owner = owner;
            this.power = power;
            this.ticks = ticks;
        }
    }

    private static final List<Vortex> VORTICES = new ArrayList<>();

    private ArtifactEffects() {
    }

    /** Puissance de l'artefact : dépend du palier et de l'Esprit. */
    public static double power(PlayerProfile p) {
        return 1.0 + 0.08 * p.stat(Stat.ESPRIT) + 0.35 * Math.max(0, p.tier.ordinal() - Tier.DIEU.ordinal());
    }

    /** Recharge en ticks, réduite par l'Esprit (jusqu'à −50 %). */
    public static int cooldown(PlayerProfile p, int base) {
        double r = Math.min(0.5, 0.02 * p.stat(Stat.ESPRIT));
        return (int) Math.round(base * (1 - r));
    }

    /** Vérifie que le joueur peut manier cet artefact. */
    public static boolean canWield(ServerPlayer player, Specialization required) {
        if (player.isCreative()) return true;
        PlayerProfile p = GodData.get(player);
        if (!p.tier.isGod()) {
            Msg.err(player, "Seul un dieu peut manier cet artefact. Vous êtes " + p.tier.label + ".");
            return false;
        }
        if (p.specialization != required) {
            Msg.err(player, "Cet artefact répond au domaine " + required.label + ". Le vôtre : " + p.specialization.label + ".");
            return false;
        }
        return true;
    }

    public static boolean isAlly(ServerPlayer caster, Entity target) {
        if (!(target instanceof Player tp)) return false;
        PlayerProfile a = GodData.get(caster), b = GodData.get(tp);
        return GodData.isAbove(a.uuid, b) || GodData.isAbove(b.uuid, a);
    }

    // ------------------------------------------------------------------ Trident des Marées

    public static boolean wave(ServerPlayer player, ServerLevel level) {
        if (!canWield(player, Specialization.OCEAN) || !PowerSystem.spendFaith(player, WAVE_COST)) return false;
        double pw = power(GodData.get(player));
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double range = 10 * pw;

        for (int i = 1; i <= (int) range; i++) {
            Vec3 at = eye.add(dir.scale(i));
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y - 0.5, at.z, 25, 1.2, 0.6, 1.2, 0.2);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y - 1, at.z, 4, 1.0, 0.2, 1.0, 0.02);
        }
        AABB box = player.getBoundingBox().inflate(range);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            Vec3 to = e.position().subtract(player.position());
            if (to.length() > range || to.normalize().dot(dir) < 0.5) continue;
            if (isAlly(player, e)) continue;
            e.hurtServer(level, level.damageSources().playerAttack(player), (float) (5 * pw));
            e.setDeltaMovement(dir.x * 1.8 * pw, 0.6, dir.z * 1.8 * pw);
            e.hurtMarked = true;
            e.clearFire();
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
        }
        level.playSound((Player) null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2.0F, 0.6F);
        return true;
    }

    // ------------------------------------------------------------------ Sceptre de Foudre

    public static boolean lightning(ServerPlayer player, ServerLevel level) {
        if (!canWield(player, Specialization.CIEL)) return false;
        Vec3 target = aim(player, level, 64);
        if (target == null) {
            Msg.err(player, "Visez un point à moins de 64 blocs.");
            return false;
        }
        if (!PowerSystem.spendFaith(player, LIGHTNING_COST)) return false;
        PlayerProfile p = GodData.get(player);
        int bolts = 1 + Math.max(0, p.tier.ordinal() - Tier.DIEU.ordinal()) * 2 + p.stat(Stat.ESPRIT) / 8;
        for (int i = 0; i < bolts; i++) {
            double ox = i == 0 ? 0 : (level.random.nextDouble() - 0.5) * 8;
            double oz = i == 0 ? 0 : (level.random.nextDouble() - 0.5) * 8;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt == null) continue;
            bolt.snapTo(target.x + ox, target.y, target.z + oz);
            bolt.setCause(player);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 1, target.z, 60, 2, 2, 2, 0.3);
        return true;
    }

    // ------------------------------------------------------------------ Orbe du Néant

    public static boolean blackHole(ServerPlayer player, ServerLevel level) {
        if (!canWield(player, Specialization.COSMOS)) return false;
        Vec3 target = aim(player, level, 24);
        if (target == null) target = player.getEyePosition().add(player.getLookAngle().scale(10));
        if (!PowerSystem.spendFaith(player, VOID_COST)) return false;
        double pw = power(GodData.get(player));
        VORTICES.add(new Vortex(level, target.add(0, 1.5, 0), player, pw, (int) (80 + 20 * pw)));
        level.playSound((Player) null, target.x, target.y, target.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.5F, 0.5F);
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Vortex> it = VORTICES.iterator();
        while (it.hasNext()) {
            Vortex v = it.next();
            v.ticks--;
            Vec3 c = v.center;
            double radius = 10 * v.power;
            v.level.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 40, 1.5, 1.5, 1.5, 1.2);
            v.level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.05);
            if (v.ticks % 2 == 0) {
                for (Entity e : v.level.getEntities((Entity) null, new AABB(c, c).inflate(radius), e -> e != v.owner)) {
                    if (isAlly(v.owner, e)) continue;
                    Vec3 pull = c.subtract(e.position());
                    double d = pull.length();
                    if (d > radius || d < 0.3) continue;
                    Vec3 force = pull.normalize().scale(0.35 * v.power * (1.2 - d / radius));
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(force));
                    e.hurtMarked = true;
                }
            }
            if (v.ticks <= 0) {
                it.remove();
                v.level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 8, 1.5, 1.5, 1.5, 0.1);
                v.level.playSound((Player) null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
                for (LivingEntity e : v.level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4), e -> e != v.owner)) {
                    if (isAlly(v.owner, e)) continue;
                    e.hurtServer(v.level, v.level.damageSources().playerAttack(v.owner), (float) (10 * v.power));
                }
            }
        }
    }

    private static Vec3 aim(ServerPlayer player, ServerLevel level, double dist) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(dist));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return null;
        return hit.getLocation();
    }

    public static BlockPos below(Vec3 v) {
        return BlockPos.containing(v);
    }
}
