package com.godmod.data;

/** Caractéristiques physiques et mentales améliorables. */
public enum Stat {
    FORCE("Force", "Physique : +0,5 dégât d'attaque par niveau"),
    VITALITE("Vitalité", "Physique : +1 cœur de vie max tous les 2 niveaux"),
    AGILITE("Agilité", "Physique : +2 % de vitesse par niveau"),
    ESPRIT("Esprit", "Mental : +20 Foi max, régénération de Foi plus rapide, artefacts plus puissants");

    public final String label;
    public final String description;

    Stat(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public static Stat parse(String s) {
        for (Stat st : values()) {
            if (st.name().equalsIgnoreCase(s) || st.label.equalsIgnoreCase(s)) return st;
        }
        return null;
    }
}
