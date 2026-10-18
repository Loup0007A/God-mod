package com.godmod.data;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Permissions qu'un dieu peut accorder sur son île (monde divin). */
public enum IslandPerm {
    ENTRER("Entrer", "Venir sur l'île", Items.OAK_DOOR),
    CASSER("Casser", "Casser des blocs", Items.IRON_PICKAXE),
    POSER("Poser", "Poser des blocs", Items.BRICKS),
    INTERAGIR("Interagir", "Portes, boutons, leviers, animaux…", Items.LEVER),
    COFFRES("Coffres", "Ouvrir coffres, fours et autres conteneurs", Items.CHEST);

    public final String label;
    public final String description;
    public final Item icon;

    IslandPerm(String label, String description, Item icon) {
        this.label = label;
        this.description = description;
        this.icon = icon;
    }

    public static IslandPerm parse(String s) {
        for (IslandPerm p : values()) if (p.name().equalsIgnoreCase(s) || p.label.equalsIgnoreCase(s)) return p;
        return null;
    }
}
