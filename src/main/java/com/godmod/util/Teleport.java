package com.godmod.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public final class Teleport {
    private Teleport() {
    }

    public static void to(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
    }
}
