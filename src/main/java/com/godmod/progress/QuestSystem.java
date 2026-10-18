package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.data.QuestProgress;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Quêtes du jour : 7 quêtes proposées chaque jour à chaque joueur, 5 actives au maximum.
 * Elles avancent toutes seules pendant le jeu (minage, coupe, récolte, chasse, pêche).
 */
public final class QuestSystem {
    public static final int MAX_ACTIVE = 5;
    public static final int OFFERS = 7;

    public record Quest(String id, String title, String description, String action, int amount,
                        long money, long essence, Job job, double jobXp, CrateSystem.Crate key, Item icon) {
    }

    public static final Map<String, Quest> QUESTS = new LinkedHashMap<>();

    static {
        // Mineur
        q("mine_stone", "Tailleur de pierre", "Minez 256 blocs de pierre", "mine:stone", 256, 300, 120, Job.MINEUR, 60, null, Items.STONE_PICKAXE);
        q("mine_coal", "Charbonnier", "Minez 48 minerais de charbon", "mine:minecraft:coal_ore", 48, 400, 150, Job.MINEUR, 80, null, Items.COAL);
        q("mine_iron", "Forge-fer", "Minez 32 minerais de fer", "mine:minecraft:iron_ore", 32, 700, 220, Job.MINEUR, 120, null, Items.RAW_IRON);
        q("mine_iron_deep", "Fer des profondeurs", "Minez 24 minerais de fer des abîmes", "mine:minecraft:deepslate_iron_ore", 24, 800, 250, Job.MINEUR, 140, null, Items.IRON_INGOT);
        q("mine_gold", "Chercheur d'or", "Minez 16 minerais d'or des abîmes", "mine:minecraft:deepslate_gold_ore", 16, 1000, 300, Job.MINEUR, 160, CrateSystem.Crate.COMMUNE, Items.RAW_GOLD);
        q("mine_redstone", "Électricien", "Minez 24 minerais de redstone", "mine:ore", 24, 500, 180, Job.MINEUR, 90, null, Items.REDSTONE);
        q("mine_diamond", "Cœur de diamant", "Minez 8 minerais de diamant des abîmes", "mine:minecraft:deepslate_diamond_ore", 8, 2500, 600, Job.MINEUR, 300, CrateSystem.Crate.RARE, Items.DIAMOND);
        q("mine_ores", "Prospecteur", "Minez 100 minerais (tous types)", "mine:ore", 100, 1500, 450, Job.MINEUR, 200, CrateSystem.Crate.COMMUNE, Items.IRON_PICKAXE);
        // Bûcheron
        q("chop_64", "Coupe franche", "Coupez 64 bûches", "chop:any", 64, 350, 130, Job.BUCHERON, 70, null, Items.OAK_LOG);
        q("chop_200", "Déforestation", "Coupez 200 bûches", "chop:any", 200, 1000, 350, Job.BUCHERON, 180, CrateSystem.Crate.COMMUNE, Items.IRON_AXE);
        q("chop_birch", "Écorce blanche", "Coupez 48 bûches de bouleau", "chop:minecraft:birch_log", 48, 450, 160, Job.BUCHERON, 90, null, Items.BIRCH_LOG);
        q("chop_spruce", "Taïga", "Coupez 48 bûches de sapin", "chop:minecraft:spruce_log", 48, 450, 160, Job.BUCHERON, 90, null, Items.SPRUCE_LOG);
        q("chop_dark", "Forêt noire", "Coupez 64 bûches de chêne noir", "chop:minecraft:dark_oak_log", 64, 600, 200, Job.BUCHERON, 110, null, Items.DARK_OAK_LOG);
        // Fermier
        q("harvest_wheat", "Moisson", "Récoltez 64 blés mûrs", "harvest:minecraft:wheat", 64, 400, 150, Job.FERMIER, 80, null, Items.WHEAT);
        q("harvest_carrot", "Potager", "Récoltez 64 carottes mûres", "harvest:minecraft:carrots", 64, 400, 150, Job.FERMIER, 80, null, Items.CARROT);
        q("harvest_potato", "Patates !", "Récoltez 64 pommes de terre mûres", "harvest:minecraft:potatoes", 64, 400, 150, Job.FERMIER, 80, null, Items.POTATO);
        q("harvest_any", "Grand fermier", "Récoltez 200 cultures", "harvest:any", 200, 1100, 380, Job.FERMIER, 200, CrateSystem.Crate.COMMUNE, Items.GOLDEN_HOE);
        q("harvest_melon", "Pastèques", "Récoltez 32 melons", "harvest:minecraft:melon", 32, 500, 170, Job.FERMIER, 90, null, Items.MELON_SLICE);
        // Chasseur
        q("kill_zombie", "Purge", "Tuez 25 zombies", "kill:minecraft:zombie", 25, 500, 180, Job.CHASSEUR, 100, null, Items.ROTTEN_FLESH);
        q("kill_skeleton", "Os brisés", "Tuez 20 squelettes", "kill:minecraft:skeleton", 20, 550, 190, Job.CHASSEUR, 110, null, Items.BONE);
        q("kill_creeper", "Désamorçage", "Tuez 12 creepers", "kill:minecraft:creeper", 12, 700, 220, Job.CHASSEUR, 130, null, Items.GUNPOWDER);
        q("kill_spider", "Arachnophobe", "Tuez 20 araignées", "kill:minecraft:spider", 20, 500, 180, Job.CHASSEUR, 100, null, Items.STRING);
        q("kill_enderman", "Regard du Néant", "Tuez 8 Endermen", "kill:minecraft:enderman", 8, 1500, 400, Job.CHASSEUR, 200, CrateSystem.Crate.COMMUNE, Items.ENDER_PEARL);
        q("kill_hostile", "Gardien de la nuit", "Tuez 60 monstres", "kill:hostile", 60, 1200, 400, Job.CHASSEUR, 200, CrateSystem.Crate.COMMUNE, Items.IRON_SWORD);
        q("kill_animals", "Boucher", "Tuez 30 animaux", "kill:any", 30, 300, 110, Job.CHASSEUR, 60, null, Items.COOKED_BEEF);
        // Pêcheur
        q("fish_10", "Première prise", "Pêchez 10 fois", "fish:any", 10, 350, 130, Job.PECHEUR, 70, null, Items.COD);
        q("fish_32", "Loup de mer", "Pêchez 32 fois", "fish:any", 32, 900, 300, Job.PECHEUR, 160, CrateSystem.Crate.COMMUNE, Items.FISHING_ROD);
        q("fish_64", "Maître pêcheur", "Pêchez 64 fois", "fish:any", 64, 1800, 550, Job.PECHEUR, 300, CrateSystem.Crate.RARE, Items.TROPICAL_FISH);
    }

    private static void q(String id, String title, String desc, String action, int amount, long money, long essence,
                          Job job, double jobXp, CrateSystem.Crate key, Item icon) {
        QUESTS.put(id, new Quest(id, title, desc, action, amount, money, essence, job, jobXp, key, icon));
    }

    private QuestSystem() {
    }

    /** Fuseau des « jours » du serveur (quêtes, récompense quotidienne). */
    public static final java.time.ZoneId ZONE = java.time.ZoneId.of("Europe/Paris");

    public static String today() {
        return LocalDate.now(ZONE).toString();
    }

    /** Millisecondes avant minuit (nouvelles quêtes, nouvelle récompense). */
    public static long untilTomorrow() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZONE);
        java.time.ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
        return java.time.Duration.between(now, next).toMillis();
    }

    /** Renouvelle les quêtes proposées si un nouveau jour a commencé. */
    public static void refreshOffers(PlayerProfile p) {
        String day = today();
        if (day.equals(p.questDay) && !p.questOffers.isEmpty()) return;
        p.questDay = day;
        p.questOffers.clear();
        List<String> pool = new ArrayList<>(QUESTS.keySet());
        for (QuestProgress qp : p.quests) pool.remove(qp.id);
        Random r = new Random((p.uuid + day).hashCode());
        java.util.Collections.shuffle(pool, r);
        for (int i = 0; i < Math.min(OFFERS, pool.size()); i++) p.questOffers.add(pool.get(i));
        GodData.dirty();
    }

    public static String accept(ServerPlayer player, String id) {
        PlayerProfile p = GodData.get(player);
        refreshOffers(p);
        if (!p.questOffers.contains(id)) return "Cette quête n'est plus proposée.";
        if (p.quests.size() >= MAX_ACTIVE) return "Vous avez déjà " + MAX_ACTIVE + " quêtes en cours. Terminez-en ou abandonnez-en une.";
        p.questOffers.remove(id);
        p.quests.add(new QuestProgress(id));
        GodData.dirty();
        Quest q = QUESTS.get(id);
        Msg.ok(player, "Quête acceptée : " + q.title() + " — " + q.description() + ".");
        return null;
    }

    public static String abandon(ServerPlayer player, String id) {
        PlayerProfile p = GodData.get(player);
        boolean removed = p.quests.removeIf(qp -> qp.id.equals(id));
        if (!removed) return "Quête introuvable.";
        GodData.dirty();
        Msg.info(player, "Quête abandonnée.");
        return null;
    }

    /** Fait avancer les quêtes dont l'action correspond à l'une des clés. */
    public static void progress(ServerPlayer player, Collection<String> keys, int amount) {
        PlayerProfile p = GodData.get(player);
        if (p.quests.isEmpty()) return;
        List<QuestProgress> done = new ArrayList<>();
        for (QuestProgress qp : p.quests) {
            Quest q = QUESTS.get(qp.id);
            if (q == null) {
                done.add(qp);
                continue;
            }
            if (!keys.contains(q.action())) continue;
            int before = qp.progress;
            qp.progress = Math.min(q.amount(), qp.progress + amount);
            if (qp.progress >= q.amount()) done.add(qp);
            else if (qp.progress * 4 / q.amount() > before * 4 / q.amount()) {
                player.displayClientMessage(Component.literal("Quête « " + q.title() + " » : " + qp.progress + " / " + q.amount()).withStyle(ChatFormatting.AQUA), true);
            }
        }
        for (QuestProgress qp : done) {
            p.quests.remove(qp);
            Quest q = QUESTS.get(qp.id);
            if (q != null) complete(player, p, q);
        }
        GodData.dirty();
    }

    private static void complete(ServerPlayer player, PlayerProfile p, Quest q) {
        p.questsDone++;
        p.money += q.money();
        p.essence += Math.round(q.essence() * GodData.settings().essenceMultiplier);
        String extra = "";
        if (q.key() != null) {
            CrateSystem.addKey(p, q.key(), 1);
            extra = " + 1 clé " + q.key().label;
        }
        // Toutes les 10 quêtes : une clé Rare en bonus.
        if (p.questsDone % 10 == 0) {
            CrateSystem.addKey(p, CrateSystem.Crate.RARE, 1);
            extra += " + 1 clé Rare (bonus 10 quêtes)";
        }
        Fx.title(player, Component.literal("Quête accomplie !").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(q.title() + " · +" + q.money() + " pièces · +" + q.essence() + " Essence" + extra).withStyle(ChatFormatting.YELLOW));
        Fx.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        Fx.particles(player, ParticleTypes.TOTEM_OF_UNDYING, 40, 0.8);
        Msg.ok(player, "Quête « " + q.title() + " » accomplie : +" + q.money() + " pièces, +" + q.essence() + " Essence" + extra + ".");
        if (q.job() != null) JobSystem.gain(player, q.job(), q.jobXp());
        PowerSystem.checkPromotion(player, p);
    }
}
