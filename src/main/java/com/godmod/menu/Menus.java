package com.godmod.menu;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Specialization;
import com.godmod.data.Stat;
import com.godmod.data.Tier;
import com.godmod.system.DivineWorld;
import com.godmod.system.FaithSystem;
import com.godmod.system.PowerActions;
import com.godmod.system.PowerSystem;
import com.godmod.system.RebellionSystem;
import com.godmod.system.Shop;
import com.godmod.system.TournamentSystem;
import com.godmod.system.WarSystem;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Toutes les pages de l'interface du Panthéon. */
public final class Menus {
    /** Cases intérieures (4 lignes × 7 colonnes) disponibles pour les listes. */
    public static final int[] INNER = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private Menus() {
    }

    /** Ouvre une page au tick suivant (évite d'ouvrir un menu pendant le traitement d'un clic). */
    private static void later(ServerPlayer player, Runnable r) {
        MinecraftServer server = player.level().getServer();
        server.execute(r);
    }

    private static void report(ServerPlayer player, String error) {
        if (error != null && !error.isEmpty()) Msg.err(player, error);
    }

    private static Item paneFor(PlayerProfile p) {
        return switch (p.tier) {
            case MORTEL -> Items.GRAY_STAINED_GLASS_PANE;
            case EVEILLE -> Items.LIME_STAINED_GLASS_PANE;
            case DEMI_DIEU -> Items.LIGHT_BLUE_STAINED_GLASS_PANE;
            case DIEU -> Items.ORANGE_STAINED_GLASS_PANE;
            case DIEU_SUPREME -> Items.MAGENTA_STAINED_GLASS_PANE;
        };
    }

    // ================================================================== Accueil

    public static void openMain(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("✦ Panthéon — " + p.tier.label).frame(paneFor(p));
        page.set(4, profileIcon(p));

        page.set(19, Page.icon(Items.BLAZE_POWDER, "Caractéristiques", ChatFormatting.RED,
                "§7Force, Vitalité, Agilité, Esprit",
                "§a" + PowerSystem.freeStatPoints(p) + " point(s) disponible(s)",
                "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openStats(sp)));
        page.set(21, Page.icon(Items.BLAZE_ROD, "Pouvoirs divins", ChatFormatting.GOLD,
                "§7Bénédiction, châtiment, convocation,", "§7maîtrise du temps et de l'espace",
                "§bFoi : " + (int) p.faith + " / " + (int) p.maxFaith(), "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openPowers(sp)));
        page.set(23, Page.icon(Items.TOTEM_OF_UNDYING, "Fidèles", ChatFormatting.YELLOW,
                "§7Directs : " + GodData.followersOf(p.uuid).size() + "  Total : " + GodData.totalFollowers(p.uuid),
                "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openFollowers(sp, 0)));
        if (DivineWorld.hasWorld(p)) {
            page.set(25, Page.icon(Items.GRASS_BLOCK, "Monde divin", ChatFormatting.GREEN,
                    "§7" + DivineWorld.describe(p), "", "§eClic gauche : y aller", "§eClic droit : revenir au monde principal"),
                    (sp, c) -> {
                        sp.closeContainer();
                        if (c == GodMenu.Click.RIGHT) report(sp, com.godmod.system.Travel.startRun(sp, "Vers le Sanctuaire", () -> DivineWorld.leave(sp)));
                        else report(sp, com.godmod.system.Travel.start(sp, "Vers votre monde divin", () -> DivineWorld.sendHome(sp)));
                    });
        } else {
            page.set(25, Page.icon(Items.DIRT, "Monde divin (verrouillé)", ChatFormatting.DARK_GRAY,
                    "§7Les mortels n'ont pas de monde divin.", "§eDevenez Dieu pour recevoir le vôtre.",
                    "§7Vous pouvez visiter celui de votre dieu : /monde visiter <dieu>"));
        }
        page.set(10, Page.icon(com.godmod.progress.JobSystem.best(p).item, "Métiers", ChatFormatting.GREEN,
                "§7Mineur, Bûcheron, Fermier, Chasseur, Pêcheur",
                "§7Total : " + com.godmod.progress.JobSystem.totalLevels(p) + " niveaux", "", "§eCliquez pour ouvrir  §8(/metiers)"),
                (sp, c) -> later(sp, () -> openJobs(sp)));
        page.set(12, Page.icon(Items.WRITABLE_BOOK, "Quêtes", ChatFormatting.YELLOW,
                "§7" + p.quests.size() + " / " + com.godmod.progress.QuestSystem.MAX_ACTIVE + " en cours · " + p.questsDone + " accomplies",
                "", "§eCliquez pour ouvrir  §8(/quetes)"), (sp, c) -> later(sp, () -> openQuests(sp)));
        page.set(14, Page.icon(Items.TRIPWIRE_HOOK, "Clés de caisse", ChatFormatting.LIGHT_PURPLE,
                "§7" + com.godmod.progress.CrateSystem.totalKeys(p) + " clé(s)", "§7Les caisses sont au Sanctuaire", "", "§eCliquez pour voir"),
                (sp, c) -> later(sp, () -> openKeys(sp)));
        page.set(16, Page.icon(Items.EXPERIENCE_BOTTLE, "Comment progresser ?", ChatFormatting.AQUA,
                "§7Tout ce qui rapporte de l'Essence", "§7et fait monter de palier", "", "§eCliquez pour lire"),
                (sp, c) -> later(sp, () -> openGuide(sp)));
        boolean daily = com.godmod.progress.DailyReward.available(p);
        page.set(20, Page.icon(daily ? Items.CHEST_MINECART : Items.CLOCK, 1, "Récompense quotidienne", ChatFormatting.GOLD, List.of(
                Page.line(daily ? "§aDisponible ! Cliquez pour la récupérer" : "§7Déjà récupérée aujourd'hui"),
                Page.line("§7Série : §e" + p.dailyStreak + " jour(s)§7 · record " + p.dailyBest),
                Page.line(daily ? "" : "§7Prochaine dans " + Msg.time(com.godmod.progress.QuestSystem.untilTomorrow())),
                Page.line("§8(/recompense)")), daily), (sp, c) -> {
            if (com.godmod.progress.DailyReward.available(GodData.get(sp))) report(sp, com.godmod.progress.DailyReward.claim(sp));
            later(sp, () -> com.godmod.progress.DailyReward.open(sp));
        });
        com.godmod.progress.EventSystem.Type ev = com.godmod.progress.EventSystem.current();
        long nextEv = com.godmod.progress.EventSystem.nextAt();
        page.set(22, Page.icon(ev != null ? ev.icon : Items.FIREWORK_ROCKET, 1, ev != null ? "Événement : " + ev.label : "Événements", ev != null ? ev.color : ChatFormatting.AQUA, List.of(
                Page.line(ev != null ? "§f" + ev.description : "§7Aucun événement en cours"),
                Page.line(ev != null ? "§eFin dans " + Msg.clock(com.godmod.progress.EventSystem.timeLeft())
                        : nextEv > 0 ? "§7Prochain dans " + Msg.time(Math.max(0, nextEv - System.currentTimeMillis())) : "§7Lancés par les admins"),
                Page.line(""),
                Page.line(ev != null ? "§eCliquez pour participer" : "§eCliquez pour les détails"),
                Page.line("§8(/event)")), ev != null), (sp, c) -> {
            sp.closeContainer();
            if (com.godmod.progress.EventSystem.current() != null) {
                String err = com.godmod.progress.EventSystem.join(sp);
                if (err != null) Msg.info(sp, err);
            } else com.godmod.progress.EventSystem.info(sp);
        });
        page.set(28, Page.icon(Items.WRITABLE_BOOK, "Statistiques", ChatFormatting.AQUA,
                "§7Combat, récolte, artisanat, temps de jeu…", "§7et les records du serveur", "", "§eCliquez pour ouvrir  §8(/stats)"),
                (sp, c) -> later(sp, () -> com.godmod.progress.StatsSystem.open(sp, GodData.get(sp))));
        page.set(34, Page.icon(Items.RECOVERY_COMPASS, "Zone AFK", ChatFormatting.LIGHT_PURPLE,
                "§71 jeton et 5 pièces par minute", "§7Jetons : §d" + p.afkTokens,
                "", "§eClic gauche : y aller  §8(/afk)", "§eClic droit : boutique AFK  §8(/afkshop)"), (sp, c) -> {
            if (c == GodMenu.Click.RIGHT) later(sp, () -> com.godmod.progress.AfkShop.open(sp));
            else {
                sp.closeContainer();
                report(sp, com.godmod.system.AfkSystem.toggle(sp));
            }
        });
        page.set(24, Page.icon(Items.KNOWLEDGE_BOOK, "Aide et commandes", ChatFormatting.AQUA,
                "§7Toutes les commandes, par thème,", "§7cliquables dans le chat", "", "§eCliquez pour ouvrir  §8(/aide)"), (sp, c) -> {
            sp.closeContainer();
            com.godmod.command.Help.summary(sp);
        });
        if (com.godmod.system.Moderation.roleOf(player).atLeast(com.godmod.data.Role.ADMIN)) {
            page.set(42, Page.icon(Items.GOLDEN_HELMET, "Équipe du serveur", ChatFormatting.RED,
                    "§7Nommer modérateurs et admins", "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openTeam(sp)));
        }
        page.set(11, Page.icon(Items.BEACON, "Sanctuaire (spawn)", ChatFormatting.LIGHT_PURPLE,
                "§7Retour au temple céleste", "", "§eCliquez pour y aller  §8(/spawn)"), (sp, c) -> {
            sp.closeContainer();
            report(sp, com.godmod.system.Travel.startRun(sp, "Vers le Sanctuaire", () -> com.godmod.system.Hub.teleport(sp)));
        });
        page.set(15, Page.icon(Items.DIAMOND_PICKAXE, "Monde ressource", ChatFormatting.AQUA,
                "§7Un monde sauvage pour miner et récolter", "", "§eCliquez pour y aller  §8(/mine)"), (sp, c) -> {
            sp.closeContainer();
            report(sp, com.godmod.system.Travel.start(sp, "Vers le monde ressource", () -> com.godmod.system.Hub.toMine(sp)));
        });
        if (DivineWorld.hasWorld(p)) {
            page.set(40, Page.icon(Items.IRON_DOOR, "Permissions de l'île", ChatFormatting.GOLD,
                    "§7Qui peut entrer, casser, poser, ouvrir", "§7les coffres sur votre monde divin", "", "§eCliquez pour ouvrir"),
                    (sp, c) -> later(sp, () -> openIsland(sp)));
        }
        page.set(37, Page.icon(Items.ENDER_PEARL, "Warps des joueurs", ChatFormatting.DARK_AQUA,
                "§7" + GodData.state().warps.size() + " warp(s) publics",
                "§7Créez le vôtre : /setwarp <nom>", "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openWarps(sp, 0)));

        page.set(29, Page.icon(Items.EMERALD, "Boutique", ChatFormatting.GREEN,
                "§7Nether, End, ressources, artefacts", "§6Solde : " + p.money + " pièces", "", "§eCliquez pour ouvrir"),
                (sp, c) -> later(sp, () -> openShop(sp)));
        page.set(31, Page.icon(Items.IRON_AXE, "Rébellion", ChatFormatting.DARK_RED,
                p.master == null ? "§7Vous êtes libre" : "§7Contre : " + nameOf(p.master),
                RebellionSystem.cooldownLeft(p) > 0 ? "§cDisponible dans " + Msg.time(RebellionSystem.cooldownLeft(p)) : "§aDisponible",
                "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openRebellion(sp)));
        page.set(33, Page.icon(Items.GOLDEN_SWORD, "Tournoi", ChatFormatting.GOLD,
                TournamentSystem.current() == null ? "§7Aucun tournoi en cours" : "§aTournoi en cours !",
                "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openTournament(sp)));
        page.set(39, Page.icon(Items.NETHERITE_SWORD, "Guerre divine", ChatFormatting.DARK_RED,
                "§7Soumettez un autre panthéon", WarSystem.warOf(p) != null ? "§cEn guerre !" : "§7En paix",
                "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openWar(sp)));
        page.set(41, Page.icon(Items.GOLD_INGOT, "Classement", ChatFormatting.YELLOW,
                "§7Les êtres les plus puissants", "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openRanking(sp)));
        if (GodData.settings().payEnabled) {
            page.set(43, Page.icon(Items.DIAMOND, "Boutique premium", ChatFormatting.AQUA,
                    "§bGemmes : " + p.gems, "", "§eCliquez pour ouvrir"), (sp, c) -> later(sp, () -> openPremium(sp)));
        }
        if (p.pendingThroneVictim != null && System.currentTimeMillis() < p.pendingThroneUntil) {
            page.set(13, Page.icon(Items.GOLDEN_HELMET, "Un trône vous attend !", ChatFormatting.GOLD,
                    "§7Vous avez terrassé " + nameOf(p.pendingThroneVictim),
                    "§aClic gauche : devenir dieu et l'asservir", "§cClic droit : refuser"),
                    (sp, c) -> {
                        report(sp, FaithSystem.resolveThrone(sp, c != GodMenu.Click.RIGHT));
                        later(sp, () -> openMain(sp));
                    });
        }
        page.close();
        page.open(player);
    }

    private static ItemStack profileIcon(PlayerProfile p) {
        List<Component> lore = new ArrayList<>();
        Tier next = p.tier.next();
        lore.add(Page.line("§7Palier : ").append(Component.literal(p.tier.label).withStyle(p.tier.color)));
        lore.add(Page.line("§dEssence : " + p.essence + (next == null ? " (maximum)" : " / " + PowerSystem.threshold(next))));
        if (next != null && next.followers > 0) lore.add(Page.line("§7  " + next.label + " requiert " + next.followers + " fidèle(s)"));
        lore.add(Page.line("§bFoi : " + (int) p.faith + " / " + (int) p.maxFaith()));
        lore.add(Page.line("§6Argent : " + p.money + " pièces"));
        if (GodData.settings().payEnabled) lore.add(Page.line("§bGemmes : " + p.gems));
        lore.add(Page.line("§7Dieu : " + (p.master == null ? "aucun (libre)" : nameOf(p.master))));
        lore.add(Page.line("§7Fidèles : " + GodData.followersOf(p.uuid).size() + " directs, " + GodData.totalFollowers(p.uuid) + " au total"));
        lore.add(Page.line("§7Domaine : " + p.specialization.label));
        lore.add(Page.line("§ePuissance : " + PowerSystem.powerScore(p)));
        lore.add(Page.line("§7Victoires : " + p.kills + "  Morts : " + p.deaths + "  Déicides : " + p.godsSlain));
        if (p.isOathbreaker()) lore.add(Page.line("§cParjure encore " + Msg.time(p.oathbreakerUntil - System.currentTimeMillis())));
        return Page.icon(Items.NETHER_STAR, 1, p.name, p.tier.color, lore, true);
    }

    // ================================================================== Caractéristiques

    public static void openStats(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Caractéristiques").frame(paneFor(p));
        page.set(4, Page.icon(Items.EXPERIENCE_BOTTLE, "Points disponibles : " + PowerSystem.freeStatPoints(p), ChatFormatting.GREEN,
                "§7Gagnés avec l'Essence (1 / 200) et les paliers (+3)",
                "§7Plafond par caractéristique : " + p.tier.statCap));
        Item[] icons = {Items.IRON_SWORD, Items.GOLDEN_APPLE, Items.FEATHER, Items.ENDER_EYE};
        int[] slots = {20, 22, 24, 31};
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Stat s = stats[i];
            int lvl = p.stat(s);
            page.set(slots[i], Page.icon(icons[i], Math.max(1, lvl), s.label + " — niveau " + lvl, ChatFormatting.GOLD,
                    List.of(Page.line("§7" + s.description), Page.line(""), Page.line("§eCliquez pour améliorer (+1)")), false),
                    (sp, c) -> {
                        report(sp, PowerSystem.upgradeStat(sp, s));
                        later(sp, () -> openStats(sp));
                    });
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    // ================================================================== Pouvoirs

    public static void openPowers(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Pouvoirs divins — Foi " + (int) p.faith + "/" + (int) p.maxFaith()).frame(paneFor(p));
        page.set(4, Page.icon(Items.LAPIS_LAZULI, "Foi : " + (int) p.faith + " / " + (int) p.maxFaith(), ChatFormatting.AQUA,
                "§7Se régénère toute seule (plus vite avec l'Esprit,", "§7le palier et vos fidèles)",
                "§7Raccourci : accroupi + clic droit avec le Codex"));
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33};
        PowerActions.Power[] powers = PowerActions.Power.values();
        for (int i = 0; i < powers.length && i < slots.length; i++) {
            PowerActions.Power power = powers[i];
            boolean unlocked = p.tier.atLeast(power.required);
            long cd = PowerActions.cooldownLeft(player, power);
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§7" + power.description));
            lore.add(Page.line("§bCoût : " + (int) power.cost + " Foi  §7·  Recharge : " + power.cooldownSeconds + " s"));
            if (!unlocked) lore.add(Page.line("§cDébloqué au palier " + power.required.label));
            else if (cd > 0) lore.add(Page.line("§eDisponible dans " + Msg.time(cd)));
            else lore.add(Page.line("§aPrêt !"));
            lore.add(Page.line(""));
            lore.add(Page.line(unlocked ? "§eCliquez pour lancer" : "§8Verrouillé"));
            page.set(slots[i], Page.icon(unlocked ? power.icon : Items.GRAY_DYE, 1, power.label,
                    unlocked ? (cd > 0 ? ChatFormatting.YELLOW : ChatFormatting.GOLD) : ChatFormatting.DARK_GRAY, lore, unlocked && cd == 0),
                    (sp, c) -> {
                        if (!GodData.get(sp).tier.atLeast(power.required)) {
                            Msg.err(sp, "Requiert le palier " + power.required.label + ".");
                            return;
                        }
                        switch (power) {
                            case CHATIER -> later(sp, () -> openFollowers(sp, 0));
                            case TEMPS -> later(sp, () -> openTime(sp));
                            case ESPACE -> later(sp, () -> openSpace(sp));
                            default -> {
                                sp.closeContainer();
                                PowerActions.cast(sp, power);
                            }
                        }
                    });
        }
        if (p.specialization != Specialization.AUCUNE) {
            Item art = com.godmod.util.DivineItems.artifact(p.specialization).getItem();
            page.set(40, Page.icon(art, "Artefact : " + p.specialization.label, p.specialization.color,
                    "§7" + p.specialization.description, "§7Clic droit avec l'artefact en main pour l'utiliser"));
        } else if (p.tier.isGod()) {
            page.set(40, Page.icon(Items.WRITABLE_BOOK, "Choisir un domaine", ChatFormatting.GOLD,
                    "§7Vous êtes dieu : choisissez votre spécialisation", "§eCliquez pour choisir"),
                    (sp, c) -> later(sp, () -> openSpecialization(sp)));
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    public static void openSpecialization(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Choisir votre domaine").frame(paneFor(p));
        Item[] icons = {Items.HEART_OF_THE_SEA, Items.LIGHTNING_ROD, Items.ENDER_EYE};
        Specialization[] specs = {Specialization.OCEAN, Specialization.CIEL, Specialization.COSMOS};
        int[] slots = {20, 22, 24};
        for (int i = 0; i < 3; i++) {
            Specialization s = specs[i];
            page.set(slots[i], Page.icon(icons[i], s.label, s.color, "§7" + s.description, "", "§eCliquez pour choisir (définitif)"),
                    (sp, c) -> {
                        sp.closeContainer();
                        report(sp, chooseSpecialization(sp, s));
                    });
        }
        page.back((sp, c) -> later(sp, () -> openPowers(sp))).close();
        page.open(player);
    }

    public static String chooseSpecialization(ServerPlayer player, Specialization s) {
        PlayerProfile p = GodData.get(player);
        if (!p.tier.isGod()) return "Seul un dieu peut choisir un domaine.";
        if (p.specialization != Specialization.AUCUNE) return "Votre domaine est déjà " + p.specialization.label + ".";
        if (s == Specialization.AUCUNE) return "Domaine invalide.";
        p.specialization = s;
        GodData.dirty();
        Shop.give(player, com.godmod.util.DivineItems.artifact(s));
        Msg.ok(player, "Vous êtes désormais dieu du domaine " + s.label + ". Votre artefact vous a été remis.");
        GodData.log("DOMAINE : " + p.name + " choisit " + s.label);
        return null;
    }

    public static void openTime(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Maîtrise du temps").frame(paneFor(p));
        String[] modes = {"jour", "nuit", "pluie", "orage", "clair"};
        Item[] icons = {Items.SUNFLOWER, Items.BLACK_DYE, Items.WATER_BUCKET, Items.TRIDENT, Items.LIGHT_BLUE_DYE};
        int[] slots = {20, 21, 22, 23, 24};
        for (int i = 0; i < modes.length; i++) {
            String m = modes[i];
            page.set(slots[i], Page.icon(icons[i], m.substring(0, 1).toUpperCase() + m.substring(1), ChatFormatting.LIGHT_PURPLE,
                    "§bCoût : " + (int) PowerActions.Power.TEMPS.cost + " Foi"), (sp, c) -> {
                sp.closeContainer();
                PowerActions.time(sp, m);
            });
        }
        page.back((sp, c) -> later(sp, () -> openPowers(sp))).close();
        page.open(player);
    }

    public static void openSpace(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Maîtrise de l'espace").frame(paneFor(p));
        int i = 0;
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other == player || i >= INNER.length) continue;
            PlayerProfile op = GodData.get(other);
            page.set(INNER[i++], Page.icon(Items.ENDER_PEARL, op.name, op.tier.color, "§7" + op.tier.label,
                    "§bCoût : " + (int) PowerActions.Power.ESPACE.cost + " Foi", "§eCliquez pour vous y rendre"), (sp, c) -> {
                sp.closeContainer();
                ServerPlayer target = PowerSystem.online(sp.level().getServer(), op.uuid);
                if (target != null) PowerActions.space(sp, target);
            });
        }
        page.back((sp, c) -> later(sp, () -> openPowers(sp))).close();
        page.open(player);
    }

    // ================================================================== Fidèles

    public static void openFollowers(ServerPlayer player, int pageIndex) {
        PlayerProfile p = GodData.get(player);
        List<PlayerProfile> followers = GodData.followersOf(p.uuid);
        followers.sort(Comparator.comparingLong((PlayerProfile x) -> PowerSystem.powerScore(x)).reversed());
        Page page = new Page("Fidèles (" + followers.size() + ")").frame(paneFor(p));
        MinecraftServer server = player.level().getServer();

        if (p.master != null) {
            PlayerProfile m = GodData.find(p.master);
            if (m != null) {
                page.set(4, Page.icon(Items.GOLDEN_HELMET, "Votre dieu : " + m.name, m.tier.color,
                        "§7" + m.tier.label, "§7Il perçoit 10 % de vos gains", "§7Révoltez-vous avec /rebellion"));
            }
        } else {
            page.set(4, Page.icon(Items.FEATHER, "Vous êtes libre", ChatFormatting.WHITE, "§7Aucun dieu ne vous contrôle"));
        }

        int start = pageIndex * INNER.length;
        for (int i = 0; i < INNER.length && start + i < followers.size(); i++) {
            PlayerProfile f = followers.get(start + i);
            boolean on = PowerSystem.online(server, f.uuid) != null;
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§7" + f.tier.label + (on ? "  §a● en ligne" : "")));
            lore.add(Page.line("§dEssence : " + f.essence + "   §ePuissance : " + PowerSystem.powerScore(f)));
            lore.add(Page.line("§7Force " + f.stat(Stat.FORCE) + " · Vitalité " + f.stat(Stat.VITALITE)
                    + " · Agilité " + f.stat(Stat.AGILITE) + " · Esprit " + f.stat(Stat.ESPRIT)));
            lore.add(Page.line("§6Argent : " + f.money + "   §7Fidèles : " + GodData.totalFollowers(f.uuid)));
            lore.add(Page.line(""));
            lore.add(Page.line("§eClic gauche : convoquer (10 Foi)"));
            lore.add(Page.line("§eClic droit : châtier (" + (int) PowerActions.Power.CHATIER.cost + " Foi)"));
            lore.add(Page.line("§cMaj + clic : libérer"));
            page.set(INNER[i], Page.icon(on ? Items.PLAYER_HEAD : Items.SKELETON_SKULL, 1, f.name, f.tier.color, lore, false),
                    (sp, c) -> {
                        sp.closeContainer();
                        followerAction(sp, f, c);
                    });
        }
        if (followers.isEmpty()) {
            page.set(22, Page.icon(Items.BOOK, "Aucun fidèle", ChatFormatting.GRAY,
                    "§7Devenez demi-dieu, puis battez en PvP", "§7des joueurs moins puissants pour les convertir."));
        }
        if (start + INNER.length < followers.size()) {
            page.set(53, Page.icon(Items.SPECTRAL_ARROW, "Page suivante →", ChatFormatting.YELLOW), (sp, c) -> later(sp, () -> openFollowers(sp, pageIndex + 1)));
        }
        page.back((sp, c) -> later(sp, () -> {
            if (pageIndex > 0) openFollowers(sp, pageIndex - 1);
            else openMain(sp);
        })).close();
        page.open(player);
    }

    private static void followerAction(ServerPlayer god, PlayerProfile f, GodMenu.Click click) {
        switch (click) {
            case SHIFT -> report(god, FaithSystem.releaseFollower(god, f));
            case RIGHT -> PowerActions.smite(god, f);
            default -> {
                ServerPlayer fp = PowerSystem.online(god.level().getServer(), f.uuid);
                if (fp == null) {
                    Msg.err(god, f.name + " n'est pas connecté.");
                } else if (PowerSystem.spendFaith(god, 10)) {
                    PowerActions.summonOne(god, fp);
                }
            }
        }
    }

    // ================================================================== Boutique

    public static void openPerkShop(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Privilèges — " + Msg.money(p.money)).frame(Items.MAGENTA_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.GOLDEN_HELMET, "Privilèges", ChatFormatting.LIGHT_PURPLE,
                "§7Achetés une fois, utilisables pour toujours.", "§7Aussi disponibles en jetons AFK (/afkshop).",
                "§7Possédés : " + p.perks.size() + "/" + com.godmod.system.Perks.Perk.values().length));
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 30, 32};
        com.godmod.system.Perks.Perk[] all = com.godmod.system.Perks.Perk.values();
        for (int i = 0; i < all.length && i < slots.length; i++) {
            com.godmod.system.Perks.Perk k = all[i];
            boolean owned = com.godmod.system.Perks.ownsProfile(p, k);
            boolean can = p.money >= k.price;
            page.set(slots[i], Page.icon(owned ? Items.LIME_DYE : k.icon, 1, k.usage, owned ? ChatFormatting.GREEN : can ? ChatFormatting.GOLD : ChatFormatting.GRAY, List.of(
                    Page.line("§7" + k.description),
                    Page.line(k.cooldownSeconds > 0 ? "§7Délai : " + Msg.time(k.cooldownSeconds * 1000L) : "§7Sans délai"),
                    Page.line(owned ? "§aDébloqué" : "§6Prix : " + Msg.money(k.price) + " §8(ou " + k.tokens + " jetons AFK)"),
                    Page.line(owned ? "" : can ? "§eMaj + clic pour acheter" : "§cPas assez d'argent")), owned), (sp, c) -> {
                if (owned) return;
                if (c != GodMenu.Click.SHIFT) {
                    Msg.info(sp, "Maj + clic pour confirmer l'achat de " + k.usage + " (" + Msg.money(k.price) + ").");
                    return;
                }
                report(sp, com.godmod.system.Perks.buy(sp, k, false));
                later(sp, () -> openPerkShop(sp));
            });
        }
        page.back((sp, c) -> later(sp, () -> openShop(sp))).close();
        page.open(player);
    }

    public static void openShop(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Boutique — " + p.money + " pièces").frame(Items.LIME_STAINED_GLASS_PANE);
        Shop.Category[] cats = Shop.Category.values();
        int[] slots = {19, 21, 23, 25};
        for (int i = 0; i < cats.length; i++) {
            Shop.Category cat = cats[i];
            page.set(slots[i], Page.icon(cat.icon, cat.label, ChatFormatting.GREEN,
                    "§7" + Shop.entries(cat).size() + " articles", "", "§eCliquez pour parcourir"),
                    (sp, c) -> later(sp, () -> openShopCategory(sp, cat, 0)));
        }
        page.set(29, Page.icon(Items.GOLDEN_HELMET, "Privilèges", ChatFormatting.LIGHT_PURPLE,
                "§7Commandes de confort à vie :", "§7/craft, /ec, /enclume, /tete, /soin, /vol…", "§7Chères, mais définitives", "", "§eCliquez pour ouvrir"),
                (sp, c) -> later(sp, () -> openPerkShop(sp)));
        page.set(31, Page.icon(Items.HOPPER, "Vendre", ChatFormatting.GOLD,
                "§7Revendez vos objets (5 à 10× moins cher qu'à l'achat)", "", "§eCliquez pour ouvrir"),
                (sp, c) -> later(sp, () -> openSell(sp)));
        if (GodData.settings().payEnabled) {
            page.set(33, Page.icon(Items.DIAMOND, "Premium", ChatFormatting.AQUA, "§bGemmes : " + p.gems),
                    (sp, c) -> later(sp, () -> openPremium(sp)));
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    public static void openShopCategory(ServerPlayer player, Shop.Category cat, int pageIndex) {
        PlayerProfile p = GodData.get(player);
        List<Shop.Entry> entries = Shop.entries(cat);
        Page page = new Page(cat.label + " — " + p.money + " pièces").frame(Items.LIME_STAINED_GLASS_PANE);
        int start = pageIndex * INNER.length;
        for (int i = 0; i < INNER.length && start + i < entries.size(); i++) {
            Shop.Entry e = entries.get(start + i);
            ItemStack icon = e.stack().get();
            int max = icon.getMaxStackSize();
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§6Achat : " + e.price() + " pièces"));
            lore.add(Page.line(e.special() ? "§7Non revendable" : "§7Revente : " + Shop.sellPrice(icon.getItem()) + " pièces"));
            lore.add(Page.line(""));
            lore.add(Page.line("§eClic gauche : acheter ×1"));
            if (max > 1) {
                lore.add(Page.line("§eClic droit : acheter ×" + Math.min(16, max) + " (" + e.price() * Math.min(16, max) + ")"));
                lore.add(Page.line("§eMaj + clic : acheter ×" + max + " (" + e.price() * max + ")"));
            }
            icon.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
            page.set(INNER[i], icon, (sp, c) -> {
                int n = switch (c) {
                    case LEFT -> 1;
                    case RIGHT -> Math.min(16, max);
                    case SHIFT -> max;
                };
                report(sp, Shop.buy(sp, e, n));
                later(sp, () -> openShopCategory(sp, cat, pageIndex));
            });
        }
        if (start + INNER.length < entries.size()) {
            page.set(53, Page.icon(Items.SPECTRAL_ARROW, "Page suivante →", ChatFormatting.YELLOW), (sp, c) -> later(sp, () -> openShopCategory(sp, cat, pageIndex + 1)));
        }
        page.back((sp, c) -> later(sp, () -> openShop(sp))).close();
        page.open(player);
    }

    public static void openSell(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Vendre — " + p.money + " pièces").frame(Items.YELLOW_STAINED_GLASS_PANE);
        page.set(22, Page.icon(Items.HOPPER, "Cliquez sur un objet de VOTRE inventaire", ChatFormatting.GOLD,
                "§eClic gauche : vendre 1", "§eClic droit ou Maj + clic : vendre la pile",
                "§7Le prix s'affiche dans le chat après la vente"));
        page.onInventoryClick((sp, slot, c) -> {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return;
            report(sp, Shop.sell(sp, stack, c != GodMenu.Click.LEFT));
            slot.setChanged();
        });
        page.back((sp, c) -> later(sp, () -> openShop(sp))).close();
        page.open(player);
    }

    public static void openPremium(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Premium — " + p.gems + " gemmes").frame(Items.CYAN_STAINED_GLASS_PANE);
        if (!GodData.settings().payEnabled) {
            page.set(22, Page.icon(Items.BARRIER, "Premium désactivé", ChatFormatting.RED, "§7Un admin peut l'activer avec /pay enable"));
        } else {
            int[] slots = {19, 21, 23, 25, 31};
            int i = 0;
            for (Shop.PremiumOffer o : Shop.PREMIUM.values()) {
                if (i >= slots.length) break;
                page.set(slots[i], Page.icon(o.icon(), o.label(), ChatFormatting.AQUA,
                        "§7" + o.description(), "§bPrix : " + o.gems() + " gemmes", "", "§eCliquez pour acheter"), (sp, c) -> {
                    report(sp, Shop.buyPremium(sp, o.id()));
                    later(sp, () -> openPremium(sp));
                });
                i++;
            }
            page.set(4, Page.icon(Items.DIAMOND, "Gemmes : " + p.gems, ChatFormatting.AQUA,
                    "§7Les gemmes s'obtiennent en soutenant le serveur", "§7(attribuées par un administrateur)"));
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    // ================================================================== Rébellion / Tournoi / Guerre / Classement

    public static void openRebellion(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Rébellion").frame(Items.RED_STAINED_GLASS_PANE);
        RebellionSystem.Rebellion r = RebellionSystem.involving(p.uuid);
        if (r == null && p.master != null) r = RebellionSystem.against(p.master);
        if (r != null) {
            page.set(13, Page.icon(Items.FIRE_CHARGE, "Rébellion en cours", ChatFormatting.RED,
                    "§7Contre : " + nameOf(r.god), "§7Meneur : " + nameOf(r.leader),
                    "§7Rebelles : " + r.rebels.size(),
                    r.fighting ? "§cCombat ! Fin dans " + Msg.time(r.phaseEnd - System.currentTimeMillis())
                            : "§eRalliement : " + Msg.time(r.phaseEnd - System.currentTimeMillis())));
        }
        if (p.master == null) {
            page.set(22, Page.icon(Items.FEATHER, "Vous êtes libre", ChatFormatting.WHITE, "§7Aucun dieu contre qui vous rebeller"));
        } else {
            long cd = RebellionSystem.cooldownLeft(p);
            page.set(20, Page.icon(Items.IRON_AXE, "Déclarer une rébellion", ChatFormatting.DARK_RED,
                    "§7Contre " + nameOf(p.master),
                    "§7Réussite : il chute et vous pouvez prendre son trône",
                    "§cÉchec : −30 % Essence, −25 % argent, Parjure 3 jours",
                    cd > 0 ? "§cDisponible dans " + Msg.time(cd) : "§aDisponible",
                    "", "§eCliquez pour déclarer"), (sp, c) -> {
                sp.closeContainer();
                report(sp, RebellionSystem.declare(sp));
            });
            page.set(24, Page.icon(Items.SHIELD, "Rejoindre la rébellion", ChatFormatting.RED,
                    "§7Pendant les 60 s de ralliement", "", "§eCliquez pour rejoindre"), (sp, c) -> {
                sp.closeContainer();
                report(sp, RebellionSystem.join(sp));
            });
        }
        page.set(31, Page.icon(Items.BOOK, "Règles", ChatFormatting.YELLOW,
                "§71. Un fidèle déclare la rébellion (1 fois / " + GodData.settings().rebellionCooldownHours + " h)",
                "§72. 60 s pour que d'autres fidèles la rejoignent",
                "§73. 10 min de combat : abattez votre dieu",
                "§74. Dieu inactif depuis " + GodData.settings().inactiveGodDays + " j : /trone reclamer <dieu>"));
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    public static void openTournament(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Tournoi").frame(Items.YELLOW_STAINED_GLASS_PANE);
        TournamentSystem.Tournament t = TournamentSystem.current();
        if (t == null) {
            page.set(22, Page.icon(Items.CLOCK, "Aucun tournoi", ChatFormatting.GRAY,
                    "§7Un admin lance un tournoi avec /tournoi creer",
                    "§7ou réclamez le trône d'un dieu inactif : /trone reclamer <dieu>"));
        } else {
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§7Phase : " + t.phase.name().toLowerCase()));
            if (t.throneOf != null) lore.add(Page.line("§6Enjeu : le trône de " + nameOf(t.throneOf)));
            lore.add(Page.line("§6Prix : " + t.prize + " pièces"));
            lore.add(Page.line(""));
            for (Map.Entry<String, Integer> e : TournamentSystem.standings().entrySet()) {
                lore.add(Page.line("§f" + e.getKey() + " §7— " + e.getValue() + " victoire(s)"));
            }
            page.set(13, Page.icon(Items.GOLDEN_SWORD, 1, "Tournoi en cours", ChatFormatting.GOLD, lore, true));
            boolean in = t.participants.contains(p.uuid);
            page.set(22, Page.icon(in ? Items.RED_DYE : Items.LIME_DYE, in ? "Se désinscrire" : "S'inscrire",
                    in ? ChatFormatting.RED : ChatFormatting.GREEN, "§7Duels 1v1 en poules, morts sans perte d'objets"), (sp, c) -> {
                report(sp, in ? TournamentSystem.leave(sp) : TournamentSystem.join(sp));
                later(sp, () -> openTournament(sp));
            });
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    public static void openWar(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Guerre divine").frame(Items.BLACK_STAINED_GLASS_PANE);
        WarSystem.War w = WarSystem.warOf(p);
        if (w != null) {
            page.set(4, Page.icon(Items.NETHERITE_SWORD, nameOf(w.attacker) + " ⚔ " + nameOf(w.defender), ChatFormatting.DARK_RED,
                    "§7Fin dans " + Msg.time(w.endsAt - System.currentTimeMillis()),
                    "§7Abattez le dieu adverse pour soumettre son panthéon",
                    "§7Les mondes divins des deux camps peuvent être saccagés"));
        } else {
            page.set(4, Page.icon(Items.WHITE_BANNER, "Déclarer la guerre", ChatFormatting.RED,
                    "§7Réservé aux dieux · Coût : " + (int) WarSystem.DECLARE_COST + " Foi",
                    "§7Durée : 30 min · Choisissez une cible ci-dessous"));
        }
        int i = 0;
        List<PlayerProfile> gods = new ArrayList<>();
        for (PlayerProfile o : GodData.state().players.values()) if (o.tier.isGod() && !o.uuid.equals(p.uuid)) gods.add(o);
        gods.sort(Comparator.comparingLong((PlayerProfile x) -> PowerSystem.powerScore(x)).reversed());
        for (PlayerProfile g : gods) {
            if (i >= INNER.length) break;
            boolean sameTree = GodData.isAbove(p.uuid, g) || GodData.isAbove(g.uuid, p);
            page.set(INNER[i++], Page.icon(sameTree ? Items.GRAY_BANNER : Items.RED_BANNER, g.name, g.tier.color,
                    "§7" + g.tier.label + " · " + GodData.totalFollowers(g.uuid) + " fidèles",
                    "§ePuissance : " + PowerSystem.powerScore(g),
                    sameTree ? "§8Même hiérarchie" : "§eCliquez pour déclarer la guerre"), (sp, c) -> {
                sp.closeContainer();
                report(sp, WarSystem.declare(sp, g));
            });
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    public static void openRanking(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Classement de puissance").frame(Items.YELLOW_STAINED_GLASS_PANE);
        List<PlayerProfile> all = new ArrayList<>(GodData.state().players.values());
        all.sort(Comparator.comparingLong((PlayerProfile x) -> PowerSystem.powerScore(x)).reversed());
        boolean seeDetails = p.tier.isGod();
        for (int i = 0; i < INNER.length && i < all.size(); i++) {
            PlayerProfile o = all.get(i);
            Item icon = i == 0 ? Items.GOLD_BLOCK : i == 1 ? Items.IRON_BLOCK : i == 2 ? Items.COPPER_BLOCK : Items.PAPER;
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§7" + o.tier.label + (o.master != null ? " · fidèle de " + nameOf(o.master) : "")));
            lore.add(Page.line("§ePuissance : " + PowerSystem.powerScore(o)));
            lore.add(Page.line("§7Fidèles : " + GodData.totalFollowers(o.uuid)));
            if (seeDetails) {
                lore.add(Page.line("§dEssence : " + o.essence + " · §6" + o.money + " pièces"));
                lore.add(Page.line("§7Domaine : " + o.specialization.label));
            }
            page.set(INNER[i], Page.icon(icon, i + 1, "#" + (i + 1) + " " + o.name, o.tier.color, lore, i == 0));
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    // ================================================================== Métiers

    public static void openJobs(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        com.godmod.data.Job featured = com.godmod.progress.JobSystem.featured();
        Page page = new Page("Métiers — " + com.godmod.progress.JobSystem.totalLevels(p) + " niveaux").frame(Items.GREEN_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.EXPERIENCE_BOTTLE, "Vos métiers", ChatFormatting.GREEN,
                "§7Chaque action donne de l'XP de métier", "§7et de l'Essence divine (½ de l'XP).",
                "§7Chaque niveau : pièces + Essence. Tous les 5 niveaux : une clé.",
                "§7Des talents se débloquent en montant.",
                "§6Métier en vogue aujourd'hui : " + featured.icon + " " + featured.label + " (XP ×1,5)"));
        int[] slots = {20, 21, 22, 23, 24};
        com.godmod.data.Job[] jobs = com.godmod.data.Job.values();
        for (int i = 0; i < jobs.length; i++) {
            com.godmod.data.Job job = jobs[i];
            int lvl = com.godmod.progress.JobSystem.level(p, job);
            double xp = com.godmod.progress.JobSystem.xp(p, job);
            double need = com.godmod.data.Job.needed(lvl);
            boolean max = lvl >= com.godmod.data.Job.MAX_LEVEL;
            int filled = max ? 20 : (int) Math.round(20 * xp / need);
            com.godmod.progress.JobSystem.Perk next = com.godmod.progress.JobSystem.nextPerk(p, job);
            int unlocked = 0;
            for (com.godmod.progress.JobSystem.Perk k : com.godmod.progress.JobSystem.PERKS.get(job)) if (lvl >= k.level()) unlocked++;
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§a" + "|".repeat(Math.max(0, filled)) + "§8" + "|".repeat(Math.max(0, 20 - filled))));
            lore.add(Page.line(max ? "§6Niveau maximum !" : "§7XP : " + (int) xp + " / " + (int) need + " (" + (int) (100 * xp / need) + " %)"));
            lore.add(Page.line("§7Talents : §d" + unlocked + "/" + com.godmod.progress.JobSystem.PERKS.get(job).size()));
            if (next != null) lore.add(Page.line("§7Prochain : §f" + next.name() + " §8(niv. " + next.level() + ")"));
            if (job == featured) lore.add(Page.line("§6✦ En vogue aujourd'hui : XP ×1,5"));
            lore.add(Page.line(""));
            lore.add(Page.line("§eCliquez : talents, barème et classement"));
            page.set(slots[i], Page.icon(job.item, Math.max(1, Math.min(64, lvl)), job.icon + " " + job.label + " — niveau " + lvl,
                    job.color, lore, job == featured), (sp, c) -> later(sp, () -> openJobDetail(sp, job)));
        }
        page.set(31, Page.icon(Items.WRITABLE_BOOK, "Quêtes liées aux métiers", ChatFormatting.YELLOW,
                "§7Les quêtes du jour donnent aussi de l'XP", "§7de métier. Cliquez pour les voir."), (sp, c) -> later(sp, () -> openQuests(sp)));
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    private static final java.util.Map<com.godmod.data.Job, String[]> XP_TABLE = new java.util.EnumMap<>(java.util.Map.of(
            com.godmod.data.Job.MINEUR, new String[]{"§7Pierre, ardoise, granite… : 0,25", "§7Charbon / cuivre : 4-5 · fer : 8-9", "§7Redstone : 7-8 · lapis : 10-11 · or : 12-13",
                    "§7Diamant : 35-38 · émeraude : 45-48", "§7Lingot sorti du four : 0,5"},
            com.godmod.data.Job.BUCHERON, new String[]{"§7Bûche : 2,5", "§7Bûche abattue d'un coup : 1,2"},
            com.godmod.data.Job.FERMIER, new String[]{"§7Culture mûre : 3", "§7Pastèque, citrouille, canne, cactus, cacao : 1,5", "§7Animal né : 6"},
            com.godmod.data.Job.CHASSEUR, new String[]{"§7Monstre : 7 · animal : 2,5", "§7Wither, dragon, Warden : 300"},
            com.godmod.data.Job.PECHEUR, new String[]{"§7Prise : 10"}));

    public static void openJobDetail(ServerPlayer player, com.godmod.data.Job job) {
        PlayerProfile p = GodData.get(player);
        int lvl = com.godmod.progress.JobSystem.level(p, job);
        Page page = new Page(job.icon + " " + job.label + " — niveau " + lvl).frame(Items.LIME_STAINED_GLASS_PANE);
        double xp = com.godmod.progress.JobSystem.xp(p, job), need = com.godmod.data.Job.needed(lvl);
        boolean max = lvl >= com.godmod.data.Job.MAX_LEVEL;
        int filled = max ? 20 : (int) Math.round(20 * xp / need);
        page.set(4, Page.icon(job.item, Math.max(1, Math.min(64, lvl)), job.label + " niveau " + lvl, job.color, List.of(
                Page.line("§a" + "|".repeat(Math.max(0, filled)) + "§8" + "|".repeat(Math.max(0, 20 - filled))),
                Page.line(max ? "§6Niveau maximum : l'XP devient de l'Essence ×1,5" : "§7XP : " + (int) xp + " / " + (int) need),
                Page.line("§7Comment gagner : §f" + job.how),
                Page.line("§7Prochain niveau : §6+" + 25 * (lvl + 1) + " pièces §7· §d+" + 15 * (lvl + 1) + " Essence")), true));
        List<com.godmod.progress.JobSystem.Perk> perks = com.godmod.progress.JobSystem.PERKS.get(job);
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < perks.size() && i < slots.length; i++) {
            com.godmod.progress.JobSystem.Perk k = perks.get(i);
            boolean on = lvl >= k.level();
            page.set(slots[i], Page.icon(on ? Items.ENCHANTED_BOOK : Items.BOOK, Math.max(1, k.level()), (on ? "✔ " : "✖ ") + k.name(),
                    on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY, List.of(
                            Page.line("§7" + k.description()),
                            Page.line(on ? "§aDébloqué" : "§8Débloqué au niveau " + k.level() + " (encore " + (k.level() - lvl) + ")")), on));
        }
        String[] table = XP_TABLE.get(job);
        page.set(38, Page.icon(Items.PAPER, "Barème d'XP", ChatFormatting.YELLOW, table));
        List<Component> lore = new ArrayList<>();
        int rank = 1;
        for (PlayerProfile o : com.godmod.progress.JobSystem.top(job, 5)) {
            lore.add(Page.line((rank == 1 ? "§6" : "§7") + rank + ". " + o.name + " — niveau " + com.godmod.progress.JobSystem.level(o, job)));
            rank++;
        }
        page.set(42, Page.icon(Items.GOLD_INGOT, 1, "Meilleurs " + job.label.toLowerCase() + "s", ChatFormatting.GOLD, lore, false));
        page.set(40, Page.icon(Items.CLOCK, "Bonus actuels", ChatFormatting.AQUA,
                job == com.godmod.progress.JobSystem.featured() ? "§6Métier en vogue : XP ×1,5" : "§7En vogue aujourd'hui : " + com.godmod.progress.JobSystem.featured().label,
                com.godmod.progress.EventSystem.jobXpMultiplier() > 1 ? "§aÉvénement : XP ×2 !" : "§7Aucun événement d'XP en cours",
                "§7Les blocs posés par un joueur ne rapportent rien."));
        page.back((sp, c) -> later(sp, () -> openJobs(sp))).close();
        page.open(player);
    }

    // ================================================================== Quêtes

    private static List<Component> questLore(PlayerProfile p, com.godmod.progress.QuestSystem.Quest q, com.godmod.data.QuestProgress qp, String... tail) {
        List<Component> lore = new ArrayList<>();
        lore.add(Page.line("§7" + q.description()));
        lore.add(Page.line("§7Difficulté : ").append(Component.literal(q.difficulty().stars + " " + q.difficulty().label).withStyle(q.difficulty().color)));
        if (qp != null) {
            int filled = (int) Math.round(20.0 * qp.progress / q.amount());
            lore.add(Page.line("§a" + "|".repeat(filled) + "§8" + "|".repeat(20 - filled) + " §f" + qp.progress + "/" + q.amount()));
        }
        lore.add(Page.line(com.godmod.progress.QuestSystem.rewardLine(p, q)));
        for (String t : tail) lore.add(Page.line(t));
        return lore;
    }

    public static void openQuests(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        com.godmod.progress.QuestSystem.refreshOffers(p);
        com.godmod.progress.AscensionQuests.check(player);
        int max = com.godmod.progress.QuestSystem.MAX_ACTIVE;
        Page page = new Page("Quêtes — " + p.quests.size() + "/" + max + " en cours").frame(Items.YELLOW_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.WRITABLE_BOOK, "Quêtes du jour", ChatFormatting.YELLOW,
                "§7Acceptez jusqu'à " + max + " quêtes : elles avancent toutes seules.",
                "§7Plus elles sont difficiles, plus elles rapportent,", "§7et les gains montent avec votre palier (+20 %/palier).",
                "§7Aujourd'hui : §e" + p.questsToday + "§7 accomplie(s) · bonus à 3 et 5",
                "§7Total : §e" + p.questsDone + "§7 · une clé Rare toutes les 10",
                "§7Nouvelles quêtes dans " + Msg.time(com.godmod.progress.QuestSystem.untilTomorrow())));
        int[] active = {11, 12, 13, 14, 15};
        for (int i = 0; i < active.length; i++) {
            if (i < p.quests.size()) {
                com.godmod.data.QuestProgress qp = p.quests.get(i);
                com.godmod.progress.QuestSystem.Quest q = com.godmod.progress.QuestSystem.QUESTS.get(qp.id);
                if (q == null) continue;
                page.set(active[i], Page.icon(q.icon(), 1, q.title(), q.difficulty().color, questLore(p, q, qp, "", "§cMaj + clic : abandonner"), true), (sp, c) -> {
                    if (c == GodMenu.Click.SHIFT) {
                        report(sp, com.godmod.progress.QuestSystem.abandon(sp, qp.id));
                        later(sp, () -> openQuests(sp));
                    }
                });
            } else {
                page.set(active[i], Page.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, "Emplacement libre", ChatFormatting.GRAY, "§7Acceptez une quête ci-dessous"));
            }
        }
        page.set(22, Page.icon(Items.PAPER, "Propositions du jour", ChatFormatting.WHITE, "§7Cliquez sur une quête pour l'accepter"));
        int[] offers = {28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < offers.length && i < p.questOffers.size(); i++) {
            String id = p.questOffers.get(i);
            com.godmod.progress.QuestSystem.Quest q = com.godmod.progress.QuestSystem.QUESTS.get(id);
            if (q == null) continue;
            page.set(offers[i], Page.icon(q.icon(), 1, q.difficulty().stars + " " + q.title(), q.difficulty().color,
                    questLore(p, q, null, "§7Métier : " + (q.job() == null ? "—" : q.job().label), "", "§eCliquez pour accepter"), false), (sp, c) -> {
                report(sp, com.godmod.progress.QuestSystem.accept(sp, id));
                later(sp, () -> openQuests(sp));
            });
        }
        if (p.questOffers.isEmpty()) {
            page.set(31, Page.icon(Items.BARRIER, "Plus de propositions", ChatFormatting.GRAY, "§7Renouvelez-les ou revenez demain."));
        }
        long price = com.godmod.progress.QuestSystem.rerollPrice(p);
        page.set(40, Page.icon(Items.ENDER_EYE, "Renouveler les propositions", ChatFormatting.AQUA,
                "§7Tire 7 nouvelles quêtes (les quêtes en cours restent).",
                price == 0 ? "§aGratuit (1 fois par jour)" : "§6Prix : " + Msg.money(price), "", "§eCliquez pour renouveler"), (sp, c) -> {
            report(sp, com.godmod.progress.QuestSystem.reroll(sp));
            later(sp, () -> openQuests(sp));
        });
        com.godmod.progress.AscensionQuests.Step step = com.godmod.progress.AscensionQuests.current(p);
        page.set(38, Page.icon(Items.ENCHANTED_BOOK, 1, "Quêtes d'ascension", ChatFormatting.LIGHT_PURPLE, List.of(
                Page.line("§7Le fil rouge de mortel à dieu suprême"),
                Page.line(step == null ? "§aTerminées !" : "§dÉtape " + (p.storyStep + 1) + " : " + step.title()),
                Page.line(step == null ? "" : "§7" + step.goal() + " (" + com.godmod.progress.AscensionQuests.progress(p, step) + "/" + step.target() + ")"),
                Page.line(""), Page.line("§eCliquez pour voir le chemin")), true), (sp, c) -> later(sp, () -> com.godmod.progress.AscensionQuests.open(sp)));
        int weeklyDone = 0;
        for (com.godmod.data.QuestProgress w : p.weekly) if (w.done) weeklyDone++;
        page.set(42, Page.icon(Items.HEART_OF_THE_SEA, 1, "Défis de la semaine", ChatFormatting.AQUA, List.of(
                Page.line("§73 grands objectifs, actifs d'office"),
                Page.line("§7Relevés : §b" + weeklyDone + "/" + p.weekly.size() + (p.weeklyBonus ? " §a(bonus Épique obtenu)" : "")),
                Page.line("§7Renouvelés dans " + Msg.time(com.godmod.progress.QuestSystem.untilNextWeek())),
                Page.line(""), Page.line("§eCliquez pour les voir")), weeklyDone < p.weekly.size()), (sp, c) -> later(sp, () -> openWeekly(sp)));
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    public static void openWeekly(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        com.godmod.progress.QuestSystem.refreshOffers(p);
        Page page = new Page("Défis de la semaine").frame(Items.CYAN_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.HEART_OF_THE_SEA, "Défis de la semaine", ChatFormatting.AQUA,
                "§7Ils avancent tout seuls, sans les accepter.", "§7Chacun : beaucoup de pièces, d'Essence et une clé Rare.",
                "§7Les 3 relevés : une clé Épique en bonus.", "§7Renouvelés lundi (dans " + Msg.time(com.godmod.progress.QuestSystem.untilNextWeek()) + ")"));
        int[] slots = {20, 22, 24};
        for (int i = 0; i < slots.length && i < p.weekly.size(); i++) {
            com.godmod.data.QuestProgress qp = p.weekly.get(i);
            com.godmod.progress.QuestSystem.Quest q = com.godmod.progress.QuestSystem.WEEKLIES.get(qp.id);
            if (q == null) continue;
            page.set(slots[i], Page.icon(qp.done ? Items.LIME_DYE : q.icon(), 1, (qp.done ? "✔ " : "") + q.title(), qp.done ? ChatFormatting.GREEN : ChatFormatting.AQUA,
                    questLore(p, q, qp, qp.done ? "§aRelevé !" : ""), !qp.done));
        }
        page.back((sp, c) -> later(sp, () -> openQuests(sp))).close();
        page.open(player);
    }

    // ================================================================== Clés & caisses

    public static void openKeys(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Clés de caisse").frame(Items.PURPLE_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.TRIPWIRE_HOOK, "Vos clés", ChatFormatting.LIGHT_PURPLE,
                "§7Ouvrez les caisses au Sanctuaire (clic droit dessus)",
                "§7Clés gagnées : niveaux de métier, quêtes, pêche,", "§7ou achetées ici."));
        int[] slots = {20, 22, 24, 31};
        com.godmod.progress.CrateSystem.Crate[] crates = com.godmod.progress.CrateSystem.Crate.values();
        for (int i = 0; i < crates.length; i++) {
            com.godmod.progress.CrateSystem.Crate c = crates[i];
            int n = com.godmod.progress.CrateSystem.keys(p, c);
            page.set(slots[i], Page.icon(c.icon, Math.max(1, Math.min(64, n)), "Clé " + c.label + " × " + n, c.color, List.of(
                    Page.line("§7Clic gauche : voir le contenu de la caisse"),
                    Page.line("§eClic droit : acheter une clé (" + c.keyPrice + " pièces)")), n > 0), (sp, click) -> {
                if (click == GodMenu.Click.RIGHT) {
                    PlayerProfile me = GodData.get(sp);
                    if (me.money < c.keyPrice) Msg.err(sp, "Pas assez d'argent (" + me.money + "/" + c.keyPrice + ").");
                    else {
                        me.money -= c.keyPrice;
                        com.godmod.progress.CrateSystem.addKey(me, c, 1);
                        Msg.ok(sp, "Clé " + c.label + " achetée.");
                    }
                    later(sp, () -> openKeys(sp));
                } else later(sp, () -> com.godmod.progress.CrateSystem.openPreview(sp, c));
            });
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    // ================================================================== Guide de progression

    public static void openGuide(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Comment progresser ?").frame(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        com.godmod.data.Tier next = p.tier.next();
        page.set(4, profileIcon(p));
        page.set(19, Page.icon(Items.IRON_PICKAXE, "1. Les métiers", ChatFormatting.GREEN,
                "§7Minez, coupez, récoltez, chassez, pêchez :", "§7chaque action donne de l'XP de métier", "§7ET de l'Essence (½ de l'XP).", "§e/metiers"));
        page.set(20, Page.icon(Items.WRITABLE_BOOK, "2. Les quêtes", ChatFormatting.YELLOW,
                "§75 quêtes en parallèle, renouvelées chaque jour.", "§7Jusqu'à 600 Essence, des pièces et des clés.", "§e/quetes"));
        page.set(21, Page.icon(Items.TRIPWIRE_HOOK, "3. Les caisses", ChatFormatting.LIGHT_PURPLE,
                "§7Clés des métiers et quêtes → caisses au Sanctuaire :", "§7diamants, Essence, générateurs de mobs…"));
        page.set(22, Page.icon(Items.IRON_SWORD, "4. Le PvP", ChatFormatting.RED,
                "§7Victoire : +25 Essence. Demi-dieu : convertissez", "§7les plus faibles en fidèles (+60). Un dieu vaincu", "§7par un mortel chute (+500 pour le vainqueur)."));
        page.set(23, Page.icon(Items.GOLDEN_SWORD, "5. Tournois & guerres", ChatFormatting.GOLD,
                "§7Tournoi gagné : +300 Essence et des pièces.", "§7Guerre gagnée : tout le panthéon adverse."));
        page.set(24, Page.icon(Items.CLOCK, "6. Le temps de jeu", ChatFormatting.WHITE, "§7+2 Essence par minute connectée."));
        page.set(25, Page.icon(Items.BLAZE_POWDER, "7. Les caractéristiques", ChatFormatting.RED,
                "§7Chaque palier et chaque tranche d'Essence", "§7donnent des points : Force, Vitalité, Agilité, Esprit."));
        page.set(28, Page.icon(Items.CLOCK, "8. La récompense quotidienne", ChatFormatting.GOLD,
                "§7Connectez-vous chaque jour : série de 7 jours,", "§7+10 % par semaine complète, clés au jour 3, 5 et 7.", "§e/recompense"),
                (sp, c) -> later(sp, () -> com.godmod.progress.DailyReward.open(sp)));
        page.set(29, Page.icon(Items.FIREWORK_ROCKET, "9. Les événements", ChatFormatting.AQUA,
                "§7Régulièrement : XP ou Essence ×2, Titan à abattre,", "§7chasse au trésor, concours, quiz, pluie de clés…", "§e/event"),
                (sp, c) -> {
                    sp.closeContainer();
                    com.godmod.progress.EventSystem.info(sp);
                });
        int i = 0;
        int[] tierSlots = {37, 38, 39, 40, 41};
        for (com.godmod.data.Tier t : com.godmod.data.Tier.values()) {
            boolean reached = p.tier.atLeast(t);
            page.set(tierSlots[i++], Page.icon(reached ? Items.LIME_DYE : Items.GRAY_DYE, 1, t.label, t.color, List.of(
                    Page.line("§7Essence : " + com.godmod.system.PowerSystem.threshold(t)),
                    Page.line(t.followers > 0 ? "§7Fidèles : " + t.followers : "§7Aucun fidèle requis"),
                    Page.line("§7Plafond de caractéristique : " + t.statCap),
                    Page.line(reached ? "§aAtteint" : (t == next ? "§eProchain palier" : "§8À venir"))), t == next));
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    // ================================================================== Équipe (admins)

    public static void openTeam(ServerPlayer player) {
        Page page = new Page("Équipe du serveur").frame(Items.RED_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.GOLDEN_HELMET, "Nommer l'équipe", ChatFormatting.RED,
                "§7Clic gauche : passer Modérateur", "§7Clic droit : passer Admin", "§7Maj + clic : redevenir Joueur",
                "§7(aussi : /modo ajouter <joueur>)"));
        int i = 0;
        List<PlayerProfile> people = new ArrayList<>(GodData.state().players.values());
        people.sort(Comparator.comparing((PlayerProfile x) -> x.role == null ? 0 : -x.role.ordinal()).thenComparing(x -> x.name));
        for (PlayerProfile o : people) {
            if (i >= INNER.length) break;
            com.godmod.data.Role role = o.role == null ? com.godmod.data.Role.JOUEUR : o.role;
            boolean online = PowerSystem.online(player.level().getServer(), o.uuid) != null;
            page.set(INNER[i++], Page.icon(online ? Items.PLAYER_HEAD : Items.SKELETON_SKULL, 1, o.name, role.color, List.of(
                    Page.line("§7Rang : " + role.label + (online ? "  §a● en ligne" : "")),
                    Page.line(""), Page.line("§bClic gauche : Modérateur"), Page.line("§cClic droit : Admin"), Page.line("§7Maj + clic : Joueur")), false), (sp, c) -> {
                com.godmod.data.Role r = c == GodMenu.Click.SHIFT ? com.godmod.data.Role.JOUEUR
                        : c == GodMenu.Click.RIGHT ? com.godmod.data.Role.ADMIN : com.godmod.data.Role.MODERATEUR;
                Msg.ok(sp, com.godmod.system.Moderation.setRole(sp.level().getServer(), o, r, sp.getName().getString()));
                later(sp, () -> openTeam(sp));
            });
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    // ================================================================== Permissions de l'île

    public static void openIsland(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Permissions de votre île").frame(Items.ORANGE_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.GRASS_BLOCK, "Votre monde divin", ChatFormatting.GREEN,
                "§7" + DivineWorld.describe(p),
                "§7Ajouter un joueur : /ile ajouter <joueur>",
                "§7Réglage fin : /ile perm <joueur|fideles|visiteurs> <perm> <on|off>"));
        page.set(10, Page.icon(Items.TOTEM_OF_UNDYING, "Vos fidèles", ChatFormatting.YELLOW,
                "§7" + permSummary(p, "fideles"), "", "§eCliquez pour régler"), (sp, c) -> later(sp, () -> openIslandTarget(sp, "fideles")));
        page.set(11, Page.icon(Items.OAK_SIGN, "Visiteurs", ChatFormatting.WHITE,
                "§7Tous les autres joueurs", "§7" + permSummary(p, "visiteurs"), "", "§eCliquez pour régler"), (sp, c) -> later(sp, () -> openIslandTarget(sp, "visiteurs")));
        page.set(12, Page.icon(p.islandPvp ? Items.IRON_SWORD : Items.WOODEN_SWORD, "Combat entre joueurs : " + (p.islandPvp ? "ON" : "OFF"),
                p.islandPvp ? ChatFormatting.RED : ChatFormatting.GRAY, "§7Guerres, rébellions et duels restent", "§7toujours possibles", "", "§eCliquez pour basculer"),
                (sp, c) -> {
                    PlayerProfile me = GodData.get(sp);
                    me.islandPvp = !me.islandPvp;
                    GodData.dirty();
                    later(sp, () -> openIsland(sp));
                });
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int i = 0;
        for (var e : p.islandMembers.entrySet()) {
            if (i >= slots.length) break;
            PlayerProfile m = GodData.find(e.getKey());
            String name = m == null ? "?" : m.name;
            String uuid = e.getKey();
            page.set(slots[i++], Page.icon(Items.PLAYER_HEAD, 1, name, ChatFormatting.AQUA, List.of(
                    Page.line("§7" + permSummary(p, uuid)), Page.line(""),
                    Page.line("§eCliquez pour régler"), Page.line("§cMaj + clic : retirer")), false), (sp, c) -> {
                if (c == GodMenu.Click.SHIFT) {
                    GodData.get(sp).islandMembers.remove(uuid);
                    GodData.dirty();
                    later(sp, () -> openIsland(sp));
                } else later(sp, () -> openIslandTarget(sp, uuid));
            });
        }
        if (p.islandMembers.isEmpty()) {
            page.set(31, Page.icon(Items.BOOK, "Aucun joueur ajouté", ChatFormatting.GRAY, "§7/ile ajouter <joueur> pour donner", "§7l'accès complet à un ami"));
        }
        page.back((sp, c) -> later(sp, () -> openMain(sp))).close();
        page.open(player);
    }

    private static String permSummary(PlayerProfile owner, String target) {
        List<String> on = new ArrayList<>();
        for (com.godmod.data.IslandPerm perm : com.godmod.data.IslandPerm.values()) if (DivineWorld.hasPerm(owner, target, perm)) on.add(perm.label);
        return on.isEmpty() ? "Aucune permission" : String.join(", ", on);
    }

    public static void openIslandTarget(ServerPlayer player, String target) {
        PlayerProfile p = GodData.get(player);
        String title = target.equals("fideles") ? "Fidèles" : target.equals("visiteurs") ? "Visiteurs" : nameOf(target);
        Page page = new Page("Île — " + title).frame(Items.ORANGE_STAINED_GLASS_PANE);
        int[] slots = {20, 21, 22, 23, 24};
        com.godmod.data.IslandPerm[] perms = com.godmod.data.IslandPerm.values();
        for (int i = 0; i < perms.length; i++) {
            com.godmod.data.IslandPerm perm = perms[i];
            boolean on = DivineWorld.hasPerm(p, target, perm);
            page.set(slots[i], Page.icon(perm.icon, 1, perm.label + (on ? " : OUI" : " : NON"), on ? ChatFormatting.GREEN : ChatFormatting.RED,
                    List.of(Page.line("§7" + perm.description), Page.line(""), Page.line("§eCliquez pour basculer")), on), (sp, c) -> {
                PlayerProfile me = GodData.get(sp);
                DivineWorld.setPerm(me, target, perm, !DivineWorld.hasPerm(me, target, perm));
                later(sp, () -> openIslandTarget(sp, target));
            });
            page.set(slots[i] + 9, Page.icon(on ? Items.LIME_DYE : Items.GRAY_DYE, on ? "Autorisé" : "Interdit", on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        page.back((sp, c) -> later(sp, () -> openIsland(sp))).close();
        page.open(player);
    }

    // ================================================================== Warps

    public static void openWarps(ServerPlayer player, int pageIndex) {
        PlayerProfile p = GodData.get(player);
        List<com.godmod.data.Warp> warps = com.godmod.system.WarpSystem.all();
        Page page = new Page("Warps (" + warps.size() + ")").frame(Items.CYAN_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.ENDER_EYE, "Warps des joueurs", ChatFormatting.DARK_AQUA,
                "§7Créer ou déplacer : /setwarp <nom>", "§7Supprimer : /delwarp <nom>",
                "§7Vos warps : " + com.godmod.system.WarpSystem.owned(p.uuid).size() + " / "
                        + com.godmod.system.WarpSystem.limit(p, com.godmod.system.Moderation.roleOf(player))));
        int start = pageIndex * INNER.length;
        for (int i = 0; i < INNER.length && start + i < warps.size(); i++) {
            com.godmod.data.Warp w = warps.get(start + i);
            boolean mine = w.owner.equals(p.uuid);
            Item icon = w.dimension.endsWith("divine") ? Items.GRASS_BLOCK : w.dimension.endsWith("ressources") ? Items.IRON_PICKAXE : Items.COMPASS;
            page.set(INNER[i], Page.icon(icon, 1, w.name, mine ? ChatFormatting.GREEN : ChatFormatting.AQUA, List.of(
                    Page.line("§7Par " + w.ownerName),
                    Page.line("§7" + com.godmod.system.WarpSystem.describe(w)),
                    Page.line("§7Visites : " + w.visits),
                    Page.line(""),
                    Page.line("§eCliquez pour y aller"),
                    Page.line(mine ? "§cMaj + clic : supprimer" : "")), mine), (sp, c) -> {
                sp.closeContainer();
                if (c == GodMenu.Click.SHIFT && w.owner.equals(sp.getUUID().toString())) report(sp, com.godmod.system.WarpSystem.delete(sp, w.name));
                else report(sp, com.godmod.command.GodCommands.goWarp(sp, w.name));
            });
        }
        if (warps.isEmpty()) {
            page.set(22, Page.icon(Items.MAP, "Aucun warp", ChatFormatting.GRAY, "§7Soyez le premier : /setwarp <nom>"));
        }
        if (start + INNER.length < warps.size()) {
            page.set(53, Page.icon(Items.SPECTRAL_ARROW, "Page suivante →", ChatFormatting.YELLOW), (sp, c) -> later(sp, () -> openWarps(sp, pageIndex + 1)));
        }
        page.back((sp, c) -> later(sp, () -> {
            if (pageIndex > 0) openWarps(sp, pageIndex - 1);
            else openMain(sp);
        })).close();
        page.open(player);
    }

    private static String nameOf(String uuid) {
        PlayerProfile p = GodData.find(uuid);
        return p == null ? "?" : p.name;
    }
}
