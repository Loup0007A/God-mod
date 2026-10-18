package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.data.Settings;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Le Sanctuaire (spawn flottant), sa protection, le filet de sécurité en cas de chute,
 * et l'accès au monde ressource (/mine).
 */
public final class Hub {
    /** Monde ressource : un Overworld classique, à part, où l'on mine et récolte. */
    public static final ResourceKey<Level> RESOURCES = ResourceKey.create(Registries.DIMENSION, GodMod.id("ressources"));
    /** Rayon protégé autour du Sanctuaire (îles, pont et arène compris). */
    public static final int PROTECT_RADIUS = 165;
    /** Distance minimale / maximale d'arrivée dans le monde ressource. */
    public static final int MINE_MIN = 300, MINE_MAX = 3000;

    private Hub() {
    }

    public static boolean ready() {
        return GodData.settings().hubSet;
    }

    public static void teleport(ServerPlayer player) {
        Settings s = GodData.settings();
        ServerLevel ow = player.level().getServer().overworld();
        if (!s.hubSet) {
            BlockPos spawn = ow.getLevelData().getRespawnData().pos();
            BlockPos top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, spawn);
            Teleport.to(player, ow, top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5);
            return;
        }
        DivineWorld.rememberReturn(player);
        Teleport.to(player, ow, s.hubX, s.hubY, s.hubZ);
        player.setYRot(180F);
    }

    /** Zone protégée : le décor flottant du Sanctuaire (le sol en dessous reste libre). */
    public static boolean isProtected(Level level, BlockPos pos) {
        Settings s = GodData.settings();
        if (!s.hubSet || level.dimension() != Level.OVERWORLD) return false;
        double dx = pos.getX() + 0.5 - s.hubX, dz = pos.getZ() + 0.5 - s.hubZ;
        if (dx * dx + dz * dz > (double) PROTECT_RADIUS * PROTECT_RADIUS) return false;
        return pos.getY() > s.hubY - 34; // le sol sous les îles reste constructible
    }

    public static boolean canEditHub(ServerPlayer player) {
        return player.isCreative() && Moderation.roleOf(player).atLeast(Role.ADMIN);
    }

    /** Filet de sécurité : un joueur qui tombe des îles du Sanctuaire y est ramené sans dégâts. */
    public static void fallNet(ServerPlayer player) {
        Settings s = GodData.settings();
        if (!s.hubSet || player.level().dimension() != Level.OVERWORLD) return;
        if (player.fallDistance < 20 || player.getY() > s.hubY - 30) return;
        double dx = player.getX() - s.hubX, dz = player.getZ() - s.hubZ;
        if (dx * dx + dz * dz > (double) PROTECT_RADIUS * PROTECT_RADIUS) return;
        Teleport.to(player, (ServerLevel) player.level(), s.hubX, s.hubY, s.hubZ);
        Msg.info(player, "Les dieux vous rattrapent avant la chute.");
    }

    // ------------------------------------------------------------------ Monde ressource

    public static ServerLevel resources(MinecraftServer server) {
        return server.getLevel(RESOURCES);
    }

    /** Téléporte à un endroit sûr et aléatoire du monde ressource. */
    public static String toMine(ServerPlayer player) {
        ServerLevel mine = resources(player.level().getServer());
        if (mine == null) return "Le monde ressource n'est pas chargé (datapack manquant).";
        DivineWorld.rememberReturn(player);
        RandomSource rnd = mine.random;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            int dist = MINE_MIN + rnd.nextInt(MINE_MAX - MINE_MIN);
            int x = (int) (Math.cos(angle) * dist), z = (int) (Math.sin(angle) * dist);
            BlockPos top = mine.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            BlockState ground = mine.getBlockState(top.below());
            if (top.getY() <= mine.getMinY() + 1 || !ground.getFluidState().isEmpty()) continue;
            Teleport.to(player, mine, x + 0.5, top.getY() + 0.1, z + 0.5);
            Msg.ok(player, "Bienvenue dans le monde ressource (" + x + ", " + top.getY() + ", " + z + "). /spawn pour revenir au Sanctuaire.");
            return null;
        }
        return "Aucun endroit sûr trouvé, réessayez.";
    }

    public static boolean isResources(Level level) {
        return level.dimension() == RESOURCES;
    }

    /** Joueur qui n'a jamais vu le Sanctuaire : utilisé par l'accueil. */
    public static void welcome(ServerPlayer player, PlayerProfile p) {
        teleport(player);
    }
}
