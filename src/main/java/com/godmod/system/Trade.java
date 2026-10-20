package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.menu.GodMenu;
import com.godmod.menu.Page;
import com.godmod.util.DivineItems;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Échange sécurisé entre deux joueurs (/echange &lt;joueur&gt;) : chacun dépose des objets et de l'argent,
 * les deux valident, puis l'échange se fait après 3 secondes. Toute modification annule les validations.
 * Les objets proposés sont gardés par le serveur et rendus si l'échange est annulé (fermeture, départ, combat).
 */
public final class Trade {
    public static final long REQUEST_MS = 60_000;
    public static final int MAX_ITEMS = 16;
    private static final long CONFIRM_MS = 3_000;

    /** Cases de chaque côté : 4 colonnes × 4 lignes. */
    private static final int[] LEFT = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] RIGHT = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};

    private static final class Side {
        final ServerPlayer player;
        final List<ItemStack> items = new ArrayList<>();
        long money;
        boolean ready;

        Side(ServerPlayer p) {
            this.player = p;
        }
    }

    private static final class Session {
        final long id;
        final Side a, b;
        long readyAt;
        long lastRender;
        boolean over;

        Session(long id, ServerPlayer a, ServerPlayer b) {
            this.id = id;
            this.a = new Side(a);
            this.b = new Side(b);
        }

        Side side(ServerPlayer p) {
            return a.player.getUUID().equals(p.getUUID()) ? a : b;
        }

        Side other(ServerPlayer p) {
            return a.player.getUUID().equals(p.getUUID()) ? b : a;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Demande reçue : cible → (demandeur, date). */
    private static final Map<UUID, Map<UUID, Long>> REQUESTS = new HashMap<>();
    private static long nextId = 1;

    private Trade() {
    }

    public static boolean busy(ServerPlayer p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    // ------------------------------------------------------------------ Demande

    public static String request(ServerPlayer from, ServerPlayer to) {
        if (to == null) return "Joueur introuvable ou hors ligne.";
        if (to == from) return "Vous ne pouvez pas échanger avec vous-même.";
        if (AuthSystem.isLocked(to)) return to.getName().getString() + " n'est pas encore connecté.";
        if (busy(from)) return "Vous avez déjà un échange en cours.";
        if (busy(to)) return to.getName().getString() + " est déjà en train d'échanger.";
        if (Travel.inCombat(from)) return "Impossible pendant un combat.";
        Map<UUID, Long> mine = REQUESTS.get(from.getUUID());
        Long back = mine == null ? null : mine.get(to.getUUID());
        if (back != null && System.currentTimeMillis() - back < REQUEST_MS) return accept(from, to.getName().getString()); // il me l'avait déjà demandé
        REQUESTS.computeIfAbsent(to.getUUID(), k -> new HashMap<>()).put(from.getUUID(), System.currentTimeMillis());
        String n = from.getName().getString();
        Msg.send(to, Msg.prefix().append(Msg.text(n + " vous propose un échange. ", ChatFormatting.AQUA))
                .append(Msg.button("[Accepter]", "/echange accepter " + n, "Ouvrir l'échange", ChatFormatting.GREEN))
                .append(Msg.text(" ", ChatFormatting.GRAY))
                .append(Msg.button("[Refuser]", "/echange refuser " + n, "Refuser", ChatFormatting.RED)));
        Msg.ok(from, "Demande d'échange envoyée à " + to.getName().getString() + " (60 s pour accepter).");
        return null;
    }

    public static String accept(ServerPlayer me, String fromName) {
        MinecraftServer server = me.level().getServer();
        Map<UUID, Long> got = REQUESTS.get(me.getUUID());
        if (got == null || got.isEmpty()) return "Aucune demande d'échange en attente.";
        ServerPlayer from = null;
        if (fromName == null) {
            long best = 0;
            for (var e : got.entrySet()) {
                ServerPlayer c = server.getPlayerList().getPlayer(e.getKey());
                if (c != null && e.getValue() > best) {
                    best = e.getValue();
                    from = c;
                }
            }
        } else {
            from = server.getPlayerList().getPlayerByName(fromName);
        }
        if (from == null) return "Ce joueur n'est plus en ligne.";
        Long at = got.get(from.getUUID());
        if (at == null || System.currentTimeMillis() - at > REQUEST_MS) return "Pas de demande récente de " + from.getName().getString() + ".";
        got.remove(from.getUUID());
        if (busy(me) || busy(from)) return "L'un de vous a déjà un échange en cours.";
        Session s = new Session(nextId++, from, me);
        SESSIONS.put(from.getUUID(), s);
        SESSIONS.put(me.getUUID(), s);
        GodData.log("ÉCHANGE : ouvert entre " + from.getName().getString() + " et " + me.getName().getString());
        render(s);
        return null;
    }

    public static String deny(ServerPlayer me, String fromName) {
        Map<UUID, Long> got = REQUESTS.get(me.getUUID());
        if (got == null || got.isEmpty()) return "Aucune demande en attente.";
        ServerPlayer from = fromName == null ? null : me.level().getServer().getPlayerList().getPlayerByName(fromName);
        if (from != null) {
            got.remove(from.getUUID());
            Msg.info(from, me.getName().getString() + " a refusé votre échange.");
        } else got.clear();
        Msg.info(me, "Demande refusée.");
        return null;
    }

    // ------------------------------------------------------------------ Interface

    private static void later(ServerPlayer p, Runnable r) {
        p.level().getServer().execute(r);
    }

    private static void changed(Session s) {
        s.a.ready = false;
        s.b.ready = false;
        s.readyAt = 0;
    }

    private static void render(Session s) {
        if (s.over) return;
        s.lastRender = System.currentTimeMillis();
        open(s, s.a);
        open(s, s.b);
    }

    private static ItemStack withLore(ItemStack stack, String... extra) {
        ItemStack icon = stack.copy();
        List<Component> lore = new ArrayList<>();
        ItemLore old = icon.get(DataComponents.LORE);
        if (old != null) lore.addAll(old.lines());
        for (String e : extra) lore.add(Page.line(e));
        icon.set(DataComponents.LORE, new ItemLore(lore));
        return icon;
    }

    private static void open(Session s, Side me) {
        ServerPlayer p = me.player;
        Side other = s.other(p);
        String on = other.player.getName().getString();
        Page page = new Page("Échange avec " + on).tag("trade:" + s.id);
        // Séparation.
        for (int row = 0; row < 5; row++)
            page.set(row * 9 + 4, Page.icon(Items.IRON_BARS, "◀ Vous · " + on + " ▶", ChatFormatting.DARK_GRAY));
        for (int i = 0; i < LEFT.length; i++) {
            if (i < me.items.size()) {
                int idx = i;
                page.set(LEFT[i], withLore(me.items.get(i), "", "§cCliquez pour le reprendre"), (sp, c) -> {
                    if (s.over || idx >= me.items.size()) return;
                    Shop.give(sp, me.items.remove(idx));
                    changed(s);
                    later(sp, () -> render(s));
                });
            } else page.set(LEFT[i], Page.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, "Votre offre", ChatFormatting.GRAY,
                    "§7Cliquez un objet de votre inventaire", "§7pour l'ajouter"));
        }
        for (int i = 0; i < RIGHT.length; i++) {
            if (i < other.items.size()) page.set(RIGHT[i], withLore(other.items.get(i), "", "§7Proposé par " + on));
            else page.set(RIGHT[i], Page.icon(Items.GRAY_STAINED_GLASS_PANE, "Offre de " + on, ChatFormatting.DARK_GRAY));
        }
        // Argent.
        long[] steps = {-1000, -100, 100, 1000};
        int[] mslots = {36, 37, 38, 39};
        for (int i = 0; i < steps.length; i++) {
            long step = steps[i];
            page.set(mslots[i], Page.icon(step < 0 ? Items.RED_DYE : Items.LIME_DYE, (step > 0 ? "+" : "") + Msg.num(step) + " pièces",
                            step < 0 ? ChatFormatting.RED : ChatFormatting.GREEN, "§7Clic droit : ×10 · shift-clic : ÷100"),
                    (sp, c) -> {
                        if (s.over) return;
                        long d = c == GodMenu.Click.RIGHT ? step * 10 : c == GodMenu.Click.SHIFT ? step / 100 : step;
                        long have = GodData.get(sp).money;
                        me.money = Math.max(0, Math.min(have, me.money + d));
                        changed(s);
                        later(sp, () -> render(s));
                    });
        }
        page.set(46, Page.icon(Items.GOLD_INGOT, "Vous donnez : " + Msg.money(me.money), ChatFormatting.GOLD,
                "§7Votre solde : " + Msg.money(GodData.get(p).money), "§7Boutons de gauche pour ajuster"));
        page.set(52, Page.icon(Items.GOLD_INGOT, on + " donne : " + Msg.money(other.money), ChatFormatting.GOLD));
        page.set(41, Page.icon(Items.PAPER, "Comment ça marche", ChatFormatting.AQUA,
                "§71. Cliquez vos objets pour les proposer", "§72. Réglez l'argent à donner", "§73. Validez tous les deux",
                "§7L'échange a lieu 3 s plus tard.", "§cToute modification annule les validations.", "§7Fermer l'écran annule l'échange."));
        // Validation.
        page.set(45, Page.icon(me.ready ? Items.LIME_CONCRETE : Items.RED_CONCRETE, 1, me.ready ? "Vous avez validé" : "Valider l'échange",
                me.ready ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                List.of(Page.line(me.ready ? "§7Cliquez pour annuler la validation" : "§eCliquez quand l'offre vous convient")), me.ready), (sp, c) -> {
            if (s.over) return;
            me.ready = !me.ready;
            s.readyAt = s.a.ready && s.b.ready ? System.currentTimeMillis() + CONFIRM_MS : 0;
            if (s.readyAt > 0) {
                Msg.info(s.a.player, "Échange validé des deux côtés : il aura lieu dans 3 secondes.");
                Msg.info(s.b.player, "Échange validé des deux côtés : il aura lieu dans 3 secondes.");
            }
            later(sp, () -> render(s));
        });
        page.set(53, Page.icon(other.ready ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE, on + (other.ready ? " a validé" : " n'a pas validé"),
                other.ready ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        page.set(49, Page.icon(Items.BARRIER, "Annuler l'échange", ChatFormatting.RED, "§7Les objets sont rendus"), (sp, c) -> cancel(s, sp.getName().getString() + " a annulé l'échange."));
        page.onInventoryClick((sp, slot, c) -> {
            if (s.over || sp != me.player) return;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) return;
            if (DivineItems.CODEX.equals(DivineItems.kind(st)) || ZoneSystem.isWand(st)) {
                Msg.err(sp, "Cet objet ne s'échange pas.");
                return;
            }
            if (me.items.size() >= MAX_ITEMS) {
                Msg.err(sp, "16 piles au maximum par échange.");
                return;
            }
            me.items.add(st.copy());
            slot.set(ItemStack.EMPTY);
            changed(s);
            later(sp, () -> render(s));
        });
        page.open(p);
    }

    // ------------------------------------------------------------------ Fin

    private static void end(Session s) {
        s.over = true;
        SESSIONS.remove(s.a.player.getUUID());
        SESSIONS.remove(s.b.player.getUUID());
    }

    private static void refund(Side side) {
        for (ItemStack st : side.items) Shop.give(side.player, st);
        side.items.clear();
    }

    private static void cancel(Session s, String why) {
        if (s.over) return;
        end(s);
        refund(s.a);
        refund(s.b);
        for (Side side : new Side[]{s.a, s.b}) {
            if (side.player.containerMenu instanceof GodMenu gm && ("trade:" + s.id).equals(gm.tag)) side.player.closeContainer();
            Msg.err(side.player, why + " Vos objets vous ont été rendus.");
        }
    }

    private static void complete(Session s) {
        PlayerProfile pa = GodData.get(s.a.player), pb = GodData.get(s.b.player);
        if (pa.money < s.a.money || pb.money < s.b.money) {
            cancel(s, "Échange annulé : l'un de vous n'a plus assez d'argent.");
            return;
        }
        end(s);
        pa.money += s.b.money - s.a.money;
        pb.money += s.a.money - s.b.money;
        for (ItemStack st : s.b.items) Shop.give(s.a.player, st);
        for (ItemStack st : s.a.items) Shop.give(s.b.player, st);
        GodData.dirty();
        String an = s.a.player.getName().getString(), bn = s.b.player.getName().getString();
        GodData.log("ÉCHANGE : " + an + " (" + count(s.a) + " objets, " + s.a.money + " pièces) ⇄ " + bn + " (" + count(s.b) + " objets, " + s.b.money + " pièces)");
        for (Side side : new Side[]{s.a, s.b}) {
            Side o = side == s.a ? s.b : s.a;
            side.player.closeContainer();
            Msg.ok(side.player, "Échange réussi avec " + o.player.getName().getString() + " : vous recevez " + count(o) + " objet(s)"
                    + (o.money > 0 ? " et " + Msg.money(o.money) : "") + ".");
        }
    }

    private static int count(Side s) {
        int n = 0;
        for (ItemStack st : s.items) n += st.getCount();
        return n;
    }

    /** Chaque tick : validation finale et surveillance (écran fermé, déconnexion, combat). */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Session s : new ArrayList<>(new java.util.LinkedHashSet<>(SESSIONS.values()))) {
            if (s.over) continue;
            for (Side side : new Side[]{s.a, s.b}) {
                ServerPlayer p = side.player;
                if (p.hasDisconnected() || server.getPlayerList().getPlayer(p.getUUID()) != p) {
                    cancel(s, p.getName().getString() + " s'est déconnecté.");
                    break;
                }
                if (now - s.lastRender > 1500 && !(p.containerMenu instanceof GodMenu gm && ("trade:" + s.id).equals(gm.tag))) {
                    cancel(s, p.getName().getString() + " a fermé l'échange.");
                    break;
                }
            }
            if (!s.over && s.readyAt > 0 && now >= s.readyAt && s.a.ready && s.b.ready) complete(s);
        }
    }

    public static void onLogout(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) cancel(s, p.getName().getString() + " s'est déconnecté.");
        REQUESTS.remove(p.getUUID());
    }

    /** Arrêt du serveur : on rend tout. */
    public static void cancelAll() {
        for (Session s : new ArrayList<>(SESSIONS.values())) cancel(s, "Le serveur redémarre.");
    }

    public static List<String> pendingFrom(ServerPlayer me) {
        List<String> out = new ArrayList<>();
        Map<UUID, Long> got = REQUESTS.get(me.getUUID());
        if (got == null) return out;
        for (UUID u : got.keySet()) {
            ServerPlayer p = me.level().getServer().getPlayerList().getPlayer(u);
            if (p != null) out.add(p.getName().getString());
        }
        return out;
    }
}
