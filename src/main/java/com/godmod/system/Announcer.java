package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.Settings;
import com.godmod.util.Glyphs;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Annonces automatiques : un message de la liste est diffusé toutes les N minutes (dès qu'un joueur est en ligne),
 * à tour de rôle. Les commandes écrites dans le message (/vote, /kit…) deviennent cliquables.
 */
public final class Announcer {
    private static long last = System.currentTimeMillis();
    private static int index = 0;

    private Announcer() {
    }

    /** Message mis en forme : icône, texte, et commandes cliquables. */
    public static MutableComponent format(String text) {
        MutableComponent c = Glyphs.on() ? Glyphs.of(Glyphs.STAR).append(Component.literal(" ")) : Msg.text("✦ ", ChatFormatting.GOLD);
        for (String w : text.split(" ", -1)) {
            String clean = w.replaceAll("[.,;!?)]+$", "");
            if (clean.startsWith("/") && clean.length() > 1) {
                c.append(Msg.suggest(clean, clean + " ", "Cliquez pour taper la commande", ChatFormatting.YELLOW));
                c.append(Msg.text(w.substring(clean.length()) + " ", ChatFormatting.AQUA));
            } else {
                c.append(Msg.text(w + " ", ChatFormatting.AQUA));
            }
        }
        return c;
    }

    public static void broadcast(MinecraftServer server, String text) {
        Component c = format(text);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Trailer.isViewer(p) || AuthSystem.isLocked(p)) continue;
            p.sendSystemMessage(c);
            ChatSystem.recordSystem(p, c);
        }
    }

    public static void tickSecond(MinecraftServer server) {
        Settings st = GodData.settings();
        if (!st.announcements || st.announceMessages.isEmpty() || st.announceMinutes <= 0) return;
        if (server.getPlayerList().getPlayerCount() == 0) return;
        long now = System.currentTimeMillis();
        if (now - last < st.announceMinutes * 60_000L) return;
        last = now;
        index = index % st.announceMessages.size();
        broadcast(server, st.announceMessages.get(index++));
    }

    /** Annonce suivante tout de suite. */
    public static String next(MinecraftServer server) {
        Settings st = GodData.settings();
        if (st.announceMessages.isEmpty()) return "Aucune annonce. /annonces ajouter <texte>";
        last = System.currentTimeMillis();
        index = index % st.announceMessages.size();
        broadcast(server, st.announceMessages.get(index++));
        return null;
    }
}
