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

    // --- Compte & modération ---
    public Role role = Role.JOUEUR;
    /** Mot de passe haché (PBKDF2) et son sel, en Base64. Null = pas encore inscrit. */
    public String passwordHash = null;
    public String passwordSalt = null;
    public String lastIp = null;
    public long lastAuth = 0;
    public long mutedUntil = 0;
    public String muteReason = null;
    /** 0 = pas banni, Long.MAX_VALUE = définitif. */
    public long bannedUntil = 0;
    public String banReason = null;
    public String bannedBy = null;
    public java.util.List<String> warnings = new java.util.ArrayList<>();
    /** Position réelle du joueur pendant qu'il attend de se connecter au spawn (restaurée après /login). */
    public boolean hasAuthReturn = false;
    public String authReturnDim = null;
    public double authReturnX, authReturnY, authReturnZ;

    // --- Monde divin ---
    public int plotIndex = -1;
    /** Faux tant que le propriétaire n'a posé aucun bloc : le bloc de terre initial est alors indestructible. */
    public boolean plotStarted = false;
    /** Position de retour dans le monde principal. */
    public boolean hasReturn = false;
    public double retX, retY, retZ;

    // --- Permissions de l'île (monde divin) ---
    /** Joueurs autorisés : uuid -> permissions (voir IslandPerm). */
    public java.util.Map<String, java.util.List<String>> islandMembers = new java.util.HashMap<>();
    /** Permissions des fidèles du propriétaire. */
    public java.util.List<String> islandFollowerPerms = new java.util.ArrayList<>(java.util.List.of("ENTRER", "INTERAGIR"));
    /** Permissions de tous les autres joueurs (visiteurs). */
    public java.util.List<String> islandVisitorPerms = new java.util.ArrayList<>();
    /** Combats entre joueurs autorisés sur l'île (hors guerre, rébellion et tournoi). */
    public boolean islandPvp = false;
    /** Prestige du générateur lave + eau de l'île (1 à 20). */
    public int generatorLevel = 1;

    // --- Métiers ---
    public java.util.Map<String, Double> jobXp = new java.util.HashMap<>();
    public java.util.Map<String, Integer> jobLevel = new java.util.HashMap<>();

    // --- Quêtes ---
    public java.util.List<QuestProgress> quests = new java.util.ArrayList<>();
    /** Jour (AAAA-MM-JJ) des quêtes proposées. */
    public String questDay = "";
    public java.util.List<String> questOffers = new java.util.ArrayList<>();
    public int questsDone = 0;
    /** Renouvellements des propositions aujourd'hui (le premier est gratuit). */
    public int questRerolls = 0;
    /** Quêtes accomplies aujourd'hui (bonus d'assiduité à 3 et 5). */
    public int questsToday = 0;
    /** Défis de la semaine. */
    public String weekId = "";
    public java.util.List<QuestProgress> weekly = new java.util.ArrayList<>();
    public boolean weeklyBonus = false;
    /** Défis de la semaine relevés depuis le début. */
    public int weeklyDone = 0;
    /** Prochaine étape des quêtes d'ascension. */
    public int storyStep = 0;
    /** Animaux nés grâce au joueur, objets fabriqués (statistiques pour l'ascension). */
    public int bred = 0;

    // --- Caisses ---
    /** Clés de caisse : type -> nombre. */
    public java.util.Map<String, Integer> keys = new java.util.HashMap<>();
    public int cratesOpened = 0;

    // --- Affichage ---
    public boolean hud = true;
    /** Fraction d'Essence accumulée par les métiers (versée par unités entières). */
    public double essenceFraction = 0;

    // --- Social ---
    /** Refuse toutes les demandes /tpa. */
    public boolean tpaBlocked = false;
    /** Dernier signalement envoyé (anti-spam). */
    public long lastReport = 0;

    // --- Récompense quotidienne ---
    /** Jour (AAAA-MM-JJ) de la dernière récompense récupérée. */
    public String dailyLast = "";
    /** Nombre de jours consécutifs. */
    public int dailyStreak = 0;
    public int dailyBest = 0;

    // --- AFK ---
    public long afkTokens = 0;
    public int afkTokensToday = 0;
    public String afkDay = "";
    public long afkMinutes = 0;
    /** Boost d'XP de métier (×1,5) acheté dans /afkshop, jusqu'à cette date. */
    public long jobBoostUntil = 0;

    // --- Privilèges (commandes achetées) ---
    public java.util.List<String> perks = new java.util.ArrayList<>();
    public java.util.Map<String, Long> perkCooldowns = new java.util.HashMap<>();

    // --- Statistiques ---
    public java.util.Map<String, Long> counters = new java.util.HashMap<>();
    public long firstSeen = 0;

    // --- Événements ---
    public int eventsWon = 0;

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
