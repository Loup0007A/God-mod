package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Tier;
import com.godmod.menu.Page;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Kits (/kit) : un kit de départ unique, un kit quotidien qui grandit avec le palier,
 * et un kit d'équipement par palier (Éveillé → Titan) à récupérer chaque semaine.
 */
public final class Kits {
    public static final long ONCE = Long.MAX_VALUE;
    private static final long DAY = 24 * 3600_000L;

    public record Kit(String id, String label, Item icon, Tier tier, long cooldown, String description,
                      Function<PlayerProfile, List<ItemStack>> items) {
    }

    public static final Map<String, Kit> KITS = new LinkedHashMap<>();

    static {
        add("depart", "Kit de départ", Items.WOODEN_PICKAXE, Tier.MORTEL, ONCE, "Une seule fois : de quoi bien commencer", p -> List.of(
                st(Items.STONE_PICKAXE, 1), st(Items.STONE_AXE, 1), st(Items.STONE_SHOVEL, 1), st(Items.STONE_SWORD, 1),
                st(Items.LEATHER_HELMET, 1), st(Items.LEATHER_CHESTPLATE, 1), st(Items.LEATHER_LEGGINGS, 1), st(Items.LEATHER_BOOTS, 1),
                st(Items.BREAD, 16), st(Items.TORCH, 32), st(Items.OAK_LOG, 16), st(Items.WHITE_BED, 1)));
        add("quotidien", "Kit quotidien", Items.BREAD, Tier.MORTEL, DAY, "Chaque jour, plus riche à chaque palier", p -> {
            int t = p.tier.ordinal();
            List<ItemStack> l = new ArrayList<>();
            l.add(st(Items.COOKED_BEEF, 8 + 4 * t));
            l.add(st(Items.COAL, 8 + 4 * t));
            l.add(st(Items.IRON_INGOT, 2 + 2 * t));
            if (t >= 1) l.add(st(Items.GOLD_INGOT, 2 * t));
            if (t >= 2) l.add(st(Items.EXPERIENCE_BOTTLE, 4 * t));
            if (t >= 3) l.add(st(Items.DIAMOND, t - 1));
            if (t >= 4) l.add(st(Items.GOLDEN_APPLE, t - 3));
            if (t >= 5) l.add(st(Items.EMERALD, 4 * (t - 4)));
            return l;
        });
        add("eveille", "Kit de l'Éveillé", Items.IRON_PICKAXE, Tier.EVEILLE, 3 * DAY, "Tous les 3 jours : outils en fer", p -> List.of(
                st(Items.IRON_PICKAXE, 1), st(Items.IRON_AXE, 1), st(Items.IRON_SHOVEL, 1), st(Items.IRON_SWORD, 1),
                st(Items.SHIELD, 1), st(Items.COOKED_PORKCHOP, 16)));
        add("demidieu", "Kit du Demi-dieu", Items.IRON_CHESTPLATE, Tier.DEMI_DIEU, 7 * DAY, "Chaque semaine : armure de fer", p -> List.of(
                st(Items.IRON_HELMET, 1), st(Items.IRON_CHESTPLATE, 1), st(Items.IRON_LEGGINGS, 1), st(Items.IRON_BOOTS, 1),
                st(Items.GOLDEN_APPLE, 2), st(Items.BOW, 1), st(Items.ARROW, 32)));
        add("dieu", "Kit du Dieu", Items.DIAMOND_PICKAXE, Tier.DIEU, 7 * DAY, "Chaque semaine : outils en diamant", p -> List.of(
                st(Items.DIAMOND_PICKAXE, 1), st(Items.DIAMOND_AXE, 1), st(Items.DIAMOND_SWORD, 1), st(Items.DIAMOND_SHOVEL, 1),
                st(Items.GOLDEN_APPLE, 4), st(Items.ENDER_PEARL, 8)));
        add("supreme", "Kit du Dieu suprême", Items.DIAMOND_CHESTPLATE, Tier.DIEU_SUPREME, 7 * DAY, "Chaque semaine : armure de diamant", p -> List.of(
                st(Items.DIAMOND_HELMET, 1), st(Items.DIAMOND_CHESTPLATE, 1), st(Items.DIAMOND_LEGGINGS, 1), st(Items.DIAMOND_BOOTS, 1),
                st(Items.GOLDEN_APPLE, 6), st(Items.EXPERIENCE_BOTTLE, 32)));
        add("titan", "Kit du Titan", Items.NETHERITE_INGOT, Tier.TITAN, 7 * DAY, "Chaque semaine : trésors des transcendants", p -> List.of(
                st(Items.NETHERITE_INGOT, 2), st(Items.ENCHANTED_GOLDEN_APPLE, 1), st(Items.TOTEM_OF_UNDYING, 1),
                st(Items.DIAMOND_BLOCK, 4), st(Items.EXPERIENCE_BOTTLE, 64)));
    }

    private Kits() {
    }

    private static void add(String id, String label, Item icon, Tier tier, long cd, String desc, Function<PlayerProfile, List<ItemStack>> items) {
        KITS.put(id, new Kit(id, label, icon, tier, cd, desc, items));
    }

    private static ItemStack st(Item i, int n) {
        return new ItemStack(i, n);
    }

    /** Temps avant de pouvoir reprendre le kit (0 = disponible, -1 = déjà pris une fois pour toutes). */
    public static long waitLeft(PlayerProfile p, Kit k) {
        Long last = p.kits.get(k.id());
        if (last == null) return 0;
        if (k.cooldown() == ONCE) return -1;
        return Math.max(0, last + k.cooldown() - System.currentTimeMillis());
    }

    private static int freeSlots(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) n++;
        return n;
    }

    public static String claim(ServerPlayer player, String id) {
        Kit k = KITS.get(id.toLowerCase(java.util.Locale.ROOT));
        if (k == null) return "Kit inconnu. Liste : " + String.join(", ", KITS.keySet()) + ".";
        PlayerProfile p = GodData.get(player);
        if (!p.tier.atLeast(k.tier())) return k.label() + " : réservé au palier " + k.tier().label + " et au-delà.";
        long w = waitLeft(p, k);
        if (w < 0) return "Vous avez déjà reçu le " + k.label().toLowerCase() + ".";
        if (w > 0) return k.label() + " : de nouveau disponible dans " + Msg.time(w) + ".";
        List<ItemStack> items = k.items().apply(p);
        if (freeSlots(player) < items.size()) return "Il faut " + items.size() + " cases libres dans votre inventaire (vous en avez " + freeSlots(player) + ").";
        for (ItemStack s : items) Shop.give(player, s.copy());
        p.kits.put(k.id(), System.currentTimeMillis());
        GodData.dirty();
        GodData.log("KIT : " + p.name + " reçoit " + k.id());
        Msg.ok(player, k.label() + " reçu !" + (k.cooldown() == ONCE ? "" : " Prochain dans " + Msg.time(k.cooldown()) + "."));
        return null;
    }

    public static void open(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Kits").frame(Items.BROWN_STAINED_GLASS_PANE);
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        int i = 0;
        for (Kit k : KITS.values()) {
            if (i >= slots.length) break;
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§7" + k.description()));
            lore.add(Page.line(""));
            for (ItemStack s : k.items().apply(p)) lore.add(Page.line("§f• " + s.getCount() + " × " + s.getHoverName().getString()));
            lore.add(Page.line(""));
            boolean tierOk = p.tier.atLeast(k.tier());
            long w = waitLeft(p, k);
            boolean ok = tierOk && w == 0;
            if (!tierOk) lore.add(Page.line("§cRequiert le palier " + k.tier().label));
            else if (w < 0) lore.add(Page.line("§8Déjà reçu"));
            else if (w > 0) lore.add(Page.line("§cDisponible dans " + Msg.time(w)));
            else lore.add(Page.line("§aDisponible ! Cliquez pour le recevoir"));
            String id = k.id();
            page.set(slots[i++], Page.icon(ok ? k.icon() : Items.GRAY_DYE, 1, k.label(), ok ? ChatFormatting.GREEN : ChatFormatting.GRAY, lore, ok),
                    (sp, c) -> {
                        String err = claim(sp, id);
                        if (err != null) Msg.err(sp, err);
                        sp.level().getServer().execute(() -> open(sp));
                    });
        }
        page.close();
        page.open(player);
    }

    /** Nombre de kits disponibles maintenant (pour les rappels). */
    public static int available(PlayerProfile p) {
        int n = 0;
        for (Kit k : KITS.values()) if (p.tier.atLeast(k.tier()) && waitLeft(p, k) == 0) n++;
        return n;
    }
}
