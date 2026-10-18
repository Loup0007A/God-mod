package com.godmod.data;

import net.minecraft.ChatFormatting;

/** Spécialisation divine : détermine l'artefact qu'un dieu peut manier. */
public enum Specialization {
    AUCUNE("Aucune", ChatFormatting.GRAY, "Pas encore choisie"),
    OCEAN("Océan", ChatFormatting.BLUE, "Vagues et marées (Trident des Marées)"),
    CIEL("Ciel", ChatFormatting.YELLOW, "Foudre et tempêtes (Sceptre de Foudre)"),
    COSMOS("Cosmos", ChatFormatting.DARK_PURPLE, "Gravité et trous noirs (Orbe du Néant)");

    public final String label;
    public final ChatFormatting color;
    public final String description;

    Specialization(String label, ChatFormatting color, String description) {
        this.label = label;
        this.color = color;
        this.description = description;
    }

    public static Specialization parse(String s) {
        for (Specialization sp : values()) {
            if (sp.name().equalsIgnoreCase(s) || sp.label.equalsIgnoreCase(s)) return sp;
        }
        return null;
    }
}
