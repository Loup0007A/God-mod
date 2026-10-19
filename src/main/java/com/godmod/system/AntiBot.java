package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.data.Settings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Anti-bot. Ne concerne que les comptes qui n'ont jamais fait /register (les joueurs connus ne sont jamais touchés) :
 * <ul>
 *   <li><b>pseudos générés</b> du type « QuietWhale5404 » (Adjectif + Animal + 4 chiffres) refusés ;</li>
 *   <li><b>adresses de datacenters, VPN et proxys</b> refusées (vérification par ip-api.com) ;</li>
 *   <li><b>vague de nouveaux comptes</b> (4 en une minute) : inscriptions fermées 10 minutes ;</li>
 *   <li><b>adresses IP bannies</b> (/antibot bannirip).</li>
 * </ul>
 * Un admin peut laisser passer un pseudo précis avec /antibot autoriser &lt;pseudo&gt;.
 */
public final class AntiBot {
    /** Adjectif + Nom + 4 chiffres, sans séparateur : BraveShark7307, DarkComet6143, EpicElk2730… */
    private static final Pattern GENERATED = Pattern.compile("^[A-Z][a-z]{2,10}[A-Z][a-z]{1,10}\\d{4}$");
    private static final long FLOOD_WINDOW_MS = 60_000, LOCKDOWN_MS = 10 * 60_000L;
    private static final int FLOOD_COUNT = 4;

    private static final Deque<Long> NEW_JOINS = new ArrayDeque<>();
    private static long lockdownUntil = 0;
    /** IP -> {hébergeur/proxy ? 1 : 0, date de vérification}. */
    private static final Map<String, long[]> IP_CACHE = new HashMap<>();

    private AntiBot() {
    }

    public static boolean looksGenerated(String name) {
        return GENERATED.matcher(name).matches();
    }

    /** Joueur « connu » : inscrit (mot de passe) ; si l'authentification est coupée, 30 min de jeu. */
    private static boolean known(PlayerProfile p) {
        if (p.passwordHash != null) return true;
        return !GodData.settings().authEnabled && p.playTicks > 20 * 60 * 30;
    }

    /** /antibot purge : supprime les profils jamais inscrits, quasi sans temps de jeu, hors ligne. */
    public static int purge(MinecraftServer server) {
        int n = 0;
        var it = GodData.state().players.values().iterator();
        while (it.hasNext()) {
            PlayerProfile p = it.next();
            if (p.passwordHash != null || p.playTicks > 20 * 60 || p.plotIndex >= 0 || p.role != Role.JOUEUR && p.role != null) continue;
            if (PowerSystem.online(server, p.uuid) != null || !GodData.followersOf(p.uuid).isEmpty()) continue;
            it.remove();
            n++;
        }
        if (n > 0) {
            GodData.dirty();
            GodData.log("ANTIBOT : " + n + " profil(s) fantôme(s) supprimé(s)");
        }
        return n;
    }

    /**
     * À la connexion (après le contrôle des bannissements). Renvoie vrai si le joueur a été expulsé.
     */
    public static boolean onLogin(ServerPlayer sp, PlayerProfile p) {
        Settings s = GodData.settings();
        MinecraftServer server = sp.level().getServer();
        String ip = AuthSystem.ip(sp);
        String name = sp.getName().getString();
        if (ip != null && s.bannedIps.contains(ip)) return kick(sp, "Cette adresse est bannie du serveur.", "IP bannie " + ip);
        if (server.getPlayerList().isOp(sp.nameAndId()) || (p.role != null && p.role.atLeast(Role.MODERATEUR))) return false;
        if (known(p) || s.antibotAllowed.stream().anyMatch(n -> n.equalsIgnoreCase(name))) return false;

        long now = System.currentTimeMillis();
        if (s.antibotFlood) {
            if (now < lockdownUntil)
                return kick(sp, "Trop de nouveaux comptes en même temps : les inscriptions rouvrent dans " + com.godmod.util.Msg.time(lockdownUntil - now) + ".", "inscriptions fermées");
            NEW_JOINS.addLast(now);
            while (!NEW_JOINS.isEmpty() && now - NEW_JOINS.peekFirst() > FLOOD_WINDOW_MS) NEW_JOINS.pollFirst();
            if (NEW_JOINS.size() >= FLOOD_COUNT) {
                lockdownUntil = now + LOCKDOWN_MS;
                NEW_JOINS.clear();
                Moderation.staffAlert(server, "Anti-bot : vague de nouveaux comptes détectée, inscriptions fermées 10 minutes (les joueurs déjà inscrits peuvent entrer).");
                GodData.log("ANTIBOT : inscriptions fermées 10 min (vague de nouveaux comptes)");
                kickUnregistered(server, "Vague de bots détectée : réessayez dans quelques minutes.");
                return true;
            }
        }
        if (s.antibotNames && looksGenerated(name))
            return kick(sp, "Les pseudos générés automatiquement (ex. QuietWhale5404) ne sont pas acceptés.\nDemandez à un admin de vous autoriser.", "pseudo généré");
        if (s.antibotIpCheck && ip != null) checkIp(server, sp.getUUID(), name, ip);
        return false;
    }

    private static boolean kick(ServerPlayer sp, String message, String reason) {
        String name = sp.getName().getString();
        String ip = AuthSystem.ip(sp);
        GodData.log("ANTIBOT : " + name + " refusé (" + reason + ", " + ip + ")");
        Moderation.staffAlert(sp.level().getServer(), "Anti-bot : " + name + " refusé (" + reason + "). /antibot autoriser " + name + " pour le laisser entrer.");
        sp.connection.disconnect(Component.literal("[Panthéon] " + message));
        return true;
    }

    private static boolean privateAddress(String ip) {
        try {
            InetAddress a = InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }

    /** Vérifie l'adresse en arrière-plan (sans bloquer le serveur) et expulse si c'est un datacenter, un VPN ou un proxy. */
    private static void checkIp(MinecraftServer server, UUID id, String name, String ip) {
        if (privateAddress(ip)) return;
        long[] cached = IP_CACHE.get(ip);
        if (cached != null && System.currentTimeMillis() - cached[1] < 6 * 3600_000L) {
            if (cached[0] == 1) server.execute(() -> kickIfStillNew(server, id, ip));
            return;
        }
        // HttpURLConnection (module java.base) dans un fil séparé : le serveur n'attend jamais la réponse.
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create("http://ip-api.com/json/" + ip + "?fields=status,hosting,proxy,isp").toURL().openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(5000);
                String body;
                try (var in = c.getInputStream()) {
                    body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                if (!"success".equals(o.has("status") ? o.get("status").getAsString() : "")) return;
                boolean bad = (o.has("hosting") && o.get("hosting").getAsBoolean()) || (o.has("proxy") && o.get("proxy").getAsBoolean());
                String isp = o.has("isp") ? o.get("isp").getAsString() : "?";
                server.execute(() -> {
                    IP_CACHE.put(ip, new long[]{bad ? 1 : 0, System.currentTimeMillis()});
                    if (bad) {
                        GodData.log("ANTIBOT : " + name + " vient d'un datacenter/VPN (" + isp + ", " + ip + ")");
                        kickIfStillNew(server, id, ip);
                    }
                });
            } catch (Exception e) {
                GodMod.LOGGER.debug("[God Mod] Vérification d'IP impossible : {}", e.getMessage());
            }
        });
    }

    private static void kickIfStillNew(MinecraftServer server, UUID id, String ip) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp == null || known(GodData.get(sp))) return;
        kick(sp, "Les connexions depuis un datacenter, un VPN ou un proxy ne sont pas acceptées pour les nouveaux comptes.", "datacenter / VPN");
    }

    /** Expulse tous les joueurs connectés qui ne sont pas encore inscrits (vague de bots). */
    public static int kickUnregistered(MinecraftServer server, String message) {
        int n = 0;
        for (ServerPlayer sp : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            PlayerProfile p = GodData.get(sp);
            if (known(p) || Moderation.roleOf(sp).atLeast(Role.MODERATEUR)) continue;
            sp.connection.disconnect(Component.literal("[Panthéon] " + message));
            n++;
        }
        return n;
    }

    /** /antibot scan : applique les règles aux joueurs déjà connectés. */
    public static int scan(MinecraftServer server) {
        int n = 0;
        Settings s = GodData.settings();
        for (ServerPlayer sp : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            PlayerProfile p = GodData.get(sp);
            if (known(p) || Moderation.roleOf(sp).atLeast(Role.MODERATEUR)) continue;
            String name = sp.getName().getString();
            if (s.antibotAllowed.stream().anyMatch(x -> x.equalsIgnoreCase(name))) continue;
            if (s.antibotNames && looksGenerated(name)) {
                kick(sp, "Les pseudos générés automatiquement ne sont pas acceptés.", "pseudo généré (scan)");
                n++;
            } else if (s.antibotIpCheck) {
                String ip = AuthSystem.ip(sp);
                if (ip != null) checkIp(server, sp.getUUID(), name, ip);
            }
        }
        return n;
    }

    public static long lockdownLeft() {
        return Math.max(0, lockdownUntil - System.currentTimeMillis());
    }

    public static void endLockdown() {
        lockdownUntil = 0;
        NEW_JOINS.clear();
    }
}
