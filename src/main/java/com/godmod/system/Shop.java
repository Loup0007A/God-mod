package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Specialization;
import com.godmod.util.DivineItems;
import com.godmod.util.Msg;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Boutique : items du Nether et de l'End (dimensions désactivées), ressources, artefacts,
 * et une section premium (Gemmes) activable avec /pay enable.
 * Le prix de revente est 5 à 10 fois inférieur au prix d'achat (fixe pour chaque item).
 */
public final class Shop {
    public enum Category {
        NETHER("Nether", Items.NETHERRACK),
        END("End", Items.END_STONE),
        RESSOURCES("Ressources", Items.IRON_INGOT),
        DIVIN("Divin", Items.NETHER_STAR);

        public final String label;
        public final Item icon;

        Category(String label, Item icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public record Entry(Supplier<ItemStack> stack, long price, Category category) {
        public Item item() {
            return stack.get().getItem();
        }

        public boolean special() {
            return DivineItems.isDivine(stack.get());
        }
    }

    public record PremiumOffer(String id, String label, String description, long gems, Item icon) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<Item, Long> SELL_ONLY = new HashMap<>();
    public static final Map<String, PremiumOffer> PREMIUM = new LinkedHashMap<>();

    static {
        // Nether
        add(Category.NETHER, () -> Items.NETHERRACK, 2);
        add(Category.NETHER, () -> Items.NETHER_BRICKS, 5);
        add(Category.NETHER, () -> Items.BLACKSTONE, 3);
        add(Category.NETHER, () -> Items.BASALT, 3);
        add(Category.NETHER, () -> Items.SOUL_SAND, 6);
        add(Category.NETHER, () -> Items.CRIMSON_STEM, 6);
        add(Category.NETHER, () -> Items.WARPED_STEM, 6);
        add(Category.NETHER, () -> Items.GLOWSTONE_DUST, 10);
        add(Category.NETHER, () -> Items.QUARTZ, 12);
        add(Category.NETHER, () -> Items.SHROOMLIGHT, 20);
        add(Category.NETHER, () -> Items.NETHER_WART, 25);
        add(Category.NETHER, () -> Items.MAGMA_CREAM, 40);
        add(Category.NETHER, () -> Items.CRYING_OBSIDIAN, 40);
        add(Category.NETHER, () -> Items.BLAZE_ROD, 60);
        add(Category.NETHER, () -> Items.GHAST_TEAR, 120);
        add(Category.NETHER, () -> Items.NETHERITE_SCRAP, 1500);
        add(Category.NETHER, () -> Items.ANCIENT_DEBRIS, 1800);
        add(Category.NETHER, () -> Items.WITHER_SKELETON_SKULL, 2500);
        // End
        add(Category.END, () -> Items.END_STONE, 3);
        add(Category.END, () -> Items.PURPUR_BLOCK, 8);
        add(Category.END, () -> Items.CHORUS_FRUIT, 15);
        add(Category.END, () -> Items.END_ROD, 30);
        add(Category.END, () -> Items.ENDER_PEARL, 40);
        add(Category.END, () -> Items.ENDER_EYE, 90);
        add(Category.END, () -> Items.DRAGON_BREATH, 300);
        add(Category.END, () -> Items.SHULKER_SHELL, 800);
        add(Category.END, () -> Items.DRAGON_HEAD, 5000);
        add(Category.END, () -> Items.ELYTRA, 15000);
        // Ressources
        add(Category.RESSOURCES, () -> Items.COBBLESTONE, 1);
        add(Category.RESSOURCES, () -> Items.DIRT, 1);
        add(Category.RESSOURCES, () -> Items.OAK_LOG, 4);
        add(Category.RESSOURCES, () -> Items.COAL, 5);
        add(Category.RESSOURCES, () -> Items.BREAD, 4);
        add(Category.RESSOURCES, () -> Items.COOKED_BEEF, 8);
        add(Category.RESSOURCES, () -> Items.REDSTONE, 4);
        add(Category.RESSOURCES, () -> Items.LAPIS_LAZULI, 6);
        add(Category.RESSOURCES, () -> Items.IRON_INGOT, 20);
        add(Category.RESSOURCES, () -> Items.GOLD_INGOT, 30);
        add(Category.RESSOURCES, () -> Items.EMERALD, 120);
        add(Category.RESSOURCES, () -> Items.DIAMOND, 200);
        add(Category.RESSOURCES, () -> Items.OBSIDIAN, 25);
        add(Category.RESSOURCES, () -> Items.EXPERIENCE_BOTTLE, 50);
        add(Category.RESSOURCES, () -> Items.GOLDEN_APPLE, 250);
        // Divin
        ENTRIES.add(new Entry(DivineItems::codex, 50, Category.DIVIN));
        ENTRIES.add(new Entry(() -> DivineItems.artifact(Specialization.OCEAN), 20000, Category.DIVIN));
        ENTRIES.add(new Entry(() -> DivineItems.artifact(Specialization.CIEL), 20000, Category.DIVIN));
        ENTRIES.add(new Entry(() -> DivineItems.artifact(Specialization.COSMOS), 20000, Category.DIVIN));
        // Générateurs de créatures.
        ENTRIES.add(new Entry(() -> com.godmod.progress.SpawnerSystem.item("minecraft:chicken"), 15000, Category.DIVIN));
        ENTRIES.add(new Entry(() -> com.godmod.progress.SpawnerSystem.item("minecraft:cow"), 25000, Category.DIVIN));
        ENTRIES.add(new Entry(() -> com.godmod.progress.SpawnerSystem.item("minecraft:zombie"), 30000, Category.DIVIN));
        ENTRIES.add(new Entry(() -> com.godmod.progress.SpawnerSystem.item("minecraft:skeleton"), 35000, Category.DIVIN));
        ENTRIES.add(new Entry(() -> com.godmod.progress.SpawnerSystem.item("minecraft:blaze"), 80000, Category.DIVIN));
        ENTRIES.add(new Entry(() -> com.godmod.progress.SpawnerSystem.item("minecraft:iron_golem"), 150000, Category.DIVIN));

        // Items revendables uniquement (récoltés dans le monde principal).
        SELL_ONLY.put(Items.WHEAT, 3L);
        SELL_ONLY.put(Items.CARROT, 3L);
        SELL_ONLY.put(Items.POTATO, 3L);
        SELL_ONLY.put(Items.STRING, 6L);
        SELL_ONLY.put(Items.BONE, 6L);
        SELL_ONLY.put(Items.ROTTEN_FLESH, 2L);
        SELL_ONLY.put(Items.GUNPOWDER, 15L);
        SELL_ONLY.put(Items.LEATHER, 10L);
        SELL_ONLY.put(Items.COPPER_INGOT, 8L);
        SELL_ONLY.put(Items.RAW_IRON, 15L);
        SELL_ONLY.put(Items.RAW_GOLD, 22L);
        SELL_ONLY.put(Items.STONE, 1L);
        SELL_ONLY.put(Items.SAND, 1L);
        SELL_ONLY.put(Items.TOTEM_OF_UNDYING, 3000L);

        premium("boost", "Bénédiction ×2", "Essence doublée pendant 1 heure", 10, Items.EXPERIENCE_BOTTLE);
        premium("money", "Trésor divin", "+5 000 pièces", 15, Items.GOLD_BLOCK);
        premium("artifact", "Artefact de domaine", "L'artefact de votre spécialisation", 40, Items.NETHER_STAR);
        premium("respec", "Renaissance", "Réinitialise vos caractéristiques et votre spécialisation", 20, Items.TOTEM_OF_UNDYING);
        premium("pardon", "Absolution", "Efface le statut de Parjure", 25, Items.GOLDEN_APPLE);
    }

    private Shop() {
    }

    private static void add(Category c, Supplier<Item> item, long price) {
        ENTRIES.add(new Entry(() -> new ItemStack(item.get()), price, c));
    }

    private static void premium(String id, String label, String desc, long gems, Item icon) {
        PREMIUM.put(id, new PremiumOffer(id, label, desc, gems, icon));
    }

    public static List<Entry> entries(Category c) {
        List<Entry> list = new ArrayList<>();
        for (Entry e : ENTRIES) if (e.category() == c) list.add(e);
        return list;
    }

    /** Ratio achat/revente : entre 5 et 10, fixe pour chaque item. */
    public static int ratio(Item item) {
        String key = BuiltInRegistries.ITEM.getKey(item).toString();
        return 5 + Math.floorMod(key.hashCode(), 6);
    }

    public static long buyPrice(Item item) {
        for (Entry e : ENTRIES) if (!e.special() && e.item() == item) return e.price();
        return -1;
    }

    /** Prix de revente unitaire, ou 0 si l'item n'est pas racheté. */
    public static long sellPrice(Item item) {
        long buy = buyPrice(item);
        if (buy > 0) return Math.max(1, buy / ratio(item));
        Long base = SELL_ONLY.get(item);
        return base == null ? 0 : Math.max(1, base * 2 / ratio(item));
    }

    public static String buy(ServerPlayer player, Entry entry, int count) {
        ItemStack proto = entry.stack().get();
        count = Math.max(1, Math.min(count, proto.getMaxStackSize()));
        long total = entry.price() * count;
        PlayerProfile p = GodData.get(player);
        if (p.money < total) return "Pas assez d'argent (" + p.money + "/" + total + ").";
        p.money -= total;
        GodData.dirty();
        ItemStack bought = proto.copy();
        bought.setCount(count);
        give(player, bought);
        Msg.ok(player, "Acheté : " + count + " × " + proto.getHoverName().getString() + " pour " + total + " pièces.");
        return null;
    }

    /** Vend la pile donnée (en totalité ou une seule unité). */
    public static String sell(ServerPlayer player, ItemStack stack, boolean all) {
        if (stack.isEmpty()) return "Rien à vendre.";
        if (DivineItems.isDivine(stack)) return "Les objets divins et les générateurs ne peuvent pas être revendus.";
        long unit = sellPrice(stack.getItem());
        if (unit <= 0) return "Le marchand divin ne rachète pas cet objet.";
        int n = all ? stack.getCount() : 1;
        String name = stack.getHoverName().getString();
        stack.shrink(n);
        PlayerProfile p = GodData.get(player);
        PowerSystem.earnMoney(player.level().getServer(), p, unit * n);
        Msg.ok(player, "Vendu : " + n + " × " + name + " pour " + (unit * n) + " pièces.");
        return null;
    }

    public static String buyPremium(ServerPlayer player, String id) {
        if (!GodData.settings().payEnabled) return "La boutique premium est désactivée sur ce serveur.";
        PremiumOffer o = PREMIUM.get(id);
        if (o == null) return "Offre inconnue.";
        PlayerProfile p = GodData.get(player);
        if (p.gems < o.gems()) return "Pas assez de Gemmes (" + p.gems + "/" + o.gems() + ").";
        switch (id) {
            case "boost" -> p.boostUntil = Math.max(System.currentTimeMillis(), p.boostUntil) + 3600_000L;
            case "money" -> p.money += 5000;
            case "artifact" -> {
                if (p.specialization == Specialization.AUCUNE) return "Choisissez d'abord une spécialisation (palier Dieu).";
                give(player, DivineItems.artifact(p.specialization));
            }
            case "respec" -> {
                p.stats.clear();
                p.specialization = Specialization.AUCUNE;
                PowerSystem.applyAttributes(player);
            }
            case "pardon" -> {
                p.oathbreakerUntil = 0;
                PowerSystem.applyAttributes(player);
            }
            default -> {
                return "Offre inconnue.";
            }
        }
        p.gems -= o.gems();
        GodData.dirty();
        GodData.log("PREMIUM : " + p.name + " achète " + o.label() + " (" + o.gems() + " gemmes)");
        Msg.ok(player, "Merci ! " + o.label() + " activé.");
        return null;
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
