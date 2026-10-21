package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.Letter;
import com.godmod.data.PlayerProfile;
import com.godmod.menu.GodMenu;
import com.godmod.menu.Page;
import com.godmod.util.DivineItems;
import com.godmod.util.ItemCodec;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Courrier (/courrier) : écrire à n'importe quel joueur, même hors ligne, avec en pièce jointe l'objet tenu en main
 * ou des pièces. La boîte aux lettres s'ouvre en menu ; les pièces jointes se récupèrent d'un clic.
 */
public final class MailSystem {
    public static final int BOX_MAX = 30;
    public static final int TEXT_MAX = 200;
    private static final long COOLDOWN = 8_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Europe/Paris"));
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private MailSystem() {
    }

    private static List<Letter> box(String uuid) {
        return GodData.state().mail.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    public static int unread(String uuid) {
        List<Letter> b = GodData.state().mail.get(uuid);
        if (b == null) return 0;
        int n = 0;
        for (Letter l : b) if (!l.read) n++;
        return n;
    }

    /** Envoie une lettre ; {@code attachHand} joint la pile tenue en main, {@code money} des pièces. */
    public static String send(ServerPlayer from, String toName, String text, boolean attachHand, long money) {
        PlayerProfile me = GodData.get(from);
        PlayerProfile to = GodData.byName(toName);
        if (to == null) return "Joueur inconnu : " + toName + ".";
        if (to.uuid.equals(me.uuid)) return "Vous ne pouvez pas vous écrire à vous-même.";
        if (Moderation.isMuted(me)) return "Vous êtes muet : impossible d'envoyer du courrier.";
        if (to.ignored.contains(me.uuid)) return to.name + " ne reçoit pas votre courrier.";
        long now = System.currentTimeMillis();
        Long last = LAST.get(from.getUUID());
        if (last != null && now - last < COOLDOWN && !Moderation.roleOf(from).atLeast(com.godmod.data.Role.MODERATEUR))
            return "Patientez quelques secondes avant d'envoyer une autre lettre.";
        text = text == null ? "" : text.trim();
        if (text.length() > TEXT_MAX) return "Message trop long (" + TEXT_MAX + " caractères au plus).";
        List<Letter> b = box(to.uuid);
        if (b.size() >= BOX_MAX) return "La boîte aux lettres de " + to.name + " est pleine (" + BOX_MAX + " lettres).";
        if (money < 0) return "Montant invalide.";
        if (money > 0 && me.money < money) return "Solde insuffisant : vous avez " + Msg.money(me.money) + ".";
        ItemStack hand = ItemStack.EMPTY;
        String handLabel = "";
        if (attachHand) {
            hand = from.getMainHandItem();
            if (hand.isEmpty()) return "Tenez en main l'objet à envoyer.";
            if (DivineItems.CODEX.equals(DivineItems.kind(hand)) || ZoneSystem.isWand(hand)) return "Cet objet ne peut pas être envoyé.";
            handLabel = hand.getCount() + " × " + hand.getHoverName().getString();
        }
        if (text.isEmpty() && !attachHand && money == 0) return "Lettre vide.";
        Letter l = new Letter();
        l.id = GodData.state().nextLetterId++;
        l.from = me.uuid;
        l.fromName = me.name;
        l.text = text;
        l.sent = now;
        if (attachHand) {
            l.items.add(ItemCodec.encode(from.level().getServer(), hand));
            from.getMainHandItem().setCount(0);
        }
        if (money > 0) {
            me.money -= money;
            l.money = money;
        }
        b.add(l);
        LAST.put(from.getUUID(), now);
        GodData.dirty();
        GodData.log("COURRIER : " + me.name + " → " + to.name + (l.items.isEmpty() ? "" : " + objet") + (money > 0 ? " + " + money + " pièces" : "")
                + (text.isEmpty() ? "" : " : " + text));
        Msg.ok(from, "Lettre envoyée à " + to.name + (attachHand ? " avec " + handLabel : "")
                + (money > 0 ? (attachHand ? " et " : " avec ") + Msg.money(money) : "") + ".");
        ServerPlayer online = from.level().getServer().getPlayerList().getPlayer(UUID.fromString(to.uuid));
        if (online != null) notify(online, me.name);
        return null;
    }

    private static void notify(ServerPlayer p, String from) {
        Msg.send(p, Msg.text("✉ Nouvelle lettre de " + from + ". ", ChatFormatting.LIGHT_PURPLE)
                .append(Msg.button("Lire", "/courrier", "Ouvrir la boîte aux lettres", ChatFormatting.GOLD)));
    }

    public static void onLogin(ServerPlayer p) {
        int n = unread(p.getUUID().toString());
        if (n > 0) Msg.send(p, Msg.text("✉ Vous avez " + n + " lettre(s) non lue(s). ", ChatFormatting.LIGHT_PURPLE)
                .append(Msg.button("Ouvrir", "/courrier", "Ouvrir la boîte aux lettres", ChatFormatting.GOLD)));
    }

    public static void onLogout(ServerPlayer p) {
        LAST.remove(p.getUUID());
    }

    /** Ouvre la boîte aux lettres (les plus récentes d'abord). */
    public static void open(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        List<Letter> b = box(uuid);
        Page page = new Page("Courrier — " + unread(uuid) + " non lue(s)").frame(Items.PURPLE_STAINED_GLASS_PANE);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        List<Letter> sorted = new ArrayList<>(b);
        sorted.sort((x, y) -> Long.compare(y.sent, x.sent));
        for (int i = 0; i < sorted.size() && i < slots.length; i++) {
            Letter l = sorted.get(i);
            boolean attach = !l.items.isEmpty() || l.money > 0;
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§7De : §f" + l.fromName + " §8· " + DATE.format(Instant.ofEpochMilli(l.sent))));
            if (!l.text.isEmpty()) {
                for (String part : wrap(l.text, 38)) lore.add(Page.line("§f" + part));
            }
            if (attach) {
                lore.add(Page.line(""));
                if (!l.items.isEmpty()) {
                    ItemStack st = ItemCodec.decode(player.level().getServer(), l.items.get(0));
                    lore.add(Page.line("§6Pièce jointe : " + st.getCount() + " × " + st.getHoverName().getString()));
                }
                if (l.money > 0) lore.add(Page.line("§6Pièces jointes : " + Msg.money(l.money)));
            }
            lore.add(Page.line(""));
            lore.add(Page.line(attach ? "§eClic : lire et récupérer" : "§eClic : marquer comme lue"));
            lore.add(Page.line("§cShift-clic : supprimer" + (attach ? " (les pièces jointes sont récupérées d'abord)" : "")));
            lore.add(Page.line("§7Clic droit : répondre"));
            long id = l.id;
            page.set(slots[i], Page.icon(attach ? Items.CHEST_MINECART : l.read ? Items.PAPER : Items.WRITABLE_BOOK, 1,
                    (l.read ? "" : "● ") + "Lettre de " + l.fromName, l.read ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE, lore, !l.read), (sp, c) -> {
                if (c == GodMenu.Click.RIGHT) {
                    sp.closeContainer();
                    Msg.send(sp, Msg.suggest("[Cliquez pour répondre à " + l.fromName + "]", "/courrier envoyer " + l.fromName + " ", "Écrire la réponse", ChatFormatting.GOLD));
                    return;
                }
                String err = claim(sp, id, c == GodMenu.Click.SHIFT);
                if (err != null) Msg.err(sp, err);
                sp.level().getServer().execute(() -> open(sp));
            });
        }
        if (b.isEmpty()) page.set(22, Page.icon(Items.STRUCTURE_VOID, "Boîte vide", ChatFormatting.GRAY,
                "§7Écrire : /courrier envoyer <joueur> <message>", "§7Joindre l'objet en main : /courrier objet <joueur> [message]",
                "§7Joindre des pièces : /courrier argent <joueur> <montant> [message]"));
        page.set(4, Page.icon(Items.WRITABLE_BOOK, "Écrire une lettre", ChatFormatting.GOLD,
                "§7/courrier envoyer <joueur> <message>", "§7/courrier objet <joueur> [message]", "§7/courrier argent <joueur> <montant> [message]",
                "§8" + b.size() + " / " + BOX_MAX + " lettres"), (sp, c) -> {
            sp.closeContainer();
            Msg.send(sp, Msg.suggest("[Cliquez pour écrire une lettre]", "/courrier envoyer ", "/courrier envoyer <joueur> <message>", ChatFormatting.GOLD));
        });
        page.close();
        page.open(player);
    }

    /** Lit une lettre : récupère les pièces jointes, la marque lue, et la supprime si demandé. */
    public static String claim(ServerPlayer p, long id, boolean delete) {
        List<Letter> b = box(p.getUUID().toString());
        Letter l = null;
        for (Letter x : b) if (x.id == id) l = x;
        if (l == null) return "Lettre introuvable.";
        MinecraftServer server = p.level().getServer();
        if (!l.items.isEmpty()) {
            int free = 0;
            for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) free++;
            if (free < l.items.size()) return "Libérez de la place dans votre inventaire pour récupérer l'objet.";
            for (String s : l.items) Shop.give(p, ItemCodec.decode(server, s));
            l.items.clear();
            Msg.ok(p, "Objet récupéré.");
        }
        if (l.money > 0) {
            GodData.get(p).money += l.money;
            Msg.ok(p, "+" + Msg.money(l.money) + " (courrier de " + l.fromName + ").");
            l.money = 0;
        }
        l.read = true;
        if (delete) b.remove(l);
        else if (!l.text.isEmpty()) Msg.send(p, Msg.text("✉ " + l.fromName + " : ", ChatFormatting.LIGHT_PURPLE).append(Msg.text(l.text, ChatFormatting.WHITE)));
        GodData.dirty();
        return null;
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() + 1 > width && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
