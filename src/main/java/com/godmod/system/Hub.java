package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.Role;
import com.godmod.data.Settings;
import com.godmod.menu.Menus;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Le Sanctuaire : il vit dans sa propre dimension vide (godmod:spawn), entièrement protégée.
 * Les admins peuvent la vider ou la reconstruire, et passer en « mode édition » pour la modifier à la main.
 * Contient aussi l'accès au monde ressource (/mine) et les portails du Sanctuaire.
 */
public final class Hub {
    public static final ResourceKey<Level> SPAWN = ResourceKey.create(Registries.DIMENSION, GodMod.id("spawn"));
    /** Monde ressource : un Overworld classique, à part, où l'on mine et récolte. */
    public static final ResourceKey<Level> RESOURCES = ResourceKey.create(Registries.DIMENSION, GodMod.id("ressources"));
    public static final int MINE_MIN = 300, MINE_MAX = 3000;

    /** Admins actuellement autorisés à modifier le Sanctuaire (/godadmin spawn edition). */
    private static final Set<UUID> EDITORS = new HashSet<>();

    /** Portail du Sanctuaire : une zone qui déclenche une action quand un joueur y entre. */
    public record Portal(String name, int x1, int y1, int z1, int x2, int y2, int z2, Consumer<ServerPlayer> action) {
        boolean contains(ServerPlayer p) {
            return p.getX() >= x1 && p.getX() <= x2 + 1 && p.getY() >= y1 && p.getY() <= y2 + 1 && p.getZ() >= z1 && p.getZ() <= z2 + 1;
        }
    }

    private static final List<Portal> PORTALS = new ArrayList<>();
    private static final Map<UUID, Long> PORTAL_COOLDOWN = new HashMap<>();

    private Hub() {
    }

    public static ServerLevel level(MinecraftServer server) {
        ServerLevel l = server.getLevel(SPAWN);
        return l != null ? l : server.overworld();
    }

    public static boolean isSpawn(Level level) {
        return level.dimension() == SPAWN;
    }

    public static boolean ready() {
        return GodData.settings().hubSet;
    }

    /** Envoie le joueur au point d'apparition du Sanctuaire. */
    public static void teleport(ServerPlayer player) {
        Settings s = GodData.settings();
        ServerLevel l = level(player.level().getServer());
        if (!s.hubSet) {
            BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
            Teleport.to(player, l, 0.5, Math.max(top.getY() + 1, 101), 0.5);
            return;
        }
        Teleport.to(player, l, s.hubX, s.hubY, s.hubZ);
        player.setYRot(180F);
        player.setXRot(0F);
    }

    // ------------------------------------------------------------------ Protection

    /** Tout le Sanctuaire est protégé, sauf pour les admins en mode édition. */
    public static boolean isProtected(Level level, BlockPos pos) {
        return isSpawn(level);
    }

    public static boolean canEditHub(ServerPlayer player) {
        return EDITORS.contains(player.getUUID()) && Moderation.roleOf(player).atLeast(Role.ADMIN);
    }

    public static boolean toggleEditor(ServerPlayer player) {
        if (!EDITORS.remove(player.getUUID())) {
            EDITORS.add(player.getUUID());
            return true;
        }
        return false;
    }

    public static void onLogout(ServerPlayer player) {
        EDITORS.remove(player.getUUID());
        PORTAL_COOLDOWN.remove(player.getUUID());
    }

    /** Chute dans le vide du Sanctuaire : retour au point d'apparition, sans dégâts. */
    public static void fallNet(ServerPlayer player) {
        if (!isSpawn(player.level())) return;
        Settings s = GodData.settings();
        double limit = s.hubSet ? s.hubY - 60 : 20;
        if (player.getY() > limit) return;
        teleport(player);
        player.setDeltaMovement(0, 0, 0);
        Msg.info(player, "Les dieux vous rattrapent avant la chute.");
    }

    // ------------------------------------------------------------------ Portails

    public static void setPortals(List<Portal> portals) {
        PORTALS.clear();
        PORTALS.addAll(portals);
    }

    /** Appelé chaque tick pour les joueurs du Sanctuaire. */
    public static void tickPortals(ServerPlayer player) {
        if (PORTALS.isEmpty() || !isSpawn(player.level())) return;
        long now = System.currentTimeMillis();
        Long cd = PORTAL_COOLDOWN.get(player.getUUID());
        if (cd != null && now < cd) return;
        for (Portal p : PORTALS) {
            if (p.contains(player)) {
                PORTAL_COOLDOWN.put(player.getUUID(), now + 3000);
                p.action().accept(player);
                return;
            }
        }
    }

    /** Actions des portails, définies ici pour que le bâtisseur n'ait qu'à placer les zones. */
    public static Consumer<ServerPlayer> mineAction() {
        return sp -> {
            String err = toMine(sp);
            if (err != null) Msg.err(sp, err);
        };
    }

    public static Consumer<ServerPlayer> divineAction() {
        return sp -> {
            String err = DivineWorld.sendHome(sp);
            if (err != null) {
                Msg.err(sp, err);
                pushBack(sp);
            }
        };
    }

    public static Consumer<ServerPlayer> warpsAction() {
        return sp -> {
            pushBack(sp);
            Menus.openWarps(sp, 0);
        };
    }

    public static Consumer<ServerPlayer> shopAction() {
        return sp -> {
            pushBack(sp);
            Menus.openShop(sp);
        };
    }

    public static Consumer<ServerPlayer> arenaAction() {
        return sp -> {
            Settings s = GodData.settings();
            if (!s.arenaSet) return;
            Teleport.to(sp, level(sp.level().getServer()), s.arenaX, s.arenaY + 6, s.arenaZ + 21);
            Msg.info(sp, "Bienvenue dans les tribunes de l'Arène. /tournoi pour participer.");
        };
    }

    /** Recule le joueur d'un pas vers le centre pour qu'il ne reste pas dans le portail. */
    private static void pushBack(ServerPlayer sp) {
        Settings s = GodData.settings();
        double dx = s.hubX - sp.getX(), dz = s.hubZ - sp.getZ();
        double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
        Teleport.to(sp, (ServerLevel) sp.level(), sp.getX() + dx / len * 2.5, sp.getY(), sp.getZ() + dz / len * 2.5);
    }

    // ------------------------------------------------------------------ Monde ressource

    public static ServerLevel resources(MinecraftServer server) {
        return server.getLevel(RESOURCES);
    }

    /** Téléporte à un endroit sûr et aléatoire du monde ressource. */
    public static String toMine(ServerPlayer player) {
        ServerLevel mine = resources(player.level().getServer());
        if (mine == null) return "Le monde ressource n'est pas chargé (datapack manquant).";
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

    public static void warnProtected(ServerPlayer sp) {
        sp.displayClientMessage(Msg.text("Le Sanctuaire est protégé par les dieux.", ChatFormatting.RED), true);
    }
}
