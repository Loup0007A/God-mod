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

    // --- Authentification ---
    /** Oblige chaque joueur à se connecter avec un mot de passe (/register, /login). */
    public boolean authEnabled = true;
    /** Temps laissé pour se connecter avant expulsion, en secondes. */
    public int authTimeoutSeconds = 90;
    /** Reconnexion automatique depuis la même adresse IP pendant ce nombre d'heures (0 = jamais). */
    public int authSessionHours = 0;

    // --- Structuration du monde ---
    /** Vrai une fois le Sanctuaire et l'arène construits. */
    public boolean worldStructured = false;
    /** Version du décor construit (2 = temple céleste). Une version plus ancienne est remplacée au démarrage. */
    public int structureVersion = 0;
    public double oldHubX, oldHubY, oldHubZ;
    /** Version des réglages (migrations automatiques). */
    public int settingsVersion = 0;
    /** Caisses et tableaux posés automatiquement dans le Sanctuaire construit par le mod. */
    public boolean pointsPlaced = false;
    /** Le Sanctuaire a été vidé par un admin pour être rebâti à la main. */
    public boolean customSpawn = false;
    public boolean hubSet = false;
    /** Zone AFK construite dans le Sanctuaire. */
    public boolean afkZoneBuilt = false;

    // --- Anti-lag ---
    /** Créatures d'un même type au plus par chunk (villageois, animaux apprivoisés et nommés exceptés). */
    public int entityChunkLimit = 15;
    /** Secondes entre deux nettoyages des objets au sol (0 = désactivé). */
    public int clearlagSeconds = 90;

    // --- Événements ---
    /** Lance automatiquement un événement à intervalle régulier. */
    public boolean eventsAuto = true;
    /** Minutes entre deux événements automatiques. */
    public int eventIntervalMinutes = 45;
    /** Joueurs connectés nécessaires pour un événement automatique. */
    public int eventMinPlayers = 2;
    /** Délai (secondes) avant une téléportation par commande. */
    public int teleportWarmupSeconds = 3;
    public double hubX, hubY, hubZ;
}
