package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Le monde divin : une dimension vide (godmod:divine) découpée en parcelles, une par joueur.
 * Chaque parcelle démarre avec un unique bloc de terre, indestructible tant que rien n'a été posé.
 * Rayon de la parcelle = base + parFidèle × fidèles totaux + 8 × palier (plafonné).
 */
public final class DivineWorld {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, GodMod.id("divine"));
    public static final int SPACING = 1024;
    public static final int GRID = 64;
    public static final int CORE_Y = 64;

    private DivineWorld() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    public static boolean isDivine(Level level) {
        return level.dimension() == DIMENSION;
    }

    public static int radius(PlayerProfile p) {
        Settings s = GodData.settings();
        int r = s.worldBaseRadius + s.worldRadiusPerFollower * GodData.totalFollowers(p.uuid) + 8 * p.tier.ordinal();
        return Math.min(r, s.worldMaxRadius);
    }

    public static void ensurePlot(PlayerProfile p) {
        if (p.plotIndex < 0) {
            p.plotIndex = GodData.state().nextPlotIndex++;
            GodData.dirty();
        }
    }

    public static BlockPos core(PlayerProfile p) {
        ensurePlot(p);
        int gx = p.plotIndex % GRID, gz = p.plotIndex / GRID;
        return new BlockPos(gx * SPACING, CORE_Y, gz * SPACING);
    }

    /** Propriétaire de la parcelle qui contient cette position (ou null). */
    public static PlayerProfile ownerAt(BlockPos pos) {
        int gx = Math.floorDiv(pos.getX() + SPACING / 2, SPACING);
        int gz = Math.floorDiv(pos.getZ() + SPACING / 2, SPACING);
        if (gx < 0 || gz < 0 || gx >= GRID) return null;
        int index = gz * GRID + gx;
        for (PlayerProfile p : GodData.state().players.values()) {
            if (p.plotIndex == index) return p;
        }
        return null;
    }

    public static boolean insidePlot(PlayerProfile owner, BlockPos pos) {
        BlockPos c = core(owner);
        int r = radius(owner);
        return Math.abs(pos.getX() - c.getX()) <= r && Math.abs(pos.getZ() - c.getZ()) <= r;
    }

    /** Peut construire/casser : propriétaire, un de ses seigneurs, ou un ennemi en guerre (saccage). */
    public static boolean canBuild(PlayerProfile actor, PlayerProfile owner) {
        if (owner == null) return false;
        if (actor.uuid.equals(owner.uuid)) return true;
        if (GodData.isAbove(actor.uuid, owner)) return true;
        return WarSystem.atWar(actor, owner);
    }

    /** Peut entrer : comme ci-dessus, plus les fidèles du propriétaire. */
    public static boolean canEnter(PlayerProfile actor, PlayerProfile owner) {
        return canBuild(actor, owner) || GodData.isAbove(owner.uuid, actor);
    }

    /** Crée le bloc de terre initial si la parcelle est vide. */
    public static void preparePlot(ServerLevel level, PlayerProfile p) {
        BlockPos c = core(p);
        if (!p.plotStarted && level.getBlockState(c).isAir()) {
            level.setBlockAndUpdate(c, Blocks.DIRT.defaultBlockState());
        }
    }

    public static void sendHome(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        sendTo(player, p, true);
    }

    public static String visit(ServerPlayer player, PlayerProfile owner) {
        PlayerProfile p = GodData.get(player);
        if (!canEnter(p, owner)) return "Vous n'avez pas accès au monde de " + owner.name + ".";
        sendTo(player, owner, false);
        return null;
    }

    private static void sendTo(ServerPlayer player, PlayerProfile owner, boolean home) {
        MinecraftServer server = player.level().getServer();
        ServerLevel divine = level(server);
        if (divine == null) {
            Msg.err(player, "Le monde divin n'est pas chargé (datapack de dimension manquant ?).");
            return;
        }
        PlayerProfile self = GodData.get(player);
        if (!isDivine(player.level())) {
            self.hasReturn = true;
            self.retX = player.getX();
            self.retY = player.getY();
            self.retZ = player.getZ();
            GodData.dirty();
        }
        preparePlot(divine, owner);
        BlockPos c = core(owner);
        BlockPos top = divine.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c);
        double y = Math.max(top.getY(), c.getY() + 1);
        Teleport.to(player, divine, c.getX() + 0.5, y, c.getZ() + 0.5);
        if (home) {
            Msg.ok(player, "Bienvenue dans votre monde divin. Rayon : " + radius(owner) + " blocs."
                    + (owner.plotStarted ? "" : " Votre bloc de terre est indestructible tant que vous n'avez rien posé."));
        } else {
            Msg.ok(player, "Vous visitez le monde de " + owner.name + ".");
        }
    }

    public static void leave(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        ServerLevel ow = player.level().getServer().overworld();
        if (p.hasReturn) {
            Teleport.to(player, ow, p.retX, p.retY, p.retZ);
        } else {
            BlockPos top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
            Teleport.to(player, ow, 0.5, top.getY() + 1, 0.5);
        }
    }

    /** Appelé chaque seconde : renvoie les joueurs hors de leurs limites ou tombés dans le vide. */
    public static void enforce(ServerPlayer player) {
        if (!isDivine(player.level())) return;
        PlayerProfile p = GodData.get(player);
        BlockPos pos = player.blockPosition();
        PlayerProfile owner = ownerAt(pos);
        boolean ok = owner != null && canEnter(p, owner) && insidePlot(owner, pos);
        if (player.getY() < 0) ok = false;
        if (!ok && !player.isCreative() && !player.isSpectator()) {
            Msg.err(player, "Limite du monde divin atteinte.");
            if (owner != null && canEnter(p, owner) && player.getY() >= 0) {
                BlockPos c = core(owner);
                Teleport.to(player, (ServerLevel) player.level(), c.getX() + 0.5, Math.max(player.getY(), c.getY() + 1), c.getZ() + 0.5);
            } else {
                sendHome(player);
            }
        }
    }

    public static String describe(PlayerProfile p) {
        return "Parcelle n°" + (p.plotIndex + 1) + " — rayon " + radius(p) + " blocs";
    }
}
