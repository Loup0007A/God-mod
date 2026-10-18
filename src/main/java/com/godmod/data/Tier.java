package com.godmod.data;

import net.minecraft.ChatFormatting;

/**
 * Les 10 paliers de puissance, du mortel au Créateur (les 5 derniers par transcendance).
 * Le seuil est exprimé en Essence divine (XP du mod). Il est multiplié par
 * {@link Settings#thresholdMultiplier} (mode « petite survie » = seuils réduits).
 */
public enum Tier {
    MORTEL("Mortel", ChatFormatting.GRAY, 0, 0, 3),
    EVEILLE("Éveillé", ChatFormatting.GREEN, 500, 0, 6),
    DEMI_DIEU("Demi-dieu", ChatFormatting.AQUA, 2500, 0, 10),
    DIEU("Dieu", ChatFormatting.GOLD, 8000, 1, 15),
    DIEU_SUPREME("Dieu suprême", ChatFormatting.LIGHT_PURPLE, 30000, 8, 25),
    // ---- Au-delà : la transcendance (/transcender), qui demande aussi métiers, générateur et une offrande.
    TITAN("Titan", ChatFormatting.DARK_RED, 80_000, 8, 30, 100, 5, 100_000),
    PRIMORDIAL("Primordial", ChatFormatting.DARK_AQUA, 200_000, 9, 35, 140, 8, 250_000),
    ASTRAL("Divinité astrale", ChatFormatting.BLUE, 450_000, 10, 40, 180, 12, 600_000),
    ETERNEL("Éternel", ChatFormatting.YELLOW, 1_000_000, 11, 45, 220, 16, 1_500_000),
    CREATEUR("Créateur", ChatFormatting.WHITE, 2_500_000, 12, 50, 250, 20, 4_000_000);

    public final String label;
    public final ChatFormatting color;
    /** Essence nécessaire pour atteindre ce palier. */
    public final int essence;
    /** Fidèles nécessaires (directs jusqu'à Dieu, au total ensuite). */
    public final int followers;
    /** Niveau maximum de chaque caractéristique à ce palier. */
    public final int statCap;
    /** Transcendance : niveaux de métier cumulés requis. */
    public final int jobLevels;
    /** Transcendance : prestige du générateur requis. */
    public final int generator;
    /** Transcendance : offrande en pièces (dépensée). */
    public final long offering;

    Tier(String label, ChatFormatting color, int essence, int followers, int statCap) {
        this(label, color, essence, followers, statCap, 0, 0, 0);
    }

    Tier(String label, ChatFormatting color, int essence, int followers, int statCap, int jobLevels, int generator, long offering) {
        this.label = label;
        this.color = color;
        this.essence = essence;
        this.followers = followers;
        this.statCap = statCap;
        this.jobLevels = jobLevels;
        this.generator = generator;
        this.offering = offering;
    }

    /** Paliers au-delà du Dieu suprême : on y accède par /transcender, pas automatiquement. */
    public boolean isTranscendent() {
        return this.ordinal() > DIEU_SUPREME.ordinal();
    }

    /** 0 jusqu'au Dieu suprême, puis 1 (Titan) à 5 (Créateur). */
    public int transcendence() {
        return Math.max(0, this.ordinal() - DIEU_SUPREME.ordinal());
    }

    /** Les fidèles comptés : directs jusqu'à Dieu, au total à partir du Dieu suprême. */
    public boolean countsAllFollowers() {
        return this.atLeast(DIEU_SUPREME);
    }

    public boolean isGod() {
        return this.ordinal() >= DIEU.ordinal();
    }

    public boolean atLeast(Tier other) {
        return this.ordinal() >= other.ordinal();
    }

    public Tier next() {
        return this.ordinal() + 1 >= values().length ? null : values()[this.ordinal() + 1];
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
