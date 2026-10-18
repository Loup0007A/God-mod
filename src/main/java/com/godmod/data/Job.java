package com.godmod.data;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Les métiers : chacun monte de niveau (1 à 50) en pratiquant son activité. */
public enum Job {
    MINEUR("Mineur", "⛏", ChatFormatting.GRAY, Items.IRON_PICKAXE,
            "Miner des minerais et de la pierre, fondre des lingots",
            "Minerai doublé · Vision nocturne · Célérité · Filon"),
    BUCHERON("Bûcheron", "♣", ChatFormatting.GOLD, Items.IRON_AXE,
            "Couper des bûches",
            "Bûches doublées · Vitesse · Abattage · Cueilleur · Force"),
    FERMIER("Fermier", "✿", ChatFormatting.GREEN, Items.WHEAT,
            "Récolter des cultures mûres, élever des animaux",
            "Replantation · Engrais divin · Moisson 3×3 · Carottes dorées"),
    CHASSEUR("Chasseur", "⚔", ChatFormatting.RED, Items.IRON_SWORD,
            "Tuer des créatures",
            "Prime · Régénération · Furie · Trophées · Générateurs"),
    PECHEUR("Pêcheur", "≈", ChatFormatting.AQUA, Items.FISHING_ROD,
            "Pêcher",
            "Prises doublées · Clés · Chance · Trésors · Respiration");

    public static final int MAX_LEVEL = 50;

    public final String label;
    public final String icon;
    public final ChatFormatting color;
    public final Item item;
    public final String how;
    public final String perks;

    Job(String label, String icon, ChatFormatting color, Item item, String how, String perks) {
        this.label = label;
        this.icon = icon;
        this.color = color;
        this.item = item;
        this.how = how;
        this.perks = perks;
    }

    /** XP nécessaire pour passer du niveau {@code level} au suivant. */
    public static double needed(int level) {
        return Math.round(60 * Math.pow(level, 1.55));
    }

    public static Job parse(String s) {
        for (Job j : values()) if (j.name().equalsIgnoreCase(s) || j.label.equalsIgnoreCase(s)) return j;
        return null;
    }
}
