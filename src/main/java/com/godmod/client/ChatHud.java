package com.godmod.client;

import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.util.ArrayList;
import java.util.List;

/**
 * Remplace l'affichage du chat : un panneau avec de vrais onglets (Tout, Global, Local, Commerce, Groupe, Privé),
 * des compteurs de messages non lus, le canal d'écriture et le défilement. Fermé, il montre les derniers messages
 * de l'onglet actif qui s'estompent comme le chat normal.
 */
public final class ChatHud {
    private static final int GOLD = 0xFFE6B84C, PANEL = 0xD015172A, PANEL_SOFT = 0x9015172A, INK = 0xFF15172A, TAB_BG = 0xC0262A52;
    private static final int LINE_H = 9, TAB_H = 12;

    private record Row(FormattedCharSequence text, int x, int y) {
    }

    private record Hit(int x0, int y0, int x1, int y1, ChatStore.Tab tab, boolean writer) {
    }

    private static final List<Row> ROWS = new ArrayList<>();
    private static final List<Hit> HITS = new ArrayList<>();
    private static int panelX0, panelY0, panelX1, panelY1;
    private static int maxScroll;

    private ChatHud() {
    }

    private static int width(Minecraft mc) {
        return ChatComponent.getWidth(mc.options.chatWidth().get());
    }

    /** Couche d'interface qui remplace le chat vanilla. */
    public static void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        ROWS.clear();
        HITS.clear();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return;
        boolean focused = mc.screen instanceof ChatScreen;
        Font font = mc.font;
        int w = width(mc);
        int x = 4;
        int bottom = gg.guiHeight() - 40;
        int maxLines = Math.max(1, ChatComponent.getHeight(focused ? mc.options.chatHeightFocused().get() : mc.options.chatHeightUnfocused().get()) / LINE_H);
        int now = mc.gui.getGuiTicks();

        // Découpe des messages de l'onglet en lignes (le plus récent en bas).
        List<ChatStore.Line> shown = ChatStore.shown();
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        for (int i = shown.size() - 1; i >= 0 && lines.size() < maxLines + ChatStore.scroll + 40; i--) {
            ChatStore.Line l = shown.get(i);
            List<FormattedCharSequence> parts = ComponentRenderUtils.wrapComponents(l.message(), w - 4, font);
            for (int k = parts.size() - 1; k >= 0; k--) {
                lines.add(parts.get(k));
                ticks.add(l.tick());
            }
        }
        maxScroll = Math.max(0, lines.size() - maxLines);
        if (!focused) ChatStore.scroll = 0;
        ChatStore.scroll = Math.min(ChatStore.scroll, maxScroll);

        if (focused) {
            int count = Math.min(maxLines, lines.size());
            int top = bottom - maxLines * LINE_H;
            panelX0 = x - 2;
            panelY0 = top - TAB_H - 4;
            panelX1 = x + w + 2;
            panelY1 = bottom + 2;
            gg.fill(panelX0, panelY0, panelX1, panelY1, PANEL);
            gg.fill(panelX0, panelY0, panelX1, panelY0 + 1, GOLD);
            gg.fill(panelX0, top - 2, panelX1, top - 1, 0x60E6B84C);
            drawTabs(gg, font, x, panelY0 + 2, w, mc);
            for (int i = 0; i < count; i++) {
                int idx = i + ChatStore.scroll;
                if (idx >= lines.size()) break;
                int y = bottom - (i + 1) * LINE_H;
                gg.drawString(font, lines.get(idx), x, y + 1, 0xFFFFFFFF, true);
                ROWS.add(new Row(lines.get(idx), x, y));
            }
            if (lines.isEmpty()) gg.drawString(font, "Aucun message dans cet onglet.", x, bottom - LINE_H + 1, 0xFF8A86A3, false);
            if (maxScroll > 0) { // barre de défilement
                int h = maxLines * LINE_H;
                int bar = Math.max(8, h * maxLines / (maxLines + maxScroll));
                int pos = (int) ((h - bar) * (1 - ChatStore.scroll / (double) maxScroll));
                gg.fill(x + w, top + pos, x + w + 1, top + pos + bar, GOLD);
            }
        } else {
            double bgOpacity = mc.options.textBackgroundOpacity().get();
            double textOpacity = mc.options.chatOpacity().get() * 0.9 + 0.1;
            int drawn = 0;
            for (int i = 0; i < lines.size() && drawn < maxLines; i++) {
                int age = now - ticks.get(i);
                if (age >= 200) break;
                double t = Math.max(0, Math.min(1, (1.0 - age / 200.0) * 10.0));
                t *= t;
                int a = (int) (255 * t * textOpacity);
                if (a <= 3) continue;
                int y = bottom - (drawn + 1) * LINE_H;
                int bgA = (int) (255 * t * bgOpacity);
                gg.fill(x - 2, y, x + w + 2, y + LINE_H, (bgA << 24) | 0x15172A);
                gg.drawString(font, lines.get(i), x, y + 1, (a << 24) | 0xFFFFFF, true);
                drawn++;
            }
            // Petit rappel des messages non lus quand le chat est fermé.
            int unread = 0;
            for (ChatStore.Tab tab : ChatStore.TABS) if (tab != ChatStore.Tab.TOUT && tab != ChatStore.active) unread += ChatStore.unread(tab);
            if (unread > 0 && ChatStore.active != ChatStore.Tab.TOUT) {
                String s = "✉ " + unread + " non lu(s) dans les autres onglets";
                int y = bottom - (drawn + 1) * LINE_H - 2;
                gg.fill(x - 2, y, x + font.width(s) + 4, y + LINE_H + 1, PANEL_SOFT);
                gg.drawString(font, s, x + 1, y + 1, GOLD, false);
            }
        }
    }

    private static void drawTabs(GuiGraphics gg, Font font, int x, int y, int w, Minecraft mc) {
        int cx = x;
        for (ChatStore.Tab tab : ChatStore.TABS) {
            int n = ChatStore.unread(tab);
            String label = tab.label + (n > 0 && tab != ChatStore.active ? " " + Math.min(99, n) : "");
            int tw = font.width(label) + 8;
            boolean on = tab == ChatStore.active;
            gg.fill(cx, y, cx + tw, y + TAB_H, on ? GOLD : TAB_BG);
            if (!on) gg.fill(cx, y + TAB_H - 1, cx + tw, y + TAB_H, (tab.color & 0x00FFFFFF) | 0xA0000000);
            gg.drawString(font, tab.label, cx + 4, y + 2, on ? INK : tab.color, false);
            if (n > 0 && !on) gg.drawString(font, " " + Math.min(99, n), cx + 4 + font.width(tab.label), y + 2, 0xFFFFE066, false);
            HITS.add(new Hit(cx, y, cx + tw, y + TAB_H, tab, false));
            cx += tw + 2;
        }
        // Canal d'écriture, à droite.
        String wr = "✎ " + ChatStore.writing.label;
        int ww = font.width(wr) + 8;
        int wx = x + w - ww;
        if (wx > cx + 4) {
            gg.fill(wx, y, wx + ww, y + TAB_H, 0xC0101226);
            gg.fill(wx, y + TAB_H - 1, wx + ww, y + TAB_H, ChatStore.writing.color);
            gg.drawString(font, wr, wx + 4, y + 2, ChatStore.writing.color, false);
            HITS.add(new Hit(wx, y, wx + ww, y + TAB_H, null, true));
        }
    }

    // ------------------------------------------------------------------ Souris

    private static boolean inPanel(double mx, double my) {
        return mx >= panelX0 && mx < panelX1 && my >= panelY0 && my < panelY1;
    }

    /** Clic dans l'écran du chat : onglets, canal, ou composant cliquable d'un message. */
    public static boolean click(ChatScreen screen, double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance();
        for (Hit h : HITS) {
            if (mx < h.x0() || mx >= h.x1() || my < h.y0() || my >= h.y1()) continue;
            if (h.writer()) {
                ChatStore.Tab[] cycle = {ChatStore.Tab.GLOBAL, ChatStore.Tab.LOCAL, ChatStore.Tab.COMMERCE, ChatStore.Tab.GROUPE};
                int i = 0;
                for (int k = 0; k < cycle.length; k++) if (cycle[k] == ChatStore.writing) i = k;
                setWriting(cycle[(i + (button == 1 ? cycle.length - 1 : 1)) % cycle.length]);
            } else {
                ChatStore.select(h.tab());
                if (h.tab().channel != null) setWriting(h.tab());
            }
            return true;
        }
        if (!inPanel(mx, my)) return false;
        Style style = styleAt(mx, my);
        if (style != null) handle(screen, style);
        return true;
    }

    public static void setWriting(ChatStore.Tab tab) {
        Minecraft mc = Minecraft.getInstance();
        if (tab.channel == null || mc.getConnection() == null) return;
        ChatStore.writing = tab;
        if (ClientInit.onGodServer()) mc.getConnection().sendCommand("canal " + tab.channel);
    }

    public static boolean scroll(double mx, double my, double delta) {
        if (!inPanel(mx, my)) return false;
        int step = delta > 0 ? 1 : -1;
        ChatStore.scroll = Math.max(0, Math.min(maxScroll, ChatStore.scroll + step * 3));
        return true;
    }

    public static Style styleAt(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        for (Row r : ROWS) {
            if (my < r.y() || my >= r.y() + LINE_H) continue;
            final double target = mx - r.x();
            if (target < 0) return null;
            final Style[] found = {null};
            final int[] cur = {0};
            r.text().accept((index, style, cp) -> {
                int cw = mc.font.width(FormattedCharSequence.codepoint(cp, style));
                if (target < cur[0] + cw) {
                    found[0] = style;
                    return false;
                }
                cur[0] += cw;
                return true;
            });
            return found[0];
        }
        return null;
    }

    private static void handle(ChatScreen screen, Style style) {
        Minecraft mc = Minecraft.getInstance();
        ClickEvent ce = style.getClickEvent();
        if (ce == null) return;
        if (ce instanceof ClickEvent.RunCommand rc) {
            String c = rc.command();
            if (mc.getConnection() == null) return;
            if (c.startsWith("/")) mc.getConnection().sendCommand(c.substring(1));
            else mc.getConnection().sendChat(c);
        } else if (ce instanceof ClickEvent.SuggestCommand sc) {
            screen.insertText(sc.command(), true);
        } else if (ce instanceof ClickEvent.OpenUrl ou) {
            Util.getPlatform().openUri(ou.uri());
        } else if (ce instanceof ClickEvent.CopyToClipboard cc) {
            mc.keyboardHandler.setClipboard(cc.value());
        }
    }

    /** Info-bulle du composant survolé (après le rendu de l'écran du chat). */
    public static void hover(GuiGraphics gg, int mx, int my) {
        if (!inPanel(mx, my)) return;
        Style style = styleAt(mx, my);
        if (style != null && style.getHoverEvent() != null) gg.renderComponentHoverEffect(Minecraft.getInstance().font, style, mx, my);
    }
}
