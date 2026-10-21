package com.godmod.client;

import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Messages reçus, classés par canal grâce au badge placé en tête par le serveur. */
public final class ChatStore {
    public enum Tab {
        TOUT("Tout", null, 0xFFE9E6F2),
        GLOBAL("Global", "global", 0xFF55FFFF),
        LOCAL("Local", "local", 0xFFFFFF55),
        COMMERCE("Commerce", "commerce", 0xFFFFAA00),
        GROUPE("Groupe", "groupe", 0xFF55FF55),
        PRIVE("Privé", null, 0xFFFF55FF),
        SERVEUR("Serveur", null, 0xFFAAAAAA);

        public final String label, channel;
        public final int color;

        Tab(String label, String channel, int color) {
            this.label = label;
            this.channel = channel;
            this.color = color;
        }
    }

    /** Onglets affichés (SERVEUR n'en est pas un : ses messages s'affichent partout). */
    public static final Tab[] TABS = {Tab.TOUT, Tab.GLOBAL, Tab.LOCAL, Tab.COMMERCE, Tab.GROUPE, Tab.PRIVE};

    public record Line(Component message, Tab tab, int tick) {
    }

    private static final int MAX = 400;
    private static final Deque<Line> LINES = new ArrayDeque<>();
    private static final Map<Tab, Integer> UNREAD = new EnumMap<>(Tab.class);
    public static Tab active = Tab.TOUT;
    /** Canal où l'on écrit (suivi côté client, envoyé au serveur avec /canal). */
    public static Tab writing = Tab.GLOBAL;
    public static int scroll = 0;
    /** Incrémenté à chaque changement (pour le cache des lignes découpées). */
    public static int version = 0;

    private ChatStore() {
    }

    public static Tab classify(String plain) {
        String s = plain.length() > 6 ? plain.substring(0, 6) : plain;
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '': return Tab.GLOBAL;
                case '': return Tab.LOCAL;
                case '': return Tab.COMMERCE;
                case '': return Tab.GROUPE;
                case '': return Tab.PRIVE;
                default:
            }
        }
        if (plain.startsWith("[G]")) return Tab.GLOBAL;
        if (plain.startsWith("[L]")) return Tab.LOCAL;
        if (plain.startsWith("[$]")) return Tab.COMMERCE;
        if (plain.startsWith("[P]")) return Tab.GROUPE;
        if (plain.startsWith("[✉]")) return Tab.PRIVE;
        return Tab.SERVEUR;
    }

    /** Ajoute un message ; renvoie false s'il est ignoré (lignes de mise en page du chat simulé du serveur). */
    public static boolean add(Component message, int tick) {
        String plain = message.getString();
        if (plain.isBlank() || plain.startsWith("Onglets ") || plain.startsWith("━━━━━━━━  ")) return false;
        Tab t = classify(plain);
        LINES.addLast(new Line(message, t, tick));
        while (LINES.size() > MAX) LINES.removeFirst();
        if (!visible(t)) UNREAD.merge(t, 1, Integer::sum);
        else if (scroll > 0) scroll++;
        version++;
        return true;
    }

    public static boolean visible(Tab t) {
        return active == Tab.TOUT || t == Tab.SERVEUR || t == active;
    }

    public static List<Line> shown() {
        List<Line> out = new ArrayList<>();
        for (Line l : LINES) if (visible(l.tab())) out.add(l);
        return out;
    }

    public static int unread(Tab t) {
        if (t == Tab.TOUT) {
            int n = 0;
            for (int v : UNREAD.values()) n += v;
            return n;
        }
        return UNREAD.getOrDefault(t, 0);
    }

    public static void select(Tab t) {
        active = t;
        scroll = 0;
        if (t == Tab.TOUT) UNREAD.clear();
        else UNREAD.remove(t);
        version++;
    }

    public static void clear() {
        LINES.clear();
        UNREAD.clear();
        scroll = 0;
        version++;
    }
}
