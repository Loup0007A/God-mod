package com.godmod.data;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tout ce que le mod sait d'un joueur. Sérialisé en JSON dans les données du monde.
 */
public class PlayerProfile {
    public String uuid;
    public String name = "?";

    // --- Puissance ---
    public Tier tier = Tier.MORTEL;
    /** Essence divine : l'XP du mod, qui fait monter de palier. */
    public long essence = 0;
    public Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
    public Specialization specialization = Specialization.AUCUNE;
    /** Foi : ressource consommée par les pouvoirs et artefacts. */
    public double faith = 0;

    // --- Hiérarchie ---
    /** UUID du dieu (ou demi-dieu) qui contrôle ce joueur, ou null s'il est libre. */
    public String master = null;

    // --- Économie ---
    public long money = 100;
    /** Monnaie premium (fonctionnalités payantes). */
    public long gems = 0;
    /** Bonus premium : Essence x2 jusqu'à cette date. */
    public long boostUntil = 0;

    // --- Statistiques & suivi ---
    public int kills = 0;
    public int deaths = 0;
    public int godsSlain = 0;
    public int tournamentsWon = 0;
    public long playTicks = 0;
    public long lastSeen = System.currentTimeMillis();
    /** Horodatage (ms) avant lequel le joueur ne peut pas lancer de nouvelle rébellion. */
    public long rebellionCooldownUntil = 0;
    /** Parjure : rébellion ratée, statistiques réduites jusqu'à cette date. */
    public long oathbreakerUntil = 0;

    public boolean codexGiven = false;

    // --- Monde divin ---
    public int plotIndex = -1;
    /** Faux tant que le propriétaire n'a posé aucun bloc : le bloc de terre initial est alors indestructible. */
    public boolean plotStarted = false;
    /** Position de retour dans le monde principal. */
    public boolean hasReturn = false;
    public double retX, retY, retZ;

    // --- Choix en attente après avoir vaincu un dieu ---
    public String pendingThroneVictim = null;
    public long pendingThroneUntil = 0;

    public PlayerProfile() {
    }

    public PlayerProfile(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public int stat(Stat s) {
        if (stats == null) stats = new EnumMap<>(Stat.class);
        return stats.getOrDefault(s, 0);
    }

    public void setStat(Stat s, int v) {
        if (stats == null) stats = new EnumMap<>(Stat.class);
        stats.put(s, Math.max(0, v));
    }

    public double maxFaith() {
        return 100 + 20.0 * stat(Stat.ESPRIT) + 50.0 * tier.ordinal();
    }

    public boolean isOathbreaker() {
        return System.currentTimeMillis() < oathbreakerUntil;
    }
}
