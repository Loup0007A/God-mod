package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.Settings;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Le Sanctuaire des Dieux, construit dans la dimension vide godmod:spawn.
 * <ul>
 *   <li>Grande île flottante aux falaises de pierre, cascades qui tombent dans le vide, jardins fleuris.</li>
 *   <li>Place en rose des vents (quartz, or, violet), fontaine à deux vasques et trident d'or géant.</li>
 *   <li>Temple du Panthéon : escalier monumental, colonnade, bannières violettes, fronton, tours d'améthyste,
 *       portail-vortex (vers le monde divin) et faisceau violet.</li>
 *   <li>Portails : monde ressource (ouest), boutique et warps (pavillons), arène (pont à l'est).</li>
 *   <li>Îles satellites, panneaux flottants (text displays) pour se repérer.</li>
 * </ul>
 * La construction est étalée sur plusieurs ticks ({@link BlockJobs}) pour ne pas bloquer le serveur.
 */
public final class WorldBuilder {
    public static final int VERSION = 3;
    public static final int CX = 0, CY = 100, CZ = 0;
    public static final int ISLAND_R = 56;
    public static final int PLAZA_R = 24;
    public static final int ARENA_DX = 125;
    public static final int ARENA_ISLAND_R = 32;
    public static final int ARENA_R = 18;
    public static final String LABEL_TAG = "godmod_label";

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

        BlockState get(int x, int y, int z) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = solid.get(p);
            return s != null ? s : detail.get(p);
        }

        Iterator<BlockJobs.Placement> steps() {
            List<BlockJobs.Placement> all = new ArrayList<>(air.size() + solid.size() + detail.size());
            for (var e : air.entrySet()) if (!solid.containsKey(e.getKey()) && !detail.containsKey(e.getKey())) all.add(new BlockJobs.Placement(e.getKey(), e.getValue()));
            for (var e : solid.entrySet()) all.add(new BlockJobs.Placement(e.getKey(), e.getValue()));
            for (var e : detail.entrySet()) all.add(new BlockJobs.Placement(e.getKey(), e.getValue()));
            return all.iterator();
        }
    }

    /** Bloc orienté (escaliers, bannières murales) ; si le bloc n'a pas d'orientation, on garde l'état par défaut. */
    private static BlockState facing(Block b, Direction d) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.setValue(HorizontalDirectionalBlock.FACING, d) : s;
    }

    private static BlockState leaves(Block b) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
    }

    // ------------------------------------------------------------------ Démarrage

    public static void onServerStarted(MinecraftServer server) {
        Settings s = GodData.settings();
        if (s.settingsVersion < 3) {
            // Nouvelle règle : le mot de passe est demandé à chaque connexion.
            s.authSessionHours = 0;
            s.settingsVersion = 3;
            GodData.dirty();
        }
        ServerLevel spawn = server.getLevel(Hub.SPAWN);
        if (spawn == null) {
            GodMod.LOGGER.error("[God Mod] Dimension du Sanctuaire (godmod:spawn) introuvable : vérifiez le datapack du mod.");
            return;
        }
        if (s.structureVersion >= VERSION) {
            registerPortals();
            com.godmod.progress.PointSystem.autoPlace(server);
            GodMod.LOGGER.info("[God Mod] Sanctuaire prêt (dimension godmod:spawn).");
            return;
        }
        build(server, null);
    }

    /** (Re)construit le Sanctuaire dans sa dimension. Le point d'apparition est défini quand tout est posé. */
    public static void build(MinecraftServer server, CommandSourceStack notify) {
        ServerLevel l = server.getLevel(Hub.SPAWN);
        if (l == null) return;
        int cx = CX, cy = CY, cz = CZ;
        Random rnd = new Random(20261018L);

        Plan plan = new Plan();
        island(plan, rnd, cx, cy, cz, ISLAND_R, 34, true);
        plaza(plan, cx, cy, cz);
        fountain(plan, cx, cy, cz);
        temple(plan, cx, cy, cz);
        avenue(plan, cx, cy, cz);
        resourceGate(plan, cx - PLAZA_R - 6, cy, cz);
        pavilion(plan, cx - 31, cy, cz - 22, Blocks.EMERALD_BLOCK, Blocks.LIME_STAINED_GLASS);
        pavilion(plan, cx + 31, cy, cz - 22, Blocks.PRISMARINE_BRICKS, Blocks.CYAN_STAINED_GLASS);
        skyTower(plan, cx - 33, cy, cz + 22);
        oceanStatue(plan, cx + 33, cy, cz + 22);
        gardens(plan, rnd, cx, cy, cz);
        waterfalls(plan, cx, cy, cz, ISLAND_R, new double[]{0.45, 2.6, 3.7, 5.6});

        int ax = cx + ARENA_DX;
        island(plan, rnd, ax, cy, cz, ARENA_ISLAND_R, 24, false);
        arena(plan, ax, cy, cz);
        bridge(plan, cx + ISLAND_R - 4, ax - ARENA_ISLAND_R + 4, cy, cz);
        waterfalls(plan, ax, cy, cz, ARENA_ISLAND_R, new double[]{1.2, 4.4});
        satellites(plan, rnd, cx, cy, cz);

        Settings s = GodData.settings();
        s.hubSet = false;
        GodData.dirty();
        Msg.broadcast(server, "Le Sanctuaire des Dieux est en construction…", ChatFormatting.GRAY);
        BlockJobs.submit(l, "Construction du Sanctuaire", plan.steps(), () -> {
            s.hubSet = true;
            s.hubX = cx + 0.5;
            s.hubY = cy + 1;
            s.hubZ = cz + 17.5;
            s.arenaSet = true;
            s.arenaX = ax + 0.5;
            s.arenaY = cy + 1;
            s.arenaZ = cz + 0.5;
            s.worldStructured = true;
            s.structureVersion = VERSION;
            GodData.dirty();
            registerPortals();
            labels(server, l, cx, cy, cz, ax);
            GodData.settings().customSpawn = false;
            GodData.settings().afkZoneBuilt = false;
            GodData.settings().pointsPlaced = false;
            com.godmod.progress.PointSystem.autoPlace(server);
            AfkSystem.ensureBuilt(server);
            GodData.log("MONDE : Sanctuaire construit (godmod:spawn)");
            Msg.broadcast(server, "✦ Le Sanctuaire des Dieux est prêt ! /spawn pour le découvrir.", ChatFormatting.GOLD);
            if (notify != null) notify.sendSuccess(() -> Msg.prefix().append(Msg.text("Sanctuaire reconstruit.", ChatFormatting.GREEN)), true);
        }, msg -> GodMod.LOGGER.info("[God Mod] {}", msg));
    }

    /** Vide entièrement le Sanctuaire (pour le rebâtir à la main) et laisse une petite plateforme au centre. */
    public static void clear(MinecraftServer server, CommandSourceStack notify) {
        ServerLevel l = server.getLevel(Hub.SPAWN);
        if (l == null) return;
        removeLabels(server, l);
        Hub.setPortals(List.of());
        int r = ARENA_DX + ARENA_ISLAND_R + 70;
        BlockJobs.submit(l, "Nettoyage du Sanctuaire", BlockJobs.clearColumns(l, -r, -r, r, r, l.getMinY(), l.getMaxY(), 0), () -> {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) l.setBlockAndUpdate(new BlockPos(dx, CY, dz), Blocks.SMOOTH_QUARTZ.defaultBlockState());
            Settings s = GodData.settings();
            s.hubSet = true;
            s.hubX = 0.5;
            s.hubY = CY + 1;
            s.hubZ = 0.5;
            s.structureVersion = VERSION; // ne pas reconstruire tout seul au prochain démarrage
            s.customSpawn = true;
            s.afkZoneBuilt = false;
            com.godmod.progress.PointSystem.clearIn(Hub.SPAWN);
            GodData.dirty();
            if (notify != null) notify.sendSuccess(() -> Msg.prefix().append(Msg.text(
                    "Sanctuaire vidé. Construisez avec /godadmin spawn edition, puis /godadmin spawn definir.", ChatFormatting.GREEN)), true);
        }, msg -> GodMod.LOGGER.info("[God Mod] {}", msg));
    }

    /** Zones des portails du Sanctuaire (recalculées à chaque démarrage). */
    public static void registerPortals() {
        if (GodData.settings().structureVersion < VERSION) return;
        int cx = CX, cy = CY, cz = CZ;
        List<Hub.Portal> list = new ArrayList<>();
        int gx = cx - PLAZA_R - 6;
        list.add(new Hub.Portal("Monde ressource", gx - 1, cy + 1, cz - 2, gx, cy + 6, cz + 2, Hub.mineAction()));
        list.add(new Hub.Portal("Monde divin", cx - 4, cy + 4, cz - 47, cx + 4, cy + 12, cz - 46, Hub.divineAction()));
        list.add(new Hub.Portal("Boutique", cx - 32, cy + 1, cz - 23, cx - 30, cy + 3, cz - 21, Hub.shopAction()));
        list.add(new Hub.Portal("Warps", cx + 30, cy + 1, cz - 23, cx + 32, cy + 3, cz - 21, Hub.warpsAction()));
        Hub.setPortals(list);
    }

    // ------------------------------------------------------------------ Île flottante

    private static void island(Plan p, Random rnd, int cx, int cy, int cz, int r, int depth, boolean main) {
        int sectors = 48;
        double[] wobble = new double[sectors];
        for (int i = 0; i < sectors; i++) wobble[i] = rnd.nextDouble() * 4 - 2;
        // Lissage du contour.
        double[] smooth = new double[sectors];
        for (int i = 0; i < sectors; i++) smooth[i] = (wobble[(i + sectors - 1) % sectors] + 2 * wobble[i] + wobble[(i + 1) % sectors]) / 4;
        for (int dx = -r - 3; dx <= r + 3; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double a = Math.atan2(dz, dx);
                int sector = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * sectors) % sectors;
                double edge = r + smooth[sector];
                if (d > edge) continue;
                int x = cx + dx, z = cz + dz;
                for (int y = cy + 1; y <= cy + 45; y++) p.clear(x, y, z);
                double t = 1 - Math.pow(d / edge, 1.35);
                int thick = Math.max(2, (int) Math.round(depth * t + rnd.nextInt(3)));
                // Stalactites sous l'île.
                if (d < edge * 0.75 && rnd.nextInt(18) == 0) thick += 4 + rnd.nextInt(10);
                for (int k = 0; k <= thick; k++) {
                    int y = cy - k;
                    Block b;
                    if (k == 0) b = d > edge - 2.2 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
                    else if (k <= 3) b = Blocks.DIRT;
                    else if (k > thick - 3) b = rnd.nextInt(3) == 0 ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE;
                    else {
                        int roll = rnd.nextInt(40);
                        b = roll == 0 ? Blocks.AMETHYST_BLOCK : roll < 6 ? Blocks.ANDESITE : roll < 10 ? Blocks.TUFF : roll < 12 ? Blocks.CALCITE : Blocks.STONE;
                    }
                    p.set(x, y, z, b);
                }
                // Lueur sous l'île (glowstone discrète).
                if (main && rnd.nextInt(60) == 0) p.set(x, cy - thick - 1, z, Blocks.GLOWSTONE);
            }
        }
    }

    private static void waterfalls(Plan p, int cx, int cy, int cz, int r, double[] angles) {
        for (double a : angles) {
            // On cherche le bord de l'île dans cette direction et on y creuse un petit canal d'eau.
            int lastX = cx, lastZ = cz;
            for (int k = r - 12; k <= r + 4; k++) {
                int x = cx + (int) Math.round(Math.cos(a) * k), z = cz + (int) Math.round(Math.sin(a) * k);
                BlockState top = p.get(x, cy, z);
                if (top == null || top.isAir()) break;
                lastX = x;
                lastZ = z;
            }
            for (int k = 0; k < 4; k++) {
                int x = lastX - (int) Math.round(Math.cos(a) * k), z = lastZ - (int) Math.round(Math.sin(a) * k);
                p.set(x, cy - 1, z, Blocks.STONE);
                p.detail(x, cy, z, Blocks.WATER);
                if (k == 3) p.set(x, cy, z, Blocks.MOSSY_COBBLESTONE);
            }
        }
    }

    // ------------------------------------------------------------------ Place en rose des vents

    private static void plaza(Plan p, int cx, int cy, int cz) {
        for (int dx = -PLAZA_R - 1; dx <= PLAZA_R + 1; dx++) {
            for (int dz = -PLAZA_R - 1; dz <= PLAZA_R + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > PLAZA_R + 0.5) continue;
                double a = Math.atan2(dz, dx);
                double major = Math.pow(Math.abs(Math.cos(2 * a)), 12);
                double minor = Math.pow(Math.abs(Math.sin(2 * a)), 12);
                double star = 8 + 14 * major + 7 * minor;
                Block b;
                if (d > PLAZA_R - 0.5) b = Blocks.POLISHED_BLACKSTONE_BRICKS;
                else if (d > PLAZA_R - 1.5) b = Blocks.GOLD_BLOCK;
                else if (d < 8.5) b = (Math.abs(d - 8) < 0.5) ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK;
                else if (Math.abs(d - star) < 0.7) b = Blocks.GOLD_BLOCK;
                else if (d < star) {
                    // Chaque branche de l'étoile a une face claire et une face sombre (effet de relief).
                    double branch = Math.round(a / (Math.PI / 4)) * (Math.PI / 4);
                    b = Math.sin(a - branch) >= 0 ? Blocks.PURPLE_CONCRETE : Blocks.PURPUR_BLOCK;
                } else if (Math.abs(d - 16) < 0.5) b = Blocks.AMETHYST_BLOCK;
                else b = (Math.floorMod(dx, 2) + Math.floorMod(dz, 2)) % 2 == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.QUARTZ_BLOCK;
                p.set(cx + dx, cy, cz + dz, b);
                p.set(cx + dx, cy - 1, cz + dz, Blocks.STONE_BRICKS);
            }
        }
        // Lampadaires autour de la place (sauf devant les quatre accès).
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            int x = cx + (int) Math.round(Math.cos(a) * (PLAZA_R - 0.5));
            int z = cz + (int) Math.round(Math.sin(a) * (PLAZA_R - 0.5));
            if (Math.abs(x - cx) <= 5 || Math.abs(z - cz) <= 5) continue;
            p.set(x, cy + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            p.set(x, cy + 2, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            p.set(x, cy + 3, z, Blocks.GOLD_BLOCK);
            p.detail(x, cy + 4, z, Blocks.LANTERN);
        }
    }

    // ------------------------------------------------------------------ Fontaine & trident

    private static void fountain(Plan p, int cx, int cy, int cz) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (d <= 7.5 && d > 6.5) {
                    p.set(x, cy, z, Blocks.CHISELED_QUARTZ_BLOCK);
                    p.set(x, cy + 1, z, Blocks.QUARTZ_BLOCK);
                } else if (d <= 6.5) {
                    p.set(x, cy - 1, z, (Math.floorMod(dx + dz, 3) == 0) ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS);
                    p.set(x, cy, z, Blocks.PRISMARINE_BRICKS);
                    if (d > 2.5) p.detail(x, cy + 1, z, Blocks.WATER);
                }
            }
        }
        // Petites colonnes autour de la vasque.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(a) * 7), z = cz + (int) Math.round(Math.sin(a) * 7);
            p.set(x, cy + 2, z, Blocks.QUARTZ_PILLAR);
            p.set(x, cy + 3, z, Blocks.CHISELED_QUARTZ_BLOCK);
            p.detail(x, cy + 4, z, Blocks.SEA_LANTERN);
        }
        // Piédestal et vasque haute (l'eau y reste, retenue par un rebord).
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 2.5) for (int y = cy + 1; y <= cy + 3; y++) p.set(cx + dx, y, cz + dz, d < 1.2 ? Blocks.SEA_LANTERN : Blocks.DARK_PRISMARINE);
            if (d <= 3.5) p.set(cx + dx, cy + 4, cz + dz, Blocks.SMOOTH_QUARTZ);
            if (d <= 3.5 && d > 2.5) p.set(cx + dx, cy + 5, cz + dz, Blocks.QUARTZ_BLOCK);
            else if (d <= 2.5 && d >= 0.9) p.detail(cx + dx, cy + 5, cz + dz, Blocks.WATER);
        }
        // Trident d'or géant.
        for (int y = cy + 5; y <= cy + 21; y++) p.set(cx, y, cz, Blocks.GOLD_BLOCK);
        for (int y = cy + 11; y <= cy + 13; y++) p.set(cx, y, cz, Blocks.DARK_PRISMARINE); // poignée
        for (int dx = -3; dx <= 3; dx++) p.set(cx + dx, cy + 21, cz, Blocks.GOLD_BLOCK);
        for (int y = cy + 22; y <= cy + 25; y++) {
            p.set(cx - 3, y, cz, Blocks.GOLD_BLOCK);
            p.set(cx + 3, y, cz, Blocks.GOLD_BLOCK);
        }
        for (int y = cy + 22; y <= cy + 27; y++) p.set(cx, y, cz, Blocks.GOLD_BLOCK);
        p.set(cx - 2, cy + 23, cz, Blocks.GOLD_BLOCK); // barbes
        p.set(cx + 2, cy + 23, cz, Blocks.GOLD_BLOCK);
        p.set(cx - 1, cy + 25, cz, Blocks.GOLD_BLOCK);
        p.set(cx + 1, cy + 25, cz, Blocks.GOLD_BLOCK);
        p.detail(cx - 3, cy + 26, cz, Blocks.END_ROD);
        p.detail(cx + 3, cy + 26, cz, Blocks.END_ROD);
        p.detail(cx, cy + 28, cz, Blocks.END_ROD);
        // Spirale d'eau gelée et lumineuse qui s'enroule autour du manche.
        for (int i = 0; i < 56; i++) {
            double a = i * 0.42;
            int y = cy + 6 + (i * 14) / 56;
            int x = cx + (int) Math.round(Math.cos(a) * 2.6), z = cz + (int) Math.round(Math.sin(a) * 2.6);
            if (p.has(x, y, z) && !p.get(x, y, z).isAir()) continue;
            p.set(x, y, z, i % 7 == 0 ? Blocks.SEA_LANTERN : Blocks.BLUE_ICE);
        }
    }

    // ------------------------------------------------------------------ Temple du Panthéon (nord)

    private static void temple(Plan p, int cx, int cy, int cz) {
        int w = 17, zf = cz - 29, zb = cz - 47, h = cy + 3;
        // Escalier monumental (3 marches) depuis la place.
        for (int step = 0; step < 3; step++) {
            int z = cz - 25 - step;
            for (int dx = -11; dx <= 11; dx++) {
                for (int y = cy; y < cy + step; y++) p.set(cx + dx, y + 1, z, Blocks.QUARTZ_BLOCK);
                p.set(cx + dx, cy + 1 + step, z, facing(Blocks.QUARTZ_STAIRS, Direction.NORTH));
            }
            p.set(cx - 12, cy + 1 + step, z, Blocks.GOLD_BLOCK);
            p.set(cx + 12, cy + 1 + step, z, Blocks.GOLD_BLOCK);
        }
        // Soubassement.
        for (int dx = -w - 2; dx <= w + 2; dx++) {
            for (int z = zb - 2; z <= zf + 1; z++) {
                int x = cx + dx;
                for (int y = cy - 2; y < h; y++) p.set(x, y, z, Blocks.STONE_BRICKS);
                boolean border = Math.abs(dx) >= w + 1 || z == zf + 1 || z <= zb - 1;
                p.set(x, h, z, border ? Blocks.GOLD_BLOCK : (Math.floorMod(dx + z, 2) == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.PURPUR_BLOCK));
            }
        }
        // Allée centrale dorée dans le temple.
        for (int z = zb; z <= zf; z++) for (int dx = -1; dx <= 1; dx++) p.set(cx + dx, h, z, dx == 0 ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK);
        // Colonnade de façade (et colonnes intérieures), bannières sur la face des colonnes.
        for (int dx = -w + 2; dx <= w - 2; dx += 4) {
            if (Math.abs(dx) < 3) continue;
            column(p, cx + dx, h + 1, zf, 14);
            column(p, cx + dx, h + 1, zf - 9, 14);
            p.detail(cx + dx, h + 11, zf + 1, facing(Blocks.PURPLE_WALL_BANNER, Direction.SOUTH));
            p.detail(cx + dx, h + 11, zf - 10, facing(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
        }
        // Murs latéraux avec vitraux violets.
        for (int z = zb; z <= zf - 1; z++) {
            for (int y = h + 1; y <= h + 15; y++) {
                boolean window = y >= h + 4 && y <= h + 10 && Math.floorMod(z - zb, 4) == 2;
                Block b = window ? Blocks.PURPLE_STAINED_GLASS : (y % 5 == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
                p.set(cx - w, y, z, b);
                p.set(cx + w, y, z, b);
            }
        }
        // Mur du fond et portail-vortex vers le monde divin.
        for (int dx = -w; dx <= w; dx++) for (int y = h + 1; y <= h + 15; y++) p.set(cx + dx, y, zb, Blocks.STONE_BRICKS);
        for (int dx = -8; dx <= 8; dx++) {
            int top = h + 1 + (int) Math.round(13 * Math.sqrt(Math.max(0, 1 - (dx * dx) / 72.0)));
            for (int y = h + 1; y <= top; y++) {
                boolean rim = Math.abs(dx) == 8 || y == top || (Math.abs(dx) == 7 && y > top - 2);
                if (rim) {
                    p.set(cx + dx, y, zb, Blocks.GOLD_BLOCK);
                    continue;
                }
                // Motif en spirale : alternance violet / obsidienne pleureuse / améthyste.
                double vx = dx, vy = y - (h + 7);
                double ang = Math.atan2(vy, vx), rad = Math.sqrt(vx * vx + vy * vy);
                int band = (int) Math.floor(ang * 3 / Math.PI + rad * 0.9);
                Block b = rad < 1.5 ? Blocks.BLACK_STAINED_GLASS : switch (Math.floorMod(band, 3)) {
                    case 0 -> Blocks.PURPLE_STAINED_GLASS;
                    case 1 -> Blocks.CRYING_OBSIDIAN;
                    default -> Blocks.AMETHYST_BLOCK;
                };
                p.set(cx + dx, y, zb, b);
                p.set(cx + dx, y, zb - 1, Blocks.OBSIDIAN);
            }
        }
        // Intérieur dégagé + lumière.
        for (int dx = -w + 1; dx <= w - 1; dx++) for (int z = zb + 1; z <= zf - 1; z++)
            for (int y = h + 1; y <= h + 15; y++) if (!p.has(cx + dx, y, z)) p.clear(cx + dx, y, z);
        for (int dx = -w + 4; dx <= w - 4; dx += 5) for (int z = zb + 3; z <= zf - 3; z += 6) p.set(cx + dx, h + 16, z, Blocks.SEA_LANTERN);
        // Entablement, frise et toit.
        for (int dx = -w - 1; dx <= w + 1; dx++) {
            for (int z = zb - 1; z <= zf + 1; z++) {
                if (p.get(cx + dx, h + 16, z) == null || p.get(cx + dx, h + 16, z).getBlock() != Blocks.SEA_LANTERN)
                    p.set(cx + dx, h + 16, z, z == zf + 1 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.STONE_BRICKS);
                p.set(cx + dx, h + 17, z, (z == zf + 1 || Math.abs(dx) == w + 1) ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ);
            }
        }
        // Fronton avec un petit trident d'or.
        for (int k = 0; k <= 7; k++) {
            for (int dx = -(w + 1 - 2 * k); dx <= w + 1 - 2 * k; dx++) {
                boolean edge = k == 7 || Math.abs(dx) >= w - 2 * k;
                p.set(cx + dx, h + 18 + k, zf + 1, edge ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK);
            }
        }
        for (int y = h + 19; y <= h + 23; y++) p.set(cx, y, zf + 2, Blocks.GOLD_BLOCK);
        p.set(cx - 1, h + 22, zf + 2, Blocks.GOLD_BLOCK);
        p.set(cx + 1, h + 22, zf + 2, Blocks.GOLD_BLOCK);
        p.set(cx - 1, h + 23, zf + 2, Blocks.GOLD_BLOCK);
        p.set(cx + 1, h + 23, zf + 2, Blocks.GOLD_BLOCK);
        // Balise : faisceau violet au-dessus du temple.
        int bz = (zf + zb) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(cx + dx, h + 17, bz + dz, Blocks.GOLD_BLOCK);
        p.set(cx, h + 18, bz, Blocks.BEACON);
        p.set(cx, h + 19, bz, Blocks.PURPLE_STAINED_GLASS);
        // Tours d'angle.
        for (int sx : new int[]{-w - 4, w + 4}) for (int sz : new int[]{zb - 2, zf + 2}) tower(p, cx + sx, cy + 1, sz, 30);
    }

    private static void column(Plan p, int x, int y0, int z, int hgt) {
        p.set(x, y0, z, Blocks.CHISELED_QUARTZ_BLOCK);
        for (int y = y0 + 1; y < y0 + hgt - 1; y++) p.set(x, y, z, Blocks.QUARTZ_PILLAR);
        p.set(x, y0 + hgt - 1, z, Blocks.CHISELED_QUARTZ_BLOCK);
        p.set(x, y0 + hgt, z, Blocks.GOLD_BLOCK);
    }

    private static void tower(Plan p, int x, int y0, int z, int hgt) {
        for (int y = y0; y <= y0 + hgt; y++) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                boolean band = (y - y0) % 7 == 6;
                boolean window = !band && (y - y0) % 7 >= 2 && (y - y0) % 7 <= 4 && (dx == 0 || dz == 0) && (Math.abs(dx) == 2 || Math.abs(dz) == 2);
                p.set(x + dx, y, z + dz, band ? Blocks.GOLD_BLOCK : window ? Blocks.PURPLE_STAINED_GLASS : Blocks.STONE_BRICKS);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) p.set(x + dx, y0 + hgt + 1, z + dz, Blocks.POLISHED_BLACKSTONE_BRICKS);
        for (int k = 0; k < 7; k++) {
            int r = k < 3 ? 1 : 0;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++)
                p.set(x + dx, y0 + hgt + 2 + k, z + dz, k < 5 ? Blocks.AMETHYST_BLOCK : Blocks.PURPLE_STAINED_GLASS);
        }
        p.detail(x, y0 + hgt + 9, z, Blocks.END_ROD);
        for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) p.detail(x + o[0], y0 + hgt + 2, z + o[1], Blocks.LANTERN);
    }

    // ------------------------------------------------------------------ Grande allée (sud) et porte d'entrée

    private static void avenue(Plan p, int cx, int cy, int cz) {
        int end = cz + ISLAND_R + 4;
        for (int z = cz + PLAZA_R; z <= end; z++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx;
                Block b;
                if (Math.abs(dx) == 4) b = Blocks.POLISHED_BLACKSTONE_BRICKS;
                else if (Math.abs(dx) == 3) b = Blocks.GOLD_BLOCK;
                else if (dx == 0) b = Math.floorMod(z, 3) == 0 ? Blocks.AMETHYST_BLOCK : Blocks.PURPLE_CONCRETE;
                else b = Math.floorMod(z + dx, 2) == 0 ? Blocks.QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ;
                p.set(x, cy, z, b);
                p.set(x, cy - 1, z, Blocks.STONE_BRICKS);
                if (z > cz + ISLAND_R - 6) for (int y = cy - 4; y <= cy - 2; y++) p.set(x, y, z, Blocks.STONE_BRICKS);
                for (int y = cy + 1; y <= cy + 8; y++) if (!p.has(x, y, z)) p.clear(x, y, z);
            }
            if (Math.floorMod(z - cz, 5) == 0) {
                for (int sx : new int[]{-5, 5}) {
                    p.set(cx + sx, cy, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.set(cx + sx, cy + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.set(cx + sx, cy + 2, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
                    p.set(cx + sx, cy + 3, z, Blocks.GOLD_BLOCK);
                    p.detail(cx + sx, cy + 4, z, Blocks.LANTERN);
                }
            } else if (Math.floorMod(z - cz, 5) == 2) {
                for (int sx : new int[]{-5, 5}) {
                    p.set(cx + sx, cy, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    for (int y = cy + 1; y <= cy + 4; y++) p.set(cx + sx, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.set(cx + sx, cy + 5, z, Blocks.GOLD_BLOCK);
                    p.detail(cx + sx + (sx < 0 ? 1 : -1), cy + 4, z, facing(Blocks.PURPLE_WALL_BANNER, sx < 0 ? Direction.EAST : Direction.WEST));
                }
            }
        }
        // Porte d'entrée monumentale.
        int gz = end - 2;
        for (int sx : new int[]{-6, 6}) {
            for (int y = cy + 1; y <= cy + 11; y++) for (int dz = -1; dz <= 1; dz++) p.set(cx + sx, y, gz + dz, y % 4 == 0 ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_PILLAR);
            p.detail(cx + sx, cy + 12, gz, Blocks.LANTERN);
        }
        for (int dx = -7; dx <= 7; dx++) {
            int arch = cy + 9 + (int) Math.round(2 * Math.sqrt(Math.max(0, 1 - dx * dx / 49.0)));
            for (int y = arch; y <= cy + 11; y++) p.set(cx + dx, y, gz, Math.abs(dx) <= 1 && y == cy + 11 ? Blocks.GOLD_BLOCK : Blocks.CHISELED_QUARTZ_BLOCK);
        }
        p.detail(cx - 3, cy + 11, gz + 1, facing(Blocks.PURPLE_WALL_BANNER, Direction.SOUTH));
        p.detail(cx + 3, cy + 11, gz + 1, facing(Blocks.PURPLE_WALL_BANNER, Direction.SOUTH));
    }

    // ------------------------------------------------------------------ Portail du monde ressource (ouest)

    private static void resourceGate(Plan p, int gx, int cy, int cz) {
        // Chemin depuis la place.
        for (int x = gx + 1; x <= gx + 7; x++) for (int dz = -3; dz <= 3; dz++)
            p.set(x, cy, cz + dz, Math.abs(dz) == 3 ? Blocks.MOSSY_STONE_BRICKS : (Math.abs(dz) == 2 ? Blocks.GOLD_BLOCK : Blocks.STONE_BRICKS));
        // Arche de pierre moussue et d'émeraude.
        for (int dz = -4; dz <= 4; dz++) {
            int top = cy + 7 + (int) Math.round(2 * Math.sqrt(Math.max(0, 1 - dz * dz / 16.0)));
            for (int y = cy + 1; y <= top + 1; y++) {
                boolean frame = Math.abs(dz) >= 3 || y >= top;
                if (frame) p.set(gx, y, cz + dz, (y + dz) % 3 == 0 ? Blocks.EMERALD_BLOCK : Blocks.MOSSY_STONE_BRICKS);
                else p.set(gx - 1, y, cz + dz, Blocks.LIME_STAINED_GLASS);
            }
        }
        for (int dz = -2; dz <= 2; dz++) p.set(gx - 1, cy, cz + dz, Blocks.EMERALD_BLOCK);
        for (int dz = -2; dz <= 2; dz++) p.set(gx - 2, cy + 1, cz + dz, Blocks.MOSSY_STONE_BRICKS);
        // Végétation autour.
        p.detail(gx + 1, cy + 1, cz - 4, Blocks.FLOWERING_AZALEA);
        p.detail(gx + 1, cy + 1, cz + 4, Blocks.FLOWERING_AZALEA);
        p.detail(gx, cy + 10, cz, Blocks.LANTERN);
    }

    /** Petit pavillon à coupole dorée, au centre duquel se trouve un portail (boutique / warps). */
    private static void pavilion(Plan p, int x, int cy, int z, Block core, Block glass) {
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 4.5) continue;
            p.set(x + dx, cy, z + dz, d > 3.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS : (d < 1.5 ? core : Blocks.SMOOTH_QUARTZ));
            for (int y = cy + 1; y <= cy + 9; y++) if (!p.has(x + dx, y, z + dz)) p.clear(x + dx, y, z + dz);
        }
        for (int[] o : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            for (int y = cy + 1; y <= cy + 5; y++) p.set(x + o[0], y, z + o[1], Blocks.QUARTZ_PILLAR);
            p.set(x + o[0], cy + 6, z + o[1], Blocks.GOLD_BLOCK);
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 3.6) p.set(x + dx, cy + 6, z + dz, d > 2.6 ? Blocks.GOLD_BLOCK : glass);
            if (d <= 2.4) p.set(x + dx, cy + 7, z + dz, d > 1.4 ? Blocks.GOLD_BLOCK : glass);
        }
        p.set(x, cy + 8, z, Blocks.GOLD_BLOCK);
        p.detail(x, cy + 9, z, Blocks.END_ROD);
        p.set(x, cy + 6, z, Blocks.SEA_LANTERN);
    }

    // ------------------------------------------------------------------ Monuments

    /** Tour du Ciel : flèche blanche et or, coiffée d'un paratonnerre. */
    private static void skyTower(Plan p, int x, int cy, int z) {
        for (int y = cy + 1; y <= cy + 22; y++) {
            int r = y < cy + 4 ? 3 : (y < cy + 16 ? 2 : 1);
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                p.set(x + dx, y, z + dz, (y - cy) % 5 == 0 ? Blocks.GOLD_BLOCK : (r == 2 && (dx == 0 || dz == 0) && (y - cy) % 5 == 2 ? Blocks.YELLOW_STAINED_GLASS : Blocks.SMOOTH_QUARTZ));
            }
        }
        p.set(x, cy + 23, z, Blocks.GOLD_BLOCK);
        p.detail(x, cy + 24, z, Blocks.LIGHTNING_ROD);
    }

    /** Statue de l'Océan : une vague de glace bleue qui se brise autour d'un trident. */
    private static void oceanStatue(Plan p, int x, int cy, int z) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            if (dx * dx + dz * dz <= 10) p.set(x + dx, cy + 1, z + dz, Blocks.DARK_PRISMARINE);
        for (int i = 0; i < 70; i++) {
            double t = i / 70.0;
            double a = t * Math.PI * 1.6;
            int r = (int) Math.round(4 - 2.5 * t);
            int y = cy + 2 + (int) Math.round(10 * Math.sin(t * Math.PI * 0.9));
            for (int k = 0; k <= 1; k++) {
                int bx = x + (int) Math.round(Math.cos(a) * r), bz = z + (int) Math.round(Math.sin(a) * r);
                p.set(bx, y - k, bz, i % 9 == 0 ? Blocks.SEA_LANTERN : (k == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE));
            }
        }
        for (int y = cy + 2; y <= cy + 14; y++) p.set(x, y, z, Blocks.GOLD_BLOCK);
        for (int dx = -2; dx <= 2; dx++) p.set(x + dx, cy + 14, z, Blocks.GOLD_BLOCK);
        p.set(x - 2, cy + 15, z, Blocks.GOLD_BLOCK);
        p.set(x + 2, cy + 15, z, Blocks.GOLD_BLOCK);
        p.set(x, cy + 15, z, Blocks.GOLD_BLOCK);
        p.set(x, cy + 16, z, Blocks.GOLD_BLOCK);
        p.detail(x, cy + 17, z, Blocks.END_ROD);
    }

    private static void gardens(Plan p, Random rnd, int cx, int cy, int cz) {
        int[][] trees = {{-20, 34}, {20, 34}, {-40, 6}, {40, 8}, {-12, 44}, {12, 44}, {-44, -8}, {44, -6}, {-26, -38}, {26, -38}};
        for (int i = 0; i < trees.length; i++) {
            int[] t = trees[i];
            if (i % 2 == 0) tree(p, cx + t[0], cy + 1, cz + t[1], 5 + rnd.nextInt(2), Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES);
            else tree(p, cx + t[0], cy + 1, cz + t[1], 5 + rnd.nextInt(2), Blocks.OAK_LOG, Blocks.FLOWERING_AZALEA_LEAVES);
        }
        Block[] flowers = {Blocks.ALLIUM, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_TULIP, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.OXEYE_DAISY};
        for (int i = 0; i < 220; i++) {
            int dx = rnd.nextInt(2 * ISLAND_R) - ISLAND_R, dz = rnd.nextInt(2 * ISLAND_R) - ISLAND_R;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < PLAZA_R + 2 || d > ISLAND_R - 3) continue;
            BlockState ground = p.get(cx + dx, cy, cz + dz);
            if (ground == null || ground.getBlock() != Blocks.GRASS_BLOCK || p.has(cx + dx, cy + 1, cz + dz)) continue;
            p.detail(cx + dx, cy + 1, cz + dz, flowers[rnd.nextInt(flowers.length)]);
        }
    }

    private static void tree(Plan p, int x, int y0, int z, int h, Block log, Block leaf) {
        BlockState l = leaves(leaf);
        for (int y = y0; y < y0 + h; y++) p.set(x, y, z, log);
        for (int dy = -2; dy <= 2; dy++) {
            int r = dy == 2 ? 1 : (dy == -2 ? 2 : 3);
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                if (dx == 0 && dz == 0 && dy <= 0) continue;
                if (!p.has(x + dx, y0 + h - 1 + dy, z + dz)) p.detail(x + dx, y0 + h - 1 + dy, z + dz, l);
            }
        }
    }

    // ------------------------------------------------------------------ Pont et arène

    private static void bridge(Plan p, int x1, int x2, int cy, int cz) {
        for (int x = x1; x <= x2; x++) {
            for (int dz = -3; dz <= 3; dz++) {
                int z = cz + dz;
                for (int y = cy + 1; y <= cy + 6; y++) if (!p.has(x, y, z)) p.clear(x, y, z);
                p.set(x, cy, z, Math.abs(dz) <= 1 ? ((x % 4 < 2) ? Blocks.QUARTZ_BLOCK : Blocks.PURPLE_CONCRETE)
                        : Math.abs(dz) == 2 ? Blocks.GOLD_BLOCK : Blocks.POLISHED_BLACKSTONE_BRICKS);
                p.set(x, cy - 1, z, Blocks.STONE_BRICKS);
            }
            p.set(x, cy + 1, cz - 3, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            p.set(x, cy + 1, cz + 3, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            if (Math.floorMod(x, 8) == 0) {
                for (int sz : new int[]{-3, 3}) {
                    p.set(x, cy + 1, cz + sz, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    p.set(x, cy + 2, cz + sz, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
                    p.set(x, cy + 3, cz + sz, Blocks.GOLD_BLOCK);
                    p.detail(x, cy + 4, cz + sz, Blocks.LANTERN);
                }
                // Arche sous le pont.
                for (int y = cy - 6; y <= cy - 2; y++) for (int dz = -3; dz <= 3; dz += 6) p.set(x, y, cz + dz, Blocks.STONE_BRICKS);
            }
            int mod = Math.floorMod(x, 8);
            if (mod >= 1 && mod <= 7) {
                int sag = (int) Math.round(2.5 * Math.sin(mod / 8.0 * Math.PI));
                for (int dz = -3; dz <= 3; dz += 6) p.set(x, cy - 2 - (2 - sag), cz + dz, Blocks.STONE_BRICKS);
            }
        }
    }

    private static void arena(Plan p, int ax, int cy, int cz) {
        int r = ARENA_R;
        for (int dx = -r - 7; dx <= r + 7; dx++) {
            for (int dz = -r - 7; dz <= r + 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = ax + dx, z = cz + dz;
                if (d <= r + 0.5) {
                    Block b = d < 2.5 ? Blocks.GOLD_BLOCK
                            : Math.abs(d - 10) < 0.55 ? Blocks.PURPLE_CONCRETE
                            : (Math.abs(dz) <= 1 && Math.abs(Math.abs(dx) - 6) <= 1) ? Blocks.RED_CONCRETE
                            : ((Math.floorMod(dx, 2) + Math.floorMod(dz, 2)) % 2 == 0 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE);
                    p.set(x, cy, z, b);
                    for (int y = cy + 1; y <= cy + 22; y++) p.clear(x, y, z);
                } else if (d <= r + 1.5) {
                    // Mur fermé : les combattants sont téléportés dans l'arène.
                    for (int y = cy; y <= cy + 4; y++) p.set(x, y, z, y == cy + 4 ? Blocks.GOLD_BLOCK : Blocks.STONE_BRICKS);
                    p.set(x, cy + 5, z, Blocks.GLASS);
                    p.set(x, cy + 6, z, Blocks.GLASS);
                } else if (d <= r + 6.5) {
                    // Gradins.
                    int step = Math.min(5, (int) Math.round(d - r - 1.5));
                    for (int y = cy; y <= cy + step; y++) p.set(x, y, z, y == cy + step ? Blocks.SMOOTH_QUARTZ : Blocks.STONE_BRICKS);
                    for (int y = cy + step + 1; y <= cy + 12; y++) if (!p.has(x, y, z)) p.clear(x, y, z);
                }
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            p.set(ax + (int) Math.round(Math.cos(a) * (r - 1)), cy, cz + (int) Math.round(Math.sin(a) * (r - 1)), Blocks.SEA_LANTERN);
            if (i % 2 == 0) {
                int px = ax + (int) Math.round(Math.cos(a) * (r + 8)), pz = cz + (int) Math.round(Math.sin(a) * (r + 8));
                if (Math.abs(pz - cz) <= 4 && px < ax) continue; // laisser l'entrée du pont libre
                column(p, px, cy + 1, pz, 10);
                p.detail(px, cy + 12, pz, Blocks.LANTERN);
            }
        }
    }

    // ------------------------------------------------------------------ Îles satellites

    private static void satellites(Plan p, Random rnd, int cx, int cy, int cz) {
        int[][] spots = {{-80, -50}, {-95, 25}, {-45, 85}, {50, 80}, {70, -75}, {-15, -95}, {185, 40}, {175, -45}, {-110, -15}, {20, 110}};
        for (int i = 0; i < spots.length; i++) {
            int[] s = spots[i];
            int r = 5 + rnd.nextInt(5);
            int y = cy - 18 + rnd.nextInt(40);
            int x = cx + s[0], z = cz + s[1];
            island(p, rnd, x, y, z, r, r + 6, false);
            switch (i % 4) {
                case 0 -> tree(p, x, y + 1, z, 5, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES);
                case 1 -> {
                    for (int k = 1; k <= 3; k++) p.set(x, y + k, z, Blocks.AMETHYST_BLOCK);
                    p.detail(x, y + 4, z, Blocks.AMETHYST_CLUSTER);
                    p.set(x + 1, y + 1, z, Blocks.AMETHYST_BLOCK);
                    p.set(x - 1, y + 1, z + 1, Blocks.AMETHYST_BLOCK);
                }
                case 2 -> {
                    // Ruine : deux colonnes brisées.
                    column(p, x - 2, y + 1, z, 4 + rnd.nextInt(3));
                    column(p, x + 2, y + 1, z, 2 + rnd.nextInt(3));
                }
                default -> tree(p, x, y + 1, z, 4, Blocks.OAK_LOG, Blocks.FLOWERING_AZALEA_LEAVES);
            }
            if (i % 3 == 0) waterfalls(p, x, y, z, r, new double[]{rnd.nextDouble() * Math.PI * 2});
        }
    }

    // ------------------------------------------------------------------ Panneaux flottants

    private static void removeLabels(MinecraftServer server, ServerLevel l) {
        run(server, l, "kill @e[type=minecraft:text_display,tag=" + LABEL_TAG + "]");
    }

    private static void labels(MinecraftServer server, ServerLevel l, int cx, int cy, int cz, int ax) {
        removeLabels(server, l);
        label(server, l, cx + 0.5, cy + 31, cz + 0.5, "✦ SANCTUAIRE DES DIEUX ✦", "gold", 2.5);
        label(server, l, cx + 0.5, cy + 15, cz - 46.5 + 2, "✦ Monde divin", "light_purple", 1.6);
        label(server, l, cx + 0.5, cy + 13.6, cz - 46.5 + 2, "Franchissez le vortex (réservé aux dieux)", "gray", 1.0);
        label(server, l, cx - PLAZA_R - 5.5, cy + 11.5, cz + 0.5, "⛏ Monde ressource", "green", 1.6);
        label(server, l, cx - 30.5, cy + 10.5, cz - 21.5, "✦ Boutique", "yellow", 1.4);
        label(server, l, cx + 31.5, cy + 10.5, cz - 21.5, "✦ Warps des joueurs", "aqua", 1.4);
        label(server, l, cx + ISLAND_R + 2.5, cy + 6, cz + 0.5, "⚔ Arène des tournois →", "red", 1.4);
        label(server, l, ax + 0.5, cy + 16, cz + 0.5, "⚔ ARÈNE ⚔", "red", 2.2);
        label(server, l, cx + 0.5, cy + 6, cz + 20.5, "/dieu : ouvrir le Panthéon", "yellow", 1.0);
    }

    public static void label(MinecraftServer server, ServerLevel l, double x, double y, double z, String text, String color, double scale) {
        String safe = text.replace("\\", "").replace("\"", "'");
        String cmd = String.format(java.util.Locale.ROOT,
                "summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"%s\"],billboard:\"center\",shadow:1b,see_through:0b,"
                        + "background:1073741824,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.1ff,%.1ff,%.1ff]},"
                        + "text:{text:\"%s\",color:\"%s\",bold:true}}",
                x, y, z, LABEL_TAG, scale, scale, scale, safe, color);
        run(server, l, cmd);
    }

    public static void run(MinecraftServer server, ServerLevel l, String cmd) {
        try {
            CommandSourceStack src = server.createCommandSourceStack().withLevel(l).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, cmd);
        } catch (Exception e) {
            GodMod.LOGGER.warn("[God Mod] Commande de décor impossible : {}", cmd);
        }
    }
}
