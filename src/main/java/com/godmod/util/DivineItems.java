package com.godmod.util;

import com.godmod.data.Specialization;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Objets divins fabriqués à partir d'objets vanilla marqués d'une étiquette invisible.
 * Aucun objet n'est ajouté au jeu : un Minecraft normal peut donc se connecter au serveur.
 */
public final class DivineItems {
    public static final String TAG = "godmod_item";
    public static final String CODEX = "codex";

    private DivineItems() {
    }

    public static ItemStack codex() {
        return make(Items.BOOK, CODEX, "Codex divin", ChatFormatting.LIGHT_PURPLE,
                "§7Clic droit : ouvrir le Panthéon", "§7(ou tapez /dieu)");
    }

    public static ItemStack artifact(Specialization s) {
        return switch (s) {
            case OCEAN -> make(Items.TRIDENT, "ocean", "Trident des Marées", ChatFormatting.BLUE,
                    "§7Artefact du domaine Océan", "§bClic droit : déferlante qui repousse et ralentit");
            case CIEL -> make(Items.BREEZE_ROD, "ciel", "Sceptre de Foudre", ChatFormatting.YELLOW,
                    "§7Artefact du domaine Ciel", "§eClic droit : foudre là où vous visez");
            case COSMOS -> make(Items.ECHO_SHARD, "cosmos", "Orbe du Néant", ChatFormatting.DARK_PURPLE,
                    "§7Artefact du domaine Cosmos", "§dClic droit : trou noir qui aspire puis implose");
            default -> codex();
        };
    }

    /** Identifiant divin de l'objet ("codex", "ocean", "ciel", "cosmos") ou chaîne vide. */
    public static String kind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        return data.copyTag().getStringOr(TAG, "");
    }

    public static boolean isDivine(ItemStack stack) {
        return !kind(stack).isEmpty();
    }

    public static Specialization specialization(ItemStack stack) {
        return switch (kind(stack)) {
            case "ocean" -> Specialization.OCEAN;
            case "ciel" -> Specialization.CIEL;
            case "cosmos" -> Specialization.COSMOS;
            default -> null;
        };
    }

    private static ItemStack make(Item item, String kind, String name, ChatFormatting color, String... lore) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, kind);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withColor(color).withItalic(false).withBold(true)));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(com.godmod.menu.Page.line(l));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }
}
