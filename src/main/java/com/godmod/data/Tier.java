package com.godmod.data;

import net.minecraft.ChatFormatting;

/**
 * Les 5 paliers de puissance, du mortel au dieu suprême.
 * Le seuil est exprimé en Essence divine (XP du mod). Il est multiplié par
 * {@link Settings#thresholdMultiplier} (mode « petite survie » = seuils réduits).
 */
public enum Tier {
    MORTEL("Mortel", ChatFormatting.GRAY, 0, 0, 3),
    EVEILLE("Éveillé", ChatFormatting.GREEN, 500, 0, 6),
    DEMI_DIEU("Demi-dieu", ChatFormatting.AQUA, 2500, 0, 10),
    DIEU("Dieu", ChatFormatting.GOLD, 8000, 1, 15),
    DIEU_SUPREME("Dieu suprême", ChatFormatting.LIGHT_PURPLE, 30000, 8, 25);

    public final String label;
    public final ChatFormatting color;
    /** Essence nécessaire pour atteindre ce palier. */
    public final int essence;
    /** Fidèles directs nécessaires pour atteindre ce palier. */
    public final int followers;
    /** Niveau maximum de chaque caractéristique à ce palier. */
    public final int statCap;

    Tier(String label, ChatFormatting color, int essence, int followers, int statCap) {
        this.label = label;
        this.color = color;
        this.essence = essence;
        this.followers = followers;
        this.statCap = statCap;
    }

    public boolean isGod() {
        return this.ordinal() >= DIEU.ordinal();
    }

    public boolean atLeast(Tier other) {
        return this.ordinal() >= other.ordinal();
    }

    public Tier next() {
        return this == DIEU_SUPREME ? null : values()[this.ordinal() + 1];
    }

    /** Palier depuis un texte : « dieu_supreme », « Dieu suprême », « demi-dieu »… */
    public static Tier parse(String s) {
        String n = java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[\\s-]", "_");
        for (Tier t : values()) {
            String l = java.text.Normalizer.normalize(t.label.toLowerCase(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replaceAll("[\\s-]", "_");
            if (t.name().equalsIgnoreCase(n) || l.equals(n)) return t;
        }
        return null;
    }
}
