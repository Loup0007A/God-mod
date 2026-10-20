package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.data.Warp;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Résidences (/sethome, /home, /delhome, /homes) et retour au dernier endroit (/back).
 * Nombre de résidences : 1 + rang du palier (Mortel 1, Éveillé 2, … Créateur 10), +5 pour l'équipe.
 */
public final class Homes {
    public static final String DEFAULT = "maison";

    private Homes() {
    }

    public static int limit(PlayerProfile p, Role role) {
        return Math.min(10, 1 + p.tier.ordinal()) + (role.atLeast(Role.MODERATEUR) ? 5 : 0);
    }

    private static String key(String name) {
        return name == null || name.isBlank() ? DEFAULT : name.toLowerCase(Locale.ROOT);
    }

    /** Monde d'une position enregistrée, ou null s'il n'est pas accessible. */
    static ServerLevel level(ServerPlayer p, String dim) {
        ServerLevel level = p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
        if (level == null || level.dimension() == Level.NETHER || level.dimension() == Level.END) return null;
        return level;
    }

    /** Refuse l'île d'un autre dieu si l'on n'a pas le droit d'y entrer. */
    static String forbidden(ServerPlayer p, ServerLevel level, double x, double y, double z) {
        if (!DivineWorld.isDivine(level)) return null;
        PlayerProfile owner = DivineWorld.ownerAt(BlockPos.containing(x, y, z));
        if (owner == null) return null;
        PlayerProfile me = GodData.get(p);
        if (owner.uuid.equals(me.uuid) || DivineWorld.canEnter(me, owner) || Moderation.roleOf(p).atLeast(Role.MODERATEUR)) return null;
        return "Le monde de " + owner.name + " vous est fermé.";
    }

    /** Téléporte en remontant si l'endroit est maintenant bouché (monde ressource réinitialisé, bloc posé…). */
    static void safeTo(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            y = Math.max(y, top);
        }
        DivineWorld.rememberReturn(p);
        Teleport.to(p, level, x, y, z);
        p.setYRot(yaw);
        p.setXRot(pitch);
        Travel.protect(p, 3000);
    }

    public static String set(ServerPlayer player, String name) {
        String k = key(name);
        if (!k.matches("[a-z0-9_\\-]{1,16}")) return "Nom invalide : 1 à 16 lettres, chiffres, _ ou -.";
        PlayerProfile p = GodData.get(player);
        Level level = player.level();
        if (level.dimension() == Level.NETHER || level.dimension() == Level.END) return "Impossible ici.";
        if (TournamentSystem.isFighting(p.uuid)) return "Impossible pendant un duel.";
        String why = forbidden(player, (ServerLevel) level, player.getX(), player.getY(), player.getZ());
        if (why != null) return why;
        int lim = limit(p, Moderation.roleOf(player));
        if (!p.homes.containsKey(k) && p.homes.size() >= lim)
            return "Limite atteinte : " + lim + " résidence(s) pour un " + p.tier.label + ". Supprimez-en une (/delhome) ou montez de palier.";
        Warp h = new Warp();
        h.name = name == null || name.isBlank() ? DEFAULT : name;
        h.owner = p.uuid;
        h.ownerName = p.name;
        h.dimension = level.dimension().identifier().toString();
        h.x = player.getX();
        h.y = player.getY();
        h.z = player.getZ();
        h.yaw = player.getYRot();
        h.pitch = player.getXRot();
        boolean moved = p.homes.put(k, h) != null;
        GodData.dirty();
        Msg.ok(player, (moved ? "Résidence « " + h.name + " » déplacée ici." : "Résidence « " + h.name + " » créée.")
                + " (" + p.homes.size() + "/" + lim + ") · /home" + (k.equals(DEFAULT) ? "" : " " + h.name)
                + (Hub.isResources(level) ? " — attention, le monde ressource est réinitialisé régulièrement." : ""));
        return null;
    }

    public static String go(ServerPlayer player, String name) {
        PlayerProfile p = GodData.get(player);
        if (p.homes.isEmpty()) return "Vous n'avez aucune résidence. Posez-en une avec /sethome [nom].";
        String k = key(name);
        Warp h = p.homes.get(k);
        if (h == null && (name == null || name.isBlank()) && p.homes.size() == 1) h = p.homes.values().iterator().next();
        if (h == null) return "Résidence « " + k + " » inconnue. Les vôtres : " + String.join(", ", names(p)) + ".";
        Warp home = h;
        ServerLevel level = level(player, home.dimension);
        if (level == null) return "Le monde de cette résidence n'existe plus.";
        String why = forbidden(player, level, home.x, home.y, home.z);
        if (why != null) return why;
        return Travel.start(player, "Vers « " + home.name + " »", () -> {
            String again = forbidden(player, level, home.x, home.y, home.z);
            if (again != null) return again;
            safeTo(player, level, home.x, home.y, home.z, home.yaw, home.pitch);
            Msg.ok(player, "Bienvenue chez vous (« " + home.name + " »).");
            return null;
        });
    }

    public static String delete(ServerPlayer player, String name) {
        PlayerProfile p = GodData.get(player);
        Warp h = p.homes.remove(key(name));
        if (h == null) return "Résidence inconnue.";
        GodData.dirty();
        Msg.ok(player, "Résidence « " + h.name + " » supprimée.");
        return null;
    }

    public static List<String> names(PlayerProfile p) {
        List<String> out = new ArrayList<>();
        for (Warp h : p.homes.values()) out.add(h.name);
        return out;
    }

    public static String list(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        int lim = limit(p, Moderation.roleOf(player));
        Msg.send(player, Msg.header("Vos résidences (" + p.homes.size() + "/" + lim + ")"));
        if (p.homes.isEmpty()) {
            Msg.info(player, "Aucune. Placez-vous où vous voulez et tapez /sethome [nom].");
            return null;
        }
        for (Warp h : p.homes.values()) {
            MutableComponent line = Msg.button("⌂ " + h.name, "/home " + h.name, "Y aller", ChatFormatting.GREEN)
                    .append(Msg.text("  " + WarpSystem.describe(h) + "  ", ChatFormatting.GRAY))
                    .append(Msg.suggest("[✖]", "/delhome " + h.name, "Supprimer", ChatFormatting.RED));
            Msg.send(player, line);
        }
        return null;
    }

    // ------------------------------------------------------------------ /back

    record Loc(String dim, double x, double y, double z, float yaw, float pitch, boolean death, boolean inVoid) {
    }

    private static final Map<UUID, Loc> LAST = new HashMap<>();

    /** Mémorise la position actuelle (avant un voyage, ou au moment de la mort). */
    public static void remember(ServerPlayer p, boolean death) {
        Loc l = snapshot(p, death);
        if (l != null) LAST.put(p.getUUID(), l);
    }

    static Loc snapshot(ServerPlayer p, boolean death) {
        Level l = p.level();
        if (l.dimension() == Level.NETHER || l.dimension() == Level.END) return null;
        if (TournamentSystem.isFighting(p.getUUID().toString())) return null;
        boolean inVoid = p.getY() < l.getMinY() + 1;
        return new Loc(l.dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), death, inVoid);
    }

    static void commit(ServerPlayer p, Loc l) {
        if (l != null) LAST.put(p.getUUID(), l);
    }

    public static String back(ServerPlayer player) {
        Loc loc = LAST.get(player.getUUID());
        if (loc == null) return "Aucun endroit où revenir (on revient au lieu de sa mort ou d'avant sa dernière téléportation).";
        if (loc.inVoid()) return "Vous êtes mort dans le vide : impossible d'y revenir. Vos objets sont dans votre tombe (/tombe).";
        ServerLevel level = level(player, loc.dim());
        if (level == null) return "Ce monde n'est plus accessible.";
        String why = forbidden(player, level, loc.x(), loc.y(), loc.z());
        if (why != null) return why;
        return Travel.start(player, loc.death() ? "Vers le lieu de votre mort" : "Retour en arrière", () -> {
            safeTo(player, level, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
            Msg.ok(player, loc.death() ? "Vous voici là où vous êtes tombé." : "Vous êtes revenu à votre position précédente.");
            return null;
        });
    }

    public static void onLogout(ServerPlayer p) {
        LAST.remove(p.getUUID());
    }
}
