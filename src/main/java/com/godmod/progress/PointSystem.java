package com.godmod.progress;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.menu.Menus;
import com.godmod.system.Hub;
import com.godmod.system.WorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Points interactifs : un bloc sur lequel on fait clic droit pour ouvrir une caisse ou le tableau des quêtes.
 * Placés automatiquement dans le Sanctuaire du mod, ou par un admin (/godadmin point placer …).
 */
public final class PointSystem {
    private PointSystem() {
    }

    public static String at(Level level, BlockPos pos) {
        return GodData.state().points.get(JobSystem.key(level, pos));
    }

    /** Clic droit sur un point : renvoie vrai si c'en était un (l'action est alors lancée). */
    public static boolean use(ServerPlayer player, BlockPos pos) {
        String type = at(player.level(), pos);
        if (type == null) return false;
        player.level().getServer().execute(() -> open(player, type));
        return true;
    }

    public static void open(ServerPlayer player, String type) {
        if (type.startsWith("crate:")) {
            CrateSystem.Crate c = CrateSystem.Crate.parse(type.substring(6));
            if (c != null) CrateSystem.openPreview(player, c);
        } else if (type.equals("quetes")) {
            Menus.openQuests(player);
        } else if (type.equals("metiers")) {
            Menus.openJobs(player);
        }
    }

    /** Type depuis le nom donné dans la commande (commune, rare, epique, divine, quetes, metiers). */
    public static String parse(String s) {
        CrateSystem.Crate c = CrateSystem.Crate.parse(s);
        if (c != null) return "crate:" + c.name();
        if (s.equalsIgnoreCase("quetes") || s.equalsIgnoreCase("quêtes")) return "quetes";
        if (s.equalsIgnoreCase("metiers") || s.equalsIgnoreCase("métiers")) return "metiers";
        return null;
    }

    public static void set(Level level, BlockPos pos, String type) {
        GodData.state().points.put(JobSystem.key(level, pos), type);
        GodData.dirty();
    }

    public static boolean remove(Level level, BlockPos pos) {
        boolean r = GodData.state().points.remove(JobSystem.key(level, pos)) != null;
        GodData.dirty();
        return r;
    }

    public static void clearIn(ResourceKey<Level> dim) {
        String prefix = dim.identifier() + "|";
        GodData.state().points.keySet().removeIf(k -> k.startsWith(prefix));
        GodData.dirty();
    }

    private static BlockState facing(Block b, Direction d) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.setValue(HorizontalDirectionalBlock.FACING, d) : s;
    }

    /** Pose les quatre caisses et le tableau des quêtes autour du point d'apparition du Sanctuaire. */
    public static void autoPlace(MinecraftServer server) {
        var s = GodData.settings();
        if (s.pointsPlaced || s.customSpawn || s.structureVersion < WorldBuilder.VERSION) return;
        ServerLevel l = server.getLevel(Hub.SPAWN);
        if (l == null) return;
        int cy = WorldBuilder.CY;
        Object[][] crates = {
                {-15, 6, CrateSystem.Crate.COMMUNE, Blocks.CHEST, Blocks.POLISHED_ANDESITE, "white", Direction.EAST},
                {-12, 11, CrateSystem.Crate.RARE, Blocks.BARREL, Blocks.LAPIS_BLOCK, "blue", Direction.EAST},
                {12, 11, CrateSystem.Crate.EPIQUE, Blocks.ENDER_CHEST, Blocks.AMETHYST_BLOCK, "light_purple", Direction.WEST},
                {15, 6, CrateSystem.Crate.DIVINE, Blocks.VAULT, Blocks.GOLD_BLOCK, "gold", Direction.WEST}};
        for (Object[] c : crates) {
            int x = (int) c[0], z = (int) c[1];
            CrateSystem.Crate crate = (CrateSystem.Crate) c[2];
            BlockPos base = new BlockPos(x, cy + 1, z), top = base.above();
            l.setBlockAndUpdate(base, ((Block) c[4]).defaultBlockState());
            l.setBlockAndUpdate(top, facing((Block) c[3], (Direction) c[6]));
            set(l, top, "crate:" + crate.name());
            WorldBuilder.label(server, l, x + 0.5, cy + 3.7, z + 0.5, "Caisse " + crate.label, (String) c[5], 1.2);
            WorldBuilder.label(server, l, x + 0.5, cy + 3.3, z + 0.5, "clic droit", "gray", 0.7);
        }
        BlockPos q = new BlockPos(0, cy + 1, 22);
        l.setBlockAndUpdate(q, facing(Blocks.LECTERN, Direction.NORTH));
        set(l, q, "quetes");
        WorldBuilder.label(server, l, 0.5, cy + 2.9, 22.5, "Quêtes du jour", "yellow", 1.2);
        BlockPos j = new BlockPos(0, cy + 1, 13);
        l.setBlockAndUpdate(j, facing(Blocks.LECTERN, Direction.SOUTH));
        set(l, j, "metiers");
        WorldBuilder.label(server, l, 0.5, cy + 2.9, 13.5, "Métiers", "green", 1.2);
        s.pointsPlaced = true;
        GodData.dirty();
        GodMod.LOGGER.info("[God Mod] Caisses et tableaux posés dans le Sanctuaire.");
    }
}
