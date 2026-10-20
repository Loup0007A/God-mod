package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.Role;
import com.godmod.data.Zone;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zones tracées au bâton (admins) : clic gauche = coin 1, clic droit = coin 2, puis /zone creer &lt;afk|spawn|libre&gt; &lt;nom&gt;.
 * <ul>
 *   <li><b>afk</b> : se tenir dedans compte comme la zone AFK (jetons, pas d'expulsion) ;</li>
 *   <li><b>spawn</b> : protégée comme le Sanctuaire, dans n'importe quel monde ;</li>
 *   <li><b>libre</b> : retire la protection d'une partie du Sanctuaire (construction, coffres…).</li>
 * </ul>
 */
public final class ZoneSystem {
    public static final String WAND_TAG = "godmod_wand";

    private static final class Selection {
        String dim;
        BlockPos a, b;
    }

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    private ZoneSystem() {
    }

    // ------------------------------------------------------------------ Bâton

    public static ItemStack wand() {
        ItemStack stack = new ItemStack(Items.STICK);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(WAND_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Bâton de zone").withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false).withBold(true)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                com.godmod.menu.Page.line("§eClic gauche : coin 1"),
                com.godmod.menu.Page.line("§eClic droit : coin 2"),
                com.godmod.menu.Page.line("§7Puis /zone creer <afk|spawn|libre> <nom>"),
                com.godmod.menu.Page.line("§7/zone supprimer : retirer la zone où vous êtes"))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isWand(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.STICK)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(WAND_TAG, false);
    }

    private static boolean canUse(ServerPlayer p) {
        return Moderation.roleOf(p).atLeast(Role.ADMIN);
    }

    /** Clic avec le bâton sur un bloc. Renvoie vrai si l'action normale doit être annulée. */
    public static boolean click(ServerPlayer p, BlockPos pos, boolean first) {
        if (!isWand(p.getMainHandItem())) return false;
        if (!canUse(p)) {
            p.displayClientMessage(Msg.text("Seuls les admins peuvent tracer des zones.", ChatFormatting.RED), true);
            return true;
        }
        Selection s = SELECTIONS.computeIfAbsent(p.getUUID(), k -> new Selection());
        String dim = p.level().dimension().identifier().toString();
        if (s.dim != null && !s.dim.equals(dim)) {
            s.a = null;
            s.b = null;
        }
        s.dim = dim;
        BlockPos old = first ? s.a : s.b;
        if (pos.equals(old)) return true; // le clic gauche est envoyé plusieurs fois : pas de doublon
        if (first) s.a = pos.immutable();
        else s.b = pos.immutable();
        String vol = s.a != null && s.b != null ? " · " + Msg.num(volume(s)) + " blocs" : "";
        Msg.send(p, Msg.text("Coin " + (first ? 1 : 2) + " : " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + vol, ChatFormatting.GOLD));
        return true;
    }

    private static long volume(Selection s) {
        return (long) (Math.abs(s.a.getX() - s.b.getX()) + 1) * (Math.abs(s.a.getY() - s.b.getY()) + 1) * (Math.abs(s.a.getZ() - s.b.getZ()) + 1);
    }

    /** /zone pos1|pos2 : coin à ses pieds. */
    public static String setCorner(ServerPlayer p, boolean first) {
        Selection s = SELECTIONS.computeIfAbsent(p.getUUID(), k -> new Selection());
        s.dim = p.level().dimension().identifier().toString();
        if (first) s.a = p.blockPosition().immutable();
        else s.b = p.blockPosition().immutable();
        Msg.ok(p, "Coin " + (first ? 1 : 2) + " placé à vos pieds.");
        return null;
    }

    // ------------------------------------------------------------------ Zones

    /** Zone qui lance une commande quand un joueur y entre. */
    public static String createCommand(ServerPlayer p, String name, boolean console, String command, boolean fullHeight) {
        String cmd = command.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) return "Indiquez la commande à lancer.";
        String err = create(p, Zone.Type.COMMANDE, name, fullHeight);
        if (err != null) return err;
        Zone z = GodData.state().zones.get(name.toLowerCase());
        z.command = cmd;
        z.asConsole = console;
        GodData.dirty();
        GodData.log("ZONE : « " + name + " » lance « /" + cmd + " » (" + (console ? "console" : "joueur") + ")");
        Msg.info(p, "Commande : /" + cmd + " — lancée par " + (console ? "la console ({joueur} = pseudo du joueur)" : "le joueur lui-même") + ", à chaque entrée dans la zone.");
        return null;
    }

    public static String create(ServerPlayer p, Zone.Type type, String name, boolean fullHeight) {
        if (!name.matches("[A-Za-z0-9_\\-]{2,24}")) return "Nom invalide : 2 à 24 lettres, chiffres, _ ou -.";
        Selection s = SELECTIONS.get(p.getUUID());
        if (s == null || s.a == null || s.b == null) return "Sélectionnez d'abord deux coins avec le bâton de zone (/zone baton).";
        if (!s.dim.equals(p.level().dimension().identifier().toString())) return "Votre sélection est dans un autre monde.";
        String key = name.toLowerCase();
        Zone z = new Zone();
        z.name = name;
        z.type = type;
        z.dimension = s.dim;
        z.minX = Math.min(s.a.getX(), s.b.getX());
        z.maxX = Math.max(s.a.getX(), s.b.getX());
        z.minZ = Math.min(s.a.getZ(), s.b.getZ());
        z.maxZ = Math.max(s.a.getZ(), s.b.getZ());
        Level l = p.level();
        z.minY = fullHeight ? l.getMinY() : Math.min(s.a.getY(), s.b.getY());
        z.maxY = fullHeight ? l.getMaxY() : Math.max(s.a.getY(), s.b.getY());
        z.createdBy = p.getName().getString();
        boolean replaced = GodData.state().zones.put(key, z) != null;
        GodData.dirty();
        GodData.log("ZONE : " + z.createdBy + " " + (replaced ? "remplace" : "crée") + " la zone " + type.name().toLowerCase() + " « " + name + " » ("
                + z.minX + " " + z.minY + " " + z.minZ + " → " + z.maxX + " " + z.maxY + " " + z.maxZ + ", " + z.dimension + ")");
        Msg.ok(p, "Zone " + label(type) + " « " + name + " » " + (replaced ? "remplacée" : "créée") + " (" + Msg.num(z.volume()) + " blocs).");
        return null;
    }

    public static String delete(ServerPlayer p, String name) {
        Zone z;
        if (name == null) {
            List<Zone> here = at(p.level(), p.blockPosition());
            if (here.isEmpty()) return "Vous n'êtes dans aucune zone. /zone liste pour les voir.";
            z = here.get(0);
        } else {
            z = GodData.state().zones.get(name.toLowerCase());
            if (z == null) return "Zone « " + name + " » inconnue.";
        }
        GodData.state().zones.remove(z.name.toLowerCase());
        GodData.dirty();
        GodData.log("ZONE : " + p.getName().getString() + " supprime la zone « " + z.name + " »");
        Msg.ok(p, "Zone " + label(z.type) + " « " + z.name + " » supprimée.");
        return null;
    }

    public static String label(Zone.Type t) {
        return switch (t) {
            case AFK -> "AFK";
            case SPAWN -> "protégée";
            case LIBRE -> "libre";
            case COMMANDE -> "commande";
        };
    }

    public static List<Zone> at(Level level, BlockPos pos) {
        List<Zone> list = new ArrayList<>();
        if (!GodData.ready()) return list;
        String dim = level.dimension().identifier().toString();
        for (Zone z : GodData.state().zones.values()) if (z.contains(dim, pos.getX(), pos.getY(), pos.getZ())) list.add(z);
        return list;
    }

    public static boolean in(Level level, BlockPos pos, Zone.Type type) {
        if (!GodData.ready() || GodData.state().zones.isEmpty()) return false;
        String dim = level.dimension().identifier().toString();
        for (Zone z : GodData.state().zones.values())
            if (z.type == type && z.contains(dim, pos.getX(), pos.getY(), pos.getZ())) return true;
        return false;
    }

    public static void list(ServerPlayer p) {
        p.sendSystemMessage(Msg.header("Zones"));
        if (GodData.state().zones.isEmpty()) {
            p.sendSystemMessage(Msg.text("Aucune zone. /zone baton puis /zone creer <afk|spawn|libre> <nom>.", ChatFormatting.GRAY));
            return;
        }
        for (Zone z : GodData.state().zones.values()) {
            String dim = z.dimension.substring(z.dimension.indexOf(':') + 1);
            p.sendSystemMessage(Msg.text(z.name, ChatFormatting.YELLOW)
                    .append(Msg.text(" — " + label(z.type) + (z.type == Zone.Type.COMMANDE ? " (/" + z.command + ", " + (z.asConsole ? "console" : "joueur") + ")" : "") + " · " + dim + " · " + z.minX + " " + z.minY + " " + z.minZ + " → " + z.maxX + " " + z.maxY + " " + z.maxZ + " ", ChatFormatting.GRAY))
                    .append(Msg.button("Supprimer", "/zone supprimer " + z.name, "Supprimer cette zone", ChatFormatting.RED)));
        }
    }

    // ------------------------------------------------------------------ Affichage (chaque seconde, bâton en main)

    public static void tickSecond(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isWand(p.getMainHandItem()) || !canUse(p) || !(p.level() instanceof ServerLevel l)) continue;
            String dim = l.dimension().identifier().toString();
            for (Zone z : GodData.state().zones.values()) {
                if (!z.dimension.equals(dim)) continue;
                double cx = (z.minX + z.maxX) / 2.0, cz = (z.minZ + z.maxZ) / 2.0;
                if (Math.abs(cx - p.getX()) > 96 || Math.abs(cz - p.getZ()) > 96) continue;
                ParticleOptions part = switch (z.type) {
                    case AFK -> ParticleTypes.WITCH;
                    case SPAWN -> ParticleTypes.HAPPY_VILLAGER;
                    case LIBRE -> ParticleTypes.FLAME;
                    case COMMANDE -> ParticleTypes.ENCHANT;
                };
                int y1 = Math.max(z.minY, (int) p.getY() - 6), y2 = Math.min(z.maxY + 1, (int) p.getY() + 8);
                outline(p, l, part, z.minX, y1, z.minZ, z.maxX + 1, y2, z.maxZ + 1);
            }
            Selection s = SELECTIONS.get(p.getUUID());
            if (s != null && s.a != null && s.b != null && dim.equals(s.dim)) {
                outline(p, l, ParticleTypes.END_ROD, Math.min(s.a.getX(), s.b.getX()), Math.min(s.a.getY(), s.b.getY()), Math.min(s.a.getZ(), s.b.getZ()),
                        Math.max(s.a.getX(), s.b.getX()) + 1, Math.max(s.a.getY(), s.b.getY()) + 1, Math.max(s.a.getZ(), s.b.getZ()) + 1);
            }
            List<Zone> here = at(l, p.blockPosition());
            if (!here.isEmpty()) {
                StringBuilder b = new StringBuilder("Zone ici : ");
                for (Zone z : here) b.append(z.name).append(" (").append(label(z.type)).append(") ");
                p.displayClientMessage(Msg.text(b.toString().trim(), ChatFormatting.GOLD), true);
            }
        }
    }

    /** Arêtes d'un pavé, visibles seulement par ce joueur (au plus ~240 particules). */
    private static void outline(ServerPlayer p, ServerLevel l, ParticleOptions part, double x1, double y1, double z1, double x2, double y2, double z2) {
        double[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2}, {x1, y2, z1, x2, y2, z1}, {x1, y2, z2, x2, y2, z2},
                {x1, y1, z1, x1, y1, z2}, {x2, y1, z1, x2, y1, z2}, {x1, y2, z1, x1, y2, z2}, {x2, y2, z1, x2, y2, z2},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x1, y1, z2, x1, y2, z2}, {x2, y1, z2, x2, y2, z2}};
        for (double[] e : edges) {
            double len = Math.sqrt(Math.pow(e[3] - e[0], 2) + Math.pow(e[4] - e[1], 2) + Math.pow(e[5] - e[2], 2));
            int n = (int) Math.min(20, Math.max(1, len));
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(part, true, false,
                        e[0] + (e[3] - e[0]) * t, e[1] + (e[4] - e[1]) * t, e[2] + (e[5] - e[2]) * t, 0F, 0F, 0F, 0F, 1));
            }
        }
    }

    // ------------------------------------------------------------------ Zones de commande (chaque tick)

    /** Joueur -> zones de commande où il se trouve déjà (la commande ne part qu'à l'entrée). */
    private static final Map<UUID, java.util.Set<String>> INSIDE = new HashMap<>();

    public static void tick(MinecraftServer server) {
        if (!GodData.ready() || GodData.state().zones.isEmpty()) {
            INSIDE.clear();
            return;
        }
        List<Zone> cmdZones = new ArrayList<>();
        for (Zone z : GodData.state().zones.values()) if (z.type == Zone.Type.COMMANDE && z.command != null) cmdZones.add(z);
        if (cmdZones.isEmpty()) {
            INSIDE.clear();
            return;
        }
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (AuthSystem.isLocked(p) || p.isSpectator() || Trailer.isViewer(p)) continue;
            java.util.Set<String> inside = INSIDE.computeIfAbsent(p.getUUID(), k -> new java.util.HashSet<>());
            String dim = p.level().dimension().identifier().toString();
            BlockPos pos = p.blockPosition();
            for (Zone z : cmdZones) {
                String key = z.name.toLowerCase();
                boolean in = z.contains(dim, pos.getX(), pos.getY(), pos.getZ());
                if (in && inside.add(key)) run(server, p, z);
                else if (!in) inside.remove(key);
            }
        }
    }

    private static void run(MinecraftServer server, ServerPlayer p, Zone z) {
        String cmd = z.command.replace("{joueur}", p.getName().getString()).replace("{player}", p.getName().getString());
        try {
            var src = z.asConsole
                    ? server.createCommandSourceStack().withLevel((ServerLevel) p.level()).withPosition(p.position()).withSuppressedOutput()
                    : p.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(src, cmd);
        } catch (Exception e) {
            com.godmod.GodMod.LOGGER.warn("[God Mod] Zone « {} » : commande impossible ({})", z.name, cmd);
        }
    }

    public static void onLogout(ServerPlayer p) {
        INSIDE.remove(p.getUUID());
        SELECTIONS.remove(p.getUUID());
    }
}
