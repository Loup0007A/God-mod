package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.data.Warp;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le monde divin : une dimension vide (godmod:divine) découpée en parcelles, une par dieu.
 * <ul>
 *   <li>Un mortel n'a pas de monde divin : il le reçoit en devenant Dieu.</li>
 *   <li>Chaque parcelle a sa propre bordure (world border) visible, envoyée au joueur qui s'y trouve.</li>
 *   <li>Dépasser la bordure renvoie à la dernière position valide ; seule une chute dans le vide ramène au centre.</li>
 *   <li>Les pistons ne peuvent pas pousser de blocs au-delà de la bordure.</li>
 * </ul>
 */
public final class DivineWorld {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, GodMod.id("divine"));
    public static final int SPACING = 1024;
    public static final int GRID = 64;
    public static final int CORE_Y = 64;
    /** En dessous de cette hauteur, le joueur est considéré comme tombé dans le vide. */
    public static final int VOID_Y = 0;

    /** Dernière position valide de chaque joueur dans le monde divin. */
    private static final Map<UUID, double[]> LAST_VALID = new HashMap<>();
    /** Bordure actuellement affichée chez chaque joueur ("uuidPropriétaire:rayon"). */
    private static final Map<UUID, String> SENT_BORDER = new HashMap<>();

    private DivineWorld() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    public static boolean isDivine(Level level) {
        return level.dimension() == DIMENSION;
    }

    /** Un joueur possède un monde divin dès qu'il est Dieu (il le garde en données s'il chute, mais n'y a plus accès). */
    public static boolean hasWorld(PlayerProfile p) {
        return p.tier.isGod();
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
        return insidePlot(owner, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /** La bordure va de core-r à core+r+1 (blocs inclus). */
    public static boolean insidePlot(PlayerProfile owner, double x, double z) {
        BlockPos c = core(owner);
        int r = radius(owner);
        return x >= c.getX() - r && x <= c.getX() + r + 1 && z >= c.getZ() - r && z <= c.getZ() + r + 1;
    }

    // ------------------------------------------------------------------ Permissions de l'île

    /**
     * Permissions d'un joueur sur l'île d'un dieu :
     * propriétaire et seigneurs = tout ; ennemi en guerre = saccage ; sinon permissions accordées
     * au joueur, plus celles du groupe « fidèles » ou « visiteurs ». Un warp public ouvre l'entrée à tous.
     */
    public static java.util.EnumSet<com.godmod.data.IslandPerm> permsOf(PlayerProfile actor, PlayerProfile owner) {
        java.util.EnumSet<com.godmod.data.IslandPerm> set = java.util.EnumSet.noneOf(com.godmod.data.IslandPerm.class);
        if (owner == null) return set;
        if (actor.uuid.equals(owner.uuid)) {
            if (hasWorld(owner)) set.addAll(java.util.EnumSet.allOf(com.godmod.data.IslandPerm.class));
            return set;
        }
        if (GodData.isAbove(actor.uuid, owner) || WarSystem.atWar(actor, owner)) {
            set.addAll(java.util.EnumSet.allOf(com.godmod.data.IslandPerm.class));
            return set;
        }
        java.util.List<String> group = GodData.isAbove(owner.uuid, actor) ? owner.islandFollowerPerms : owner.islandVisitorPerms;
        addAll(set, group);
        if (owner.islandMembers != null) addAll(set, owner.islandMembers.get(actor.uuid));
        if (hasPublicWarp(owner)) set.add(com.godmod.data.IslandPerm.ENTRER);
        // Pouvoir agir implique de pouvoir entrer.
        if (!set.isEmpty()) set.add(com.godmod.data.IslandPerm.ENTRER);
        return set;
    }

    private static void addAll(java.util.EnumSet<com.godmod.data.IslandPerm> set, java.util.List<String> names) {
        if (names == null) return;
        for (String n : names) {
            com.godmod.data.IslandPerm perm = com.godmod.data.IslandPerm.parse(n);
            if (perm != null) set.add(perm);
        }
    }

    public static boolean has(PlayerProfile actor, PlayerProfile owner, com.godmod.data.IslandPerm perm) {
        return permsOf(actor, owner).contains(perm);
    }

    /** Ancienne règle « construire » : casser ET poser. */
    public static boolean canBuild(PlayerProfile actor, PlayerProfile owner) {
        return has(actor, owner, com.godmod.data.IslandPerm.CASSER) && has(actor, owner, com.godmod.data.IslandPerm.POSER);
    }

    public static boolean canEnter(PlayerProfile actor, PlayerProfile owner) {
        return has(actor, owner, com.godmod.data.IslandPerm.ENTRER);
    }

    /** Modifie une permission. cible = uuid d'un joueur, "fideles" ou "visiteurs". */
    public static void setPerm(PlayerProfile owner, String target, com.godmod.data.IslandPerm perm, boolean on) {
        java.util.List<String> list;
        if (target.equals("fideles")) list = owner.islandFollowerPerms;
        else if (target.equals("visiteurs")) list = owner.islandVisitorPerms;
        else list = owner.islandMembers.computeIfAbsent(target, k -> new java.util.ArrayList<>());
        list.remove(perm.name());
        if (on) list.add(perm.name());
        if (!target.equals("fideles") && !target.equals("visiteurs") && list.isEmpty()) owner.islandMembers.remove(target);
        GodData.dirty();
    }

    public static boolean hasPerm(PlayerProfile owner, String target, com.godmod.data.IslandPerm perm) {
        java.util.List<String> list = target.equals("fideles") ? owner.islandFollowerPerms
                : target.equals("visiteurs") ? owner.islandVisitorPerms : owner.islandMembers.get(target);
        return list != null && list.contains(perm.name());
    }

    private static boolean hasPublicWarp(PlayerProfile owner) {
        for (Warp w : GodData.state().warps.values()) {
            if (owner.uuid.equals(w.owner) && DIMENSION.identifier().toString().equals(w.dimension)) return true;
        }
        return false;
    }

    /** Crée le bloc de terre initial si la parcelle est vide. */
    public static void preparePlot(ServerLevel level, PlayerProfile p) {
        BlockPos c = core(p);
        if (!p.plotStarted && level.getBlockState(c).isAir()) {
            level.setBlockAndUpdate(c, Blocks.DIRT.defaultBlockState());
        }
    }

    /** Appelé quand un joueur devient Dieu. */
    public static void grant(ServerPlayer player, PlayerProfile p) {
        boolean fresh = p.plotIndex < 0;
        ensurePlot(p);
        if (fresh) {
            ServerLevel divine = level(player.level().getServer());
            if (divine != null) preparePlot(divine, p);
            Msg.send(player, Msg.text("Un monde divin vous est accordé ! ", net.minecraft.ChatFormatting.GOLD)
                    .append(Msg.button("Y aller", "/monde", "Rejoindre votre monde divin", net.minecraft.ChatFormatting.YELLOW)));
        }
    }

    public static String sendHome(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (!hasWorld(p)) {
            return "Seuls les dieux possèdent un monde divin. Montez jusqu'au palier Dieu pour obtenir le vôtre.";
        }
        sendTo(player, p, true);
        return null;
    }

    public static String visit(ServerPlayer player, PlayerProfile owner) {
        PlayerProfile p = GodData.get(player);
        if (!hasWorld(owner) && !p.uuid.equals(owner.uuid)) return owner.name + " ne possède pas de monde divin.";
        if (!canEnter(p, owner)) return "Vous n'avez pas accès au monde de " + owner.name + ".";
        sendTo(player, owner, false);
        return null;
    }

    private static void sendTo(ServerPlayer player, PlayerProfile owner, boolean home) {
        MinecraftServer server = player.level().getServer();
        ServerLevel divine = level(server);
        if (divine == null) {
            Msg.err(player, "Le monde divin n'est pas chargé (datapack de dimension manquant).");
            return;
        }
        rememberReturn(player);
        preparePlot(divine, owner);
        BlockPos c = core(owner);
        BlockPos top = divine.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c);
        double y = Math.max(top.getY(), c.getY() + 1);
        Teleport.to(player, divine, c.getX() + 0.5, y, c.getZ() + 0.5);
        LAST_VALID.put(player.getUUID(), new double[]{c.getX() + 0.5, y, c.getZ() + 0.5});
        SENT_BORDER.remove(player.getUUID());
        syncBorder(player);
        if (home) {
            Msg.ok(player, "Bienvenue dans votre monde divin. Taille : " + (2 * radius(owner) + 1) + " × " + (2 * radius(owner) + 1) + " blocs."
                    + (owner.plotStarted ? "" : " Votre bloc de terre est indestructible tant que vous n'avez rien posé."));
        } else {
            Msg.ok(player, "Vous visitez le monde de " + owner.name + ".");
        }
    }

    /** Mémorise la position dans le monde principal pour pouvoir y revenir. */
    public static void rememberReturn(ServerPlayer player) {
        if (isDivine(player.level())) return;
        PlayerProfile self = GodData.get(player);
        if (player.level().dimension() != Level.OVERWORLD) return;
        self.hasReturn = true;
        self.retX = player.getX();
        self.retY = player.getY();
        self.retZ = player.getZ();
        GodData.dirty();
    }

    public static void leave(ServerPlayer player) {
        Hub.teleport(player);
    }

    // ------------------------------------------------------------------ Bordure & limites

    /** Appelé chaque tick pour chaque joueur. */
    public static void enforce(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!isDivine(player.level())) {
            LAST_VALID.remove(id);
            SENT_BORDER.remove(id);
            return;
        }
        if (player.isSpectator()) return;
        PlayerProfile p = GodData.get(player);
        double x = player.getX(), z = player.getZ();
        PlayerProfile owner = ownerAt(player.blockPosition());

        // Chute dans le vide : retour au centre de la parcelle (la seule téléportation « au milieu »).
        if (player.getY() < VOID_Y) {
            PlayerProfile target = owner != null && canEnter(p, owner) ? owner : (hasWorld(p) ? p : null);
            if (target == null) {
                leave(player);
                return;
            }
            BlockPos c = core(target);
            ServerLevel l = (ServerLevel) player.level();
            BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c);
            double y = Math.max(top.getY(), c.getY() + 1);
            Teleport.to(player, l, c.getX() + 0.5, y, c.getZ() + 0.5);
            LAST_VALID.put(id, new double[]{c.getX() + 0.5, y, c.getZ() + 0.5});
            Msg.err(player, "Vous êtes tombé dans le vide : retour au centre du monde.");
            return;
        }

        boolean allowed = owner != null && canEnter(p, owner) && insidePlot(owner, x, z);
        if (allowed) {
            LAST_VALID.put(id, new double[]{x, player.getY(), z});
            return;
        }
        if (player.isCreative() && Moderation.roleOf(player).atLeast(com.godmod.data.Role.ADMIN)) return;

        // Au-delà de la bordure : on recule simplement à la dernière position valide.
        double[] last = LAST_VALID.get(id);
        if (last != null) {
            Teleport.to(player, (ServerLevel) player.level(), last[0], last[1], last[2]);
            player.setDeltaMovement(0, 0, 0);
            player.displayClientMessage(Msg.text("Vous avez atteint la limite de ce monde divin.", net.minecraft.ChatFormatting.RED), true);
        } else {
            leave(player);
        }
    }

    /** Envoie au joueur la bordure de la parcelle où il se trouve (si elle a changé). */
    public static void syncBorder(ServerPlayer player) {
        if (!isDivine(player.level())) return;
        PlayerProfile owner = ownerAt(player.blockPosition());
        if (owner == null) return;
        int r = radius(owner);
        String key = owner.uuid + ":" + r;
        if (key.equals(SENT_BORDER.get(player.getUUID()))) return;
        BlockPos c = core(owner);
        WorldBorder border = new WorldBorder();
        border.setCenter(c.getX() + 0.5, c.getZ() + 0.5);
        border.setSize(2 * r + 1);
        player.connection.send(new ClientboundInitializeBorderPacket(border));
        SENT_BORDER.put(player.getUUID(), key);
    }

    public static void onDimensionChange(ServerPlayer player) {
        SENT_BORDER.remove(player.getUUID());
        LAST_VALID.remove(player.getUUID());
    }

    public static void onLogout(ServerPlayer player) {
        SENT_BORDER.remove(player.getUUID());
        LAST_VALID.remove(player.getUUID());
    }

    /**
     * Vrai si ce mouvement de piston ferait sortir (ou entrer) un bloc d'une parcelle :
     * on vérifie le piston, la tête, chaque bloc déplacé et sa destination.
     */
    public static boolean pistonCrossesBorder(Level level, BlockPos piston, Direction dir, boolean extend) {
        PlayerProfile owner = ownerAt(piston);
        if (owner == null) return true;
        List<BlockPos> check = new ArrayList<>();
        check.add(piston.relative(dir));
        check.add(piston.relative(dir, 2));
        PistonStructureResolver resolver = new PistonStructureResolver(level, piston, dir, extend);
        if (resolver.resolve()) {
            Direction move = extend ? dir : dir.getOpposite();
            for (BlockPos p : resolver.getToPush()) {
                check.add(p);
                check.add(p.relative(move));
            }
            check.addAll(resolver.getToDestroy());
        }
        for (BlockPos p : check) {
            if (!insidePlot(owner, p)) return true;
        }
        return false;
    }

    public static String describe(PlayerProfile p) {
        if (!hasWorld(p)) return "Obtenu en devenant Dieu";
        int r = radius(p);
        return "Parcelle n°" + (p.plotIndex + 1) + " — " + (2 * r + 1) + " × " + (2 * r + 1) + " blocs";
    }
}
