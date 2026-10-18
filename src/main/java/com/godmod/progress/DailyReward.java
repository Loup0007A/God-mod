package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.menu.GodMenu;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Récompense quotidienne (/recompense) : un cycle de 7 jours qui grandit avec la série de connexions.
 * Manquer un jour remet la série à 1. Chaque semaine complète augmente les gains de 10 % (jusqu'à +100 %).
 */
public final class DailyReward {
    private DailyReward() {
    }

    /** Gains du jour {@code day} (1 à 7) du cycle. */
    public record Day(long money, long essence, CrateSystem.Crate key, int keys) {
    }

    private static final Day[] CYCLE = {
            new Day(250, 40, null, 0),
            new Day(400, 60, null, 0),
            new Day(600, 90, CrateSystem.Crate.COMMUNE, 1),
            new Day(800, 120, null, 0),
            new Day(1000, 160, CrateSystem.Crate.COMMUNE, 2),
            new Day(1500, 220, null, 0),
            new Day(2500, 350, CrateSystem.Crate.RARE, 1)};

    public static Day day(int index) {
        return CYCLE[Math.floorMod(index - 1, CYCLE.length)];
    }

    /** Bonus de fidélité : +10 % par semaine complète, plafonné à +100 %. */
    public static double multiplier(int streak) {
        return 1.0 + 0.1 * Math.min(10, Math.max(0, streak - 1) / 7);
    }

    public static boolean available(PlayerProfile p) {
        return !QuestSystem.today().equals(p.dailyLast);
    }

    /** Série en cours si le joueur récupère aujourd'hui (1 si la série est rompue). */
    public static int nextStreak(PlayerProfile p) {
        if (p.dailyLast == null || p.dailyLast.isEmpty()) return 1;
        try {
            LocalDate last = LocalDate.parse(p.dailyLast);
            LocalDate today = LocalDate.parse(QuestSystem.today());
            if (last.equals(today)) return p.dailyStreak;
            return last.plusDays(1).equals(today) ? p.dailyStreak + 1 : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    public static String claim(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (!available(p)) return "Récompense déjà récupérée aujourd'hui. Prochaine dans " + Msg.time(QuestSystem.untilTomorrow()) + ".";
        int streak = nextStreak(p);
        boolean broken = streak == 1 && p.dailyStreak > 1;
        int dayIndex = Math.floorMod(streak - 1, CYCLE.length) + 1;
        Day d = day(dayIndex);
        double mult = multiplier(streak);
        long money = Math.round(d.money() * mult);
        long essence = Math.round(d.essence() * mult);
        p.dailyLast = QuestSystem.today();
        p.dailyStreak = streak;
        p.dailyBest = Math.max(p.dailyBest, streak);
        p.money += money;
        StringBuilder extra = new StringBuilder();
        if (d.key() != null) {
            CrateSystem.addKey(p, d.key(), d.keys());
            extra.append(" · ").append(d.keys()).append(" clé(s) ").append(d.key().label);
        }
        // Toutes les 4 semaines de suite : une clé Épique en plus.
        if (streak % 28 == 0) {
            CrateSystem.addKey(p, CrateSystem.Crate.EPIQUE, 1);
            extra.append(" · 1 clé Épique (4 semaines de suite !)");
        }
        GodData.dirty();
        PowerSystem.addEssence(player, essence, "récompense quotidienne");
        Fx.title(player, Component.literal("Récompense du jour " + dayIndex + "/7").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("+" + Msg.money(money) + " · +" + essence + " Essence" + extra).withStyle(ChatFormatting.YELLOW));
        Fx.sound(player, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.4F);
        Fx.particles(player, ParticleTypes.HAPPY_VILLAGER, 25, 0.6);
        if (broken) Msg.info(player, "Votre série a été interrompue : elle repart de 1. Revenez chaque jour pour de meilleures récompenses !");
        Msg.ok(player, "Jour " + dayIndex + " (série de " + streak + " jour" + (streak > 1 ? "s" : "") + ") : +" + Msg.money(money)
                + ", +" + essence + " Essence" + extra + (mult > 1 ? " (bonus de fidélité ×" + String.format(java.util.Locale.FRANCE, "%.1f", mult) + ")" : "") + ".");
        return null;
    }

    /** Rappel à la connexion. */
    public static void remind(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (!available(p)) return;
        int streak = nextStreak(p);
        Msg.send(player, Msg.text("Votre récompense quotidienne vous attend (jour " + (Math.floorMod(streak - 1, 7) + 1) + "/7"
                        + (streak > 1 ? ", série de " + streak + " jours" : "") + ") ", ChatFormatting.GOLD)
                .append(Msg.button("Récupérer", "/recompense", "Récupérer la récompense du jour", ChatFormatting.GREEN)));
    }

    // ------------------------------------------------------------------ Interface

    public static void open(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        boolean avail = available(p);
        int streak = avail ? nextStreak(p) : p.dailyStreak;
        int current = Math.floorMod(streak - 1, 7) + 1;
        double mult = multiplier(streak);
        Page page = new Page("Récompense quotidienne — série " + streak).frame(Items.ORANGE_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.CLOCK, "Récompense quotidienne", ChatFormatting.GOLD,
                "§7Revenez chaque jour : la série augmente", "§7les gains. Un jour manqué = retour au jour 1.",
                "§7Série actuelle : §e" + p.dailyStreak + "§7 · record : §e" + p.dailyBest,
                mult > 1 ? "§aBonus de fidélité : ×" + String.format(java.util.Locale.FRANCE, "%.1f", mult) : "§7Bonus de fidélité : +10 % par semaine complète",
                avail ? "§aDisponible maintenant !" : "§7Prochaine dans " + Msg.time(QuestSystem.untilTomorrow())));
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 1; i <= 7; i++) {
            Day d = day(i);
            boolean done = i < current || (i == current && !avail);
            boolean today = i == current && avail;
            Item icon = done ? Items.LIME_STAINED_GLASS_PANE : today ? (i == 7 ? Items.NETHER_STAR : Items.CHEST) : (i == 7 ? Items.ENDER_CHEST : Items.BARREL);
            List<Component> lore = new ArrayList<>();
            lore.add(Page.line("§6+" + Msg.money(Math.round(d.money() * mult))));
            lore.add(Page.line("§d+" + Math.round(d.essence() * mult) + " Essence"));
            if (d.key() != null) lore.add(Page.line("§b+" + d.keys() + " clé(s) " + d.key().label));
            lore.add(Page.line(""));
            lore.add(Page.line(done ? "§aRécupérée" : today ? "§eCliquez pour récupérer !" : "§8Pas encore"));
            GodMenu.Action action = today ? (sp, c) -> {
                String err = claim(sp);
                if (err != null) Msg.err(sp, err);
                sp.level().getServer().execute(() -> open(sp));
            } : null;
            page.set(slots[i - 1], Page.icon(icon, i, "Jour " + i, done ? ChatFormatting.GREEN : today ? ChatFormatting.YELLOW : ChatFormatting.GRAY, lore, today), action);
        }
        page.set(31, Page.icon(Items.ENDER_EYE, "Toutes les 4 semaines", ChatFormatting.LIGHT_PURPLE,
                "§7Une série de 28 jours offre", "§7une clé Épique en plus."));
        page.back((sp, c) -> sp.level().getServer().execute(() -> com.godmod.menu.Menus.openMain(sp))).close();
        page.open(player);
    }
}
