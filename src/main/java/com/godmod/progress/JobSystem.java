package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.system.Hub;
import com.godmod.system.PowerSystem;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Métiers : chaque action rapporte de l'XP de métier, de l'Essence (progression divine) et fait avancer les quêtes.
 * Chaque niveau donne des pièces et de l'Essence ; tous les 5 niveaux une clé de caisse.
 */
public final class JobSystem {
    /** XP de métier convertie en Essence (progression vers les paliers divins). */
    public static final double ESSENCE_PER_XP = 0.5;
    private static final Random RANDOM = new Random();

    /** Blocs posés par des joueurs : ils ne rapportent pas d'XP quand on les casse (anti-triche). */
    private static final LinkedHashSet<String> PLACED = new LinkedHashSet<>();
    private static final int PLACED_MAX = 300_000;

    private static final Map<Block, double[]> ORES = new HashMap<>();   // xp, (unused)
    private static final Map<Block, ItemStack> ORE_DROP = new HashMap<>();
    private static final Set<Block> STONES = new HashSet<>();

    static {
        ore(Blocks.COAL_ORE, 4, new ItemStack(Items.COAL));
        ore(Blocks.DEEPSLATE_COAL_ORE, 5, new ItemStack(Items.COAL));
        ore(Blocks.COPPER_ORE, 4, new ItemStack(Items.RAW_COPPER, 2));
        ore(Blocks.DEEPSLATE_COPPER_ORE, 5, new ItemStack(Items.RAW_COPPER, 2));
        ore(Blocks.IRON_ORE, 8, new ItemStack(Items.RAW_IRON));
        ore(Blocks.DEEPSLATE_IRON_ORE, 9, new ItemStack(Items.RAW_IRON));
        ore(Blocks.GOLD_ORE, 12, new ItemStack(Items.RAW_GOLD));
        ore(Blocks.DEEPSLATE_GOLD_ORE, 13, new ItemStack(Items.RAW_GOLD));
        ore(Blocks.REDSTONE_ORE, 7, new ItemStack(Items.REDSTONE, 4));
        ore(Blocks.DEEPSLATE_REDSTONE_ORE, 8, new ItemStack(Items.REDSTONE, 4));
        ore(Blocks.LAPIS_ORE, 10, new ItemStack(Items.LAPIS_LAZULI, 4));
        ore(Blocks.DEEPSLATE_LAPIS_ORE, 11, new ItemStack(Items.LAPIS_LAZULI, 4));
        ore(Blocks.DIAMOND_ORE, 35, new ItemStack(Items.DIAMOND));
        ore(Blocks.DEEPSLATE_DIAMOND_ORE, 38, new ItemStack(Items.DIAMOND));
        ore(Blocks.EMERALD_ORE, 45, new ItemStack(Items.EMERALD));
        ore(Blocks.DEEPSLATE_EMERALD_ORE, 48, new ItemStack(Items.EMERALD));
        ore(Blocks.AMETHYST_CLUSTER, 6, new ItemStack(Items.AMETHYST_SHARD, 2));
        STONES.add(Blocks.STONE);
        STONES.add(Blocks.DEEPSLATE);
        STONES.add(Blocks.ANDESITE);
        STONES.add(Blocks.DIORITE);
        STONES.add(Blocks.GRANITE);
        STONES.add(Blocks.TUFF);
        STONES.add(Blocks.CALCITE);
        STONES.add(Blocks.OBSIDIAN);
    }

    private static void ore(Block b, double xp, ItemStack drop) {
        ORES.put(b, new double[]{xp});
        ORE_DROP.put(b, drop);
    }

    private JobSystem() {
    }

    public static int level(PlayerProfile p, Job job) {
        return Math.max(1, p.jobLevel.getOrDefault(job.name(), 1));
    }

    public static double xp(PlayerProfile p, Job job) {
        return p.jobXp.getOrDefault(job.name(), 0.0);
    }

    /** Meilleur métier du joueur (affiché dans le HUD). */
    public static Job best(PlayerProfile p) {
        Job best = Job.MINEUR;
        double bestScore = -1;
        for (Job j : Job.values()) {
            double score = level(p, j) * 100000 + xp(p, j);
            if (score > bestScore) {
                bestScore = score;
                best = j;
            }
        }
        return best;
    }

    public static int totalLevels(PlayerProfile p) {
        int n = 0;
        for (Job j : Job.values()) n += level(p, j);
        return n;
    }

    // ------------------------------------------------------------------ Gain d'XP

    public static void gain(ServerPlayer player, Job job, double amount) {
        if (amount <= 0 || Hub.isSpawn(player.level())) return;
        PlayerProfile p = GodData.get(player);
        double mult = GodData.settings().essenceMultiplier;
        if (GodData.settings().payEnabled && System.currentTimeMillis() < p.boostUntil) mult *= 2;
        amount *= mult;
        int lvl = level(p, job);
        if (lvl >= Job.MAX_LEVEL) {
            giveEssence(player, p, amount * ESSENCE_PER_XP);
            return;
        }
        double xp = xp(p, job) + amount;
        while (lvl < Job.MAX_LEVEL && xp >= Job.needed(lvl)) {
            xp -= Job.needed(lvl);
            lvl++;
            p.jobLevel.put(job.name(), lvl);
            levelUp(player, p, job, lvl);
        }
        p.jobXp.put(job.name(), lvl >= Job.MAX_LEVEL ? 0 : xp);
        giveEssence(player, p, amount * ESSENCE_PER_XP);
        GodData.dirty();
        String bar = lvl >= Job.MAX_LEVEL ? "MAX" : (int) xp + "/" + (int) Job.needed(lvl);
        player.displayClientMessage(Component.literal(job.icon + " " + job.label + " ").withStyle(job.color)
                .append(Component.literal("+" + fmt(amount) + " XP").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  Niv. " + lvl + " (" + bar + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  +" + fmt(amount * ESSENCE_PER_XP) + " Essence").withStyle(ChatFormatting.LIGHT_PURPLE)), true);
    }

    private static void giveEssence(ServerPlayer player, PlayerProfile p, double amount) {
        p.essenceFraction += amount;
        long whole = (long) Math.floor(p.essenceFraction);
        if (whole <= 0) return;
        p.essenceFraction -= whole;
        p.essence += whole;
        PowerSystem.checkPromotion(player, p);
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    private static void levelUp(ServerPlayer player, PlayerProfile p, Job job, int lvl) {
        long money = 25L * lvl;
        long essence = 15L * lvl;
        p.money += money;
        p.essence += essence;
        StringBuilder extra = new StringBuilder();
        if (lvl % 5 == 0) {
            CrateSystem.addKey(p, CrateSystem.Crate.COMMUNE, 1);
            extra.append(" + 1 clé Commune");
        }
        if (lvl % 10 == 0) {
            CrateSystem.addKey(p, CrateSystem.Crate.RARE, 1);
            extra.append(" + 1 clé Rare");
        }
        if (lvl == 25 || lvl == 40) {
            CrateSystem.addKey(p, CrateSystem.Crate.EPIQUE, 1);
            extra.append(" + 1 clé Épique");
        }
        if (lvl == Job.MAX_LEVEL) {
            CrateSystem.addKey(p, CrateSystem.Crate.DIVINE, 1);
            extra.append(" + 1 clé Divine");
        }
        GodData.dirty();
        Fx.title(player, Component.literal(job.icon + " " + job.label + " niveau " + lvl).withStyle(job.color, ChatFormatting.BOLD),
                Component.literal("+" + money + " pièces · +" + essence + " Essence" + extra).withStyle(ChatFormatting.YELLOW));
        Fx.sound(player, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
        Fx.particles(player, ParticleTypes.HAPPY_VILLAGER, 30, 0.6);
        Msg.ok(player, job.label + " niveau " + lvl + " ! +" + money + " pièces, +" + essence + " Essence" + extra + ".");
        if (lvl % 10 == 0) Msg.broadcast(player.level().getServer(), p.name + " atteint le niveau " + lvl + " de " + job.label + " !", job.color);
        PowerSystem.checkPromotion(player, p);
    }

    // ------------------------------------------------------------------ Actions

    public static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    public static void onPlaced(Level level, BlockPos pos) {
        PLACED.add(key(level, pos));
        if (PLACED.size() > PLACED_MAX) {
            var it = PLACED.iterator();
            for (int i = 0; i < 10_000 && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
    }

    /** Un joueur vient de casser un bloc (événement non annulé). */
    public static void onBroken(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (Hub.isSpawn(level) || player.isCreative()) return;
        boolean placed = PLACED.remove(key(level, pos));
        if (placed) return;
        Block b = state.getBlock();
        PlayerProfile p = GodData.get(player);
        String id = BuiltInRegistries.BLOCK.getKey(b).toString();

        double[] ore = ORES.get(b);
        if (ore != null) {
            gain(player, Job.MINEUR, ore[0]);
            QuestSystem.progress(player, List.of("mine:" + id, "mine:ore", "mine:any"), 1);
            int lvl = level(p, Job.MINEUR);
            if (RANDOM.nextDouble() < Math.min(0.40, lvl * 0.008)) {
                Block.popResource(level, pos, ORE_DROP.get(b).copy());
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
            }
            return;
        }
        if (STONES.contains(b)) {
            gain(player, Job.MINEUR, 0.25);
            QuestSystem.progress(player, List.of("mine:stone", "mine:any"), 1);
            return;
        }
        if (state.is(BlockTags.LOGS)) {
            gain(player, Job.BUCHERON, 2.5);
            QuestSystem.progress(player, List.of("chop:" + id, "chop:any"), 1);
            int lvl = level(p, Job.BUCHERON);
            if (RANDOM.nextDouble() < Math.min(0.40, lvl * 0.01)) Block.popResource(level, pos, new ItemStack(b.asItem()));
            if (lvl >= 20 && !player.isShiftKeyDown()) fellTree(player, level, pos, b);
            return;
        }
        if (b instanceof CropBlock crop && crop.isMaxAge(state)) {
            gain(player, Job.FERMIER, 3);
            QuestSystem.progress(player, List.of("harvest:" + id, "harvest:any"), 1);
            int lvl = level(p, Job.FERMIER);
            if (RANDOM.nextDouble() < Math.min(0.40, lvl * 0.01)) Block.popResource(level, pos, new ItemStack(crop.asItem() == Items.AIR ? Items.WHEAT : cropDrop(b)));
            if (lvl >= 5) {
                BlockState young = b.defaultBlockState();
                level.getServer().execute(() -> {
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) level.setBlockAndUpdate(pos, young);
                });
            }
            return;
        }
        if (b == Blocks.MELON || b == Blocks.PUMPKIN || b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.COCOA) {
            gain(player, Job.FERMIER, 1.5);
            QuestSystem.progress(player, List.of("harvest:" + id, "harvest:any"), 1);
        }
    }

    private static net.minecraft.world.item.Item cropDrop(Block b) {
        if (b == Blocks.WHEAT) return Items.WHEAT;
        if (b == Blocks.CARROTS) return Items.CARROT;
        if (b == Blocks.POTATOES) return Items.POTATO;
        if (b == Blocks.BEETROOTS) return Items.BEETROOT;
        return Items.WHEAT;
    }

    /** Bûcheron niv. 20+ : abat tout l'arbre (jusqu'à 64 bûches reliées). */
    private static void fellTree(ServerPlayer player, ServerLevel level, BlockPos start, Block log) {
        level.getServer().execute(() -> {
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(start);
            int felled = 0;
            while (!queue.isEmpty() && felled < 64) {
                BlockPos cur = queue.poll();
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = cur.offset(dx, dy, dz);
                    if (!seen.add(n)) continue;
                    if (level.getBlockState(n).getBlock() != log || PLACED.contains(key(level, n))) continue;
                    level.destroyBlock(n, true, player);
                    felled++;
                    queue.add(n);
                    if (felled >= 64) break;
                }
            }
            if (felled > 0) {
                gain(player, Job.BUCHERON, felled * 1.2);
                QuestSystem.progress(player, List.of("chop:any"), felled);
            }
        });
    }

    public static void onKill(ServerPlayer player, LivingEntity victim) {
        if (Hub.isSpawn(player.level())) return;
        PlayerProfile p = GodData.get(player);
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        boolean hostile = victim instanceof Enemy;
        double xp = hostile ? 7 : 2.5;
        if (id.endsWith("wither") || id.endsWith("ender_dragon") || id.endsWith("warden")) xp = 300;
        gain(player, Job.CHASSEUR, xp);
        int lvl = level(p, Job.CHASSEUR);
        PowerSystem.earnMoney(player.level().getServer(), p, (hostile ? 2 : 1) + lvl / 5);
        List<String> keys = new ArrayList<>(List.of("kill:" + id, "kill:any"));
        if (hostile) keys.add("kill:hostile");
        QuestSystem.progress(player, keys, 1);
    }

    public static void onFish(ServerPlayer player, List<ItemStack> drops) {
        PlayerProfile p = GodData.get(player);
        gain(player, Job.PECHEUR, 10);
        QuestSystem.progress(player, List.of("fish:any"), 1);
        int lvl = level(p, Job.PECHEUR);
        if (RANDOM.nextDouble() < Math.min(0.35, lvl * 0.01) && !drops.isEmpty()) {
            ItemStack bonus = drops.get(0).copy();
            if (!player.getInventory().add(bonus)) player.drop(bonus, false);
        }
        if (RANDOM.nextDouble() < lvl * 0.002) {
            CrateSystem.addKey(p, CrateSystem.Crate.COMMUNE, 1);
            Msg.ok(player, "Une clé Commune était accrochée à votre hameçon !");
        }
    }

    /** Avantages passifs, appelés toutes les 5 secondes. */
    public static void perks(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        int mine = level(p, Job.MINEUR);
        if (mine >= 15 && player.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) {
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 140, mine >= 35 ? 1 : 0, true, false));
        }
    }
}
