package com.godmod.system;

import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Arrivée en parachute (monde ressource) : le joueur apparaît haut dans le ciel, descend lentement
 * et ne subit aucun dégât de chute tant qu'il n'a pas touché le sol. S'accroupir replie le parachute
 * (chute rapide, toujours sans dégât).
 */
public final class Parachute {
    private static final long MAX_MS = 90_000;

    private static final class State {
        final long started = System.currentTimeMillis();
        boolean open = true;
        int ticks;
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private Parachute() {
    }

    public static void deploy(ServerPlayer p) {
        ACTIVE.put(p.getUUID(), new State());
        p.resetFallDistance();
        p.setDeltaMovement(0, 0, 0);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 8, 4, false, false));
        com.godmod.util.Fx.sound(p, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 1F, 0.8F);
        Msg.info(p, "Parachute déployé ! Aucun dégât de chute jusqu'au sol. Accroupissez-vous pour le replier et tomber plus vite.");
    }

    public static boolean active(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    /** Chaque tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Map.Entry<UUID, State> e : new ArrayList<>(ACTIVE.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            State st = e.getValue();
            if (p == null) {
                ACTIVE.remove(e.getKey());
                continue;
            }
            st.ticks++;
            p.resetFallDistance(); // jamais de dégât de chute pendant la descente
            boolean landed = st.ticks > 5 && (p.onGround() || p.isInWater() || p.isInLava() || p.isPassenger());
            if (landed || !Hub.isResources(p.level()) || System.currentTimeMillis() - st.started > MAX_MS) {
                ACTIVE.remove(e.getKey());
                p.removeEffect(MobEffects.SLOW_FALLING);
                if (landed) {
                    p.displayClientMessage(Msg.text("✔ Atterrissage réussi !", ChatFormatting.GREEN), true);
                    com.godmod.util.Fx.sound(p, SoundEvents.WOOL_PLACE, 1F, 0.8F);
                    Travel.protect(p, 3_000);
                }
                continue;
            }
            if (st.open && p.isShiftKeyDown()) {
                st.open = false;
                p.removeEffect(MobEffects.SLOW_FALLING);
                p.displayClientMessage(Msg.text("Parachute replié : chute libre (sans dégât).", ChatFormatting.YELLOW), true);
            }
            if (st.open && !p.hasEffect(MobEffects.SLOW_FALLING))
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60, 0, false, false));
            if (st.ticks % 4 == 0 && p.level() instanceof ServerLevel l) {
                if (st.open) {
                    // La « voile » : un petit dôme de nuages au-dessus de la tête.
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8;
                        l.sendParticles(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * 1.2, p.getY() + 2.6, p.getZ() + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
                    }
                    l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 2.9, p.getZ(), 2, 0.3, 0, 0.3, 0);
                } else {
                    l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
                }
            }
            if (st.ticks % 20 == 0) {
                int height = Math.max(0, (int) (p.getY() - p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ())));
                p.displayClientMessage(Component.literal("🪂 " + (st.open ? "Parachute" : "Chute libre") + " · " + height + " m du sol"
                        + (st.open ? " · accroupi : replier" : "")).withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    public static void onLogout(ServerPlayer p) {
        if (ACTIVE.remove(p.getUUID()) != null) p.removeEffect(MobEffects.SLOW_FALLING);
    }
}
