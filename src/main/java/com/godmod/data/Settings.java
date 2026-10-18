package com.godmod.data;

/**
 * Réglages du serveur, modifiables en jeu par les admins (/godadmin ...).
 * Sauvegardés dans le monde avec le reste des données.
 */
public class Settings {
    /** Fonctionnalités payantes (Gemmes divines, boutique premium). Désactivé par défaut. */
    public boolean payEnabled = false;
    /** Bloque les portails vers le Nether et l'End (leurs items sont vendus au shop). */
    public boolean disableNetherEnd = true;
    /** Multiplicateur des seuils de palier (1.0 = serveur, 0.25 = petite survie). */
    public double thresholdMultiplier = 1.0;
    /** Multiplicateur de gain d'Essence. */
    public double essenceMultiplier = 1.0;
    /** Délai entre deux rébellions d'un même joueur, en heures. */
    public int rebellionCooldownHours = 72;
    /** Un dieu absent depuis ce nombre de jours peut être détrôné par tournoi. */
    public int inactiveGodDays = 7;
    /** Arène des tournois (dimension overworld). */
    public boolean arenaSet = false;
    public double arenaX, arenaY, arenaZ;
    /** Taille du monde divin : rayon = base + parFidele × fidèles (+ bonus de palier). */
    public int worldBaseRadius = 16;
    public int worldRadiusPerFollower = 8;
    public int worldMaxRadius = 400;
}
