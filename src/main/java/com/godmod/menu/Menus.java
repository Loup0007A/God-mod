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
                        if (c == GodMenu.Click.RIGHT) DivineWorld.leave(sp);
                        else report(sp, DivineWorld.sendHome(sp));
                    });
        } else {
            page.set(25, Page.icon(Items.DIRT, "Monde divin (verrouillé)", ChatFormatting.DARK_GRAY,
                    "§7Les mortels n'ont pas de monde divin.", "§eDevenez Dieu pour recevoir le vôtre.",
                    "§7Vous pouvez visiter celui de votre dieu : /monde visiter <dieu>"));
        }
        page.set(11, Page.icon(Items.BEACON, "Sanctuaire (spawn)", ChatFormatting.LIGHT_PURPLE,
                "§7Retour au temple céleste", "", "§eCliquez pour y aller  §8(/spawn)"), (sp, c) -> {
            sp.closeContainer();
            com.godmod.system.Hub.teleport(sp);
        });
        page.set(15, Page.icon(Items.DIAMOND_PICKAXE, "Monde ressource", ChatFormatting.AQUA,
                "§7Un monde sauvage pour miner et récolter", "", "§eCliquez pour y aller  §8(/mine)"), (sp, c) -> {
            sp.closeContainer();
            report(sp, com.godmod.system.Hub.toMine(sp));
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
        Page page = new Page("Pouvoirs divins — Foi " + (int) p.faith).frame(paneFor(p));
        page.set(4, Page.icon(Items.LAPIS_LAZULI, "Foi : " + (int) p.faith + " / " + (int) p.maxFaith(), ChatFormatting.AQUA,
                "§7Se régénère avec le temps, l'Esprit et vos fidèles"));

        addPower(page, p, 19, Items.GLISTERING_MELON_SLICE, PowerActions.Power.BENIR, (sp, c) -> {
            sp.closeContainer();
            PowerActions.bless(sp);
        });
        addPower(page, p, 21, Items.LIGHTNING_ROD, PowerActions.Power.CHATIER, (sp, c) -> later(sp, () -> openFollowers(sp, 0)));
        addPower(page, p, 23, Items.ENDER_PEARL, PowerActions.Power.CONVOQUER, (sp, c) -> {
            sp.closeContainer();
            PowerActions.summonAll(sp);
        });
        addPower(page, p, 25, Items.CLOCK, PowerActions.Power.TEMPS, (sp, c) -> later(sp, () -> openTime(sp)));
        addPower(page, p, 31, Items.ENDER_EYE, PowerActions.Power.ESPACE, (sp, c) -> later(sp, () -> openSpace(sp)));

        // Artefact du domaine
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

    private static void addPower(Page page, PlayerProfile p, int slot, Item icon, PowerActions.Power power, GodMenu.Action action) {
        boolean unlocked = p.tier.atLeast(power.required);
        page.set(slot, Page.icon(unlocked ? icon : Items.GRAY_DYE, power.label, unlocked ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY,
                "§7" + power.description,
                "§bCoût : " + (int) power.cost + " Foi",
                unlocked ? "§aDébloqué" : "§cRequiert : " + power.required.label,
                "", "§eCliquez pour utiliser"), unlocked ? action : (sp, c) -> Msg.err(sp, "Requiert le palier " + power.required.label + "."));
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
                else report(sp, com.godmod.system.WarpSystem.go(sp, w.name));
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
