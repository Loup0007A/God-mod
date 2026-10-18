package com.godmod.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Effets visuels et sonores : titres à l'écran, sons, particules. */
public final class Fx {
    private Fx() {
    }

    public static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void particles(ServerPlayer p, ParticleOptions type, int count, double spread) {
        ((ServerLevel) p.level()).sendParticles(type, p.getX(), p.getY() + 1, p.getZ(), count, spread, spread, spread, 0.1);
    }

    /** Cercle de particules au sol autour d'un point. */
    public static void ring(ServerLevel l, ParticleOptions type, double x, double y, double z, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            l.sendParticles(type, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0.01);
        }
    }
}
