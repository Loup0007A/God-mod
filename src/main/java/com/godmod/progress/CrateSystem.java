package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Specialization;
import com.godmod.menu.GodMenu;
import com.godmod.menu.Page;
import com.godmod.system.Shop;
import com.godmod.util.DivineItems;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Caisses du Sanctuaire : Commune, Rare, Épique, Divine.
 * Elles s'ouvrent avec des clés (virtuelles, impossibles à dupliquer) gagnées par les métiers,
 * les quêtes, la pêche, la boutique… L'ouverture lance une roulette animée.
 */
public final class CrateSystem {
    private static final Random RANDOM = new Random();

    public enum Crate {
        COMMUNE("Commune", ChatFormatting.WHITE, Items.CHEST, 2500),
        RARE("Rare", ChatFormatting.BLUE, Items.BARREL, 10000),
        EPIQUE("Épique", ChatFormatting.DARK_PURPLE, Items.ENDER_CHEST, 40000),
        DIVINE("Divine", ChatFormatting.GOLD, Items.NETHER_STAR, 150000);

        public final String label;
        public final ChatFormatting color;
        public final Item icon;
        /** Prix d'une clé à la boutique (en pièces). */
        public final long keyPrice;

        Crate(String label, ChatFormatting color, Item icon, long keyPrice) {
            this.label = label;
            this.color = color;
            this.icon = icon;
            this.keyPrice = keyPrice;
        }

        public static Crate parse(String s) {
            for (Crate c : values()) if (c.name().equalsIgnoreCase(s) || c.label.equalsIgnoreCase(s)) return c;
            if (s.equalsIgnoreCase("epique")) return EPIQUE;
            return null;
        }
    }

    /** Une récompense possible : son icône, son poids (probabilité relative) et ce qu'elle donne. */
    public record Reward(String label, Supplier<ItemStack> icon, int weight, BiConsumer<ServerPlayer, PlayerProfile> give) {
    }

    private static Reward item(Item item, int count, int weight) {
        return new Reward(count + " × " + new ItemStack(item).getHoverName().getString(), () -> new ItemStack(item, count), weight,
                (sp, p) -> Shop.give(sp, new ItemStack(item, count)));
    }

    private static Reward money(long amount, int weight) {
        return new Reward(amount + " pièces", () -> new ItemStack(Items.GOLD_NUGGET), weight, (sp, p) -> p.money += amount);
    }

    private static Reward essence(long amount, int weight) {
        return new Reward(amount + " Essence divine", () -> new ItemStack(Items.EXPERIENCE_BOTTLE), weight, (sp, p) -> p.essence += amount);
    }

    private static Reward key(Crate c, int weight) {
        return new Reward("Clé " + c.label, () -> new ItemStack(Items.TRIPWIRE_HOOK), weight, (sp, p) -> addKey(p, c, 1));
    }

    private static Reward spawner(String mob, int weight) {
        return new Reward("Générateur : " + SpawnerSystem.mobName(mob), () -> SpawnerSystem.item(mob), weight,
                (sp, p) -> Shop.give(sp, SpawnerSystem.item(mob)));
    }

    private static Reward artifact(int weight) {
        return new Reward("Artefact divin", () -> new ItemStack(Items.TRIDENT), weight, (sp, p) -> {
            Specialization s = p.specialization != Specialization.AUCUNE ? p.specialization
                    : Specialization.values()[1 + RANDOM.nextInt(Specialization.values().length - 1)];
            Shop.give(sp, DivineItems.artifact(s));
        });
    }

    public static List<Reward> rewards(Crate c) {
        return switch (c) {
            case COMMUNE -> List.of(
                    money(400, 20), money(1000, 10), essence(150, 15), essence(350, 8),
                    item(Items.IRON_INGOT, 16, 12), item(Items.GOLD_INGOT, 8, 8), item(Items.DIAMOND, 2, 6),
                    item(Items.GOLDEN_APPLE, 2, 6), item(Items.EXPERIENCE_BOTTLE, 16, 8), item(Items.COOKED_BEEF, 32, 8),
                    item(Items.OAK_LOG, 64, 6), spawner("minecraft:chicken", 2), spawner("minecraft:pig", 2), key(Crate.RARE, 2));
            case RARE -> List.of(
                    money(2500, 18), money(5000, 8), essence(600, 14), essence(1200, 6),
                    item(Items.DIAMOND, 6, 12), item(Items.EMERALD, 12, 8), item(Items.ENCHANTED_GOLDEN_APPLE, 1, 4),
                    item(Items.ENDER_PEARL, 16, 8), item(Items.BLAZE_ROD, 12, 6), item(Items.NAME_TAG, 2, 4),
                    spawner("minecraft:cow", 4), spawner("minecraft:zombie", 4), spawner("minecraft:skeleton", 3),
                    spawner("minecraft:spider", 3), key(Crate.EPIQUE, 2));
            case EPIQUE -> List.of(
                    money(10000, 16), money(20000, 6), essence(2000, 12), essence(4000, 5),
                    item(Items.DIAMOND_BLOCK, 3, 8), item(Items.NETHERITE_SCRAP, 3, 6), item(Items.TOTEM_OF_UNDYING, 1, 6),
                    item(Items.ENCHANTED_GOLDEN_APPLE, 3, 5), item(Items.SHULKER_SHELL, 4, 5),
                    spawner("minecraft:creeper", 5), spawner("minecraft:blaze", 4), spawner("minecraft:sheep", 4),
                    spawner("minecraft:cave_spider", 4), key(Crate.DIVINE, 2));
            case DIVINE -> List.of(
                    money(40000, 14), essence(8000, 12), item(Items.NETHERITE_INGOT, 2, 10), item(Items.ELYTRA, 1, 6),
                    item(Items.BEACON, 1, 6), item(Items.DIAMOND_BLOCK, 12, 10), spawner("minecraft:iron_golem", 5),
                    spawner("minecraft:blaze", 8), spawner("minecraft:enderman", 6), artifact(6));
        };
    }

    // ------------------------------------------------------------------ Clés

    public static int keys(PlayerProfile p, Crate c) {
        return p.keys.getOrDefault(c.name(), 0);
    }

    public static void addKey(PlayerProfile p, Crate c, int n) {
        p.keys.merge(c.name(), n, Integer::sum);
        if (p.keys.get(c.name()) <= 0) p.keys.remove(c.name());
        GodData.dirty();
    }

    public static int totalKeys(PlayerProfile p) {
        int n = 0;
        for (int v : p.keys.values()) n += v;
        return n;
    }

    // ------------------------------------------------------------------ Aperçu et ouverture

    /** Rareté d'une récompense selon sa probabilité. */
    public record Rarity(String label, ChatFormatting color, String code, boolean glow) {
    }

    public static Rarity rarity(double pct) {
        if (pct >= 12) return new Rarity("Commun", ChatFormatting.GRAY, "§7", false);
        if (pct >= 6) return new Rarity("Peu commun", ChatFormatting.GREEN, "§a", false);
        if (pct >= 3) return new Rarity("Rare", ChatFormatting.AQUA, "§b", false);
        if (pct >= 1.5) return new Rarity("Épique", ChatFormatting.LIGHT_PURPLE, "§d", true);
        return new Rarity("Légendaire", ChatFormatting.GOLD, "§6", true);
    }

    private static Item paneFor(Crate c) {
        return switch (c) {
            case COMMUNE -> Items.WHITE_STAINED_GLASS_PANE;
            case RARE -> Items.BLUE_STAINED_GLASS_PANE;
            case EPIQUE -> Items.PURPLE_STAINED_GLASS_PANE;
            case DIVINE -> Items.YELLOW_STAINED_GLASS_PANE;
        };
    }

    private static Item darkPane(Crate c) {
        return switch (c) {
            case COMMUNE -> Items.LIGHT_GRAY_STAINED_GLASS_PANE;
            case RARE -> Items.LIGHT_BLUE_STAINED_GLASS_PANE;
            case EPIQUE -> Items.MAGENTA_STAINED_GLASS_PANE;
            case DIVINE -> Items.ORANGE_STAINED_GLASS_PANE;
        };
    }

    /** Cadre bicolore aux couleurs de la caisse (alternance des deux teintes, décalée par {@code phase}). */
    private static void paintFrame(SimpleContainer box, Crate c, int phase, java.util.Set<Integer> skip) {
        for (int i = 0; i < GodMenu.SIZE; i++) {
            int row = i / 9, col = i % 9;
            boolean edge = row == 0 || row == GodMenu.ROWS - 1 || col == 0 || col == 8;
            if (!edge || skip.contains(i)) continue;
            Item pane = ((row + col + phase) % 2 == 0) ? paneFor(c) : darkPane(c);
            box.setItem(i, Page.icon(pane, " ", ChatFormatting.DARK_GRAY));
        }
    }

    public static void openPreview(ServerPlayer player, Crate c) {
        PlayerProfile p = GodData.get(player);
        List<Reward> list = new ArrayList<>(rewards(c));
        int total = 0;
        for (Reward r : list) total += r.weight();
        list.sort((x, y) -> Integer.compare(y.weight(), x.weight()));
        int keys = keys(p, c);
        Page page = new Page("✦ Caisse " + c.label + " ✦");
        java.util.Set<Integer> reserved = java.util.Set.of(4, 45, 47, 49, 51, 53);
        paintFrame(page.container(), c, 0, reserved);

        // En-tête : la caisse.
        page.set(4, Page.icon(c.icon, 1, "Caisse " + c.label, c.color, List.of(
                Page.line("§7Vos clés : " + (keys > 0 ? "§a" : "§c") + keys),
                Page.line("§7" + list.size() + " récompenses possibles"),
                Page.line(""),
                Page.line("§8Clés : métiers, quêtes, votes, événements,"),
                Page.line("§8récompense du jour, /afkshop, /cles")), true));

        // Récompenses, de la plus fréquente à la plus rare.
        int[] inner = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < list.size() && i < inner.length; i++) {
            Reward r = list.get(i);
            ItemStack icon = r.icon().get();
            double pct = 100.0 * r.weight() / total;
            Rarity rar = rarity(pct);
            page.set(inner[i], Page.icon(icon.getItem(), Math.max(1, icon.getCount()), r.label(), rar.color(), List.of(
                    Page.line(rar.code() + "✦ " + rar.label()),
                    Page.line(String.format(java.util.Locale.ROOT, "§7Chance : §f%.1f %%", pct)),
                    Page.line("§8" + chanceBar(pct))), rar.glow()));
        }

        // Boutons.
        boolean can = keys > 0;
        page.set(49, Page.icon(can ? Items.TRIPWIRE_HOOK : Items.BARRIER, 1, can ? "Ouvrir (1 clé)" : "Aucune clé " + c.label,
                can ? ChatFormatting.GREEN : ChatFormatting.RED, List.of(
                        Page.line("§7Lance la roulette"),
                        Page.line(can ? "§a" + keys + " clé(s) disponible(s)" : "§7Prix d'une clé : " + Msg.money(c.keyPrice) + " (/cles)")), can),
                (sp, click) -> {
                    String err = open(sp, c);
                    if (err != null) Msg.err(sp, err);
                });
        if (keys >= 5) {
            page.set(51, Page.icon(Items.HOPPER, 5, "Ouverture rapide ×5", ChatFormatting.GOLD, List.of(
                    Page.line("§7Ouvre 5 caisses d'un coup, sans roulette"),
                    Page.line("§75 clés " + c.label)), true), (sp, click) -> {
                String err = openMany(sp, c, 5);
                if (err != null) Msg.err(sp, err);
                sp.level().getServer().execute(() -> openPreview(sp, c));
            });
        }
        page.set(47, Page.icon(Items.KNOWLEDGE_BOOK, 1, "Raretés", ChatFormatting.YELLOW, List.of(
                Page.line("§7✦ Commun §8≥ 12 %"), Page.line("§a✦ Peu commun §8≥ 6 %"), Page.line("§b✦ Rare §8≥ 3 %"),
                Page.line("§d✦ Épique §8≥ 1,5 %"), Page.line("§6✦ Légendaire §8< 1,5 %")), false));
        page.set(53, Page.icon(Items.BARRIER, "Fermer", ChatFormatting.RED), (sp, click) -> sp.closeContainer());
        page.open(player);
    }

    private static String chanceBar(double pct) {
        int n = (int) Math.max(1, Math.min(10, Math.round(pct / 2.5)));
        return "▮".repeat(n) + "▯".repeat(10 - n);
    }

    /** Ouvre plusieurs caisses d'un coup (sans animation) et résume les gains. */
    public static String openMany(ServerPlayer player, Crate c, int n) {
        PlayerProfile p = GodData.get(player);
        if (keys(p, c) < n) return "Il vous faut " + n + " clés " + c.label + ".";
        if (isRolling(player)) return "Une ouverture est déjà en cours.";
        List<Reward> list = rewards(c);
        int total = 0;
        for (Reward r : list) total += r.weight();
        List<String> won = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            addKey(p, c, -1);
            p.cratesOpened++;
            Reward win = pick(list);
            win.give().accept(player, p);
            Rarity rar = rarity(100.0 * win.weight() / total);
            won.add(rar.code() + win.label());
            if (c.ordinal() >= Crate.EPIQUE.ordinal() && rar.glow())
                Msg.broadcast(player.level().getServer(), p.name + " a obtenu " + win.label() + " dans une caisse " + c.label + " !", c.color);
        }
        QuestSystem.progress(player, List.of("crate:any"), n);
        GodData.dirty();
        CrateDecor.onOpen(player, c);
        GodData.log("CAISSE : " + p.name + " ouvre " + n + " caisses " + c.label);
        Fx.sound(player, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
        Msg.send(player, Msg.text(n + " caisses " + c.label + " ouvertes :", c.color, ChatFormatting.BOLD));
        for (String w : won) Msg.send(player, com.godmod.menu.Page.line("  " + w));
        return null;
    }

    public static String open(ServerPlayer player, Crate c) {
        PlayerProfile p = GodData.get(player);
        if (keys(p, c) <= 0) return "Il vous faut une clé " + c.label + ".";
        if (ROLLS.stream().anyMatch(r -> r.player == player)) return "Une ouverture est déjà en cours.";
        addKey(p, c, -1);
        p.cratesOpened++;
        QuestSystem.progress(player, List.of("crate:any"), 1);
        GodData.dirty();
        List<Reward> list = rewards(c);
        Reward win = pick(list);

        Page page = new Page("✦ Caisse " + c.label + " ✦");
        paintFrame(page.container(), c, 0, java.util.Set.of());
        for (int i = 10; i <= 16; i++) page.set(i, Page.icon(Items.BLACK_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
        for (int i = 28; i <= 34; i++) page.set(i, Page.icon(Items.BLACK_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
        page.set(13, Page.icon(Items.HOPPER, "▼", ChatFormatting.GOLD));
        page.set(31, Page.icon(Items.HOPPER, "▲", ChatFormatting.GOLD));
        CrateDecor.onOpen(player, c);
        Roll roll = new Roll(player, c, page.container(), list, win);
        roll.fill();
        page.open(player);
        ROLLS.add(roll);
        GodData.log("CAISSE : " + p.name + " ouvre une caisse " + c.label);
        return null;
    }

    private static Reward pick(List<Reward> list) {
        int total = 0;
        for (Reward r : list) total += r.weight();
        int n = RANDOM.nextInt(total);
        for (Reward r : list) {
            n -= r.weight();
            if (n < 0) return r;
        }
        return list.get(0);
    }

    // ------------------------------------------------------------------ Roulette

    private static final List<Roll> ROLLS = new ArrayList<>();
    private static final int[] ROW = {19, 20, 21, 22, 23, 24, 25};
    private static final int CENTER = 3;

    private static final class Roll {
        final ServerPlayer player;
        final Crate crate;
        final SimpleContainer box;
        final List<Reward> strip = new ArrayList<>();
        final Reward win;
        int offset = 0;
        int steps;
        int wait = 0;
        int done = 0;
        boolean finished = false;

        Roll(ServerPlayer player, Crate crate, SimpleContainer box, List<Reward> list, Reward win) {
            this.player = player;
            this.crate = crate;
            this.box = box;
            this.win = win;
            this.steps = 28 + RANDOM.nextInt(6);
            for (int i = 0; i < steps + ROW.length + 2; i++) strip.add(pick(list));
            // La récompense tirée s'arrêtera pile au centre.
            strip.set(steps + CENTER, win);
        }

        void fill() {
            for (int i = 0; i < ROW.length; i++) {
                Reward r = strip.get(offset + i);
                ItemStack icon = r.icon().get();
                box.setItem(ROW[i], Page.icon(icon.getItem(), Math.max(1, icon.getCount()), r.label(),
                        i == CENTER ? ChatFormatting.GOLD : ChatFormatting.GRAY, List.of(), i == CENTER));
            }
            if (player.containerMenu != null) player.containerMenu.broadcastChanges();
        }

        /** Renvoie vrai quand la roulette est terminée. */
        boolean tick() {
            if (finished) return true;
            if (wait-- > 0) return false;
            if (done < steps) {
                offset++;
                done++;
                fill();
                if (done % 2 == 0) paintFrame(box, crate, done / 2, java.util.Set.of());
                Fx.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, 1.0F + 0.5F * done / steps);
                // Ralentit progressivement.
                wait = done < steps - 12 ? 1 : done < steps - 5 ? 3 : 6;
                return false;
            }
            finished = true;
            PlayerProfile p = GodData.get(player);
            // Révélation : cadre doré, la récompense au centre, mise en valeur.
            for (int i = 0; i < GodMenu.SIZE; i++) {
                int row = i / 9, col = i % 9;
                if (row == 0 || row == GodMenu.ROWS - 1 || col == 0 || col == 8)
                    box.setItem(i, Page.icon((row + col) % 2 == 0 ? Items.YELLOW_STAINED_GLASS_PANE : Items.LIME_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
            }
            ItemStack shown = win.icon().get();
            box.setItem(40, Page.icon(shown.getItem(), Math.max(1, shown.getCount()), "✦ " + win.label() + " ✦", ChatFormatting.GOLD,
                    List.of(Page.line("§7Gagné dans la caisse " + crate.label)), true));
            if (player.containerMenu != null) player.containerMenu.broadcastChanges();
            win.give().accept(player, p);
            GodData.dirty();
            Fx.sound(player, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
            Fx.particles(player, ParticleTypes.TOTEM_OF_UNDYING, 50, 0.8);
            Fx.title(player, Component.literal(win.label()).withStyle(crate.color, ChatFormatting.BOLD),
                    Component.literal("Caisse " + crate.label).withStyle(ChatFormatting.GRAY));
            Msg.ok(player, "Caisse " + crate.label + " : vous gagnez " + win.label() + " !");
            if (crate.ordinal() >= Crate.EPIQUE.ordinal()) {
                Msg.broadcast(player.level().getServer(), p.name + " a obtenu " + win.label() + " dans une caisse " + crate.label + " !", crate.color);
            }
            return true;
        }
    }

    public static void tick(MinecraftServer server) {
        Iterator<Roll> it = ROLLS.iterator();
        while (it.hasNext()) {
            Roll r = it.next();
            if (r.player.isRemoved() || r.tick()) it.remove();
        }
    }

    /** Joueur déconnecté pendant une roulette : il reçoit quand même sa récompense. */
    public static void onLogout(ServerPlayer player) {
        Iterator<Roll> it = ROLLS.iterator();
        while (it.hasNext()) {
            Roll r = it.next();
            if (r.player == player) {
                if (!r.finished) {
                    r.finished = true;
                    r.win.give().accept(player, GodData.get(player));
                    GodData.dirty();
                }
                it.remove();
            }
        }
    }

    public static boolean isRolling(ServerPlayer player) {
        for (Roll r : ROLLS) if (r.player == player) return true;
        return false;
    }

    /** Utilisé pour éviter qu'un clic dans la roulette ne fasse quoi que ce soit. */
    public static GodMenu.Action noop() {
        return (sp, c) -> {
        };
    }
}
