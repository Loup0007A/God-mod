package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Specialization;
import com.godmod.data.Tier;
import com.godmod.menu.Menus;
import com.godmod.menu.Page;
import com.godmod.system.PowerSystem;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Quêtes d'ascension : un fil rouge de 18 étapes qui guide le joueur de mortel à dieu suprême.
 * Une seule étape à la fois ; elle se valide toute seule dès que l'objectif est atteint.
 */
public final class AscensionQuests {
    private AscensionQuests() {
    }

    public record Step(String title, String goal, Item icon, ToLongFunction<PlayerProfile> current, long target,
                       long money, long essence, CrateSystem.Crate key) {
    }

    private static int maxJob(PlayerProfile p) {
        int m = 0;
        for (Job j : Job.values()) m = Math.max(m, JobSystem.level(p, j));
        return m;
    }

    private static int jobsAtLeast(PlayerProfile p, int lvl) {
        int n = 0;
        for (Job j : Job.values()) if (JobSystem.level(p, j) >= lvl) n++;
        return n;
    }

    private static long tier(PlayerProfile p, Tier t) {
        return p.tier.atLeast(t) ? 1 : 0;
    }

    public static final List<Step> STEPS = List.of(
            new Step("Premiers pas", "Récupérer la récompense quotidienne (/recompense)", Items.CLOCK,
                    p -> p.dailyBest, 1, 300, 50, null),
            new Step("Apprenti", "Atteindre le niveau 5 dans un métier (/metiers)", Items.WOODEN_PICKAXE,
                    AscensionQuests::maxJob, 5, 500, 80, CrateSystem.Crate.COMMUNE),
            new Step("Au service des dieux", "Accomplir 3 quêtes du jour (/quetes)", Items.WRITABLE_BOOK,
                    p -> p.questsDone, 3, 800, 120, null),
            new Step("Coup de chance", "Ouvrir une caisse au Sanctuaire", Items.TRIPWIRE_HOOK,
                    p -> p.cratesOpened, 1, 500, 80, CrateSystem.Crate.COMMUNE),
            new Step("L'Éveil", "Devenir Éveillé (500 Essence)", Items.GLOWSTONE_DUST,
                    p -> tier(p, Tier.EVEILLE), 1, 1000, 0, CrateSystem.Crate.RARE),
            new Step("Polyvalent", "Atteindre le niveau 10 dans 2 métiers", Items.IRON_PICKAXE,
                    p -> jobsAtLeast(p, 10), 2, 1500, 200, null),
            new Step("Force et esprit", "Dépenser 5 points de caractéristique (/dieu stats)", Items.BLAZE_POWDER,
                    PowerSystem::spentStatPoints, 5, 1200, 150, null),
            new Step("Pèlerin", "Accomplir 15 quêtes du jour", Items.BOOK,
                    p -> p.questsDone, 15, 2000, 300, CrateSystem.Crate.RARE),
            new Step("Demi-dieu", "Atteindre le palier Demi-dieu (2 500 Essence)", Items.GOLDEN_HELMET,
                    p -> tier(p, Tier.DEMI_DIEU), 1, 3000, 0, CrateSystem.Crate.RARE),
            new Step("Premier fidèle", "Convertir un joueur en fidèle (le vaincre en PvP)", Items.TOTEM_OF_UNDYING,
                    p -> GodData.followersOf(p.uuid).size(), 1, 3000, 400, null),
            new Step("Artisan accompli", "Cumuler 75 niveaux de métier", Items.SMITHING_TABLE,
                    JobSystem::totalLevels, 75, 4000, 500, CrateSystem.Crate.EPIQUE),
            new Step("Dieu", "Atteindre le palier Dieu (8 000 Essence et 1 fidèle)", Items.NETHER_STAR,
                    p -> tier(p, Tier.DIEU), 1, 6000, 0, CrateSystem.Crate.EPIQUE),
            new Step("Spécialiste", "Choisir un domaine divin : océan, ciel ou cosmos", Items.TRIDENT,
                    p -> p.specialization == Specialization.AUCUNE ? 0 : 1, 1, 3000, 400, null),
            new Step("Maître d'un art", "Atteindre le niveau 30 dans un métier", Items.DIAMOND_PICKAXE,
                    AscensionQuests::maxJob, 30, 6000, 800, CrateSystem.Crate.EPIQUE),
            new Step("Défis hebdomadaires", "Relever 3 défis de la semaine (au total)", Items.HEART_OF_THE_SEA,
                    p -> p.weeklyDone, 3, 5000, 700, CrateSystem.Crate.EPIQUE),
            new Step("Panthéon", "Réunir 8 fidèles au total", Items.BEACON,
                    p -> GodData.totalFollowers(p.uuid), 8, 10000, 1200, CrateSystem.Crate.DIVINE),
            new Step("Dieu suprême", "Atteindre le palier Dieu suprême", Items.DRAGON_HEAD,
                    p -> tier(p, Tier.DIEU_SUPREME), 1, 15000, 0, CrateSystem.Crate.DIVINE),
            new Step("Légende", "Atteindre le niveau 50 dans un métier", Items.NETHERITE_PICKAXE,
                    AscensionQuests::maxJob, 50, 20000, 3000, CrateSystem.Crate.DIVINE));

    public static Step current(PlayerProfile p) {
        return p.storyStep < STEPS.size() ? STEPS.get(p.storyStep) : null;
    }

    public static long progress(PlayerProfile p, Step s) {
        return Math.min(s.target(), Math.max(0, s.current().applyAsLong(p)));
    }

    /** Valide les étapes atteintes (appelé régulièrement et à l'ouverture des menus). */
    public static void check(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        for (int guard = 0; guard < 3; guard++) {
            Step s = current(p);
            if (s == null || progress(p, s) < s.target()) return;
            p.storyStep++;
            p.money += s.money();
            String extra = "";
            if (s.key() != null) {
                CrateSystem.addKey(p, s.key(), 1);
                extra = " · clé " + s.key().label;
            }
            GodData.dirty();
            if (s.essence() > 0) PowerSystem.addEssence(player, s.essence(), "ascension");
            Fx.title(player, Component.literal("✦ Ascension : " + s.title() + " ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                    Component.literal("+" + Msg.money(s.money()) + (s.essence() > 0 ? " · +" + s.essence() + " Essence" : "") + extra).withStyle(ChatFormatting.YELLOW));
            Fx.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1.2F);
            Fx.particles(player, ParticleTypes.END_ROD, 40, 0.8);
            Step next = current(p);
            Msg.send(player, Msg.text("Étape d'ascension « " + s.title() + " » validée : +" + Msg.money(s.money())
                    + (s.essence() > 0 ? ", +" + s.essence() + " Essence" : "") + extra + ". ", ChatFormatting.LIGHT_PURPLE)
                    .append(Msg.text(next == null ? "Vous avez achevé l'ascension !" : "Prochaine : " + next.goal(), ChatFormatting.GRAY)));
            if (next == null) Msg.broadcast(player.level().getServer(), p.name + " a achevé toutes les quêtes d'ascension !", ChatFormatting.LIGHT_PURPLE);
        }
    }

    /** Texte court pour le tableau latéral. */
    public static String hud(PlayerProfile p) {
        Step s = current(p);
        if (s == null) return null;
        return s.title() + (s.target() > 1 ? " " + progress(p, s) + "/" + s.target() : "");
    }

    public static void open(ServerPlayer player) {
        check(player);
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Ascension — étape " + Math.min(p.storyStep + 1, STEPS.size()) + "/" + STEPS.size()).frame(Items.PURPLE_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.ENCHANTED_BOOK, "Quêtes d'ascension", ChatFormatting.LIGHT_PURPLE,
                "§7Le chemin du mortel jusqu'au trône suprême.", "§7Chaque étape se valide toute seule",
                "§7dès que l'objectif est atteint.", "§dÉtapes accomplies : " + Math.min(p.storyStep, STEPS.size()) + "/" + STEPS.size()));
        for (int i = 0; i < STEPS.size() && i < Menus.INNER.length; i++) {
            Step s = STEPS.get(i);
            boolean done = i < p.storyStep, cur = i == p.storyStep;
            long have = progress(p, s);
            Item icon = done ? Items.LIME_DYE : cur ? s.icon() : Items.GRAY_DYE;
            page.set(Menus.INNER[i], Page.icon(icon, i + 1, (i + 1) + ". " + s.title(), done ? ChatFormatting.GREEN : cur ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY, List.of(
                    Page.line("§7" + s.goal()),
                    Page.line(done ? "§aAccomplie" : cur ? "§eEn cours : " + have + " / " + s.target() : "§8Verrouillée"),
                    Page.line("§6+" + Msg.money(s.money()) + (s.essence() > 0 ? " §7· §d+" + s.essence() + " Essence" : "")
                            + (s.key() != null ? " §7· §bclé " + s.key().label : ""))), cur));
        }
        page.back((sp, c) -> sp.level().getServer().execute(() -> Menus.openQuests(sp))).close();
        page.open(player);
    }
}
