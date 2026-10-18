package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.menu.Menus;
import com.godmod.menu.Page;
import com.godmod.system.PowerSystem;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Statistiques des joueurs (/stats [joueur]) et du serveur (/stats serveur).
 * Les compteurs sont alimentés par les métiers, la boutique, le combat…
 */
public final class StatsSystem {
    private StatsSystem() {
    }

    // Clés des compteurs
    public static final String MINED = "mined", ORES = "ores", LOGS = "logs", CROPS = "crops", MOBS = "mobs", ANIMALS = "animals",
            FISH = "fish", CRAFTED = "crafted", SMELTED = "smelted", BRED = "bred", TRADES = "trades", SOLD = "sold",
            EARNED = "earned", PLACED = "placed", DIAMONDS = "diamonds", BOUGHT = "bought";

    public static void add(PlayerProfile p, String key, long n) {
        if (n <= 0) return;
        p.counters.merge(key, n, Long::sum);
    }

    public static long get(PlayerProfile p, String key) {
        return p.counters.getOrDefault(key, 0L);
    }

    private static String kd(PlayerProfile p) {
        return p.deaths == 0 ? String.valueOf(p.kills) : String.format(java.util.Locale.FRANCE, "%.2f", p.kills / (double) p.deaths);
    }

    private static String rank(PlayerProfile p, ToLongFunction<PlayerProfile> score) {
        List<PlayerProfile> all = new ArrayList<>(GodData.state().players.values());
        all.sort(Comparator.comparingLong(score).reversed());
        int i = all.indexOf(p);
        return i < 0 ? "?" : "#" + (i + 1) + "/" + all.size();
    }

    private static String n(long v) {
        return Msg.num(v);
    }

    // ------------------------------------------------------------------ Panneau d'un joueur

    public static void open(ServerPlayer viewer, PlayerProfile t) {
        if (t.firstSeen == 0) t.firstSeen = System.currentTimeMillis();
        Page page = new Page("Statistiques — " + t.name).frame(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        long play = t.playTicks * 50;
        page.set(4, Page.icon(Items.PLAYER_HEAD, 1, t.name, t.tier.color, List.of(
                Page.line("§7Palier : ").append(Component.literal(t.tier.label).withStyle(t.tier.color)),
                Page.line("§7Temps de jeu : §f" + Msg.time(play)),
                Page.line("§7Membre depuis : §f" + new java.text.SimpleDateFormat("dd/MM/yyyy").format(new java.util.Date(t.firstSeen))),
                Page.line("§7Puissance : §e" + n(PowerSystem.powerScore(t)) + " §8(" + rank(t, PowerSystem::powerScore) + ")")), true));

        page.set(19, icon(Items.IRON_SWORD, "Combat", ChatFormatting.RED,
                "§7Victoires (PvP) : §f" + t.kills,
                "§7Morts : §f" + t.deaths + " §8· ratio " + kd(t),
                "§7Dieux terrassés : §f" + t.godsSlain,
                "§7Tournois gagnés : §f" + t.tournamentsWon,
                "§7Monstres tués : §f" + n(get(t, MOBS)),
                "§7Animaux tués : §f" + n(get(t, ANIMALS))));
        page.set(21, icon(Items.DIAMOND_PICKAXE, "Récolte", ChatFormatting.AQUA,
                "§7Blocs minés : §f" + n(get(t, MINED)),
                "§7Minerais : §f" + n(get(t, ORES)) + " §8(dont " + n(get(t, DIAMONDS)) + " diamants)",
                "§7Bûches coupées : §f" + n(get(t, LOGS)),
                "§7Cultures récoltées : §f" + n(get(t, CROPS)),
                "§7Poissons pêchés : §f" + n(get(t, FISH)),
                "§7Blocs posés : §f" + n(get(t, PLACED))));
        page.set(23, icon(Items.CRAFTING_TABLE, "Artisanat et commerce", ChatFormatting.GOLD,
                "§7Objets fabriqués : §f" + n(get(t, CRAFTED)),
                "§7Objets fondus ou cuits : §f" + n(get(t, SMELTED)),
                "§7Animaux nés : §f" + n(get(t, BRED)),
                "§7Échanges avec les villageois : §f" + n(get(t, TRADES)),
                "§7Objets vendus au marchand : §f" + n(get(t, SOLD)),
                "§7Achats à la boutique : §f" + n(get(t, BOUGHT)),
                "§7Pièces gagnées au total : §6" + n(get(t, EARNED))));
        page.set(25, icon(Items.NETHER_STAR, "Progression", ChatFormatting.LIGHT_PURPLE,
                "§7Essence : §d" + n(t.essence),
                "§7Niveaux de métier : §f" + JobSystem.totalLevels(t) + " §8(" + rank(t, JobSystem::totalLevels) + ")",
                "§7Quêtes du jour : §f" + t.questsDone + " §8· défis hebdo : " + t.weeklyDone,
                "§7Ascension : §f" + Math.min(t.storyStep, AscensionQuests.STEPS.size()) + "/" + AscensionQuests.STEPS.size(),
                "§7Caisses ouvertes : §f" + t.cratesOpened,
                "§7Événements gagnés : §f" + t.eventsWon,
                "§7Fidèles : §f" + GodData.totalFollowers(t.uuid)));
        page.set(29, icon(Items.CLOCK, "Assiduité", ChatFormatting.YELLOW,
                "§7Temps de jeu : §f" + Msg.time(play),
                "§7Temps en zone AFK : §f" + Msg.time(t.afkMinutes * 60_000L),
                "§7Jetons AFK : §d" + t.afkTokens,
                "§7Série quotidienne : §f" + t.dailyStreak + " §8(record " + t.dailyBest + ")"));
        page.set(31, icon(Items.GOLD_INGOT, "Richesse", ChatFormatting.GOLD,
                "§7Solde : §6" + Msg.money(t.money) + " §8(" + rank(t, x -> x.money) + ")",
                "§7Clés de caisse : §f" + CrateSystem.totalKeys(t),
                "§7Privilèges : §f" + t.perks.size() + "/" + com.godmod.system.Perks.Perk.values().length));
        List<String> jobs = new ArrayList<>();
        for (Job j : Job.values()) jobs.add("§7" + j.icon + " " + j.label + " : §f" + JobSystem.level(t, j) + " §8(" + rank(t, x -> JobSystem.level(x, j) * 1_000_000L + (long) JobSystem.xp(x, j)) + ")");
        page.set(33, icon(Items.IRON_PICKAXE, "Métiers", ChatFormatting.GREEN, jobs.toArray(new String[0])));
        page.set(40, Page.icon(Items.BEACON, "Statistiques du serveur", ChatFormatting.AQUA, "§7Totaux et records de tous les joueurs", "", "§eCliquez pour voir"),
                (sp, c) -> sp.level().getServer().execute(() -> openServer(sp)));
        page.back((sp, c) -> sp.level().getServer().execute(() -> Menus.openMain(sp))).close();
        page.open(viewer);
    }

    private static net.minecraft.world.item.ItemStack icon(Item item, String name, ChatFormatting color, String... lines) {
        return Page.icon(item, name, color, lines);
    }

    // ------------------------------------------------------------------ Serveur

    private record Record(String label, Item icon, ToLongFunction<PlayerProfile> score, String unit) {
    }

    private static final List<Record> RECORDS = List.of(
            new Record("Temps de jeu", Items.CLOCK, p -> p.playTicks / 1200, " min"),
            new Record("Puissance", Items.NETHER_STAR, PowerSystem::powerScore, ""),
            new Record("Richesse", Items.GOLD_BLOCK, p -> p.money, " pièces"),
            new Record("Blocs minés", Items.DIAMOND_PICKAXE, p -> get(p, MINED), ""),
            new Record("Bûches", Items.DIAMOND_AXE, p -> get(p, LOGS), ""),
            new Record("Cultures", Items.DIAMOND_HOE, p -> get(p, CROPS), ""),
            new Record("Monstres tués", Items.DIAMOND_SWORD, p -> get(p, MOBS), ""),
            new Record("Poissons", Items.FISHING_ROD, p -> get(p, FISH), ""),
            new Record("Victoires PvP", Items.NETHERITE_SWORD, p -> p.kills, ""),
            new Record("Quêtes", Items.WRITABLE_BOOK, p -> p.questsDone, ""),
            new Record("Niveaux de métier", Items.EXPERIENCE_BOTTLE, JobSystem::totalLevels, ""),
            new Record("Caisses ouvertes", Items.ENDER_CHEST, p -> p.cratesOpened, ""),
            new Record("Événements gagnés", Items.FIREWORK_ROCKET, p -> p.eventsWon, ""),
            new Record("Série quotidienne", Items.SUNFLOWER, p -> p.dailyBest, " jours"));

    public static void openServer(ServerPlayer viewer) {
        List<PlayerProfile> all = new ArrayList<>(GodData.state().players.values());
        long play = 0, mined = 0, mobs = 0, quests = 0, money = 0, crates = 0;
        int gods = 0;
        for (PlayerProfile p : all) {
            play += p.playTicks * 50;
            mined += get(p, MINED);
            mobs += get(p, MOBS);
            quests += p.questsDone;
            money += p.money;
            crates += p.cratesOpened;
            if (p.tier.isGod()) gods++;
        }
        Page page = new Page("Statistiques du serveur").frame(Items.CYAN_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.BEACON, "Le Panthéon en chiffres", ChatFormatting.AQUA,
                "§7Joueurs : §f" + all.size() + " §8(" + viewer.level().getServer().getPlayerCount() + " en ligne)",
                "§7Dieux : §f" + gods,
                "§7Temps de jeu cumulé : §f" + Msg.time(play),
                "§7Blocs minés : §f" + n(mined) + " §8· monstres : " + n(mobs),
                "§7Quêtes accomplies : §f" + n(quests) + " §8· caisses : " + n(crates),
                "§7Argent en circulation : §6" + Msg.money(money)));
        for (int i = 0; i < RECORDS.size() && i < Menus.INNER.length; i++) {
            Record r = RECORDS.get(i);
            List<PlayerProfile> sorted = new ArrayList<>(all);
            sorted.sort(Comparator.comparingLong(r.score()).reversed());
            List<Component> lore = new ArrayList<>();
            for (int k = 0; k < Math.min(3, sorted.size()); k++) {
                PlayerProfile p = sorted.get(k);
                lore.add(Page.line((k == 0 ? "§6" : "§7") + (k + 1) + ". " + p.name + " — " + n(r.score().applyAsLong(p)) + r.unit()));
            }
            if (lore.isEmpty()) lore.add(Page.line("§8Personne pour l'instant"));
            page.set(Menus.INNER[i + 7], Page.icon(r.icon(), 1, "Record : " + r.label(), ChatFormatting.YELLOW, lore, false));
        }
        page.back((sp, c) -> sp.level().getServer().execute(() -> open(sp, GodData.get(sp)))).close();
        page.open(viewer);
    }
}
