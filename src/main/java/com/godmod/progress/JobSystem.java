package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.IslandPerm;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.system.DivineWorld;
import com.godmod.system.Hub;
import com.godmod.system.PowerSystem;
import com.godmod.system.Shop;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Métiers : chaque action rapporte de l'XP de métier, de l'Essence (progression divine) et fait avancer les quêtes.
 * Chaque niveau donne des pièces et de l'Essence, des clés tous les 5 niveaux, et débloque des talents (voir {@link #PERKS}).
 * Chaque jour, un « métier en vogue » gagne 50 % d'XP en plus.
 */
public final class JobSystem {
    /** XP de métier convertie en Essence (progression vers les paliers divins). */
    public static final double ESSENCE_PER_XP = 0.5;
    private static final Random RANDOM = new Random();

    /** Blocs posés par des joueurs : ils ne rapportent pas d'XP quand on les casse (anti-triche). */
    private static final LinkedHashSet<String> PLACED = new LinkedHashSet<>();
    private static final int PLACED_MAX = 300_000;

    private static final Map<Block, Double> ORES = new HashMap<>();
    private static final Map<Block, ItemStack> ORE_DROP = new HashMap<>();
    private static final Set<Block> STONES = new HashSet<>();

    // ------------------------------------------------------------------ Talents

    /** Un talent débloqué à un niveau de métier. */
    public record Perk(int level, String name, String description) {
    }

    public static final Map<Job, List<Perk>> PERKS = new EnumMap<>(Job.class);

    static {
        PERKS.put(Job.MINEUR, List.of(
                new Perk(1, "Main sûre", "Chance de minerai doublé : 0,8 % par niveau (40 % max)"),
                new Perk(10, "Œil de lynx", "Vision nocturne sous la couche 40, pioche en main"),
                new Perk(15, "Célérité", "Célérité I, pioche en main"),
                new Perk(25, "Filon", "Un minerai cassé emporte les minerais identiques reliés (8 max, accroupi = non)"),
                new Perk(35, "Célérité II", "Célérité II, pioche en main"),
                new Perk(50, "Fortune divine", "+15 % de minerai doublé · Essence ×1,5 au niveau maximum")));
        PERKS.put(Job.BUCHERON, List.of(
                new Perk(1, "Coupe nette", "Chance de bûche doublée : 1 % par niveau (40 % max)"),
                new Perk(10, "Pas du sylvain", "Vitesse I, hache en main"),
                new Perk(20, "Abattage", "Tout l'arbre tombe d'un coup (64 bûches, accroupi = non)"),
                new Perk(30, "Cueilleur", "Pommes et pousses en abattant un arbre"),
                new Perk(40, "Colosse", "Abattage jusqu'à 160 bûches · Force I, hache en main"),
                new Perk(50, "Sève divine", "Essence ×1,5 au niveau maximum")));
        PERKS.put(Job.FERMIER, List.of(
                new Perk(1, "Main verte", "Chance de récolte doublée : 1 % par niveau (40 % max)"),
                new Perk(5, "Replantation", "Les cultures récoltées se replantent toutes seules"),
                new Perk(10, "Estomac solide", "La faim remonte doucement, houe en main"),
                new Perk(20, "Engrais divin", "Les cultures autour de vous poussent plus vite, houe en main"),
                new Perk(30, "Moisson large", "Récolte en 3×3 avec une houe"),
                new Perk(40, "Récolte dorée", "2 % de chance d'une carotte dorée à chaque récolte"),
                new Perk(50, "Corne d'abondance", "Essence ×1,5 au niveau maximum")));
        PERKS.put(Job.CHASSEUR, List.of(
                new Perk(1, "Prime", "Pièces à chaque victime (plus avec le niveau)"),
                new Perk(10, "Soif de sang", "Régénération brève après chaque monstre tué"),
                new Perk(20, "Furie", "Force I pendant 8 s après chaque monstre tué"),
                new Perk(30, "Trophée", "0,5 % de chance d'une clé Commune par monstre"),
                new Perk(40, "Collectionneur", "0,15 % de chance d'obtenir le générateur du monstre tué"),
                new Perk(50, "Prédateur divin", "Prime doublée · Essence ×1,5 au niveau maximum")));
        PERKS.put(Job.PECHEUR, List.of(
                new Perk(1, "Bonne pioche", "Chance de prise doublée : 1 % par niveau (35 % max)"),
                new Perk(10, "Hameçon doré", "Chance de clé Commune à chaque prise"),
                new Perk(20, "Chance de la mer", "Effet Chance, canne en main"),
                new Perk(30, "Trésors engloutis", "4 % de chance d'un trésor en plus (diamant, émeraude…)"),
                new Perk(40, "Enfant de l'océan", "Respiration aquatique et grâce du dauphin dans l'eau"),
                new Perk(50, "Roi des abysses", "1 % de clé Rare par prise · Essence ×1,5 au niveau maximum")));

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
        for (Block b : new Block[]{Blocks.STONE, Blocks.DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
                Blocks.TUFF, Blocks.CALCITE, Blocks.OBSIDIAN, Blocks.SANDSTONE, Blocks.TERRACOTTA}) STONES.add(b);
    }

    private static void ore(Block b, double xp, ItemStack drop) {
        ORES.put(b, xp);
        ORE_DROP.put(b, drop);
    }

    private JobSystem() {
    }

    // ------------------------------------------------------------------ Lecture

    public static int level(PlayerProfile p, Job job) {
        return Math.max(1, p.jobLevel.getOrDefault(job.name(), 1));
    }

    public static double xp(PlayerProfile p, Job job) {
        return p.jobXp.getOrDefault(job.name(), 0.0);
    }

    public static boolean has(PlayerProfile p, Job job, int perkLevel) {
        return level(p, job) >= perkLevel;
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

    /** Métier en vogue du jour : +50 % d'XP pour tout le monde. */
    public static Job featured() {
        Job[] jobs = Job.values();
        return jobs[Math.floorMod(QuestSystem.today().hashCode() * 31 + 7, jobs.length)];
    }

    /** Prochain talent à débloquer, ou null. */
    public static Perk nextPerk(PlayerProfile p, Job job) {
        int lvl = level(p, job);
        for (Perk k : PERKS.get(job)) if (k.level() > lvl) return k;
        return null;
    }

    // ------------------------------------------------------------------ Gain d'XP

    public static void gain(ServerPlayer player, Job job, double amount) {
        if (amount <= 0 || Hub.isSpawn(player.level())) return;
        PlayerProfile p = GodData.get(player);
        double mult = GodData.settings().essenceMultiplier;
        if (GodData.settings().payEnabled && System.currentTimeMillis() < p.boostUntil) mult *= 2;
        if (job == featured()) mult *= 1.5;
        amount *= mult * EventSystem.jobXpMultiplier();
        EventSystem.onJobXp(player, job, amount);
        int lvl = level(p, job);
        if (lvl >= Job.MAX_LEVEL) {
            double ess = amount * ESSENCE_PER_XP * 1.5; // talent du niveau 50
            giveEssence(player, p, ess);
            GodData.dirty();
            player.displayClientMessage(Component.literal(job.icon + " " + job.label + " MAX ").withStyle(job.color)
                    .append(Component.literal("+" + fmt(ess * EventSystem.essenceMultiplier()) + " Essence").withStyle(ChatFormatting.LIGHT_PURPLE)), true);
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
        int filled = lvl >= Job.MAX_LEVEL ? 10 : (int) Math.floor(10 * xp / Job.needed(lvl));
        player.displayClientMessage(Component.literal(job.icon + " " + job.label + " " + lvl + " ").withStyle(job.color)
                .append(Component.literal("■".repeat(filled)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("■".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("  +" + fmt(amount) + " XP").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(job == featured() ? " (en vogue ×1,5)" : "").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  +" + fmt(amount * ESSENCE_PER_XP * EventSystem.essenceMultiplier()) + " Essence").withStyle(ChatFormatting.LIGHT_PURPLE)), true);
    }

    private static void giveEssence(ServerPlayer player, PlayerProfile p, double amount) {
        p.essenceFraction += amount * EventSystem.essenceMultiplier();
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
        Perk unlocked = null;
        for (Perk k : PERKS.get(job)) if (k.level() == lvl) unlocked = k;
        Component sub = unlocked != null
                ? Component.literal("Nouveau talent : " + unlocked.name()).withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal("+" + money + " pièces · +" + essence + " Essence" + extra).withStyle(ChatFormatting.YELLOW);
        Fx.title(player, Component.literal(job.icon + " " + job.label + " niveau " + lvl).withStyle(job.color, ChatFormatting.BOLD), sub);
        Fx.sound(player, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
        Fx.particles(player, ParticleTypes.HAPPY_VILLAGER, 30, 0.6);
        Msg.ok(player, job.label + " niveau " + lvl + " ! +" + money + " pièces, +" + essence + " Essence" + extra + ".");
        if (unlocked != null) {
            Msg.send(player, Msg.text("✦ Talent débloqué : ", ChatFormatting.LIGHT_PURPLE)
                    .append(Msg.text(unlocked.name(), ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                    .append(Msg.text(" — " + unlocked.description() + " ", ChatFormatting.GRAY))
                    .append(Msg.button("Talents", "/metiers", "Voir les talents de vos métiers", ChatFormatting.YELLOW)));
        }
        if (lvl % 10 == 0) Msg.broadcast(player.level().getServer(), p.name + " atteint le niveau " + lvl + " de " + job.label + " !", job.color);
        PowerSystem.checkPromotion(player, p);
    }

    // ------------------------------------------------------------------ Clés de quête

    public static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    /** « minecraft:deepslate_iron_ore » → « iron ». */
    private static String oreFamily(String id) {
        return path(id).replace("deepslate_", "").replace("_ore", "").replace("_cluster", "");
    }

    // ------------------------------------------------------------------ Blocs

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

    /** Le joueur aurait-il le droit de casser ce bloc lui-même ? (pour l'abattage, le filon, la moisson large) */
    private static boolean canBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (Hub.isSpawn(level)) return false;
        if (!DivineWorld.isDivine(level)) return true;
        PlayerProfile owner = DivineWorld.ownerAt(pos);
        return owner != null && DivineWorld.insidePlot(owner, pos) && DivineWorld.has(GodData.get(player), owner, IslandPerm.CASSER);
    }

    /** Un joueur vient de casser un bloc (événement non annulé). */
    public static void onBroken(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (Hub.isSpawn(level) || player.isCreative()) return;
        boolean placed = PLACED.remove(key(level, pos));
        if (placed) return;
        Block b = state.getBlock();
        PlayerProfile p = GodData.get(player);
        String id = BuiltInRegistries.BLOCK.getKey(b).toString();

        Double ore = ORES.get(b);
        if (ore != null) {
            gain(player, Job.MINEUR, ore);
            QuestSystem.progress(player, List.of("mine:" + oreFamily(id), "mine:ore", "mine:any"), 1);
            int lvl = level(p, Job.MINEUR);
            double chance = Math.min(0.40, lvl * 0.008) + (lvl >= 50 ? 0.15 : 0);
            if (RANDOM.nextDouble() < chance) {
                Block.popResource(level, pos, ORE_DROP.get(b).copy());
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
            }
            if (lvl >= 25 && !player.isShiftKeyDown()) vein(player, level, pos, b, ore, id);
            return;
        }
        if (STONES.contains(b)) {
            gain(player, Job.MINEUR, 0.25);
            QuestSystem.progress(player, List.of("mine:stone", "mine:any"), 1);
            return;
        }
        if (state.is(BlockTags.LOGS)) {
            gain(player, Job.BUCHERON, 2.5);
            QuestSystem.progress(player, List.of("chop:" + path(id), "chop:any"), 1);
            int lvl = level(p, Job.BUCHERON);
            if (RANDOM.nextDouble() < Math.min(0.40, lvl * 0.01)) Block.popResource(level, pos, new ItemStack(b.asItem()));
            if (lvl >= 20 && !player.isShiftKeyDown()) fellTree(player, level, pos, b, lvl >= 40 ? 160 : 64, lvl >= 30);
            return;
        }
        if (b instanceof CropBlock crop && crop.isMaxAge(state)) {
            harvested(player, level, pos, b, p, id);
            // Moisson large (niv. 30) : les cultures mûres autour, à la houe.
            if (level(p, Job.FERMIER) >= 30 && player.getMainHandItem().is(ItemTags.HOES) && !player.isShiftKeyDown()) {
                level.getServer().execute(() -> {
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        BlockPos n = pos.offset(dx, 0, dz);
                        BlockState ns = level.getBlockState(n);
                        if (!(ns.getBlock() instanceof CropBlock nc) || !nc.isMaxAge(ns) || PLACED.contains(key(level, n)) || !canBreak(player, level, n)) continue;
                        level.destroyBlock(n, true, player);
                        harvested(player, level, n, ns.getBlock(), p, BuiltInRegistries.BLOCK.getKey(ns.getBlock()).toString());
                    }
                });
            }
            return;
        }
        if (b == Blocks.MELON || b == Blocks.PUMPKIN || b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.COCOA) {
            gain(player, Job.FERMIER, 1.5);
            QuestSystem.progress(player, List.of("harvest:" + path(id), "harvest:any"), 1);
        }
    }

    /** Une culture mûre vient d'être récoltée (directement ou par la moisson large). */
    private static void harvested(ServerPlayer player, ServerLevel level, BlockPos pos, Block b, PlayerProfile p, String id) {
        gain(player, Job.FERMIER, 3);
        QuestSystem.progress(player, List.of("harvest:" + path(id), "harvest:any"), 1);
        int lvl = level(p, Job.FERMIER);
        if (RANDOM.nextDouble() < Math.min(0.40, lvl * 0.01)) Block.popResource(level, pos, new ItemStack(cropDrop(b)));
        if (lvl >= 40 && RANDOM.nextDouble() < 0.02) Block.popResource(level, pos, new ItemStack(Items.GOLDEN_CARROT));
        if (lvl >= 5) {
            BlockState young = b.defaultBlockState();
            level.getServer().execute(() -> {
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) level.setBlockAndUpdate(pos, young);
            });
        }
    }

    private static Item cropDrop(Block b) {
        if (b == Blocks.CARROTS) return Items.CARROT;
        if (b == Blocks.POTATOES) return Items.POTATO;
        if (b == Blocks.BEETROOTS) return Items.BEETROOT;
        return Items.WHEAT;
    }

    /** Mineur niv. 25 : filon (8 minerais identiques reliés). */
    private static void vein(ServerPlayer player, ServerLevel level, BlockPos start, Block ore, double xp, String id) {
        level.getServer().execute(() -> {
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(start);
            int n = 0;
            while (!queue.isEmpty() && n < 8) {
                BlockPos cur = queue.poll();
                for (int dx = -1; dx <= 1 && n < 8; dx++) for (int dy = -1; dy <= 1 && n < 8; dy++) for (int dz = -1; dz <= 1 && n < 8; dz++) {
                    BlockPos nb = cur.offset(dx, dy, dz);
                    if (!seen.add(nb) || level.getBlockState(nb).getBlock() != ore || PLACED.contains(key(level, nb)) || !canBreak(player, level, nb)) continue;
                    level.destroyBlock(nb, true, player);
                    n++;
                    queue.add(nb);
                }
            }
            if (n > 0) {
                gain(player, Job.MINEUR, xp * n * 0.75);
                QuestSystem.progress(player, List.of("mine:" + oreFamily(id), "mine:ore", "mine:any"), n);
                Fx.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.6F);
            }
        });
    }

    /** Bûcheron niv. 20+ : abat tout l'arbre. */
    private static void fellTree(ServerPlayer player, ServerLevel level, BlockPos start, Block log, int max, boolean gatherer) {
        level.getServer().execute(() -> {
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(start);
            int felled = 0;
            while (!queue.isEmpty() && felled < max) {
                BlockPos cur = queue.poll();
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = cur.offset(dx, dy, dz);
                    if (!seen.add(n)) continue;
                    if (level.getBlockState(n).getBlock() != log || PLACED.contains(key(level, n)) || !canBreak(player, level, n)) continue;
                    level.destroyBlock(n, true, player);
                    felled++;
                    queue.add(n);
                    if (felled >= max) break;
                }
            }
            if (felled > 0) {
                gain(player, Job.BUCHERON, felled * 1.2);
                QuestSystem.progress(player, List.of("chop:" + path(BuiltInRegistries.BLOCK.getKey(log).toString()), "chop:any"), felled);
                if (gatherer) {
                    if (RANDOM.nextDouble() < 0.5) Block.popResource(level, start, new ItemStack(Items.APPLE, 1 + RANDOM.nextInt(2)));
                    Item sapling = sapling(log);
                    if (sapling != null) Block.popResource(level, start, new ItemStack(sapling, 1 + RANDOM.nextInt(2)));
                }
            }
        });
    }

    private static Item sapling(Block log) {
        if (log == Blocks.OAK_LOG) return Items.OAK_SAPLING;
        if (log == Blocks.BIRCH_LOG) return Items.BIRCH_SAPLING;
        if (log == Blocks.SPRUCE_LOG) return Items.SPRUCE_SAPLING;
        if (log == Blocks.JUNGLE_LOG) return Items.JUNGLE_SAPLING;
        if (log == Blocks.DARK_OAK_LOG) return Items.DARK_OAK_SAPLING;
        if (log == Blocks.ACACIA_LOG) return Items.ACACIA_SAPLING;
        if (log == Blocks.CHERRY_LOG) return Items.CHERRY_SAPLING;
        return null;
    }

    // ------------------------------------------------------------------ Chasse, pêche, artisanat

    public static void onKill(ServerPlayer player, LivingEntity victim) {
        if (Hub.isSpawn(player.level()) && !EventSystem.isEventMob(victim)) return;
        PlayerProfile p = GodData.get(player);
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        boolean hostile = victim instanceof Enemy;
        double xp = hostile ? 7 : 2.5;
        if (id.endsWith("wither") || id.endsWith("ender_dragon") || id.endsWith("warden")) xp = 300;
        int lvl = level(p, Job.CHASSEUR);
        long bounty = (hostile ? 2 : 1) + lvl / 5;
        if (lvl >= 50) bounty *= 2;
        PowerSystem.earnMoney(player.level().getServer(), p, bounty);
        if (!Hub.isSpawn(player.level())) gain(player, Job.CHASSEUR, xp);
        List<String> keys = new ArrayList<>(List.of("kill:" + id, "kill:" + path(id), "kill:any"));
        if (hostile) keys.add("kill:hostile");
        if (victim instanceof Animal) keys.add("kill:animal");
        QuestSystem.progress(player, keys, 1);
        if (!hostile) return;
        if (lvl >= 10) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
        if (lvl >= 20) player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0, true, false));
        if (lvl >= 30 && RANDOM.nextDouble() < 0.005) {
            CrateSystem.addKey(p, CrateSystem.Crate.COMMUNE, 1);
            Msg.ok(player, "Trophée de chasse : une clé Commune !");
        }
        if (lvl >= 40 && RANDOM.nextDouble() < 0.0015 && SpawnerSystem.MOBS.containsKey(id)) {
            Shop.give(player, SpawnerSystem.item(id));
            Msg.broadcast(player.level().getServer(), p.name + " a obtenu un générateur de " + SpawnerSystem.mobName(id).toLowerCase() + " en chassant !", ChatFormatting.LIGHT_PURPLE);
        }
    }

    private static final Item[] TREASURES = {Items.DIAMOND, Items.EMERALD, Items.NAME_TAG, Items.GOLD_INGOT, Items.NAUTILUS_SHELL, Items.EXPERIENCE_BOTTLE};

    public static void onFish(ServerPlayer player, List<ItemStack> drops) {
        PlayerProfile p = GodData.get(player);
        gain(player, Job.PECHEUR, 10);
        List<String> keys = new ArrayList<>(List.of("fish:any"));
        if (!drops.isEmpty()) keys.add("fish:" + path(BuiltInRegistries.ITEM.getKey(drops.get(0).getItem()).toString()));
        QuestSystem.progress(player, keys, 1);
        int lvl = level(p, Job.PECHEUR);
        if (RANDOM.nextDouble() < Math.min(0.35, lvl * 0.01) && !drops.isEmpty()) Shop.give(player, drops.get(0).copy());
        if (lvl >= 10 && RANDOM.nextDouble() < lvl * 0.002) {
            CrateSystem.addKey(p, CrateSystem.Crate.COMMUNE, 1);
            Msg.ok(player, "Une clé Commune était accrochée à votre hameçon !");
        }
        if (lvl >= 30 && RANDOM.nextDouble() < 0.04) {
            Item t = TREASURES[RANDOM.nextInt(TREASURES.length)];
            Shop.give(player, new ItemStack(t, t == Items.EXPERIENCE_BOTTLE ? 8 : 1));
            Msg.ok(player, "Trésor englouti : " + new ItemStack(t).getHoverName().getString() + " !");
        }
        if (lvl >= 50 && RANDOM.nextDouble() < 0.01) {
            CrateSystem.addKey(p, CrateSystem.Crate.RARE, 1);
            Msg.ok(player, "Le roi des abysses vous offre une clé Rare !");
        }
    }

    /** Objet fabriqué à l'établi (ou dans l'inventaire). */
    public static void onCraft(ServerPlayer player, ItemStack result) {
        if (result.isEmpty()) return;
        String id = path(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        QuestSystem.progress(player, List.of("craft:any", "craft:" + id), Math.max(1, result.getCount()));
    }

    /** Objet retiré d'un four (fonte ou cuisson). */
    public static void onSmelt(ServerPlayer player, ItemStack result) {
        if (result.isEmpty()) return;
        String id = path(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        List<String> keys = new ArrayList<>(List.of("smelt:any", "smelt:" + id));
        if (result.get(net.minecraft.core.component.DataComponents.FOOD) != null) keys.add("smelt:food");
        QuestSystem.progress(player, keys, Math.max(1, result.getCount()));
        if (id.endsWith("_ingot")) gain(player, Job.MINEUR, 0.5 * result.getCount());
    }

    /** Un animal est né grâce au joueur : XP de Fermier. */
    public static void onBreed(ServerPlayer player, LivingEntity child) {
        PlayerProfile p = GodData.get(player);
        p.bred++;
        gain(player, Job.FERMIER, 6);
        String id = path(BuiltInRegistries.ENTITY_TYPE.getKey(child.getType()).toString());
        QuestSystem.progress(player, List.of("breed:any", "breed:" + id), 1);
    }

    public static void onTrade(ServerPlayer player) {
        QuestSystem.progress(player, List.of("trade:any"), 1);
    }

    // ------------------------------------------------------------------ Talents passifs (toutes les 5 secondes)

    public static void perks(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        ItemStack hand = player.getMainHandItem();
        int mine = level(p, Job.MINEUR);
        if (hand.is(ItemTags.PICKAXES)) {
            if (mine >= 15) player.addEffect(new MobEffectInstance(MobEffects.HASTE, 140, mine >= 35 ? 1 : 0, true, false));
            if (mine >= 10 && player.getY() < 40 && !Hub.isSpawn(player.level()))
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
        }
        int wood = level(p, Job.BUCHERON);
        if (hand.is(ItemTags.AXES)) {
            if (wood >= 10) player.addEffect(new MobEffectInstance(MobEffects.SPEED, 140, 0, true, false));
            if (wood >= 40) player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 140, 0, true, false));
        }
        int farm = level(p, Job.FERMIER);
        if (hand.is(ItemTags.HOES)) {
            if (farm >= 10 && player.getFoodData().getFoodLevel() < 18) player.getFoodData().eat(1, 0.3F);
            if (farm >= 20 && player.level() instanceof ServerLevel l && !Hub.isSpawn(l)) {
                BlockPos c = player.blockPosition();
                for (int i = 0; i < 6; i++) {
                    BlockPos pos = c.offset(RANDOM.nextInt(9) - 4, RANDOM.nextInt(3) - 1, RANDOM.nextInt(9) - 4);
                    BlockState st = l.getBlockState(pos);
                    if (st.getBlock() instanceof CropBlock crop && !crop.isMaxAge(st) && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), l, pos))
                        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0);
                }
            }
        }
        int fish = level(p, Job.PECHEUR);
        if (fish >= 20 && hand.is(Items.FISHING_ROD)) player.addEffect(new MobEffectInstance(MobEffects.LUCK, 140, 0, true, false));
        if (fish >= 40 && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 140, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 140, 0, true, false));
        }
    }

    /** Classement d'un métier (pour le menu). */
    public static List<PlayerProfile> top(Job job, int n) {
        List<PlayerProfile> all = new ArrayList<>(GodData.state().players.values());
        all.sort((a, b) -> {
            int c = Integer.compare(level(b, job), level(a, job));
            return c != 0 ? c : Double.compare(xp(b, job), xp(a, job));
        });
        return all.subList(0, Math.min(n, all.size()));
    }
}
