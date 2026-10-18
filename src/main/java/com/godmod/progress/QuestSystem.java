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
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Quêtes :
 * <ul>
 *   <li><b>quêtes du jour</b> : 7 propositions par jour (2 faciles, 3 normales, 1 difficile, 1 épique), 5 actives au maximum,
 *       renouvelables (1 fois gratuite par jour) ; bonus d'assiduité à 3 et 5 quêtes dans la journée ;</li>
 *   <li><b>défis de la semaine</b> : 3 grands objectifs actifs d'office, renouvelés chaque lundi ;</li>
 *   <li><b>quêtes d'ascension</b> : voir {@link AscensionQuests}.</li>
 * </ul>
 * Les récompenses grandissent avec la difficulté et avec le palier du joueur.
 */
public final class QuestSystem {
    public static final int MAX_ACTIVE = 5;
    public static final int OFFERS = 7;
    public static final int WEEKLY = 3;

    public enum Difficulty {
        FACILE("Facile", "★", ChatFormatting.GREEN, 300, 100, 60, null),
        NORMALE("Normale", "★★", ChatFormatting.YELLOW, 650, 220, 130, null),
        DIFFICILE("Difficile", "★★★", ChatFormatting.GOLD, 1400, 450, 260, CrateSystem.Crate.COMMUNE),
        EPIQUE("Épique", "★★★★", ChatFormatting.LIGHT_PURPLE, 3000, 900, 500, CrateSystem.Crate.RARE),
        HEBDO("Défi de la semaine", "✦", ChatFormatting.AQUA, 6000, 1800, 900, CrateSystem.Crate.RARE);

        public final String label;
        public final String stars;
        public final ChatFormatting color;
        public final long money, essence;
        public final double jobXp;
        public final CrateSystem.Crate key;

        Difficulty(String label, String stars, ChatFormatting color, long money, long essence, double jobXp, CrateSystem.Crate key) {
            this.label = label;
            this.stars = stars;
            this.color = color;
            this.money = money;
            this.essence = essence;
            this.jobXp = jobXp;
            this.key = key;
        }
    }

    /** Une quête : avancer {@code amount} fois l'action {@code action} (voir les clés envoyées par les métiers). */
    public record Quest(String id, String title, String description, String action, int amount, Difficulty difficulty, Job job, Item icon) {
    }

    public static final Map<String, Quest> QUESTS = new LinkedHashMap<>();
    public static final Map<String, Quest> WEEKLIES = new LinkedHashMap<>();

    private static void q(String id, Difficulty d, String title, String desc, String action, int amount, Job job, Item icon) {
        QUESTS.put(id, new Quest(id, title, desc, action, amount, d, job, icon));
    }

    private static void w(String id, String title, String desc, String action, int amount, Job job, Item icon) {
        WEEKLIES.put(id, new Quest(id, title, desc, action, amount, Difficulty.HEBDO, job, icon));
    }

    static {
        Difficulty F = Difficulty.FACILE, N = Difficulty.NORMALE, D = Difficulty.DIFFICILE, E = Difficulty.EPIQUE;
        // ---- Mineur
        q("mine_stone", F, "Tailleur de pierre", "Minez 128 blocs de pierre", "mine:stone", 128, Job.MINEUR, Items.STONE_PICKAXE);
        q("mine_stone2", N, "Carrier", "Minez 384 blocs de pierre", "mine:stone", 384, Job.MINEUR, Items.COBBLESTONE);
        q("mine_coal", F, "Charbonnier", "Minez 32 minerais de charbon", "mine:coal", 32, Job.MINEUR, Items.COAL);
        q("mine_copper", F, "Cuivre brut", "Minez 24 minerais de cuivre", "mine:copper", 24, Job.MINEUR, Items.RAW_COPPER);
        q("mine_iron", N, "Forge-fer", "Minez 32 minerais de fer", "mine:iron", 32, Job.MINEUR, Items.RAW_IRON);
        q("mine_redstone", N, "Électricien", "Minez 24 minerais de redstone", "mine:redstone", 24, Job.MINEUR, Items.REDSTONE);
        q("mine_lapis", N, "Bleu royal", "Minez 16 minerais de lapis-lazuli", "mine:lapis", 16, Job.MINEUR, Items.LAPIS_LAZULI);
        q("mine_gold", D, "Chercheur d'or", "Minez 20 minerais d'or", "mine:gold", 20, Job.MINEUR, Items.RAW_GOLD);
        q("mine_ores", D, "Prospecteur", "Minez 120 minerais (tous types)", "mine:ore", 120, Job.MINEUR, Items.IRON_PICKAXE);
        q("mine_diamond", E, "Cœur de diamant", "Minez 10 minerais de diamant", "mine:diamond", 10, Job.MINEUR, Items.DIAMOND);
        q("mine_emerald", E, "Veine d'émeraude", "Minez 4 minerais d'émeraude", "mine:emerald", 4, Job.MINEUR, Items.EMERALD);
        // ---- Bûcheron
        q("chop_32", F, "Petit bois", "Coupez 32 bûches", "chop:any", 32, Job.BUCHERON, Items.OAK_LOG);
        q("chop_oak", F, "Chêne robuste", "Coupez 48 bûches de chêne", "chop:oak_log", 48, Job.BUCHERON, Items.OAK_SAPLING);
        q("chop_birch", N, "Écorce blanche", "Coupez 64 bûches de bouleau", "chop:birch_log", 64, Job.BUCHERON, Items.BIRCH_LOG);
        q("chop_spruce", N, "Taïga", "Coupez 64 bûches de sapin", "chop:spruce_log", 64, Job.BUCHERON, Items.SPRUCE_LOG);
        q("chop_jungle", N, "Explorateur de jungle", "Coupez 48 bûches d'acajou", "chop:jungle_log", 48, Job.BUCHERON, Items.JUNGLE_LOG);
        q("chop_dark", D, "Forêt noire", "Coupez 96 bûches de chêne noir", "chop:dark_oak_log", 96, Job.BUCHERON, Items.DARK_OAK_LOG);
        q("chop_200", D, "Déforestation", "Coupez 250 bûches", "chop:any", 250, Job.BUCHERON, Items.IRON_AXE);
        q("chop_500", E, "Rasez la forêt", "Coupez 600 bûches", "chop:any", 600, Job.BUCHERON, Items.DIAMOND_AXE);
        // ---- Fermier
        q("harvest_wheat", F, "Moisson", "Récoltez 48 blés mûrs", "harvest:wheat", 48, Job.FERMIER, Items.WHEAT);
        q("harvest_carrot", F, "Potager", "Récoltez 48 carottes mûres", "harvest:carrots", 48, Job.FERMIER, Items.CARROT);
        q("harvest_potato", F, "Patates !", "Récoltez 48 pommes de terre mûres", "harvest:potatoes", 48, Job.FERMIER, Items.POTATO);
        q("harvest_beet", N, "Betteraves", "Récoltez 64 betteraves mûres", "harvest:beetroots", 64, Job.FERMIER, Items.BEETROOT);
        q("harvest_melon", N, "Pastèques", "Récoltez 32 pastèques", "harvest:melon", 32, Job.FERMIER, Items.MELON_SLICE);
        q("harvest_pumpkin", N, "Citrouilles", "Récoltez 32 citrouilles", "harvest:pumpkin", 32, Job.FERMIER, Items.PUMPKIN);
        q("harvest_cane", N, "Canne à sucre", "Récoltez 96 cannes à sucre", "harvest:sugar_cane", 96, Job.FERMIER, Items.SUGAR_CANE);
        q("breed_10", N, "Éleveur", "Faites naître 10 animaux", "breed:any", 10, Job.FERMIER, Items.WHEAT_SEEDS);
        q("harvest_any", D, "Grand fermier", "Récoltez 300 cultures", "harvest:any", 300, Job.FERMIER, Items.GOLDEN_HOE);
        q("breed_30", D, "Ferme modèle", "Faites naître 30 animaux", "breed:any", 30, Job.FERMIER, Items.HAY_BLOCK);
        q("harvest_800", E, "Grenier des dieux", "Récoltez 800 cultures", "harvest:any", 800, Job.FERMIER, Items.DIAMOND_HOE);
        // ---- Chasseur
        q("kill_zombie", F, "Purge", "Tuez 20 zombies", "kill:zombie", 20, Job.CHASSEUR, Items.ROTTEN_FLESH);
        q("kill_animals", F, "Boucher", "Tuez 20 animaux", "kill:animal", 20, Job.CHASSEUR, Items.COOKED_BEEF);
        q("kill_skeleton", N, "Os brisés", "Tuez 20 squelettes", "kill:skeleton", 20, Job.CHASSEUR, Items.BONE);
        q("kill_spider", N, "Arachnophobe", "Tuez 20 araignées", "kill:spider", 20, Job.CHASSEUR, Items.STRING);
        q("kill_creeper", N, "Désamorçage", "Tuez 12 creepers", "kill:creeper", 12, Job.CHASSEUR, Items.GUNPOWDER);
        q("kill_hostile", D, "Gardien de la nuit", "Tuez 80 monstres", "kill:hostile", 80, Job.CHASSEUR, Items.IRON_SWORD);
        q("kill_enderman", D, "Regard du Néant", "Tuez 10 Endermen", "kill:enderman", 10, Job.CHASSEUR, Items.ENDER_PEARL);
        q("kill_witch", D, "Chasse aux sorcières", "Tuez 5 sorcières", "kill:witch", 5, Job.CHASSEUR, Items.GLASS_BOTTLE);
        q("kill_200", E, "Fléau des ténèbres", "Tuez 200 monstres", "kill:hostile", 200, Job.CHASSEUR, Items.DIAMOND_SWORD);
        q("pvp_3", E, "Champion des arènes", "Battez 3 joueurs en combat", "pvp:kill", 3, null, Items.GOLDEN_SWORD);
        // ---- Pêcheur
        q("fish_10", F, "Première prise", "Pêchez 10 fois", "fish:any", 10, Job.PECHEUR, Items.COD);
        q("fish_salmon", N, "Remontée du saumon", "Pêchez 8 saumons", "fish:salmon", 8, Job.PECHEUR, Items.SALMON);
        q("fish_32", N, "Loup de mer", "Pêchez 32 fois", "fish:any", 32, Job.PECHEUR, Items.FISHING_ROD);
        q("fish_puffer", D, "Poison des mers", "Pêchez 4 poissons-globes", "fish:pufferfish", 4, Job.PECHEUR, Items.PUFFERFISH);
        q("fish_80", E, "Maître pêcheur", "Pêchez 80 fois", "fish:any", 80, Job.PECHEUR, Items.TROPICAL_FISH);
        // ---- Artisanat, commerce, divers
        q("craft_torch", F, "Porteur de lumière", "Fabriquez 64 torches", "craft:torch", 64, null, Items.TORCH);
        q("craft_any", F, "Artisan", "Fabriquez 64 objets", "craft:any", 64, null, Items.CRAFTING_TABLE);
        q("craft_bread", F, "Boulanger", "Fabriquez 16 pains", "craft:bread", 16, Job.FERMIER, Items.BREAD);
        q("smelt_iron", N, "Haut fourneau", "Faites fondre 32 lingots de fer", "smelt:iron_ingot", 32, Job.MINEUR, Items.FURNACE);
        q("smelt_any", N, "Fondeur", "Faites cuire ou fondre 96 objets", "smelt:any", 96, null, Items.BLAST_FURNACE);
        q("cook_food", F, "Cuisinier", "Cuisez 24 aliments", "smelt:food", 24, null, Items.SMOKER);
        q("trade_10", N, "Marchandage", "Faites 10 échanges avec des villageois", "trade:any", 10, null, Items.EMERALD);
        q("sell_256", N, "Commerçant", "Vendez 256 objets au marchand (/shop)", "sell:any", 256, null, Items.GOLD_NUGGET);
        q("crate_2", N, "Chasseur de trésors", "Ouvrez 2 caisses au Sanctuaire", "crate:any", 2, null, Items.TRIPWIRE_HOOK);
        q("play_60", F, "Fidèle au poste", "Jouez 60 minutes", "play:minute", 60, null, Items.CLOCK);
        q("play_180", D, "Dévotion", "Jouez 3 heures", "play:minute", 180, null, Items.RECOVERY_COMPASS);
        q("craft_gold", D, "Orfèvre", "Fabriquez 8 blocs d'or", "craft:gold_block", 8, null, Items.GOLD_BLOCK);

        // ---- Défis de la semaine
        w("w_miner", "Grand-œuvre du mineur", "Minez 500 minerais", "mine:ore", 500, Job.MINEUR, Items.DIAMOND_PICKAXE);
        w("w_stone", "Bâtisseur de montagnes", "Minez 3 000 blocs de pierre", "mine:stone", 3000, Job.MINEUR, Items.STONE_BRICKS);
        w("w_wood", "Seigneur des forêts", "Coupez 1 500 bûches", "chop:any", 1500, Job.BUCHERON, Items.DIAMOND_AXE);
        w("w_farm", "Corne d'abondance", "Récoltez 2 000 cultures", "harvest:any", 2000, Job.FERMIER, Items.HAY_BLOCK);
        w("w_breed", "Arche divine", "Faites naître 80 animaux", "breed:any", 80, Job.FERMIER, Items.LEAD);
        w("w_hunt", "Croisade nocturne", "Tuez 500 monstres", "kill:hostile", 500, Job.CHASSEUR, Items.NETHERITE_SWORD);
        w("w_fish", "Léviathan", "Pêchez 250 fois", "fish:any", 250, Job.PECHEUR, Items.HEART_OF_THE_SEA);
        w("w_quests", "Infatigable", "Accomplissez 20 quêtes du jour", "quest:daily", 20, null, Items.WRITABLE_BOOK);
        w("w_sell", "Négociant", "Vendez 3 000 objets au marchand", "sell:any", 3000, null, Items.EMERALD_BLOCK);
        w("w_play", "Pilier du Panthéon", "Jouez 10 heures dans la semaine", "play:minute", 600, null, Items.CLOCK);
        w("w_craft", "Maître artisan", "Fabriquez 1 000 objets", "craft:any", 1000, null, Items.SMITHING_TABLE);
    }

    private QuestSystem() {
    }

    // ------------------------------------------------------------------ Dates

    /** Fuseau des « jours » du serveur (quêtes, récompense quotidienne). */
    public static final java.time.ZoneId ZONE = java.time.ZoneId.of("Europe/Paris");

    public static String today() {
        return LocalDate.now(ZONE).toString();
    }

    /** Semaine ISO, par exemple 2026-W42 (renouvellement le lundi). */
    public static String week() {
        LocalDate d = LocalDate.now(ZONE);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Millisecondes avant minuit (nouvelles quêtes, nouvelle récompense). */
    public static long untilTomorrow() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZONE);
        java.time.ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
        return java.time.Duration.between(now, next).toMillis();
    }

    public static long untilNextWeek() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZONE);
        LocalDate monday = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        return java.time.Duration.between(now, monday.atStartOfDay(ZONE)).toMillis();
    }

    // ------------------------------------------------------------------ Récompenses

    public static Quest find(String id) {
        Quest q = QUESTS.get(id);
        return q != null ? q : WEEKLIES.get(id);
    }

    /** Multiplicateur de palier : +20 % par palier divin. */
    public static double tierMultiplier(PlayerProfile p) {
        return 1.0 + 0.2 * p.tier.ordinal();
    }

    public static long money(PlayerProfile p, Quest q) {
        return Math.round(q.difficulty().money * tierMultiplier(p));
    }

    public static long essence(PlayerProfile p, Quest q) {
        return Math.round(q.difficulty().essence * tierMultiplier(p));
    }

    public static String rewardLine(PlayerProfile p, Quest q) {
        Difficulty d = q.difficulty();
        return "§6" + Msg.money(money(p, q)) + " §7· §d" + essence(p, q) + " Essence"
                + (q.job() != null ? " §7· §a" + Math.round(d.jobXp) + " XP " + q.job().label : "")
                + (d.key != null ? " §7· §bclé " + d.key.label : "");
    }

    // ------------------------------------------------------------------ Propositions du jour

    /** Renouvelle les quêtes proposées si un nouveau jour a commencé ; met aussi à jour les défis de la semaine. */
    public static void refreshOffers(PlayerProfile p) {
        String day = today();
        if (!day.equals(p.questDay)) {
            p.questDay = day;
            p.questRerolls = 0;
            p.questsToday = 0;
            draw(p, 0);
        }
        // Quêtes devenues inconnues (ancienne version) : on les retire.
        p.quests.removeIf(qp -> !QUESTS.containsKey(qp.id));
        p.questOffers.removeIf(id -> !QUESTS.containsKey(id));
        refreshWeekly(p);
    }

    /** Tire 7 propositions : 2 faciles, 3 normales, 1 difficile, 1 épique. */
    private static void draw(PlayerProfile p, int salt) {
        p.questOffers.clear();
        Random r = new Random((p.uuid + p.questDay + "#" + salt).hashCode());
        int[] wanted = {2, 3, 1, 1};
        Difficulty[] order = {Difficulty.FACILE, Difficulty.NORMALE, Difficulty.DIFFICILE, Difficulty.EPIQUE};
        for (int k = 0; k < order.length; k++) {
            List<String> pool = new ArrayList<>();
            for (Quest q : QUESTS.values()) {
                if (q.difficulty() != order[k]) continue;
                boolean taken = false;
                for (QuestProgress qp : p.quests) if (qp.id.equals(q.id())) taken = true;
                if (!taken) pool.add(q.id());
            }
            java.util.Collections.shuffle(pool, r);
            for (int i = 0; i < wanted[k] && i < pool.size(); i++) p.questOffers.add(pool.get(i));
        }
        GodData.dirty();
    }

    /** Prix du prochain renouvellement des propositions (le premier de la journée est gratuit). */
    public static long rerollPrice(PlayerProfile p) {
        if (p.questRerolls == 0) return 0;
        return Math.min(4000, 250L << Math.min(4, p.questRerolls - 1));
    }

    public static String reroll(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        refreshOffers(p);
        long price = rerollPrice(p);
        if (p.money < price) return "Il faut " + Msg.money(price) + " pour renouveler les propositions.";
        p.money -= price;
        p.questRerolls++;
        draw(p, p.questRerolls);
        Msg.ok(player, "Nouvelles quêtes proposées" + (price > 0 ? " (−" + Msg.money(price) + ")." : " (gratuit aujourd'hui)."));
        Fx.sound(player, SoundEvents.BOOK_PAGE_TURN, 1F, 1F);
        return null;
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
        Msg.ok(player, "Quête acceptée : " + q.title() + " (" + q.difficulty().label + ") — " + q.description() + ".");
        Fx.sound(player, SoundEvents.BOOK_PAGE_TURN, 1F, 1.2F);
        return null;
    }

    /** Abandon : la quête retourne dans les propositions du jour (sans son avancement). */
    public static String abandon(ServerPlayer player, String id) {
        PlayerProfile p = GodData.get(player);
        boolean removed = p.quests.removeIf(qp -> qp.id.equals(id));
        if (!removed) return "Quête introuvable.";
        if (QUESTS.containsKey(id) && !p.questOffers.contains(id)) p.questOffers.add(id);
        GodData.dirty();
        Msg.info(player, "Quête abandonnée (elle reste proposée aujourd'hui).");
        return null;
    }

    // ------------------------------------------------------------------ Défis de la semaine

    private static void refreshWeekly(PlayerProfile p) {
        String wk = week();
        if (wk.equals(p.weekId) && !p.weekly.isEmpty()) return;
        p.weekId = wk;
        p.weekly.clear();
        p.weeklyBonus = false;
        List<String> pool = new ArrayList<>(WEEKLIES.keySet());
        java.util.Collections.shuffle(pool, new Random((p.uuid + wk).hashCode()));
        for (int i = 0; i < WEEKLY && i < pool.size(); i++) p.weekly.add(new QuestProgress(pool.get(i)));
        GodData.dirty();
    }

    // ------------------------------------------------------------------ Avancement

    /** Fait avancer les quêtes (du jour et de la semaine) dont l'action correspond à l'une des clés. */
    public static void progress(ServerPlayer player, Collection<String> keys, int amount) {
        if (amount <= 0) return;
        PlayerProfile p = GodData.get(player);
        if (!today().equals(p.questDay) || !week().equals(p.weekId)) refreshOffers(p);
        List<QuestProgress> done = new ArrayList<>();
        for (QuestProgress qp : p.quests) {
            Quest q = QUESTS.get(qp.id);
            if (q == null || !keys.contains(q.action())) continue;
            if (advance(player, qp, q, amount)) done.add(qp);
        }
        for (QuestProgress qp : p.weekly) {
            Quest q = WEEKLIES.get(qp.id);
            if (q == null || qp.done || !keys.contains(q.action())) continue;
            if (advance(player, qp, q, amount)) {
                qp.done = true;
                completeWeekly(player, p, q);
            }
        }
        for (QuestProgress qp : done) {
            p.quests.remove(qp);
            complete(player, p, QUESTS.get(qp.id));
        }
        GodData.dirty();
    }

    /** Renvoie vrai si la quête vient d'être terminée. */
    private static boolean advance(ServerPlayer player, QuestProgress qp, Quest q, int amount) {
        int before = qp.progress;
        qp.progress = Math.min(q.amount(), qp.progress + amount);
        if (qp.progress >= q.amount()) return true;
        // Un message aux paliers de 25 %, et un petit son.
        if (qp.progress * 4 / q.amount() > before * 4 / q.amount()) {
            player.displayClientMessage(Component.literal("✎ " + q.title() + " : ").withStyle(q.difficulty().color)
                    .append(Component.literal(qp.progress + " / " + q.amount() + "  (" + (qp.progress * 100 / q.amount()) + " %)").withStyle(ChatFormatting.WHITE)), true);
            Fx.sound(player, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.4F);
        }
        return false;
    }

    private static void complete(ServerPlayer player, PlayerProfile p, Quest q) {
        if (q == null) return;
        p.questsDone++;
        p.questsToday++;
        long money = money(p, q), essence = essence(p, q);
        p.money += money;
        StringBuilder extra = new StringBuilder();
        if (q.difficulty().key != null) {
            CrateSystem.addKey(p, q.difficulty().key, 1);
            extra.append(" · clé ").append(q.difficulty().key.label);
        }
        if (p.questsDone % 10 == 0) {
            CrateSystem.addKey(p, CrateSystem.Crate.RARE, 1);
            extra.append(" · clé Rare (").append(p.questsDone).append(" quêtes !)");
        }
        GodData.dirty();
        PowerSystem.addEssence(player, essence, "quête");
        Fx.title(player, Component.literal("Quête accomplie !").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(q.difficulty().stars + " " + q.title() + " · +" + Msg.money(money) + " · +" + essence + " Essence").withStyle(ChatFormatting.YELLOW));
        Fx.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        Fx.particles(player, ParticleTypes.TOTEM_OF_UNDYING, 40, 0.8);
        Msg.send(player, Msg.text("Quête « " + q.title() + " » accomplie : +" + Msg.money(money) + ", +" + essence + " Essence" + extra + ". ", ChatFormatting.GREEN)
                .append(Msg.button("Quêtes", "/quetes", "Choisir une nouvelle quête", ChatFormatting.YELLOW)));
        if (q.job() != null) JobSystem.gain(player, q.job(), q.difficulty().jobXp);
        // Bonus d'assiduité de la journée.
        if (p.questsToday == 3) {
            CrateSystem.addKey(p, CrateSystem.Crate.COMMUNE, 1);
            p.money += 500;
            Msg.ok(player, "Bonus d'assiduité : 3 quêtes aujourd'hui ! +500 pièces et une clé Commune.");
        } else if (p.questsToday == 5) {
            CrateSystem.addKey(p, CrateSystem.Crate.RARE, 1);
            p.money += 1500;
            Msg.ok(player, "Bonus d'assiduité : 5 quêtes aujourd'hui ! +1 500 pièces et une clé Rare.");
        }
        progress(player, List.of("quest:daily"), 1);
        PowerSystem.checkPromotion(player, p);
    }

    private static void completeWeekly(ServerPlayer player, PlayerProfile p, Quest q) {
        p.weeklyDone++;
        long money = money(p, q), essence = essence(p, q);
        p.money += money;
        CrateSystem.addKey(p, q.difficulty().key, 1);
        GodData.dirty();
        PowerSystem.addEssence(player, essence, "défi de la semaine");
        Fx.title(player, Component.literal("Défi de la semaine relevé !").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.literal(q.title() + " · +" + Msg.money(money) + " · +" + essence + " Essence · clé " + q.difficulty().key.label).withStyle(ChatFormatting.YELLOW));
        Fx.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 0.8F);
        Msg.broadcast(player.level().getServer(), p.name + " a relevé le défi de la semaine « " + q.title() + " » !", ChatFormatting.AQUA);
        if (q.job() != null) JobSystem.gain(player, q.job(), q.difficulty().jobXp);
        boolean all = true;
        for (QuestProgress qp : p.weekly) all &= qp.done;
        if (all && !p.weeklyBonus) {
            p.weeklyBonus = true;
            CrateSystem.addKey(p, CrateSystem.Crate.EPIQUE, 1);
            Msg.ok(player, "Les 3 défis de la semaine sont relevés : une clé Épique en bonus !");
        }
    }

    /** Quête la plus avancée (affichée dans le tableau latéral), ou null. */
    public static String tracked(PlayerProfile p) {
        QuestProgress best = null;
        double ratio = -1;
        for (QuestProgress qp : p.quests) {
            Quest q = QUESTS.get(qp.id);
            if (q == null) continue;
            double r = qp.progress / (double) q.amount();
            if (r > ratio) {
                ratio = r;
                best = qp;
            }
        }
        if (best == null) return null;
        Quest q = QUESTS.get(best.id);
        return q.title() + " " + best.progress + "/" + q.amount();
    }
}
