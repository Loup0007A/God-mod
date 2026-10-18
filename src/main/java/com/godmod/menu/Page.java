package com.godmod.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Construit une page d'interface : icônes, textes et actions. */
public class Page {
    private final String title;
    private final SimpleContainer box = new SimpleContainer(GodMenu.SIZE);
    private final Map<Integer, GodMenu.Action> actions = new HashMap<>();
    private GodMenu.InventoryAction inventoryAction;

    public Page(String title) {
        this.title = title;
    }

    /** Cadre décoratif autour de la page. */
    public SimpleContainer container() {
        return box;
    }

    public Page frame(Item pane) {
        for (int i = 0; i < GodMenu.SIZE; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == GodMenu.ROWS - 1 || col == 0 || col == 8) {
                box.setItem(i, icon(pane, " ", ChatFormatting.DARK_GRAY));
            }
        }
        return this;
    }

    public Page set(int slot, ItemStack stack, GodMenu.Action action) {
        box.setItem(slot, stack);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    public Page set(int slot, ItemStack stack) {
        return set(slot, stack, null);
    }

    public Page onInventoryClick(GodMenu.InventoryAction action) {
        this.inventoryAction = action;
        return this;
    }

    public Page back(GodMenu.Action action) {
        return set(45, icon(Items.ARROW, "← Retour", ChatFormatting.YELLOW), action);
    }

    public Page close() {
        return set(49, icon(Items.BARRIER, "Fermer", ChatFormatting.RED), (p, c) -> p.closeContainer());
    }

    public void open(ServerPlayer player) {
        Map<Integer, GodMenu.Action> acts = this.actions;
        GodMenu.InventoryAction inv = this.inventoryAction;
        SimpleContainer content = this.box;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new GodMenu(id, inventory, content, acts, inv),
                Component.literal(title)));
    }

    // ------------------------------------------------------------------ Icônes

    public static ItemStack icon(Item item, String name, ChatFormatting color, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(line(l));
        return icon(item, 1, name, color, lines, false);
    }

    public static ItemStack icon(Item item, int count, String name, ChatFormatting color, List<Component> lore, boolean glow) {
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(count, 64)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withColor(color).withItalic(false).withBold(true)));
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        if (glow) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /**
     * Ligne de description. Préfixes de couleur : "§a" vert, "§c" rouge, "§e" jaune, "§7" gris, "§6" or, "§b" cyan, "§d" rose.
     */
    public static MutableComponent line(String text) {
        ChatFormatting color = ChatFormatting.GRAY;
        String t = text;
        if (t.length() >= 2 && t.charAt(0) == '§') {
            color = switch (t.charAt(1)) {
                case 'a' -> ChatFormatting.GREEN;
                case 'c' -> ChatFormatting.RED;
                case 'e' -> ChatFormatting.YELLOW;
                case '6' -> ChatFormatting.GOLD;
                case 'b' -> ChatFormatting.AQUA;
                case 'd' -> ChatFormatting.LIGHT_PURPLE;
                case 'f' -> ChatFormatting.WHITE;
                case '8' -> ChatFormatting.DARK_GRAY;
                default -> ChatFormatting.GRAY;
            };
            t = t.substring(2);
        }
        final ChatFormatting c = color;
        return Component.literal(t).withStyle(s -> s.withColor(c).withItalic(false));
    }
}
