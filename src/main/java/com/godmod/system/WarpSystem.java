package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.data.Tier;
import com.godmod.data.Warp;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Warps publics : /setwarp pour en créer, /warp pour s'y rendre. */
public final class WarpSystem {
    public static final int TELEPORT_COOLDOWN_MS = 5000;

    private WarpSystem() {
    }

    /** Nombre de warps autorisés selon le palier (illimité pour l'équipe). */
    public static int limit(PlayerProfile p, Role role) {
        if (role.atLeast(Role.MODERATEUR)) return 100;
        return switch (p.tier) {
            case MORTEL -> 1;
            case EVEILLE -> 2;
            case DEMI_DIEU -> 3;
            case DIEU -> 5;
            case DIEU_SUPREME -> 10;
            case TITAN -> 12;
            case PRIMORDIAL -> 14;
            case ASTRAL -> 16;
            case ETERNEL -> 18;
            case CREATEUR -> 20;
        };
    }

    public static List<Warp> owned(String uuid) {
        List<Warp> list = new ArrayList<>();
        for (Warp w : GodData.state().warps.values()) if (uuid.equals(w.owner)) list.add(w);
        return list;
    }

    public static List<Warp> all() {
        List<Warp> list = new ArrayList<>(GodData.state().warps.values());
        list.sort(Comparator.comparingInt((Warp w) -> -w.visits).thenComparing(w -> w.name));
        return list;
    }

    public static String set(ServerPlayer player, String name) {
        if (!name.matches("[A-Za-z0-9_\\-]{2,24}")) return "Nom invalide : 2 à 24 lettres, chiffres, _ ou -.";
        PlayerProfile p = GodData.get(player);
        Role role = Moderation.roleOf(player);
        String key = name.toLowerCase();
        Warp existing = GodData.state().warps.get(key);
        if (existing != null && !existing.owner.equals(p.uuid)) return "Le warp « " + name + " » existe déjà (créé par " + existing.ownerName + ").";
        Level level = player.level();
        if (DivineWorld.isDivine(level)) {
            PlayerProfile owner = DivineWorld.ownerAt(player.blockPosition());
            if (owner == null || !owner.uuid.equals(p.uuid)) return "Dans le monde divin, vous ne pouvez poser un warp que dans votre propre monde.";
        }
        if (Hub.isProtected(level, player.blockPosition()) && !role.atLeast(Role.ADMIN)) return "Impossible de poser un warp dans le Sanctuaire.";
        if (existing == null && owned(p.uuid).size() >= limit(p, role))
            return "Limite atteinte (" + limit(p, role) + " warp(s) pour un " + p.tier.label + "). Supprimez-en un avec /delwarp.";
        Warp w = existing != null ? existing : new Warp();
        w.name = name;
        w.owner = p.uuid;
        w.ownerName = p.name;
        w.dimension = level.dimension().identifier().toString();
        w.x = player.getX();
        w.y = player.getY();
        w.z = player.getZ();
        w.yaw = player.getYRot();
        w.pitch = player.getXRot();
        GodData.state().warps.put(key, w);
        GodData.dirty();
        GodData.log("WARP : " + p.name + " pose « " + name + " » (" + w.dimension + " " + (int) w.x + " " + (int) w.y + " " + (int) w.z + ")");
        Msg.ok(player, (existing != null ? "Warp déplacé : " : "Warp créé : ") + name + ". Les autres joueurs peuvent y aller avec /warp " + name + ".");
        return null;
    }

    public static String go(ServerPlayer player, String name) {
        Warp w = GodData.state().warps.get(name.toLowerCase());
        if (w == null) return "Warp inconnu. Liste : /warps";
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(w.dimension));
        ServerLevel level = player.level().getServer().getLevel(key);
        if (level == null) return "Le monde de ce warp n'existe plus.";
        if (level.dimension() == Level.NETHER || level.dimension() == Level.END) return "Ce warp est dans une dimension scellée.";
        DivineWorld.rememberReturn(player);
        Teleport.to(player, level, w.x, w.y, w.z);
        player.setYRot(w.yaw);
        player.setXRot(w.pitch);
        if (!w.owner.equals(player.getUUID().toString())) {
            w.visits++;
            GodData.dirty();
        }
        Msg.ok(player, "Bienvenue au warp « " + w.name + " » de " + w.ownerName + ".");
        return null;
    }

    public static String delete(ServerPlayer player, String name) {
        String key = name.toLowerCase();
        Warp w = GodData.state().warps.get(key);
        if (w == null) return "Warp inconnu.";
        boolean staff = Moderation.roleOf(player).atLeast(Role.MODERATEUR);
        if (!w.owner.equals(player.getUUID().toString()) && !staff) return "Ce warp ne vous appartient pas.";
        GodData.state().warps.remove(key);
        GodData.dirty();
        GodData.log("WARP : " + player.getName().getString() + " supprime « " + w.name + " » (de " + w.ownerName + ")");
        Msg.ok(player, "Warp « " + w.name + " » supprimé.");
        return null;
    }

    public static String describe(Warp w) {
        String dim = w.dimension.endsWith("divine") ? "Monde divin" : w.dimension.endsWith("ressources") ? "Monde ressource" : "Monde principal";
        return dim + " · " + (int) w.x + " " + (int) w.y + " " + (int) w.z;
    }

    public static boolean ownsTier(PlayerProfile p, Tier t) {
        return p.tier.atLeast(t);
    }
}
