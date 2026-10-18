package com.godmod.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Point d'accès unique aux données persistantes. */
public final class GodData {
    private static GodSavedData current;
    private static MinecraftServer server;

    private GodData() {
    }

    public static void load(MinecraftServer srv) {
        server = srv;
        current = srv.overworld().getDataStorage().computeIfAbsent(GodSavedData.TYPE);
    }

    public static void unload() {
        current = null;
        server = null;
    }

    public static boolean ready() {
        return current != null;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static GodState state() {
        return current.state;
    }

    public static Settings settings() {
        return current.state.settings;
    }

    public static void dirty() {
        if (current != null) current.setDirty();
    }

    public static PlayerProfile get(Player player) {
        PlayerProfile p = get(player.getUUID());
        String n = player.getName().getString();
        if (!n.equals(p.name)) {
            p.name = n;
            dirty();
        }
        return p;
    }

    public static PlayerProfile get(UUID uuid) {
        return get(uuid.toString());
    }

    public static PlayerProfile get(String uuid) {
        PlayerProfile p = current.state.players.get(uuid);
        if (p == null) {
            p = new PlayerProfile(uuid, "?");
            current.state.players.put(uuid, p);
            dirty();
        }
        return p;
    }

    /** Profil existant uniquement (pas de création). */
    public static PlayerProfile find(String uuid) {
        return uuid == null ? null : current.state.players.get(uuid);
    }

    public static PlayerProfile byName(String name) {
        for (PlayerProfile p : current.state.players.values()) {
            if (p.name.equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    public static List<PlayerProfile> followersOf(String uuid) {
        List<PlayerProfile> list = new ArrayList<>();
        for (PlayerProfile p : current.state.players.values()) {
            if (uuid.equals(p.master)) list.add(p);
        }
        return list;
    }

    /** Fidèles directs et indirects (dieux vassaux et leurs fidèles). */
    public static int totalFollowers(String uuid) {
        return totalFollowers(uuid, 0);
    }

    private static int totalFollowers(String uuid, int depth) {
        if (depth > 32) return 0;
        int n = 0;
        for (PlayerProfile f : followersOf(uuid)) {
            n += 1 + totalFollowers(f.uuid, depth + 1);
        }
        return n;
    }

    /** Vrai si {@code lord} est au-dessus de {@code vassal} dans la hiérarchie. */
    public static boolean isAbove(String lord, PlayerProfile vassal) {
        PlayerProfile cur = vassal;
        for (int i = 0; i < 32 && cur != null && cur.master != null; i++) {
            if (cur.master.equals(lord)) return true;
            cur = find(cur.master);
        }
        return false;
    }

    public static void log(String entry) {
        if (current == null) return;
        String stamp = new SimpleDateFormat("dd/MM HH:mm").format(new Date());
        List<String> log = current.state.log;
        log.add("[" + stamp + "] " + entry);
        while (log.size() > 300) log.remove(0);
        dirty();
    }
}
