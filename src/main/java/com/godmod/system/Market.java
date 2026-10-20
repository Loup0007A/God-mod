package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.MarketListing;
import com.godmod.data.PlayerProfile;
import com.godmod.menu.GodMenu;
import com.godmod.menu.Page;
import com.godmod.progress.QuestSystem;
import com.godmod.util.DivineItems;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Marché entre joueurs (/market) : chacun met en vente ses propres objets à son prix.
 * <ul>
 *   <li>taxe : 1 pièce par objet vendu, prélevée sur le gain du vendeur ;</li>
 *   <li>tri (récents, prix, quantité, nom, meilleures affaires) et recherche (nom ou identifiant) ;</li>
 *   <li>pour chaque objet : prix moyen des ventes sur 7 jours, prix de la boutique, et courbes
 *       de l'offre (quantités mises en vente), de la demande (quantités achetées) et du prix moyen ;</li>
 *   <li>les offres expirent au bout de 7 jours : les objets reviennent au vendeur (/market recuperer).</li>
 * </ul>
 */
public final class Market {
    public static final long TAX = 1;
    public static final long MIN_PRICE = TAX + 1;
    public static final long MAX_PRICE = 1_000_000_000L;
    public static final int MAX_LISTINGS = 15;
    public static final long EXPIRY_MS = 7L * 24 * 3600_000L;
    public static final int HISTORY_DAYS = 30;

    /** 28 cases de contenu : lignes 1 à 4, colonnes 1 à 7. */
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    public enum Sort {
        RECENT("Plus récentes"),
        PRIX("Prix croissant"),
        PRIX_DESC("Prix décroissant"),
        AFFAIRE("Meilleures affaires"),
        QUANTITE("Plus grande quantité"),
        NOM("Nom (A → Z)");

        public final String label;

        Sort(String label) {
            this.label = label;
        }

        public static Sort parse(String s) {
            String k = s.toLowerCase(Locale.ROOT);
            return switch (k) {
                case "recent", "recents", "récent", "récents", "date" -> RECENT;
                case "prix", "prix_croissant", "moinscher", "moins_cher" -> PRIX;
                case "prix_desc", "prix_decroissant", "pluscher", "plus_cher" -> PRIX_DESC;
                case "affaire", "affaires", "bonnes_affaires" -> AFFAIRE;
                case "quantite", "quantité", "qte" -> QUANTITE;
                case "nom", "alpha", "alphabetique" -> NOM;
                default -> null;
            };
        }
    }

    public enum ChartMode {
        OFFRE("Offre", ChatFormatting.GREEN, Items.LIME_STAINED_GLASS_PANE, Items.LIME_CONCRETE),
        DEMANDE("Demande", ChatFormatting.RED, Items.RED_STAINED_GLASS_PANE, Items.RED_CONCRETE),
        PRIX("Prix moyen", ChatFormatting.YELLOW, Items.YELLOW_STAINED_GLASS_PANE, Items.YELLOW_CONCRETE);

        final String label;
        final ChatFormatting color;
        final Item pane, top;

        ChartMode(String label, ChatFormatting color, Item pane, Item top) {
            this.label = label;
            this.color = color;
            this.pane = pane;
            this.top = top;
        }
    }

    /** Affichage courant d'un joueur. */
    private static final class View {
        Sort sort = Sort.RECENT;
        String query = "";
        int page = 0;
        boolean mine = false;
    }

    private static final Map<UUID, View> VIEWS = new HashMap<>();

    private Market() {
    }

    private static View view(ServerPlayer p) {
        return VIEWS.computeIfAbsent(p.getUUID(), k -> new View());
    }

    private static List<MarketListing> listings() {
        return GodData.state().market;
    }

    private static void later(ServerPlayer p, Runnable r) {
        p.level().getServer().execute(r);
    }

    // ------------------------------------------------------------------ Sérialisation des objets

    static String encode(MinecraftServer server, ItemStack stack) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1)).getOrThrow();
        return tag.toString();
    }

    static ItemStack decode(MinecraftServer server, String snbt) {
        try {
            Tag tag = TagParser.parseCompoundFully(snbt);
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            com.godmod.GodMod.LOGGER.error("Marché : objet illisible " + snbt, e);
            return ItemStack.EMPTY;
        }
    }

    /** Un exemplaire de l'objet de l'offre. */
    private static ItemStack stack(MinecraftServer server, MarketListing l) {
        if (l.cached == null || l.cached.isEmpty()) l.cached = decode(server, l.snbt);
        return l.cached.copyWithCount(1);
    }

    private static String itemId(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    private static void giveCount(ServerPlayer player, ItemStack proto, int count) {
        int max = Math.max(1, proto.getMaxStackSize());
        while (count > 0) {
            int n = Math.min(max, count);
            Shop.give(player, proto.copyWithCount(n));
            count -= n;
        }
    }

    // ------------------------------------------------------------------ Historique : offre, demande, prix

    private static long[] day(String item, String day, boolean create) {
        Map<String, Map<String, long[]>> h = GodData.state().marketHistory;
        Map<String, long[]> days = h.get(item);
        if (days == null) {
            if (!create) return null;
            days = new TreeMap<>();
            h.put(item, days);
        }
        long[] v = days.get(day);
        if (v == null && create) {
            v = new long[3];
            days.put(day, v);
            if (days.size() > HISTORY_DAYS + 5) {
                String limit = LocalDate.parse(QuestSystem.today()).minusDays(HISTORY_DAYS).toString();
                days.keySet().removeIf(k -> k.compareTo(limit) < 0);
            }
        }
        return v;
    }

    private static void record(String item, int index, long amount) {
        day(item, QuestSystem.today(), true)[index] += amount;
        GodData.dirty();
    }

    /** Valeurs des {@code days} derniers jours (le plus ancien d'abord). index : 0 offre, 1 demande, 2 prix moyen (-1 sans vente). */
    public static long[] series(String item, int days, int index) {
        long[] out = new long[days];
        LocalDate today = LocalDate.parse(QuestSystem.today());
        for (int i = 0; i < days; i++) {
            long[] v = day(item, today.minusDays(days - 1 - i).toString(), false);
            if (index == 2) out[i] = v == null || v[1] == 0 ? -1 : Math.round((double) v[2] / v[1]);
            else out[i] = v == null ? 0 : v[index];
        }
        return out;
    }

    /** Prix moyen à l'unité des ventes des {@code days} derniers jours, ou -1. */
    public static long average(String item, int days) {
        long qty = 0, money = 0;
        LocalDate today = LocalDate.parse(QuestSystem.today());
        for (int i = 0; i < days; i++) {
            long[] v = day(item, today.minusDays(i).toString(), false);
            if (v == null) continue;
            qty += v[1];
            money += v[2];
        }
        return qty == 0 ? -1 : Math.round((double) money / qty);
    }

    /** Prix moyen à l'unité des offres en cours, ou -1. */
    public static long averageAsk(String item) {
        long qty = 0, money = 0;
        for (MarketListing l : listings()) {
            if (!l.item.equals(item)) continue;
            qty += l.count;
            money += l.unitPrice * l.count;
        }
        return qty == 0 ? -1 : Math.round((double) money / qty);
    }

    public static int supply(String item) {
        int n = 0;
        for (MarketListing l : listings()) if (l.item.equals(item)) n += l.count;
        return n;
    }

    /** Petite courbe en caractères ▁▂▃▄▅▆▇█ ; les valeurs négatives (pas de donnée) donnent « · ». */
    public static String spark(long[] values) {
        long min = Long.MAX_VALUE, max = 0;
        boolean price = false;
        for (long v : values) {
            if (v < 0) {
                price = true;
                continue;
            }
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        StringBuilder b = new StringBuilder();
        for (long v : values) {
            if (v < 0) b.append('·');
            else if (max == 0) b.append(BARS[0]);
            else if (price) { // prix : échelle entre le minimum et le maximum
                b.append(max == min ? BARS[4] : BARS[(int) Math.round((double) (v - min) * 7 / (max - min))]);
            } else b.append(v == 0 ? BARS[0] : BARS[1 + (int) Math.min(6, Math.round((double) v * 6 / max))]);
        }
        return b.toString();
    }

    private static boolean hasPrice(long[] values) {
        for (long v : values) if (v >= 0) return true;
        return false;
    }

    private static String shopLine(Item item) {
        long buy = Shop.buyPrice(item), sell = Shop.sellPrice(item);
        if (buy <= 0 && sell <= 0) return "§8Boutique : ni vendu ni racheté";
        return "§bBoutique : " + (buy > 0 ? "achat " + Msg.money(buy) : "non vendu") + " · " + (sell > 0 ? "revente " + Msg.money(sell) : "non racheté");
    }

    /** Lignes de statistiques d'un objet (prix moyen, boutique, courbes). */
    private static List<String> statLines(String id, Item item) {
        List<String> lines = new ArrayList<>();
        long avg = average(id, 7), ask = averageAsk(id);
        lines.add(avg > 0 ? "§ePrix moyen (ventes 7 j) : " + Msg.money(avg) : "§8Prix moyen : aucune vente ces 7 jours");
        if (ask > 0) lines.add("§7Prix moyen des offres : " + Msg.money(ask) + " · " + Msg.num(supply(id)) + " en vente");
        lines.add(shopLine(item));
        lines.add("§aOffre    " + spark(series(id, 14, 0)) + " §8(14 j)");
        lines.add("§cDemande " + spark(series(id, 14, 1)));
        long[] prices = series(id, 14, 2);
        if (hasPrice(prices)) lines.add("§ePrix     " + spark(prices));
        return lines;
    }

    // ------------------------------------------------------------------ Actions

    public static boolean sellable(ItemStack s) {
        return !s.isEmpty() && !DivineItems.isDivine(s) && !ZoneSystem.isWand(s);
    }

    /** Met en vente l'objet tenu en main. quantity ≤ 0 : toute la pile. */
    public static String sell(ServerPlayer player, long unitPrice, int quantity) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) return "Tenez en main l'objet à mettre en vente.";
        if (!sellable(hand)) return "Cet objet ne peut pas être vendu au marché (objet divin, générateur ou outil d'administration).";
        if (unitPrice < MIN_PRICE) return "Prix minimum : " + Msg.money(MIN_PRICE) + " par objet (la taxe est de " + TAX + " pièce par objet vendu).";
        if (unitPrice > MAX_PRICE) return "Prix maximum : " + Msg.money(MAX_PRICE) + " par objet.";
        String uuid = player.getUUID().toString();
        long mine = listings().stream().filter(l -> l.seller.equals(uuid)).count();
        if (mine >= MAX_LISTINGS) return "Vous avez déjà " + MAX_LISTINGS + " offres en cours. Retirez-en une (/market mesventes).";
        int n = quantity <= 0 ? hand.getCount() : Math.min(quantity, hand.getCount());
        MinecraftServer server = player.level().getServer();
        String snbt;
        try {
            snbt = encode(server, hand);
        } catch (Exception e) {
            com.godmod.GodMod.LOGGER.error("Marché : impossible d'enregistrer l'objet", e);
            return "Cet objet ne peut pas être enregistré au marché.";
        }
        MarketListing l = new MarketListing();
        l.id = GodData.state().nextMarketId++;
        l.seller = uuid;
        l.sellerName = player.getName().getString();
        l.item = itemId(hand);
        l.name = hand.getHoverName().getString();
        l.snbt = snbt;
        l.count = n;
        l.unitPrice = unitPrice;
        l.created = System.currentTimeMillis();
        l.cached = hand.copyWithCount(1);
        hand.shrink(n);
        listings().add(l);
        record(l.item, 0, n);
        GodData.log("MARCHÉ : " + l.sellerName + " met en vente " + n + " × " + l.name + " à " + unitPrice + "/u (#" + l.id + ")");
        long avg = average(l.item, 7);
        Msg.send(player, Msg.prefix().append(Msg.text("Mis en vente : " + n + " × " + l.name + " à " + Msg.money(unitPrice) + " l'unité"
                        + (n > 1 ? " (" + Msg.money(unitPrice * n) + " le lot)" : "") + ". Vous toucherez " + Msg.money((unitPrice - TAX) * n)
                        + " après la taxe" + (avg > 0 ? " · prix moyen : " + Msg.money(avg) : "") + ". ", ChatFormatting.GREEN))
                .append(Msg.button("[Retirer]", "/market retirer " + l.id, "Annuler cette vente", ChatFormatting.RED)));
        return null;
    }

    public static MarketListing find(long id) {
        for (MarketListing l : listings()) if (l.id == id) return l;
        return null;
    }

    /** Achète {@code quantity} exemplaires (≤ 0 : tout le lot). */
    public static String buy(ServerPlayer player, long id, int quantity) {
        MarketListing l = find(id);
        if (l == null) return "Cette offre n'existe plus (déjà vendue ou retirée).";
        if (l.seller.equals(player.getUUID().toString())) return "C'est votre propre offre : retirez-la depuis « Mes ventes ».";
        MinecraftServer server = player.level().getServer();
        ItemStack proto = stack(server, l);
        if (proto.isEmpty()) return "Cet objet est illisible ; prévenez un administrateur (offre #" + l.id + ").";
        int n = quantity <= 0 ? l.count : Math.min(quantity, l.count);
        long total = l.unitPrice * n;
        PlayerProfile buyer = GodData.get(player);
        if (buyer.money < total) return "Pas assez d'argent : " + Msg.money(total) + " requis, vous avez " + Msg.money(buyer.money) + ".";
        buyer.money -= total;
        buyer.marketSpent += total;
        PlayerProfile seller = GodData.get(l.seller);
        long gain = (l.unitPrice - TAX) * n;
        seller.money += gain;
        seller.marketEarned += gain;
        l.count -= n;
        if (l.count <= 0) listings().remove(l);
        giveCount(player, proto, n);
        record(l.item, 1, n);
        record(l.item, 2, total);
        com.godmod.progress.StatsSystem.add(buyer, com.godmod.progress.StatsSystem.BOUGHT, n);
        com.godmod.progress.StatsSystem.add(seller, com.godmod.progress.StatsSystem.SOLD, n);
        GodData.dirty();
        GodData.log("MARCHÉ : " + buyer.name + " achète " + n + " × " + l.name + " à " + l.sellerName + " pour " + total + " (#" + l.id + ")");
        Msg.ok(player, "Acheté : " + n + " × " + l.name + " pour " + Msg.money(total) + " à " + l.sellerName + ". Solde : " + Msg.money(buyer.money) + ".");
        ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(l.seller));
        if (online != null) {
            Msg.ok(online, "Marché : " + buyer.name + " a acheté " + n + " × " + l.name + " → +" + Msg.money(gain)
                    + " (taxe : " + Msg.money(TAX * n) + ")" + (l.count > 0 ? " · il en reste " + l.count + "." : " · offre écoulée."));
        } else {
            seller.marketOfflineMoney += gain;
            seller.marketOfflineSales += n;
        }
        return null;
    }

    /** Retire une offre : le vendeur (ou un modérateur) récupère les objets. */
    public static String cancel(ServerPlayer player, long id, boolean staff) {
        MarketListing l = find(id);
        if (l == null) return "Offre #" + id + " introuvable.";
        boolean own = l.seller.equals(player.getUUID().toString());
        if (!own && !staff) return "Ce n'est pas votre offre.";
        listings().remove(l);
        if (own) {
            giveCount(player, stack(player.level().getServer(), l), l.count);
            Msg.ok(player, "Vente retirée : " + l.count + " × " + l.name + " vous ont été rendus.");
        } else {
            returnToSeller(player.level().getServer(), l);
            Msg.ok(player, "Offre #" + id + " de " + l.sellerName + " retirée ; les objets lui sont rendus.");
            GodData.log("MARCHÉ : offre #" + id + " de " + l.sellerName + " retirée par " + player.getName().getString());
        }
        GodData.dirty();
        return null;
    }

    private static void returnToSeller(MinecraftServer server, MarketListing l) {
        GodData.get(l.seller).marketReturns.add(l);
        GodData.dirty();
        ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(l.seller));
        if (online != null) deliver(online, true);
    }

    /** Rend au joueur les objets en attente (dans la limite de la place libre). Renvoie le nombre de lots rendus. */
    public static int deliver(ServerPlayer player, boolean announce) {
        PlayerProfile p = GodData.get(player);
        if (p.marketReturns.isEmpty()) return 0;
        MinecraftServer server = player.level().getServer();
        int done = 0;
        Iterator<MarketListing> it = p.marketReturns.iterator();
        while (it.hasNext()) {
            MarketListing l = it.next();
            ItemStack proto = stack(server, l);
            int stacks = (int) Math.ceil((double) l.count / Math.max(1, proto.getMaxStackSize()));
            if (freeSlots(player) < stacks) break;
            giveCount(player, proto, l.count);
            it.remove();
            done++;
            if (announce) Msg.info(player, "Marché : " + l.count + " × " + l.name + " vous sont rendus (offre expirée ou retirée).");
        }
        if (!p.marketReturns.isEmpty() && announce) {
            Msg.send(player, Msg.prefix().append(Msg.text("Marché : " + p.marketReturns.size() + " lot(s) vous attendent, libérez de la place puis ", ChatFormatting.YELLOW))
                    .append(Msg.button("[Récupérer]", "/market recuperer", "Récupérer vos objets", ChatFormatting.GREEN)));
        }
        if (done > 0) GodData.dirty();
        return done;
    }

    private static int freeSlots(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) n++;
        return n;
    }

    public static void onLogin(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (p.marketOfflineSales > 0) {
            Msg.ok(player, "Marché : pendant votre absence, vous avez vendu " + Msg.num(p.marketOfflineSales) + " objet(s) pour "
                    + Msg.money(p.marketOfflineMoney) + " (après la taxe).");
            p.marketOfflineSales = 0;
            p.marketOfflineMoney = 0;
            GodData.dirty();
        }
        deliver(player, true);
    }

    public static void onLogout(ServerPlayer player) {
        VIEWS.remove(player.getUUID());
    }

    /** Chaque minute : expiration des offres. */
    public static void tickMinute(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<MarketListing> expired = new ArrayList<>();
        for (MarketListing l : listings()) if (now - l.created > EXPIRY_MS) expired.add(l);
        for (MarketListing l : expired) {
            listings().remove(l);
            GodData.log("MARCHÉ : offre #" + l.id + " de " + l.sellerName + " expirée");
            returnToSeller(server, l);
        }
    }

    // ------------------------------------------------------------------ Commandes d'affichage

    public static void setSort(ServerPlayer p, Sort s) {
        View v = view(p);
        v.sort = s;
        v.page = 0;
        v.mine = false;
        open(p);
    }

    public static void search(ServerPlayer p, String query) {
        View v = view(p);
        String q = query == null ? "" : query.trim();
        if (q.equalsIgnoreCase("main")) {
            ItemStack hand = p.getMainHandItem();
            q = hand.isEmpty() ? "" : itemId(hand);
        }
        v.query = q;
        v.page = 0;
        v.mine = false;
        open(p);
    }

    public static void openMine(ServerPlayer p) {
        View v = view(p);
        v.mine = true;
        v.page = 0;
        open(p);
    }

    /** Informations de prix de l'objet tenu en main, dans le chat. */
    public static String priceInfo(ServerPlayer p) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) return "Tenez un objet en main.";
        String id = itemId(hand);
        Msg.send(p, Msg.header("Marché — " + hand.getHoverName().getString()));
        for (String line : statLines(id, hand.getItem())) Msg.send(p, Page.line(line));
        long suggested = suggestedPrice(hand);
        Msg.send(p, Msg.suggest("[Mettre en vente à " + Msg.money(suggested) + "]", "/market vendre " + suggested, "Modifiez le prix si besoin", ChatFormatting.GREEN)
                .append(Msg.text("  ", ChatFormatting.GRAY))
                .append(Msg.button("[Voir les offres]", "/market recherche " + id, "Toutes les offres de cet objet", ChatFormatting.AQUA))
                .append(Msg.text("  ", ChatFormatting.GRAY))
                .append(Msg.button("[Courbe]", "/market courbe", "Graphique de l'offre et de la demande", ChatFormatting.YELLOW)));
        return null;
    }

    private static long suggestedPrice(ItemStack s) {
        String id = itemId(s);
        long avg = average(id, 7);
        if (avg > 0) return Math.max(MIN_PRICE, avg);
        long ask = averageAsk(id);
        if (ask > 0) return Math.max(MIN_PRICE, ask);
        long buy = Shop.buyPrice(s.getItem());
        if (buy > 0) return Math.max(MIN_PRICE, buy * 3 / 4);
        long sell = Shop.sellPrice(s.getItem());
        return Math.max(MIN_PRICE, sell * 2);
    }

    // ------------------------------------------------------------------ Interface principale

    private static List<MarketListing> filtered(ServerPlayer player, View v) {
        String q = v.query.toLowerCase(Locale.ROOT);
        String me = player.getUUID().toString();
        List<MarketListing> out = new ArrayList<>();
        for (MarketListing l : listings()) {
            if (v.mine) {
                if (l.seller.equals(me)) out.add(l);
                continue;
            }
            if (!q.isEmpty()) {
                String id = l.item.toLowerCase(Locale.ROOT);
                String path = id.contains(":") ? id.substring(id.indexOf(':') + 1).replace('_', ' ') : id;
                boolean match = l.name.toLowerCase(Locale.ROOT).contains(q) || id.contains(q) || path.contains(q.replace('_', ' '))
                        || l.sellerName.toLowerCase(Locale.ROOT).equals(q);
                if (!match) continue;
            }
            out.add(l);
        }
        Map<String, Long> avgs = new HashMap<>();
        Comparator<MarketListing> c = switch (v.sort) {
            case RECENT -> Comparator.comparingLong((MarketListing l) -> -l.created);
            case PRIX -> Comparator.comparingLong((MarketListing l) -> l.unitPrice);
            case PRIX_DESC -> Comparator.comparingLong((MarketListing l) -> -l.unitPrice);
            case QUANTITE -> Comparator.comparingInt((MarketListing l) -> -l.count);
            case NOM -> Comparator.comparing((MarketListing l) -> l.name.toLowerCase(Locale.ROOT));
            case AFFAIRE -> Comparator.comparingDouble((MarketListing l) -> {
                long ref = avgs.computeIfAbsent(l.item, k -> reference(k));
                return ref <= 0 ? Double.MAX_VALUE : (double) l.unitPrice / ref;
            });
        };
        out.sort(c.thenComparingLong(l -> l.unitPrice));
        return out;
    }

    /** Prix de référence : moyenne des ventes sur 7 jours, sinon prix d'achat en boutique. */
    private static long reference(String id) {
        long avg = average(id, 7);
        if (avg > 0) return avg;
        Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
        return Shop.buyPrice(item);
    }

    public static void open(ServerPlayer player) {
        View v = view(player);
        MinecraftServer server = player.level().getServer();
        PlayerProfile me = GodData.get(player);
        List<MarketListing> list = filtered(player, v);
        int pages = Math.max(1, (list.size() + SLOTS.length - 1) / SLOTS.length);
        v.page = Math.max(0, Math.min(v.page, pages - 1));
        String title = v.mine ? "Mes ventes" : "Marché" + (v.query.isEmpty() ? "" : " « " + v.query + " »");
        Page page = new Page(title + " — " + Msg.num(me.money) + " pièces").frame(v.mine ? Items.ORANGE_STAINED_GLASS_PANE : Items.CYAN_STAINED_GLASS_PANE);

        int start = v.page * SLOTS.length;
        Map<String, List<String>> statsCache = new HashMap<>();
        for (int i = 0; i < SLOTS.length && start + i < list.size(); i++) {
            MarketListing l = list.get(start + i);
            ItemStack icon = stack(server, l);
            if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
            icon.setCount(Math.max(1, Math.min(64, l.count)));
            List<Component> lore = new ArrayList<>();
            ItemLore old = icon.get(DataComponents.LORE);
            if (old != null) lore.addAll(old.lines());
            lore.add(Page.line(""));
            lore.add(Page.line("§6Prix : " + Msg.money(l.unitPrice) + " l'unité"));
            if (l.count > 1) lore.add(Page.line("§6Lot : " + l.count + " × = " + Msg.money(l.unitPrice * l.count)));
            lore.add(Page.line("§7Vendeur : " + l.sellerName));
            lore.add(Page.line("§8Expire dans " + Msg.time(Math.max(0, EXPIRY_MS - (System.currentTimeMillis() - l.created))) + " · offre #" + l.id));
            long ref = average(l.item, 7);
            if (ref > 0) {
                long pct = Math.round((l.unitPrice - ref) * 100.0 / ref);
                lore.add(Page.line(pct <= -5 ? "§a" + pct + " % sous le prix moyen" : pct >= 5 ? "§c+" + pct + " % au-dessus du prix moyen" : "§7Au prix moyen"));
            }
            for (String s : statsCache.computeIfAbsent(l.item, k -> statLines(k, l.cached == null ? Items.AIR : l.cached.getItem()))) lore.add(Page.line(s));
            lore.add(Page.line(""));
            if (v.mine) {
                lore.add(Page.line("§cClic : retirer la vente (objets rendus)"));
            } else if (l.seller.equals(player.getUUID().toString())) {
                lore.add(Page.line("§8C'est votre offre (voir « Mes ventes »)"));
            } else {
                lore.add(Page.line("§eClic gauche : tout acheter (" + Msg.money(l.unitPrice * l.count) + ")"));
                if (l.count > 1) lore.add(Page.line("§eClic droit : acheter 1 (" + Msg.money(l.unitPrice) + ")"));
            }
            lore.add(Page.line("§dShift-clic : courbe offre / demande"));
            icon.set(DataComponents.LORE, new ItemLore(lore));
            long id = l.id;
            String item = l.item;
            page.set(SLOTS[i], icon, (sp, c) -> {
                if (c == GodMenu.Click.SHIFT) {
                    later(sp, () -> openChart(sp, item, ChartMode.DEMANDE));
                    return;
                }
                String err = v.mine ? cancel(sp, id, false) : buy(sp, id, c == GodMenu.Click.RIGHT ? 1 : 0);
                if (err != null) Msg.err(sp, err);
                later(sp, () -> open(sp));
            });
        }
        if (list.isEmpty()) {
            page.set(22, Page.icon(Items.STRUCTURE_VOID, v.mine ? "Aucune vente en cours" : "Aucune offre", ChatFormatting.GRAY,
                    v.mine ? "§7Tenez un objet et tapez /market vendre <prix>" : v.query.isEmpty() ? "§7Soyez le premier à vendre !" : "§7Rien ne correspond à « " + v.query + " »",
                    "§8Astuce : /market recherche main cherche l'objet tenu"));
        }

        // En-tête.
        page.set(4, Page.icon(Items.BOOK, "Marché des joueurs", ChatFormatting.AQUA,
                "§7" + Msg.num(listings().size()) + " offre(s) en tout · " + list.size() + " affichée(s)",
                "§7Tri : §f" + v.sort.label, v.query.isEmpty() ? "§7Recherche : aucune" : "§7Recherche : §f" + v.query,
                "", "§7Vendre : tenez l'objet, §f/market vendre <prix> [quantité]",
                "§7Taxe : " + TAX + " pièce par objet vendu", "§7Les offres durent 7 jours",
                "§7Vos gains au marché : §6" + Msg.money(me.marketEarned)));

        // Barre du bas.
        if (v.page > 0) page.set(45, Page.icon(Items.ARROW, "← Page précédente", ChatFormatting.YELLOW, "§7Page " + v.page + " / " + pages),
                (sp, c) -> {
                    v.page--;
                    later(sp, () -> open(sp));
                });
        if (v.page < pages - 1) page.set(53, Page.icon(Items.ARROW, "Page suivante →", ChatFormatting.YELLOW, "§7Page " + (v.page + 2) + " / " + pages),
                (sp, c) -> {
                    v.page++;
                    later(sp, () -> open(sp));
                });
        List<String> sortLore = new ArrayList<>();
        for (Sort s : Sort.values()) sortLore.add((s == v.sort ? "§a▶ " : "§7  ") + s.label);
        sortLore.add("");
        sortLore.add("§eClic gauche : suivant · clic droit : précédent");
        page.set(46, Page.icon(Items.HOPPER, "Trier : " + v.sort.label, ChatFormatting.GOLD, sortLore.toArray(new String[0])),
                (sp, c) -> {
                    Sort[] all = Sort.values();
                    v.sort = all[Math.floorMod(v.sort.ordinal() + (c == GodMenu.Click.RIGHT ? -1 : 1), all.length)];
                    v.page = 0;
                    later(sp, () -> open(sp));
                });
        page.set(47, Page.icon(Items.OAK_SIGN, "Rechercher", ChatFormatting.AQUA,
                        v.query.isEmpty() ? "§7Aucune recherche" : "§7Actuelle : §f" + v.query,
                        "§eClic gauche : taper une recherche", "§eClic droit : objet tenu en main", v.query.isEmpty() ? "" : "§cShift-clic : effacer"),
                (sp, c) -> {
                    if (c == GodMenu.Click.SHIFT) {
                        v.query = "";
                        v.page = 0;
                        later(sp, () -> open(sp));
                    } else if (c == GodMenu.Click.RIGHT) {
                        later(sp, () -> search(sp, "main"));
                    } else {
                        sp.closeContainer();
                        Msg.send(sp, Msg.prefix().append(Msg.suggest("[Cliquez ici puis tapez votre recherche]", "/market recherche ",
                                "Nom ou identifiant (diamond, épée…), ou un pseudo", ChatFormatting.AQUA)));
                    }
                });
        ItemStack hand = player.getMainHandItem();
        if (sellable(hand)) {
            long sug = suggestedPrice(hand);
            page.set(48, Page.icon(Items.EMERALD, "Vendre l'objet en main", ChatFormatting.GREEN,
                            "§7" + hand.getCount() + " × " + hand.getHoverName().getString(), "§7Prix conseillé : " + Msg.money(sug) + " l'unité",
                            "", "§eCliquez pour choisir le prix"),
                    (sp, c) -> {
                        sp.closeContainer();
                        Msg.send(sp, Msg.prefix().append(Msg.suggest("[Cliquez ici, ajustez le prix et validez]", "/market vendre " + sug + " ",
                                "/market vendre <prix à l'unité> [quantité]", ChatFormatting.GREEN)));
                    });
        } else {
            page.set(48, Page.icon(Items.EMERALD, "Vendre", ChatFormatting.GREEN, "§7Tenez en main l'objet à vendre,", "§7puis rouvrez /market"));
        }
        page.close();
        page.set(50, Page.icon(v.mine ? Items.COMPASS : Items.CHEST, v.mine ? "Toutes les offres" : "Mes ventes", ChatFormatting.GOLD,
                        "§7" + listings().stream().filter(l -> l.seller.equals(player.getUUID().toString())).count() + " / " + MAX_LISTINGS + " offres en cours"),
                (sp, c) -> {
                    v.mine = !v.mine;
                    v.page = 0;
                    later(sp, () -> open(sp));
                });
        if (!me.marketReturns.isEmpty()) {
            page.set(51, Page.icon(Items.ENDER_CHEST, "Objets à récupérer", ChatFormatting.LIGHT_PURPLE,
                            "§7" + me.marketReturns.size() + " lot(s) d'offres expirées ou retirées", "", "§eCliquez pour récupérer"),
                    (sp, c) -> {
                        if (deliver(sp, true) == 0) Msg.err(sp, "Libérez de la place dans votre inventaire.");
                        later(sp, () -> open(sp));
                    });
        }
        if (!hand.isEmpty()) {
            String hid = itemId(hand);
            page.set(52, Page.icon(Items.SPYGLASS, "Courbes : " + hand.getHoverName().getString(), ChatFormatting.YELLOW,
                            "§7Offre, demande et prix moyen", "§7de l'objet tenu en main", "", "§eCliquez pour ouvrir"),
                    (sp, c) -> later(sp, () -> openChart(sp, hid, ChartMode.DEMANDE)));
        }
        page.open(player);
    }

    // ------------------------------------------------------------------ Graphique offre / demande / prix

    public static void openChart(ServerPlayer player, String id, ChartMode mode) {
        Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
        String name = new ItemStack(item).getHoverName().getString();
        Page page = new Page("Courbe — " + name);
        int days = 9;
        long[] offer = series(id, days, 0), demand = series(id, days, 1), price = series(id, days, 2);
        long[] shown = mode == ChartMode.OFFRE ? offer : mode == ChartMode.DEMANDE ? demand : price;
        long max = 0, min = Long.MAX_VALUE;
        for (long s : shown) {
            if (s < 0) continue;
            max = Math.max(max, s);
            min = Math.min(min, s);
        }
        LocalDate today = LocalDate.parse(QuestSystem.today());
        for (int col = 0; col < days; col++) {
            long val = shown[col];
            int h;
            if (val < 0 || max == 0) h = 0;
            else if (mode == ChartMode.PRIX) h = max == min ? 2 : 1 + (int) Math.round((double) (val - min) * 3 / (max - min));
            else h = val == 0 ? 0 : Math.max(1, (int) Math.round((double) val * 4 / max));
            LocalDate d = today.minusDays(days - 1 - col);
            String date = (col == days - 1 ? "Aujourd'hui" : String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue()));
            String[] lore = {
                    "§aOffre : " + Msg.num(offer[col]) + " mis en vente",
                    "§cDemande : " + Msg.num(demand[col]) + " acheté(s)",
                    price[col] < 0 ? "§8Prix moyen : aucune vente" : "§ePrix moyen : " + Msg.money(price[col])};
            for (int level = 1; level <= 4; level++) {
                int slot = (5 - level) * 9 + col; // ligne 4 = bas du graphique
                boolean filled = level <= h;
                Item it = !filled ? Items.BLACK_STAINED_GLASS_PANE : level == h ? mode.top : mode.pane;
                page.set(slot, Page.icon(it, date, filled ? mode.color : ChatFormatting.DARK_GRAY, lore));
            }
        }
        // Ligne du haut : l'objet et ses chiffres.
        List<String> info = new ArrayList<>(statLines(id, item));
        info.add("");
        info.add("§7Ventes 7 j : " + Msg.num(sum(series(id, 7, 1))) + " · mises en vente 7 j : " + Msg.num(sum(series(id, 7, 0))));
        long d7 = sum(series(id, 7, 1)), o7 = sum(series(id, 7, 0));
        if (d7 + o7 > 0) {
            info.add(d7 > o7 ? "§aLa demande dépasse l'offre : bon moment pour vendre"
                    : d7 * 2 < o7 ? "§cL'offre dépasse la demande : les prix risquent de baisser" : "§7Offre et demande équilibrées");
        }
        page.set(4, Page.icon(item == Items.AIR ? Items.BARRIER : item, 1, name, ChatFormatting.AQUA,
                info.stream().map(s -> (Component) Page.line(s)).toList(), true));
        page.set(0, Page.icon(Items.ARROW, "← Marché", ChatFormatting.YELLOW), (sp, c) -> later(sp, () -> open(sp)));
        page.set(2, Page.icon(Items.PAPER, "Graphique : " + mode.label, mode.color,
                "§79 derniers jours, de gauche à droite", mode == ChartMode.PRIX ? "§7Hauteur : prix moyen de vente (min → max)" : "§7Hauteur : quantité (max : " + Msg.num(max) + ")",
                "§7Survolez une colonne pour les chiffres"));
        page.set(6, Page.icon(Items.COMPASS, "Offres de cet objet", ChatFormatting.AQUA, "§7" + Msg.num(supply(id)) + " en vente", "", "§eCliquez pour voir"),
                (sp, c) -> later(sp, () -> {
                    View v = view(sp);
                    v.query = id;
                    v.mine = false;
                    v.page = 0;
                    open(sp);
                }));
        // Ligne du bas : choix de la courbe.
        int[] slots = {47, 49, 51};
        ChartMode[] modes = ChartMode.values();
        for (int i = 0; i < modes.length; i++) {
            ChartMode m = modes[i];
            page.set(slots[i], Page.icon(m.top, 1, m.label, m.color, List.of(Page.line(m == mode ? "§a▶ Affiché" : "§eCliquez pour afficher")), m == mode),
                    (sp, c) -> later(sp, () -> openChart(sp, id, m)));
        }
        page.set(8, Page.icon(Items.BARRIER, "Fermer", ChatFormatting.RED), (sp, c) -> sp.closeContainer());
        page.open(player);
    }

    private static long sum(long[] v) {
        long s = 0;
        for (long x : v) if (x > 0) s += x;
        return s;
    }

    /** /market courbe [objet] : ouvre le graphique de l'objet tenu en main ou donné. */
    public static String chart(ServerPlayer p, String item) {
        String id;
        if (item == null || item.isBlank()) {
            ItemStack hand = p.getMainHandItem();
            if (hand.isEmpty()) return "Tenez un objet en main ou précisez-le : /market courbe diamond";
            id = itemId(hand);
        } else {
            String k = item.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!k.contains(":")) k = "minecraft:" + k;
            var key = net.minecraft.resources.Identifier.tryParse(k);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return "Objet inconnu : " + item + " (identifiant anglais, ex. diamond, iron_ingot).";
            id = k;
        }
        openChart(p, id, ChartMode.DEMANDE);
        return null;
    }

    /** Identifiants des objets actuellement en vente (suggestions de commande). */
    public static List<String> listedItems() {
        List<String> ids = new ArrayList<>();
        for (MarketListing l : listings()) {
            String s = l.item.startsWith("minecraft:") ? l.item.substring(10) : l.item;
            if (!ids.contains(s)) ids.add(s);
        }
        return ids;
    }

    public static List<String> myIds(ServerPlayer p) {
        List<String> ids = new ArrayList<>();
        for (MarketListing l : listings()) if (l.seller.equals(p.getUUID().toString())) ids.add(String.valueOf(l.id));
        return ids;
    }
}
