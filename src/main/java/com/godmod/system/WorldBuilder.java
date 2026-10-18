package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Le Sanctuaire « temple céleste » : une île flottante au-dessus du point d'apparition avec
 * <ul>
 *   <li>une place en mosaïque (quartz, or, violet) et une fontaine surmontée d'un trident d'or entouré d'une spirale d'eau ;</li>
 *   <li>un temple à colonnade avec bannières violettes, un portail-vortex et un faisceau violet (balise) ;</li>
 *   <li>une grande allée éclairée, quatre monuments (Océan, Ciel, Cosmos, Jardin) ;</li>
 *   <li>un pont vers l'île de l'Arène des tournois, et des petites îles flottantes autour.</li>
 * </ul>
 * La construction est étalée sur plusieurs ticks ({@link BlockJobs}) pour ne pas bloquer le serveur.
 */
public final class WorldBuilder {
    public static final int VERSION = 2;
    public static final int ISLAND_R = 40;
    public static final int PLAZA_R = 21;
    public static final int ARENA_DX = 100;
    public static final int ARENA_ISLAND_R = 27;
    public static final int ARENA_R = 17;

    private WorldBuilder() {
    }

    // ------------------------------------------------------------------ Plan de construction

    /** Trois phases : air, structure solide, puis éléments fragiles (eau, lanternes, bannières, feuillage). */
    private static final class Plan {
        final Map<BlockPos, BlockState> air = new LinkedHashMap<>();
        final Map<BlockPos, BlockState> solid = new LinkedHashMap<>();
        final Map<BlockPos, BlockState> detail = new LinkedHashMap<>();

        void clear(int x, int y, int z) {
            air.put(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }

        void set(int x, int y, int z, Block b) {
            set(x, y, z, b.defaultBlockState());
        }

        void set(int x, int y, int z, BlockState s) {
            BlockPos p = new BlockPos(x, y, z);
            detail.remove(p);
            solid.put(p, s);
        }

        void detail(int x, int y, int z, Block b) {
            detail(x, y, z, b.defaultBlockState());
        }

        void detail(int x, int y, int z, BlockState s) {
            BlockPos p = new BlockPos(x, y, z);
            solid.remove(p);
            detail.put(p, s);
        }

        boolean has(int x, int y, int z) {
            BlockPos p = new BlockPos(x, y, z);
            return solid.containsKey(p) || detail.containsKey(p);
        }

        Iterator<BlockJobs.Placement> steps() {
            List<BlockJobs.Placement> all = new ArrayList<>(air.size() + solid.size() + detail.size());
            for (var e : air.entrySet()) if (!solid.containsKey(e.getKey()) && !detail.containsKey(e.getKey())) all.add(new BlockJobs.Placement(e.getKey(), e.getValue()));
            for (var e : solid.entrySet()) all.add(new BlockJobs.Placement(e.getKey(), e.getValue()));
            for (var e : detail.entrySet()) all.add(new BlockJobs.Placement(e.getKey(), e.getValue()));
            return all.iterator();
        }
    }

    // ------------------------------------------------------------------ Démarrage

    public static void onServerStarted(MinecraftServer server) {
        Settings s = GodData.settings();
        if (s.structureVersion >= VERSION) {
            GodMod.LOGGER.info("[God Mod] Sanctuaire déjà construit en {} {} {}.", (int) s.hubX, (int) s.hubY, (int) s.hubZ);
            return;
        }
        ServerLevel ow = server.overworld();
        BlockPos around;
        if (s.worldStructured && s.hubSet) {
            // Ancien Sanctuaire (v1, au sol) : on le démonte et on construit le temple au-dessus.
            around = BlockPos.containing(s.hubX, s.hubY, s.hubZ);
            demolishV1(ow, s);
        } else {
            around = ow.getLevelData().getRespawnData().pos();
        }
        build(server, around);
    }

    /** Construit le Sanctuaire au-dessus de {@code around}. Le spawn est déplacé une fois la construction finie. */
    public static void build(MinecraftServer server, BlockPos around) {
        ServerLevel ow = server.overworld();
        BlockPos ground = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
        int cy = Math.max(110, Math.min(ground.getY() + 50, ow.getMaxY() - 60));
        int cx = around.getX(), cz = around.getZ();
        Random rnd = new Random(cx * 31L + cz * 17L + 7);

        Plan plan = new Plan();
        island(plan, rnd, cx, cy, cz, ISLAND_R, 30);
        plaza(plan, cx, cy, cz);
        fountain(plan, cx, cy, cz);
        temple(plan, cx, cy, cz);
        avenue(plan, cx, cy, cz);
        monuments(plan, rnd, cx, cy, cz);

        int ax = cx + ARENA_DX;
        island(plan, rnd, ax, cy, cz, ARENA_ISLAND_R, 22);
        arena(plan, ax, cy, cz);
        bridge(plan, cx + ISLAND_R - 3, ax - ARENA_ISLAND_R + 3, cy, cz);
        miniIslands(plan, rnd, cx, cy, cz);

        GodData.settings().hubSet = false;
        BlockJobs.submit(ow, "Construction du Sanctuaire", plan.steps(), () -> {
            Settings s = GodData.settings();
            s.hubSet = true;
            s.hubX = cx + 0.5;
            s.hubY = cy + 1;
            s.hubZ = cz + 12.5;
            s.arenaSet = true;
            s.arenaX = ax + 0.5;
            s.arenaY = cy + 1;
            s.arenaZ = cz + 0.5;
            s.worldStructured = true;
            s.structureVersion = VERSION;
            GodData.dirty();
            ow.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, new BlockPos(cx, cy + 1, cz + 12), 180.0F, 0.0F));
            GodData.log("MONDE : Sanctuaire construit en " + cx + " " + cy + " " + cz + ", arène en " + ax + " " + cy + " " + cz);
            GodMod.LOGGER.info("[God Mod] Sanctuaire prêt en {} {} {} (arène en {} {} {}).", cx, cy + 1, cz + 12, ax, cy, cz);
            com.godmod.util.Msg.broadcast(server, "Le Sanctuaire est prêt ! /spawn pour le rejoindre.", net.minecraft.ChatFormatting.GOLD);
        }, msg -> GodMod.LOGGER.info("[God Mod] {}", msg));
    }

    // ------------------------------------------------------------------ Île flottante

    private static void island(Plan p, Random rnd, int cx, int cy, int cz, int r, int depth) {
        double[] wobble = new double[16];
        for (int i = 0; i < wobble.length; i++) wobble[i] = rnd.nextDouble() * 3 - 1.5;
        for (int dx = -r - 3; dx <= r + 3; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double a = Math.atan2(dz, dx);
                int sector = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * wobble.length) % wobble.length;
                double edge = r + wobble[sector];
                if (d > edge) continue;
                int x = cx + dx, z = cz + dz;
                for (int y = cy + 1; y <= cy + 40; y++) p.clear(x, y, z);
                double t = 1 - Math.pow(d / edge, 1.6);
                int thick = Math.max(2, (int) Math.round(depth * t + rnd.nextInt(3)));
                for (int k = 0; k <= thick; k++) {
                    int y = cy - k;
                    Block b;
                    if (k == 0) b = d > edge - 2.5 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
                    else if (k <= 2) b = Blocks.DIRT;
                    else if (k > thick - 3) b = rnd.nextInt(3) == 0 ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE;
                    else b = switch (rnd.nextInt(6)) {
                        case 0 -> Blocks.ANDESITE;
                        case 1 -> Blocks.TUFF;
                        default -> Blocks.STONE;
                    };
                    p.set(x, y, z, b);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Place centrale

    private static void plaza(Plan p, int cx, int cy, int cz) {
        for (int dx = -PLAZA_R - 1; dx <= PLAZA_R + 1; dx++) {
            for (int dz = -PLAZA_R - 1; dz <= PLAZA_R + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > PLAZA_R + 0.5) continue;
                double a = Math.atan2(dz, dx);
                Block b;
                double ray = Math.abs(Math.sin(4 * a)) * d;
                if (d > PLAZA_R - 0.5) b = Blocks.POLISHED_BLACKSTONE_BRICKS;
                else if (d > PLAZA_R - 1.5) b = Blocks.GOLD_BLOCK;
                else if (Math.abs(d - 13) < 0.55 || Math.abs(d - 7) < 0.55) b = Blocks.GOLD_BLOCK;
                else if (Math.abs(d - 10) < 0.6) b = Blocks.AMETHYST_BLOCK;
                else if (ray < 0.75 && d > 7 && d < PLAZA_R - 1.5) b = Blocks.PURPLE_CONCRETE;
                else if (ray < 1.6 && d > 7 && d < PLAZA_R - 1.5) b = Blocks.POLISHED_BLACKSTONE_BRICKS;
                else b = (Math.floorMod(dx, 2) + Math.floorMod(dz, 2)) % 2 == 0 ? Blocks.QUARTZ_BLOCK : Blocks.POLISHED_ANDESITE;
                p.set(cx + dx, cy, cz + dz, b);
            }
        }
        // Lampadaires dorés tout autour de la place.
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16 + Math.PI / 16;
            int x = cx + (int) Math.round(Math.cos(a) * (PLAZA_R - 0.5));
            int z = cz + (int) Math.round(Math.sin(a) * (PLAZA_R - 0.5));
            p.set(x, cy + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            p.set(x, cy + 2, z, Blocks.GOLD_BLOCK);
            p.detail(x, cy + 3, z, Blocks.LANTERN);
        }
    }

    // ------------------------------------------------------------------ Fontaine & trident

    private static void fountain(Plan p, int cx, int cy, int cz) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (d <= 5.5 && d > 4.5) {
                    p.set(x, cy + 1, z, Blocks.QUARTZ_BLOCK);
                    p.set(x, cy, z, Blocks.QUARTZ_BLOCK);
                } else if (d <= 4.5) {
                    p.set(x, cy, z, Blocks.PRISMARINE);
                    p.set(x, cy - 1, z, Blocks.SEA_LANTERN);
                    if (d > 1.5) p.detail(x, cy + 1, z, Blocks.WATER);
                }
            }
        }
        // Colonne centrale et vasque supérieure.
        for (int y = cy + 1; y <= cy + 4; y++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                p.set(cx + dx, y, cz + dz, (dx == 0 && dz == 0) ? Blocks.SEA_LANTERN : Blocks.DARK_PRISMARINE);
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (dx * dx + dz * dz <= 5) p.set(cx + dx, cy + 5, cz + dz, Blocks.SMOOTH_QUARTZ);
        // Eau qui déborde de la vasque : une petite cascade circulaire.
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) p.detail(cx + o[0], cy + 6, cz + o[1], Blocks.WATER);
        // Trident d'or.
        for (int y = cy + 6; y <= cy + 17; y++) p.set(cx, y, cz, Blocks.GOLD_BLOCK);
        for (int dx = -2; dx <= 2; dx++) p.set(cx + dx, cy + 15, cz, Blocks.GOLD_BLOCK);
        for (int y = cy + 16; y <= cy + 18; y++) {
            p.set(cx - 2, y, cz, Blocks.GOLD_BLOCK);
            p.set(cx + 2, y, cz, Blocks.GOLD_BLOCK);
        }
        p.set(cx, cy + 18, cz, Blocks.GOLD_BLOCK);
        p.set(cx, cy + 19, cz, Blocks.GOLD_BLOCK);
        p.detail(cx - 2, cy + 19, cz, Blocks.END_ROD);
        p.detail(cx + 2, cy + 19, cz, Blocks.END_ROD);
        p.detail(cx, cy + 20, cz, Blocks.END_ROD);
        // Spirale d'eau (verre bleu) qui s'enroule autour du manche.
        for (int i = 0; i < 40; i++) {
            double a = i * 0.55;
            int y = cy + 6 + i / 4;
            if (y > cy + 14) break;
            int x = cx + (int) Math.round(Math.cos(a) * 2.2);
            int z = cz + (int) Math.round(Math.sin(a) * 2.2);
            if (!p.has(x, y, z)) p.set(x, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
    }

    // ------------------------------------------------------------------ Temple

    private static void temple(Plan p, int cx, int cy, int cz) {
        int zf = cz - 22, zb = cz - 34, w = 14;
        // Socle et marches.
        for (int dx = -w - 2; dx <= w + 2; dx++) {
            for (int z = zb - 1; z <= zf + 2; z++) {
                int x = cx + dx;
                for (int y = cy - 2; y <= cy; y++) p.set(x, y, z, Blocks.STONE_BRICKS);
                p.set(x, cy + 1, z, z >= zf + 1 ? Blocks.SMOOTH_QUARTZ : Blocks.POLISHED_BLACKSTONE_BRICKS);
                if (z <= zf) p.set(x, cy + 2, z, (Math.floorMod(dx, 4) == 0 || z == zf) ? Blocks.GOLD_BLOCK : Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // Colonnade de façade et bannières entre les colonnes.
        for (int dx = -w; dx <= w; dx += 4) {
            if (Math.abs(dx) <= 2) continue;
            pillar(p, cx + dx, cy + 3, zf, 12);
            pillar(p, cx + dx, cy + 3, zb + 1, 12);
        }
        for (int dx = -w + 2; dx <= w - 2; dx += 4) {
            if (Math.abs(dx) <= 3) continue;
            banner(p, cx + dx, cy + 14, zf, 7);
        }
        // Murs latéraux et mur du fond.
        for (int z = zb; z <= zf; z++) {
            for (int y = cy + 3; y <= cy + 15; y++) {
                p.set(cx - w - 1, y, z, (y % 4 == 0) ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
                p.set(cx + w + 1, y, z, (y % 4 == 0) ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
            }
        }
        for (int dx = -w - 1; dx <= w + 1; dx++) {
            for (int y = cy + 3; y <= cy + 15; y++) p.set(cx + dx, y, zb, Blocks.STONE_BRICKS);
        }
        // Grand arc du portail-vortex dans le mur du fond.
        for (int dx = -6; dx <= 6; dx++) {
            int top = cy + 3 + (int) Math.round(10 - dx * dx / 4.0);
            for (int y = cy + 3; y <= top; y++) {
                boolean rim = Math.abs(dx) == 6 || y == top;
                p.set(cx + dx, y, zb, rim ? Blocks.GOLD_BLOCK : ((dx + y) % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.PURPLE_STAINED_GLASS));
                if (!rim) p.set(cx + dx, y, zb - 1, Blocks.OBSIDIAN);
            }
        }
        // Entablement, frise dorée et toit.
        for (int dx = -w - 1; dx <= w + 1; dx++) {
            for (int z = zb; z <= zf + 1; z++) {
                p.set(cx + dx, cy + 16, z, z == zf + 1 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.STONE_BRICKS);
                p.set(cx + dx, cy + 17, z, (z == zf + 1 || z == zb) ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ);
            }
        }
        // Fronton triangulaire.
        for (int k = 0; k <= 5; k++) {
            for (int dx = -(w - 2 * k); dx <= w - 2 * k; dx++) {
                p.set(cx + dx, cy + 18 + k, zf + 1, k == 5 || Math.abs(dx) == w - 2 * k ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK);
            }
        }
        // Balise : faisceau violet au-dessus du temple (pyramide d'or cachée sous la balise).
        int bz = (zf + zb) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(cx + dx, cy + 17, bz + dz, Blocks.GOLD_BLOCK);
        p.set(cx, cy + 18, bz, Blocks.BEACON);
        p.set(cx, cy + 19, bz, Blocks.PURPLE_STAINED_GLASS);
        // Intérieur : sol, autel, lumière.
        for (int dx = -w; dx <= w; dx++) {
            for (int z = zb + 1; z < zf; z++) {
                boolean edge = Math.abs(dx) == w || z == zb + 1 || z == zf - 1;
                p.set(cx + dx, cy + 2, z, edge ? Blocks.GOLD_BLOCK : ((dx + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.PURPUR_BLOCK));
                for (int y = cy + 3; y <= cy + 15; y++) if (!p.has(cx + dx, y, z)) p.clear(cx + dx, y, z);
            }
        }
        for (int dx = -2; dx <= 2; dx++) p.set(cx + dx, cy + 3, zb + 4, Blocks.CHISELED_QUARTZ_BLOCK);
        p.set(cx, cy + 4, zb + 4, Blocks.AMETHYST_BLOCK);
        p.detail(cx, cy + 5, zb + 4, Blocks.AMETHYST_CLUSTER);
        for (int dx = -w + 3; dx <= w - 3; dx += 6) p.set(cx + dx, cy + 15, bz, Blocks.SEA_LANTERN);
        // Tours d'angle à flèche d'améthyste.
        for (int sx : new int[]{-w - 3, w + 3}) for (int sz : new int[]{zb - 1, zf + 1}) tower(p, cx + sx, cy + 1, sz);
    }

    private static void pillar(Plan p, int x, int y0, int z, int h) {
        p.set(x, y0, z, Blocks.CHISELED_QUARTZ_BLOCK);
        for (int y = y0 + 1; y < y0 + h; y++) p.set(x, y, z, Blocks.QUARTZ_PILLAR);
        p.set(x, y0 + h, z, Blocks.GOLD_BLOCK);
    }

    /** Bannière verticale violette avec un emblème doré. */
    private static void banner(Plan p, int x, int top, int z, int len) {
        for (int k = 0; k < len; k++) {
            int y = top - k;
            Block b = (k == len / 2 || k == len / 2 - 1) ? Blocks.GOLD_BLOCK : Blocks.PURPLE_CONCRETE;
            p.set(x, y, z, b);
        }
        p.set(x, top - len, z, Blocks.GOLD_BLOCK);
    }

    private static void tower(Plan p, int x, int y0, int z) {
        for (int y = y0; y <= y0 + 20; y++)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                p.set(x + dx, y, z + dz, (dx == 0 && dz == 0) ? Blocks.STONE_BRICKS : (y % 5 == 0 ? Blocks.GOLD_BLOCK : Blocks.STONE_BRICKS));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(x + dx, y0 + 21, z + dz, Blocks.POLISHED_BLACKSTONE_BRICKS);
        for (int k = 0; k < 6; k++) p.set(x, y0 + 22 + k, z, k < 4 ? Blocks.AMETHYST_BLOCK : Blocks.PURPLE_STAINED_GLASS);
        p.detail(x, y0 + 28, z, Blocks.END_ROD);
    }

    // ------------------------------------------------------------------ Grande allée (sud)

    private static void avenue(Plan p, int cx, int cy, int cz) {
        for (int z = cz + PLAZA_R; z <= cz + ISLAND_R + 6; z++) {
            for (int dx = -3; dx <= 3; dx++) {
                int x = cx + dx;
                Block b;
                if (Math.abs(dx) == 3) b = Blocks.POLISHED_BLACKSTONE_BRICKS;
                else if (Math.abs(dx) == 2) b = Blocks.GOLD_BLOCK;
                else b = (Math.floorMod(z + dx, 4) < 2) ? Blocks.QUARTZ_BLOCK : Blocks.PURPLE_CONCRETE;
                p.set(x, cy, z, b);
                p.set(x, cy - 1, z, Blocks.STONE_BRICKS);
                if (z > cz + ISLAND_R - 2) p.set(x, cy - 2, z, Blocks.STONE_BRICKS);
            }
            if (Math.floorMod(z - cz, 4) == 0) {
                for (int sx : new int[]{-4, 4}) {
                    p.set(cx + sx, cy, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.set(cx + sx, cy + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.set(cx + sx, cy + 2, z, Blocks.GOLD_BLOCK);
                    p.detail(cx + sx, cy + 3, z, Blocks.LANTERN);
                }
            }
            if (Math.floorMod(z - cz, 8) == 2) {
                for (int sx : new int[]{-4, 4}) {
                    p.set(cx + sx, cy, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    for (int y = cy + 1; y <= cy + 5; y++) p.set(cx + sx, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.detail(cx + sx, cy + 6, z, Blocks.PURPLE_BANNER);
                }
            }
        }
        // Belvédère au bout de l'allée.
        int ez = cz + ISLAND_R + 6;
        for (int dx = -5; dx <= 5; dx++) {
            p.set(cx + dx, cy, ez + 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
            p.detail(cx + dx, cy + 1, ez + 1, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        }
    }

    // ------------------------------------------------------------------ Monuments

    private static void monuments(Plan p, Random rnd, int cx, int cy, int cz) {
        // Océan (nord-ouest) : obélisque de prismarine et petit trident.
        int ox = cx - 27, oz = cz - 10;
        for (int y = cy + 1; y <= cy + 2; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) p.set(ox + dx, y, oz + dz, Blocks.DARK_PRISMARINE);
        for (int y = cy + 3; y <= cy + 13; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            p.set(ox + dx, y, oz + dz, (dx == 0 && dz == 0 && y % 3 == 0) ? Blocks.SEA_LANTERN : Blocks.PRISMARINE);
        for (int y = cy + 14; y <= cy + 18; y++) p.set(ox, y, oz, Blocks.GOLD_BLOCK);
        p.set(ox - 1, cy + 17, oz, Blocks.GOLD_BLOCK);
        p.set(ox + 1, cy + 17, oz, Blocks.GOLD_BLOCK);
        p.set(ox - 1, cy + 18, oz, Blocks.GOLD_BLOCK);
        p.set(ox + 1, cy + 18, oz, Blocks.GOLD_BLOCK);
        for (int i = 0; i < 18; i++) {
            double a = i * 0.7;
            p.set(ox + (int) Math.round(Math.cos(a) * 3), cy + 1 + i / 2, oz + (int) Math.round(Math.sin(a) * 3), Blocks.BLUE_ICE);
        }

        // Cosmos (nord-est) : flèche de cristal d'améthyste.
        int vx = cx + 27, vz = cz - 10;
        for (int k = 0; k < 16; k++) {
            int r = Math.max(0, 3 - k / 4);
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++)
                if (dx * dx + dz * dz <= r * r + 1) p.set(vx + dx, cy + 1 + k, vz + dz, k % 4 == 3 ? Blocks.PURPLE_STAINED_GLASS : Blocks.AMETHYST_BLOCK);
        }
        p.set(vx, cy + 17, vz, Blocks.CRYING_OBSIDIAN);
        p.detail(vx, cy + 18, vz, Blocks.END_ROD);

        // Ciel (sud-ouest) : tour de l'orage surmontée d'un paratonnerre.
        int sx = cx - 26, sz = cz + 14;
        for (int y = cy + 1; y <= cy + 14; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            p.set(sx + dx, y, sz + dz, (y % 4 == 0) ? Blocks.YELLOW_CONCRETE : Blocks.SMOOTH_QUARTZ);
        p.set(sx, cy + 15, sz, Blocks.GOLD_BLOCK);
        p.detail(sx, cy + 16, sz, Blocks.LIGHTNING_ROD);

        // Jardin (sud-est) : arbres et fleurs.
        tree(p, cx + 24, cy + 1, cz + 14, 6);
        tree(p, cx + 30, cy + 1, cz + 6, 5);
        tree(p, cx - 32, cy + 1, cz + 2, 5);
        tree(p, cx + 14, cy + 1, cz + 28, 5);
        tree(p, cx - 14, cy + 1, cz + 28, 5);
        for (int i = 0; i < 40; i++) {
            int dx = rnd.nextInt(2 * ISLAND_R) - ISLAND_R, dz = rnd.nextInt(2 * ISLAND_R) - ISLAND_R;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < PLAZA_R + 2 || d > ISLAND_R - 3 || Math.abs(dx) < 6 || p.has(cx + dx, cy + 1, cz + dz)) continue;
            p.detail(cx + dx, cy + 1, cz + dz, switch (rnd.nextInt(4)) {
                case 0 -> Blocks.ALLIUM;
                case 1 -> Blocks.CORNFLOWER;
                case 2 -> Blocks.LILY_OF_THE_VALLEY;
                default -> Blocks.SHORT_GRASS;
            });
        }
    }

    private static void tree(Plan p, int x, int y0, int z, int h) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState flowering = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = y0; y < y0 + h; y++) p.set(x, y, z, Blocks.OAK_LOG);
        for (int dy = -2; dy <= 2; dy++) {
            int r = dy == 2 ? 1 : (dy == -2 ? 2 : 3);
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                int yy = y0 + h - 1 + dy;
                if (dx == 0 && dz == 0 && dy <= 0) continue;
                p.detail(x + dx, yy, z + dz, ((dx + dz + dy) % 3 == 0) ? flowering : leaves);
            }
        }
    }

    // ------------------------------------------------------------------ Pont et arène

    private static void bridge(Plan p, int x1, int x2, int cy, int cz) {
        for (int x = x1; x <= x2; x++) {
            for (int dz = -3; dz <= 3; dz++) {
                int z = cz + dz;
                for (int y = cy + 1; y <= cy + 5; y++) p.clear(x, y, z);
                p.set(x, cy, z, Math.abs(dz) <= 1 ? ((x % 4 < 2) ? Blocks.QUARTZ_BLOCK : Blocks.PURPLE_CONCRETE)
                        : Math.abs(dz) == 2 ? Blocks.GOLD_BLOCK : Blocks.POLISHED_BLACKSTONE_BRICKS);
                p.set(x, cy - 1, z, Blocks.STONE_BRICKS);
            }
            p.detail(x, cy + 1, cz - 3, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            p.detail(x, cy + 1, cz + 3, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            if (x % 6 == 0) {
                for (int sz : new int[]{-3, 3}) {
                    p.set(x, cy + 1, cz + sz, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.set(x, cy + 2, cz + sz, Blocks.GOLD_BLOCK);
                    p.detail(x, cy + 3, cz + sz, Blocks.LANTERN);
                }
                // Arche sous le pont.
                p.set(x, cy - 2, cz, Blocks.STONE_BRICKS);
                p.set(x, cy - 3, cz, Blocks.STONE_BRICKS);
            }
        }
    }

    private static void arena(Plan p, int ax, int cy, int cz) {
        int r = ARENA_R;
        for (int dx = -r - 5; dx <= r + 5; dx++) {
            for (int dz = -r - 5; dz <= r + 5; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = ax + dx, z = cz + dz;
                if (d <= r + 0.5) {
                    Block b = d < 2.5 ? Blocks.GOLD_BLOCK
                            : Math.abs(d - 9) < 0.55 ? Blocks.PURPLE_CONCRETE
                            : (Math.abs(dz) <= 1 && Math.abs(Math.abs(dx) - 6) <= 1) ? Blocks.RED_CONCRETE
                            : Blocks.SMOOTH_STONE;
                    p.set(x, cy, z, b);
                    for (int y = cy + 1; y <= cy + 20; y++) p.clear(x, y, z);
                } else if (d <= r + 1.5) {
                    // Mur de l'arène (fermé : les combattants sont téléportés à l'intérieur).
                    for (int y = cy; y <= cy + 4; y++) p.set(x, y, z, y == cy + 4 ? Blocks.GOLD_BLOCK : Blocks.STONE_BRICKS);
                    p.set(x, cy + 5, z, Blocks.GLASS);
                    p.set(x, cy + 6, z, Blocks.GLASS);
                } else if (d <= r + 5.5) {
                    // Gradins pour les spectateurs.
                    int step = (int) Math.round(d - r - 1.5);
                    for (int y = cy; y <= cy + step; y++) p.set(x, y, z, y == cy + step ? Blocks.SMOOTH_QUARTZ : Blocks.STONE_BRICKS);
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            p.set(ax + (int) Math.round(Math.cos(a) * (r - 1)), cy, cz + (int) Math.round(Math.sin(a) * (r - 1)), Blocks.SEA_LANTERN);
            int px = ax + (int) Math.round(Math.cos(a) * (r + 6)), pz = cz + (int) Math.round(Math.sin(a) * (r + 6));
            pillar(p, px, cy + 1, pz, 8);
            p.detail(px, cy + 10, pz, Blocks.LANTERN);
        }
    }

    // ------------------------------------------------------------------ Petites îles

    private static void miniIslands(Plan p, Random rnd, int cx, int cy, int cz) {
        int[][] spots = {{-60, -40}, {-70, 20}, {-35, 65}, {40, 60}, {55, -55}, {-10, -75}, {150, 30}, {140, -35}};
        for (int[] s : spots) {
            int r = 4 + rnd.nextInt(4);
            int y = cy - 12 + rnd.nextInt(30);
            int x = cx + s[0], z = cz + s[1];
            island(p, rnd, x, y, z, r, r + 4);
            if (rnd.nextBoolean()) tree(p, x, y + 1, z, 4 + rnd.nextInt(2));
            else {
                p.set(x, y + 1, z, Blocks.AMETHYST_BLOCK);
                p.detail(x, y + 2, z, Blocks.AMETHYST_CLUSTER);
            }
        }
    }

    // ------------------------------------------------------------------ Ancienne version

    /** Démonte le Sanctuaire v1 (au sol) : il redevient de l'herbe. */
    private static void demolishV1(ServerLevel l, Settings s) {
        int hx = (int) Math.floor(s.hubX), hy = (int) Math.floor(s.hubY) - 1, hz = (int) Math.floor(s.hubZ);
        Plan p = new Plan();
        for (int dx = -14; dx <= 14 + 48 + 17; dx++) {
            for (int dz = -17; dz <= 17; dz++) {
                int x = hx + dx, z = hz + dz;
                boolean hub = dx * dx + dz * dz <= 14 * 14;
                int adx = dx - 48;
                boolean arena = adx * adx + dz * dz <= 17 * 17;
                boolean path = dx > 10 && dx < 34 && Math.abs(dz) <= 2;
                if (!hub && !arena && !path) continue;
                for (int y = hy + 1; y <= hy + 9; y++) p.clear(x, y, z);
                p.set(x, hy, z, Blocks.GRASS_BLOCK);
            }
        }
        BlockJobs.submit(l, "Démontage de l'ancien Sanctuaire", p.steps(), null, null);
    }
}
