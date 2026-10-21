package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.Settings;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Pack de ressources du serveur (badges, menus, boutons, barres) : envoyé à chaque joueur à la connexion.
 * Le pack est publié avec le site (GitHub Pages) ; le serveur le télécharge pour calculer son empreinte SHA-1
 * (au démarrage, puis toutes les 30 minutes), ce qui permet au jeu de le garder en cache et de voir les mises à jour.
 */
public final class PackSystem {
    /** Identifiant fixe du pack : une nouvelle version remplace l'ancienne chez le joueur. */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("godciv-pack".getBytes(StandardCharsets.UTF_8));
    private static volatile String hash = "";
    private static volatile String hashUrl = "";
    private static long lastRefresh = 0;

    private PackSystem() {
    }

    public static String hash() {
        return hash;
    }

    /** Télécharge le pack en arrière-plan et met à jour son empreinte. */
    public static void refresh(MinecraftServer server, Runnable then) {
        Settings st = GodData.settings();
        String url = st.packUrl;
        lastRefresh = System.currentTimeMillis();
        if (!st.packEnabled || url == null || url.isBlank()) return;
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "GodCivilisation-Pack/1.0");
                byte[] data;
                try (var in = c.getInputStream()) {
                    data = in.readAllBytes();
                }
                String h = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
                server.execute(() -> {
                    boolean changed = !h.equals(hash);
                    hash = h;
                    hashUrl = url;
                    if (changed) GodData.log("PACK : empreinte " + h + " (" + data.length + " octets)");
                    if (then != null) then.run();
                });
            } catch (Exception e) {
                GodMod.LOGGER.warn("[God Mod] Pack de ressources injoignable ({}) : {}", url, e.getMessage());
                if (then != null) server.execute(then);
            }
        });
    }

    public static void tickMinute(MinecraftServer server) {
        if (System.currentTimeMillis() - lastRefresh > 30 * 60_000L) refresh(server, null);
    }

    /** Envoie le pack au joueur (le jeu demande de l'accepter s'il ne l'a pas déjà). */
    public static void send(ServerPlayer p) {
        Settings st = GodData.settings();
        if (!st.packEnabled || st.packUrl == null || st.packUrl.isBlank()) return;
        String h = st.packUrl.equals(hashUrl) ? hash : "";
        Component prompt = Component.literal("God Civilisation").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n" + st.packPrompt).withStyle(ChatFormatting.GRAY));
        p.connection.send(new ClientboundResourcePackPushPacket(PACK_ID, st.packUrl, h, st.packRequired, Optional.of(prompt)));
    }

    public static String status() {
        Settings st = GodData.settings();
        return "Pack : " + (st.packEnabled ? "activé" : "désactivé") + " · " + (st.packRequired ? "obligatoire (badges en images)" : "facultatif (badges en texte)")
                + " · " + st.packUrl + " · empreinte : " + (hash.isEmpty() ? "pas encore calculée" : hash.substring(0, 12) + "…");
    }

    public static void onLogin(ServerPlayer p) {
        send(p);
    }

    public static String resend(ServerPlayer p) {
        if (!GodData.settings().packEnabled) return "Le pack de ressources est désactivé sur ce serveur.";
        send(p);
        Msg.info(p, "Pack de ressources renvoyé.");
        return null;
    }
}
