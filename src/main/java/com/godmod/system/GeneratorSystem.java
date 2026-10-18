package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.menu.GodMenu;
import com.godmod.menu.Menus;
import com.godmod.menu.Page;
import com.godmod.progress.JobSystem;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Prestige du générateur : dans un monde divin, un générateur lave + eau ne produit plus seulement de la
 * pierre, mais des blocs tirés selon le niveau de prestige du propriétaire de l'île (I à XX).
 * Chaque niveau coûte de l'argent et demande un certain niveau de Mineur.
 */
public final class GeneratorSystem {
    public static final int MAX_LEVEL = 20;
    private static final Random RANDOM = new Random();

    /** Un bloc possible : débloqué au niveau {@code unlock}, poids = base + gain × (niveau − unlock). */
    private record Drop(Block block, String label, int unlock, double base, double growth) {
        double weight(int level) {
            return level < unlock ? 0 : base + growth * (level - unlock);
        }
    }

    private static final List<Drop> DROPS = List.of(
            new Drop(Blocks.STONE, "Pierre", 2, 10, 0),
            new Drop(Blocks.COAL_ORE, "Charbon", 2, 4, 0.6),
            new Drop(Blocks.COPPER_ORE, "Cuivre", 3, 3, 0.5),
            new Drop(Blocks.IRON_ORE, "Fer", 4, 2, 0.5),
            new Drop(Blocks.ANDESITE, "Andésite", 5, 3, 0),
            new Drop(Blocks.DIORITE, "Diorite", 5, 3, 0),
            new Drop(Blocks.GRANITE, "Granite", 5, 3, 0),
            new Drop(Blocks.LAPIS_ORE, "Lapis-lazuli", 6, 1, 0.3),
            new Drop(Blocks.REDSTONE_ORE, "Redstone", 7, 1.5, 0.3),
            new Drop(Blocks.GOLD_ORE, "Or", 8, 1, 0.35),
            new Drop(Blocks.DEEPSLATE, "Ardoise des abîmes", 9, 6, 0),
            new Drop(Blocks.DIAMOND_ORE, "Diamant", 10, 0.4, 0.15),
            new Drop(Blocks.EMERALD_ORE, "Émeraude", 12, 0.3, 0.1),
            new Drop(Blocks.AMETHYST_BLOCK, "Améthyste", 14, 1, 0),
            new Drop(Blocks.OBSIDIAN, "Obsidienne", 15, 1, 0.1),
            new Drop(Blocks.DEEPSLATE_DIAMOND_ORE, "Diamant des abîmes", 16, 0.3, 0.1),
            new Drop(Blocks.ANCIENT_DEBRIS, "Débris antiques", 17, 0.15, 0.08),
            new Drop(Blocks.NETHER_QUARTZ_ORE, "Quartz", 18, 1, 0.3));

    private static double cobbleWeight(int level) {
        return Math.max(20, 100 - 4.0 * (level - 1));
    }

    private GeneratorSystem() {
    }

    public static int level(PlayerProfile p) {
        return Math.max(1, Math.min(MAX_LEVEL, p.generatorLevel));
    }

    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    /** Prix pour passer du niveau {@code level} au suivant. */
    public static long price(int level) {
        return Math.round(2000 * Math.pow(1.35, level - 1) / 100.0) * 100;
    }

    /** Niveau de Mineur demandé pour atteindre le niveau {@code target}. */
    public static int minerRequired(int target) {
        return Math.max(0, (target - 1) * 2);
    }

    /** Probabilités (en %) de chaque bloc au niveau donné, dans l'ordre d'affichage. */
    public static Map<String, Double> table(int level) {
        Map<String, Double> m = new LinkedHashMap<>();
        double total = cobbleWeight(level);
        for (Drop d : DROPS) total += d.weight(level);
        m.put("Pierre taillée", 100 * cobbleWeight(level) / total);
        for (Drop d : DROPS) if (d.weight(level) > 0) m.put(d.label(), 100 * d.weight(level) / total);
        return m;
    }

    private static BlockState roll(int level) {
        double total = cobbleWeight(level);
        for (Drop d : DROPS) total += d.weight(level);
        double r = RANDOM.nextDouble() * total - cobbleWeight(level);
        if (r < 0) return Blocks.COBBLESTONE.defaultBlockState();
        for (Drop d : DROPS) {
            r -= d.weight(level);
            if (r < 0) return d.block().defaultBlockState();
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    /**
     * Un liquide va poser un bloc (générateur lave + eau). Renvoie le bloc de remplacement, ou null pour ne rien changer.
     */
    public static BlockState onFluidPlace(Level level, BlockPos pos, BlockState placed) {
        if (!DivineWorld.isDivine(level)) return null;
        if (!placed.is(Blocks.COBBLESTONE) && !placed.is(Blocks.STONE)) return null;
        PlayerProfile owner = DivineWorld.ownerAt(pos);
        if (owner == null) return null;
        int lvl = level(owner);
        return lvl <= 1 ? null : roll(lvl);
    }

    public static String upgrade(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (!DivineWorld.hasWorld(p)) return "Il faut un monde divin (palier Dieu) pour avoir un générateur.";
        int lvl = level(p);
        if (lvl >= MAX_LEVEL) return "Votre générateur est déjà au prestige maximum (XX).";
        int need = minerRequired(lvl + 1);
        if (JobSystem.level(p, Job.MINEUR) < need) return "Le prestige " + roman(lvl + 1) + " demande Mineur niveau " + need + " (vous : " + JobSystem.level(p, Job.MINEUR) + ").";
        long cost = price(lvl);
        if (p.money < cost) return "Le prestige " + roman(lvl + 1) + " coûte " + Msg.money(cost) + " (vous avez " + Msg.money(p.money) + ").";
        p.money -= cost;
        p.generatorLevel = lvl + 1;
        GodData.dirty();
        GodData.log("GÉNÉRATEUR : " + p.name + " passe au prestige " + (lvl + 1) + " (" + cost + " pièces)");
        Fx.title(player, Component.literal("Générateur prestige " + roman(lvl + 1)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("De nouveaux minerais jaillissent de votre générateur").withStyle(ChatFormatting.YELLOW));
        Fx.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1.1F);
        Msg.ok(player, "Générateur amélioré au prestige " + roman(lvl + 1) + " (−" + Msg.money(cost) + ").");
        if ((lvl + 1) % 5 == 0) Msg.broadcast(player.level().getServer(), p.name + " a porté son générateur au prestige " + roman(lvl + 1) + " !", ChatFormatting.GOLD);
        return null;
    }

    // ------------------------------------------------------------------ Interface

    public static void open(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        int lvl = level(p);
        Page page = new Page("Générateur — prestige " + roman(lvl)).frame(Items.ORANGE_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.LAVA_BUCKET, "Générateur de l'île", ChatFormatting.GOLD,
                "§7Dans votre monde divin, la lave qui touche l'eau", "§7ne fait plus que de la pierre : elle produit des",
                "§7minerais, selon le prestige de votre générateur.", "§6Prestige actuel : " + roman(lvl) + " / " + roman(MAX_LEVEL),
                DivineWorld.hasWorld(p) ? "§7Le niveau du propriétaire s'applique à toute son île." : "§cIl faut un monde divin (palier Dieu)."));
        // Composition actuelle
        List<Component> now = new ArrayList<>();
        for (var e : table(lvl).entrySet()) now.add(Page.line("§7" + e.getKey() + " : §f" + fmt(e.getValue()) + " %"));
        page.set(20, Page.icon(Items.COBBLESTONE, 1, "Production actuelle (" + roman(lvl) + ")", ChatFormatting.WHITE, now, false));
        if (lvl < MAX_LEVEL) {
            List<Component> next = new ArrayList<>();
            Map<String, Double> cur = table(lvl);
            for (var e : table(lvl + 1).entrySet()) {
                boolean fresh = !cur.containsKey(e.getKey());
                next.add(Page.line((fresh ? "§a+ " : "§7") + e.getKey() + " : §f" + fmt(e.getValue()) + " %"));
            }
            page.set(24, Page.icon(Items.DIAMOND_ORE, 1, "Prestige " + roman(lvl + 1), ChatFormatting.AQUA, next, true));
            int need = minerRequired(lvl + 1);
            boolean jobOk = JobSystem.level(p, Job.MINEUR) >= need;
            long cost = price(lvl);
            boolean moneyOk = p.money >= cost;
            page.set(31, Page.icon(Items.NETHER_STAR, 1, "Améliorer au prestige " + roman(lvl + 1), ChatFormatting.GOLD, List.of(
                    Page.line((moneyOk ? "§a✔ " : "§c✖ ") + "Prix : " + Msg.money(cost)),
                    Page.line((jobOk ? "§a✔ " : "§c✖ ") + "Mineur niveau " + need),
                    Page.line(""),
                    Page.line(moneyOk && jobOk && DivineWorld.hasWorld(p) ? "§eMaj + clic pour améliorer" : "§8Conditions non remplies")), moneyOk && jobOk), (sp, c) -> {
                if (c != GodMenu.Click.SHIFT) {
                    Msg.info(sp, "Maj + clic pour confirmer l'amélioration du générateur.");
                    return;
                }
                String err = upgrade(sp);
                if (err != null) Msg.err(sp, err);
                sp.level().getServer().execute(() -> open(sp));
            });
        } else {
            page.set(24, Page.icon(Items.NETHERITE_BLOCK, "Prestige maximum", ChatFormatting.LIGHT_PURPLE, "§7Votre générateur est au sommet."));
        }
        page.set(38, Page.icon(Items.WATER_BUCKET, "Construire un générateur", ChatFormatting.AQUA,
                "§7Creusez une rigole : de l'eau d'un côté,", "§7de la lave de l'autre, et un trou entre les deux.",
                "§7La lave qui coule vers l'eau forme un bloc,", "§7qui se reforme dès qu'on le mine."));
        page.set(42, Page.icon(Items.BOOK, "Tous les paliers", ChatFormatting.YELLOW, paliers()));
        page.back((sp, c) -> sp.level().getServer().execute(() -> Menus.openMain(sp))).close();
        page.open(player);
    }

    private static String[] paliers() {
        List<String> l = new ArrayList<>();
        for (int m = 2; m <= MAX_LEVEL; m++) {
            boolean any = false;
            for (Drop d : DROPS) any |= d.unlock() == m;
            if (!any && m != MAX_LEVEL) continue;
            StringBuilder unlocked = new StringBuilder();
            for (Drop d : DROPS) if (d.unlock() == m) unlocked.append(unlocked.length() > 0 ? ", " : "").append(d.label());
            if (unlocked.length() == 0) unlocked.append("plus de minerais");
            l.add("§6" + roman(m) + " §7: " + unlocked + " §8(" + Msg.money(price(m - 1)) + ")");
        }
        return l.toArray(new String[0]);
    }

    private static String fmt(double d) {
        return d >= 10 ? String.valueOf(Math.round(d)) : String.format(java.util.Locale.FRANCE, "%.1f", d);
    }
}
