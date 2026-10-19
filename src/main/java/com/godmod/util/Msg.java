package com.godmod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Messages de chat homogènes et lisibles. */
public final class Msg {
    private Msg() {
    }

    public static MutableComponent prefix() {
        return Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Panthéon").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent text(String s, ChatFormatting... f) {
        return Component.literal(s).withStyle(f);
    }

    public static void info(Player p, String s) {
        send(p, text(s, ChatFormatting.WHITE));
    }

    public static void ok(Player p, String s) {
        send(p, text(s, ChatFormatting.GREEN));
    }

    public static void err(Player p, String s) {
        send(p, text(s, ChatFormatting.RED));
    }

    public static void send(Player p, Component c) {
        if (p instanceof ServerPlayer sp && !com.godmod.system.Trailer.isViewer(sp)) sp.sendSystemMessage(prefix().append(c));
    }

    public static void broadcast(MinecraftServer server, String s, ChatFormatting color) {
        if (server == null) return;
        Component c = prefix().append(text(s, color));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (com.godmod.system.Trailer.isViewer(sp)) continue; // le réalisateur du trailer ne voit aucun message
            sp.sendSystemMessage(c);
        }
    }

    /** Bouton cliquable dans le chat qui exécute une commande. */
    public static MutableComponent button(String label, String command, String hover, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(s -> s
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** Texte cliquable qui pré-remplit la barre de commande (sans l'exécuter). */
    public static MutableComponent suggest(String label, String command, String hover, ChatFormatting color) {
        return Component.literal(label).withStyle(s -> s
                .withColor(color)
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** Montant formaté : « 12 345 pièces ». */
    public static String money(long amount) {
        return num(amount) + " pièce" + (Math.abs(amount) > 1 ? "s" : "");
    }

    /** Nombre avec séparateur de milliers (espace). */
    public static String num(long n) {
        String s = String.valueOf(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('\u00A0');
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    /** Ligne de titre centrée pour les messages longs (aide, profils…). */
    public static MutableComponent header(String title) {
        return Component.literal("━━━━━━ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("✦ " + title + " ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" ━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Durée exprimée en minutes et secondes « 4:05 ». */
    public static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "j " + h + "h";
        if (h > 0) return h + "h " + m + "min";
        if (m > 0) return m + "min " + (s % 60) + "s";
        return s + "s";
    }
}
