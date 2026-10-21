package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.progress.QuestSystem;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mod client facultatif (le même .jar, installé chez le joueur) : il se signale avec /godclient.
 * Le serveur propose de l'installer aux autres joueurs (une fois par jour), ou l'exige si l'admin le décide.
 */
public final class ClientMod {
    private static final Map<UUID, String> CLIENTS = new HashMap<>();
    private static final Map<UUID, Long> JOINED = new HashMap<>();

    private ClientMod() {
    }

    public static boolean has(ServerPlayer p) {
        return CLIENTS.containsKey(p.getUUID());
    }

    public static String hello(ServerPlayer p, String version) {
        boolean first = CLIENTS.put(p.getUUID(), version) == null;
        if (first) GodData.log("CLIENT : " + p.getName().getString() + " utilise le mod client (" + version + ")");
        return null;
    }

    public static void onLogin(ServerPlayer p) {
        JOINED.put(p.getUUID(), System.currentTimeMillis());
    }

    public static void onLogout(ServerPlayer p) {
        CLIENTS.remove(p.getUUID());
        JOINED.remove(p.getUUID());
    }

    private static Component link(String label, String url, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
    }

    public static void tickSecond(MinecraftServer server) {
        Settings st = GodData.settings();
        if (!st.clientPrompt && !st.clientRequired) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (has(p) || AuthSystem.isLocked(p)) continue;
            Long joined = JOINED.get(p.getUUID());
            if (joined == null) continue;
            long t = now - joined;
            if (st.clientRequired && t > 45_000) {
                p.connection.disconnect(Component.literal("God Civilisation\n\n").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Ce serveur demande le mod client (chat à onglets et interface).\n").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("Téléchargement et installation : " + st.clientUrl).withStyle(ChatFormatting.YELLOW)));
                continue;
            }
            PlayerProfile pr = GodData.get(p);
            String today = QuestSystem.today();
            if (st.clientPrompt && t > 20_000 && !today.equals(pr.clientPromptDay)) {
                pr.clientPromptDay = today;
                GodData.dirty();
                Msg.send(p, Msg.text("Envie d'une vraie interface ? Le mod client God Civilisation remplace le chat par un panneau à onglets "
                                + "(Global, Local, Commerce, Groupe, Privé) avec les messages non lus. Facultatif, gratuit. ", ChatFormatting.AQUA)
                        .append(link("Installer", st.clientUrl, ChatFormatting.GOLD)));
            }
        }
    }

    public static String status(MinecraftServer server) {
        Settings st = GodData.settings();
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (has(p)) names.add(p.getName().getString());
        return "Mod client : proposition " + (st.clientPrompt ? "activée" : "désactivée") + " · " + (st.clientRequired ? "OBLIGATOIRE" : "facultatif")
                + " · " + st.clientUrl + " · équipés : " + (names.isEmpty() ? "aucun" : String.join(", ", names));
    }
}
