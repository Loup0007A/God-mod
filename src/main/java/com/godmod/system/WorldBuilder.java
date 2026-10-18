package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

/**
 * Structure automatiquement le monde au premier démarrage :
 * <ul>
 *   <li>le Sanctuaire (place centrale) au point d'apparition, qui devient le spawn du serveur ;</li>
 *   <li>l'Arène des tournois à 48 blocs à l'est, enregistrée comme arène officielle ;</li>
 *   <li>la dimension divine, préparée pour les parcelles des joueurs.</li>
 * </ul>
 * Rien n'est reconstruit aux démarrages suivants (sauf /godadmin structurer).
 */
public final class WorldBuilder {
    public static final int HUB_RADIUS = 12;
    public static final int ARENA_RADIUS = 15;
    public static final int ARENA_OFFSET = 48;

    private WorldBuilder() {
    }

    /** Appelé au démarrage du serveur. */
    public static void onServerStarted(MinecraftServer server) {
        Settings s = GodData.settings();
        if (s.worldStructured) {
            GodMod.LOGGER.info("[God Mod] Monde déjà structuré (Sanctuaire en {} {} {}).", (int) s.hubX, (int) s.hubY, (int) s.hubZ);
            return;
        }
        ServerLevel ow = server.overworld();
        BlockPos spawn = ow.getLevelData().getRespawnData().pos();
        build(server, spawn);
    }

    /** Construit le Sanctuaire centré sur {@code around} (à la surface), puis l'arène. */
    public static void build(MinecraftServer server, BlockPos around) {
        ServerLevel ow = server.overworld();
        long start = System.currentTimeMillis();
        BlockPos surface = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
        int y = Math.max(surface.getY(), ow.getSeaLevel() + 1);
        BlockPos hub = new BlockPos(around.getX(), y, around.getZ());

        buildHub(ow, hub);
        BlockPos arenaCenter = new BlockPos(hub.getX() + ARENA_OFFSET, y, hub.getZ());
        buildArena(ow, arenaCenter);
        buildPath(ow, hub, arenaCenter);

        Settings s = GodData.settings();
        s.hubSet = true;
        s.hubX = hub.getX() + 0.5;
        s.hubY = y + 1;
        s.hubZ = hub.getZ() + 0.5;
        s.arenaSet = true;
        s.arenaX = arenaCenter.getX() + 0.5;
        s.arenaY = y + 1;
        s.arenaZ = arenaCenter.getZ() + 0.5;
        s.worldStructured = true;
        GodData.dirty();

        ow.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, hub.above(), 0.0F, 0.0F));

        ServerLevel divine = DivineWorld.level(server);
        if (divine == null) GodMod.LOGGER.error("[God Mod] Dimension divine introuvable : le datapack godmod:divine n'est pas chargé.");

        GodData.log("MONDE structuré : Sanctuaire " + hub.getX() + " " + y + " " + hub.getZ()
                + ", arène " + arenaCenter.getX() + " " + y + " " + arenaCenter.getZ());
        GodMod.LOGGER.info("[God Mod] Monde structuré en {} ms : Sanctuaire en {} {} {}, arène en {} {} {}.",
                System.currentTimeMillis() - start, hub.getX(), y, hub.getZ(), arenaCenter.getX(), y, arenaCenter.getZ());
    }

    // ------------------------------------------------------------------ Sanctuaire

    private static void buildHub(ServerLevel l, BlockPos c) {
        int r = HUB_RADIUS;
        int cx = c.getX(), cy = c.getY(), cz = c.getZ();
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                // Dégager l'espace au-dessus.
                for (int dy = 1; dy <= 12; dy++) set(l, cx + dx, cy + dy, cz + dz, Blocks.AIR.defaultBlockState());
                // Fondations jusqu'au sol.
                for (int dy = -1; dy >= -6; dy--) {
                    if (!l.getBlockState(new BlockPos(cx + dx, cy + dy, cz + dz)).isAir()) break;
                    set(l, cx + dx, cy + dy, cz + dz, Blocks.STONE_BRICKS.defaultBlockState());
                }
                // Sol : anneaux décoratifs.
                BlockState floor;
                if (d > r - 0.5) floor = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                else if (Math.abs(d - 7) < 0.6) floor = Blocks.GOLD_BLOCK.defaultBlockState();
                else if (d < 2.5) floor = Blocks.SMOOTH_QUARTZ.defaultBlockState();
                else if ((dx + dz) % 2 == 0) floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
                else floor = Blocks.STONE_BRICKS.defaultBlockState();
                set(l, cx + dx, cy, cz + dz, floor);
            }
        }
        // Autel central.
        set(l, cx, cy + 1, cz, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        set(l, cx, cy + 2, cz, Blocks.SEA_LANTERN.defaultBlockState());
        // Lumière dans le sol.
        for (int[] o : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) set(l, cx + o[0], cy, cz + o[1], Blocks.SEA_LANTERN.defaultBlockState());
        // Huit piliers.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int px = cx + (int) Math.round(Math.cos(a) * (r - 2));
            int pz = cz + (int) Math.round(Math.sin(a) * (r - 2));
            for (int dy = 1; dy <= 5; dy++) set(l, px, cy + dy, pz, Blocks.QUARTZ_PILLAR.defaultBlockState());
            set(l, px, cy + 6, pz, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            set(l, px, cy + 7, pz, Blocks.LANTERN.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ Arène

    private static void buildArena(ServerLevel l, BlockPos c) {
        int r = ARENA_RADIUS;
        int cx = c.getX(), cy = c.getY(), cz = c.getZ();
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1.5) continue;
                for (int dy = 1; dy <= 14; dy++) set(l, cx + dx, cy + dy, cz + dz, Blocks.AIR.defaultBlockState());
                for (int dy = -1; dy >= -6; dy--) {
                    if (!l.getBlockState(new BlockPos(cx + dx, cy + dy, cz + dz)).isAir()) break;
                    set(l, cx + dx, cy + dy, cz + dz, Blocks.STONE_BRICKS.defaultBlockState());
                }
                if (d > r + 0.5) {
                    // Mur d'enceinte (avec gradins de verre au sommet pour les spectateurs).
                    for (int dy = 0; dy <= 3; dy++) set(l, cx + dx, cy + dy, cz + dz, Blocks.STONE_BRICKS.defaultBlockState());
                    set(l, cx + dx, cy + 4, cz + dz, Blocks.GLASS.defaultBlockState());
                    set(l, cx + dx, cy + 5, cz + dz, Blocks.GLASS.defaultBlockState());
                } else {
                    BlockState floor = d < 1.5 ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : Math.abs(dx) == 6 && Math.abs(dz) <= 1 ? Blocks.RED_CONCRETE.defaultBlockState()
                            : (Math.abs(d - 10) < 0.6 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                    set(l, cx + dx, cy, cz + dz, floor);
                }
            }
        }
        // Éclairage (sous le sol pour ne pas gêner les combats).
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            set(l, cx + (int) Math.round(Math.cos(a) * (r - 1)), cy, cz + (int) Math.round(Math.sin(a) * (r - 1)), Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ Chemin

    private static void buildPath(ServerLevel l, BlockPos hub, BlockPos arena) {
        int y = hub.getY();
        for (int x = hub.getX() + HUB_RADIUS; x <= arena.getX() - ARENA_RADIUS; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                int z = hub.getZ() + dz;
                for (int dy = 1; dy <= 4; dy++) set(l, x, y + dy, z, Blocks.AIR.defaultBlockState());
                for (int dy = -1; dy >= -6; dy--) {
                    if (!l.getBlockState(new BlockPos(x, y + dy, z)).isAir()) break;
                    set(l, x, y + dy, z, Blocks.STONE_BRICKS.defaultBlockState());
                }
                set(l, x, y, z, dz == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
            if ((x - hub.getX()) % 6 == 0) {
                set(l, x, y + 1, hub.getZ() + 2, Blocks.STONE_BRICK_WALL.defaultBlockState());
                set(l, x, y + 2, hub.getZ() + 2, Blocks.LANTERN.defaultBlockState());
                set(l, x, y + 1, hub.getZ() - 2, Blocks.STONE_BRICK_WALL.defaultBlockState());
                set(l, x, y + 2, hub.getZ() - 2, Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState state) {
        l.setBlockAndUpdate(new BlockPos(x, y, z), state);
    }
}
