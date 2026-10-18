package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.system.AuthSystem;
import com.godmod.system.Hub;
import com.godmod.system.PowerSystem;
import com.godmod.system.RebellionSystem;
import com.godmod.system.TournamentSystem;
import com.godmod.system.Travel;
import com.godmod.system.WorldBuilder;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Événements du serveur, lancés automatiquement à intervalle régulier ou par un admin (/event lancer).
 * Un seul événement à la fois, affiché par une barre en haut de l'écran de tous les joueurs.
 */
public final class EventSystem {
    private static final Random RANDOM = new Random();

    public enum Type {
        XP_METIERS("Bénédiction des artisans", "L'XP de métier est doublée pour tout le monde.", Items.EXPERIENCE_BOTTLE,
                ChatFormatting.GREEN, BossEvent.BossBarColor.GREEN, 15, 3),
        ESSENCE("Éclipse divine", "Toute l'Essence gagnée est doublée.", Items.NETHER_STAR,
                ChatFormatting.LIGHT_PURPLE, BossEvent.BossBarColor.PURPLE, 15, 3),
        RUEE_OR("Ruée vers l'or", "Le marchand rachète tout 50 % plus cher (/shop vendre).", Items.GOLD_INGOT,
                ChatFormatting.GOLD, BossEvent.BossBarColor.YELLOW, 15, 2),
        CONCOURS("Concours de métier", "Gagnez le plus d'XP dans le métier tiré au sort : podium récompensé.", Items.GOLDEN_PICKAXE,
                ChatFormatting.YELLOW, BossEvent.BossBarColor.YELLOW, 10, 3),
        TITAN("Assaut du Titan", "Un Titan déchu envahit l'Arène du Sanctuaire : abattez-le ensemble !", Items.WITHER_SKELETON_SKULL,
                ChatFormatting.RED, BossEvent.BossBarColor.RED, 10, 2),
        TRESOR("Chasse au trésor", "Un coffre divin est caché dans le monde ressource. Le premier qui l'ouvre l'emporte.", Items.CHEST,
                ChatFormatting.AQUA, BossEvent.BossBarColor.BLUE, 15, 2),
        QUIZ("Question des dieux", "3 questions : le premier qui répond juste dans le chat gagne.", Items.WRITABLE_BOOK,
                ChatFormatting.YELLOW, BossEvent.BossBarColor.WHITE, 4, 3),
        FAVEUR("Faveur divine", "Pendant 5 minutes, la foudre bénie frappe des joueurs au hasard et leur offre des clés.", Items.TRIPWIRE_HOOK,
                ChatFormatting.LIGHT_PURPLE, BossEvent.BossBarColor.PINK, 5, 2);

        public final String label;
        public final String description;
        public final Item icon;
        public final ChatFormatting color;
        public final BossEvent.BossBarColor bar;
        public final int minutes;
        /** Poids dans le tirage automatique. */
        public final int weight;

        Type(String label, String description, Item icon, ChatFormatting color, BossEvent.BossBarColor bar, int minutes, int weight) {
            this.label = label;
            this.description = description;
            this.icon = icon;
            this.color = color;
            this.bar = bar;
            this.minutes = minutes;
            this.weight = weight;
        }

        public static Type parse(String s) {
            String n = normalize(s);
            for (Type t : values()) if (normalize(t.name()).equals(n) || normalize(t.label).equals(n)) return t;
            return switch (n) {
                case "xp", "metiers", "artisans" -> XP_METIERS;
                case "eclipse" -> ESSENCE;
                case "or", "ruee" -> RUEE_OR;
                case "boss" -> TITAN;
                case "chasse", "coffre" -> TRESOR;
                case "question", "questions" -> QUIZ;
                case "cles", "pluie" -> FAVEUR;
                default -> null;
            };
        }
    }

    // ------------------------------------------------------------------ État

    private static Type current;
    private static long startedAt, endsAt;
    private static ServerBossEvent bar;

    private static Type upcoming;
    private static long nextAt;
    private static boolean warned;
    private static Type last;

    // Concours
    private static Job contestJob;
    private static final Map<UUID, Double> SCORES = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();

    // Titan
    private static UUID titanId;
    private static double titanMax;
    private static boolean titanSlain;
    private static long nextMinions;
    private static final Set<UUID> EVENT_MOBS = new HashSet<>();

    // Trésor
    private static BlockPos treasure;

    // Quiz
    private static int round;
    private static String question;
    private static List<String> answers = List.of();
    private static boolean roundOpen;
    private static long roundEnds, nextRound;
    private static final int ROUNDS = 3;

    // Faveur
    private static long nextFavour;

    private EventSystem() {
    }

    public static Type current() {
        return current;
    }

    public static long timeLeft() {
        return current == null ? 0 : Math.max(0, endsAt - System.currentTimeMillis());
    }

    public static Type upcoming() {
        return upcoming;
    }

    /** Prochain événement automatique (ms depuis l'époque), 0 si aucun. */
    public static long nextAt() {
        return GodData.settings().eventsAuto ? nextAt : 0;
    }

    // ------------------------------------------------------------------ Multiplicateurs (utilisés par les autres systèmes)

    public static double jobXpMultiplier() {
        return current == Type.XP_METIERS ? 2.0 : 1.0;
    }

    public static double essenceMultiplier() {
        return current == Type.ESSENCE ? 2.0 : 1.0;
    }

    public static double sellMultiplier() {
        return current == Type.RUEE_OR ? 1.5 : 1.0;
    }

    public static boolean isEventMob(Entity e) {
        return EVENT_MOBS.contains(e.getUUID());
    }

    // ------------------------------------------------------------------ Lancement / fin

    public static String start(MinecraftServer server, Type type, String by) {
        if (current != null) return "Un événement est déjà en cours : " + current.label + ". (/event stop pour l'arrêter)";
        String err = switch (type) {
            case CONCOURS -> setupContest();
            case TITAN -> setupTitan(server);
            case TRESOR -> setupTreasure(server);
            case QUIZ -> setupQuiz();
            case FAVEUR -> {
                nextFavour = System.currentTimeMillis() + 5000;
                yield null;
            }
            default -> null;
        };
        if (err != null) return err;
        current = type;
        last = type;
        upcoming = null;
        warned = false;
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + type.minutes * 60_000L;
        bar = new ServerBossEvent(Component.literal(type.label), type.bar, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) bar.addPlayer(sp);

        MutableComponent subtitle = Component.literal(type.description).withStyle(ChatFormatting.WHITE);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Fx.title(sp, Component.literal("✦ " + type.label + " ✦").withStyle(type.color, ChatFormatting.BOLD), subtitle);
            Fx.sound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        }
        Component line = Msg.prefix().append(Msg.text("ÉVÉNEMENT : ", ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Msg.text(type.label, type.color, ChatFormatting.BOLD))
                .append(Msg.text(" — " + type.description + " (" + type.minutes + " min) ", ChatFormatting.WHITE))
                .append(joinable(type) ? Msg.button("Participer", "/event rejoindre", "Y aller", ChatFormatting.GREEN)
                        : Msg.button("Détails", "/event", "Voir l'événement", ChatFormatting.YELLOW));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) sp.sendSystemMessage(line);
        afterStart(server);
        GodData.log("ÉVÉNEMENT : " + type.label + " lancé" + (by == null ? " automatiquement" : " par " + by));
        return null;
    }

    private static boolean joinable(Type t) {
        return t == Type.TITAN || t == Type.TRESOR;
    }

    /** Messages propres à chaque événement juste après le lancement. */
    private static void afterStart(MinecraftServer server) {
        switch (current) {
            case CONCOURS -> Msg.broadcast(server, "Métier du concours : " + contestJob.icon + " " + contestJob.label
                    + ". Les 3 meilleurs gagnent des clés et de l'argent !", contestJob.color);
            case TRESOR -> {
                int hx = Math.round(treasure.getX() / 100F) * 100, hz = Math.round(treasure.getZ() / 100F) * 100;
                Msg.broadcast(server, "Le trésor est caché dans le monde ressource, vers X " + hx + " / Z " + hz
                        + " (à environ 100 blocs près). Une boussole divine vous guide sur place : /mine", ChatFormatting.AQUA);
            }
            case TITAN -> Msg.broadcast(server, "Le Titan déchu a " + Math.round(titanMax) + " points de vie. Toute l'équipe qui le frappe est récompensée selon les dégâts infligés !", ChatFormatting.RED);
            default -> {
            }
        }
    }

    /** Termine l'événement en cours (fin normale, victoire ou arrêt par un admin). */
    public static void end(MinecraftServer server, String reason) {
        if (current == null) return;
        Type t = current;
        try {
            switch (t) {
                case CONCOURS -> finishContest(server);
                case TITAN -> finishTitan(server);
                case TRESOR -> finishTreasure(server);
                default -> {
                }
            }
        } finally {
            current = null;
            if (bar != null) {
                bar.removeAllPlayers();
                bar = null;
            }
            SCORES.clear();
            NAMES.clear();
            Settings s = GodData.settings();
            nextAt = System.currentTimeMillis() + s.eventIntervalMinutes * 60_000L;
        }
        Msg.broadcast(server, "L'événement « " + t.label + " » est terminé" + (reason == null ? "." : " : " + reason), ChatFormatting.GRAY);
        GodData.log("ÉVÉNEMENT : " + t.label + " terminé" + (reason == null ? "" : " (" + reason + ")"));
    }

    // ------------------------------------------------------------------ Boucle

    public static void tick(MinecraftServer server) {
        if (!GodData.ready()) return;
        long now = System.currentTimeMillis();
        if (current != null && server.getTickCount() % 10 == 0 && current == Type.TRESOR) treasureFx(server);
        if (server.getTickCount() % 20 != 0) return;
        if (current != null) {
            if (now >= endsAt) {
                end(server, current == Type.TITAN && !titanSlain ? "le Titan s'est retiré dans les limbes." : null);
                return;
            }
            switch (current) {
                case TITAN -> tickTitan(server, now);
                case QUIZ -> tickQuiz(server, now);
                case FAVEUR -> tickFavour(server, now);
                case TRESOR -> tickTreasure(server);
                default -> {
                }
            }
            if (current != null) updateBar();
            return;
        }
        autoSchedule(server, now);
    }

    private static void autoSchedule(MinecraftServer server, long now) {
        Settings s = GodData.settings();
        if (!s.eventsAuto) return;
        if (nextAt == 0) {
            nextAt = now + s.eventIntervalMinutes * 60_000L;
            return;
        }
        int online = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) if (!AuthSystem.isLocked(sp)) online++;
        if (!warned && now >= nextAt - 120_000) {
            if (online < s.eventMinPlayers) {
                nextAt = now + 5 * 60_000L; // pas assez de monde : on repousse
                return;
            }
            upcoming = pick();
            warned = true;
            Msg.broadcast(server, "Événement dans 2 minutes : " + upcoming.label + " — " + upcoming.description, upcoming.color);
            return;
        }
        if (warned && now >= nextAt) {
            Type t = upcoming == null ? pick() : upcoming;
            String err = start(server, t, null);
            if (err != null) {
                // Événement impossible (arène occupée…) : on en tire un autre la prochaine fois.
                warned = false;
                upcoming = null;
                nextAt = now + 60_000L;
            }
        }
    }

    private static Type pick() {
        int total = 0;
        for (Type t : Type.values()) if (t != last) total += t.weight;
        int r = RANDOM.nextInt(Math.max(1, total));
        for (Type t : Type.values()) {
            if (t == last) continue;
            r -= t.weight;
            if (r < 0) return t;
        }
        return Type.XP_METIERS;
    }

    private static void updateBar() {
        if (bar == null) return;
        long left = timeLeft();
        float progress = (float) left / Math.max(1, endsAt - startedAt);
        MutableComponent name = Component.literal("✦ " + current.label).withStyle(current.color, ChatFormatting.BOLD);
        switch (current) {
            case CONCOURS -> {
                Map.Entry<UUID, Double> top = leader();
                name.append(Component.literal("  " + contestJob.icon + " " + contestJob.label).withStyle(contestJob.color));
                if (top != null) name.append(Component.literal("  1er : " + NAMES.get(top.getKey()) + " (" + Math.round(top.getValue()) + " XP)").withStyle(ChatFormatting.WHITE));
            }
            case TITAN -> {
                Entity e = titan();
                if (e instanceof Mob m) {
                    progress = Math.max(0, Math.min(1, m.getHealth() / (float) titanMax));
                    name.append(Component.literal("  " + Math.round(m.getHealth()) + " / " + Math.round(titanMax) + " PV").withStyle(ChatFormatting.WHITE));
                }
            }
            case QUIZ -> name.append(Component.literal("  Manche " + Math.max(1, round) + "/" + ROUNDS).withStyle(ChatFormatting.WHITE));
            default -> {
            }
        }
        name.append(Component.literal("  " + Msg.clock(left)).withStyle(ChatFormatting.GRAY));
        bar.setName(name);
        bar.setProgress(progress);
    }

    public static void onLogin(ServerPlayer player) {
        if (bar != null) bar.addPlayer(player);
        if (current != null) {
            Msg.send(player, Msg.text("Événement en cours : ", ChatFormatting.GOLD)
                    .append(Msg.text(current.label, current.color, ChatFormatting.BOLD))
                    .append(Msg.text(" — " + current.description + " ", ChatFormatting.WHITE))
                    .append(Msg.button("Détails", "/event", "Voir l'événement", ChatFormatting.YELLOW)));
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (bar != null) bar.removePlayer(player);
    }

    /** Arrêt du serveur : on ne laisse pas de Titan ni de trésor orphelins dans le monde. */
    public static void onServerStopping(MinecraftServer server) {
        if (current != null) end(server, "arrêt du serveur");
        current = null;
        nextAt = 0;
    }

    // ------------------------------------------------------------------ Rejoindre / infos

    public static String join(ServerPlayer player) {
        if (current == null) return "Aucun événement en cours. /event pour voir le prochain.";
        MinecraftServer server = player.level().getServer();
        return switch (current) {
            case TITAN -> Travel.start(player, "Vers l'Arène", () -> {
                if (current != Type.TITAN) return "Le Titan est déjà tombé.";
                double[] c = TournamentSystem.arenaCenter(server);
                double a = RANDOM.nextDouble() * Math.PI * 2;
                Teleport.to(player, Hub.level(server), c[0] + Math.cos(a) * 12, c[1], c[2] + Math.sin(a) * 12);
                Msg.ok(player, "Bienvenue dans l'Arène ! Abattez le Titan. (/spawn pour en sortir)");
                return null;
            });
            case TRESOR -> {
                if (Hub.isResources(player.level())) yield "Vous êtes déjà dans le monde ressource : suivez la boussole divine (barre d'action).";
                yield Hub.toMine(player);
            }
            case CONCOURS -> "Pas besoin de vous déplacer : gagnez de l'XP de " + contestJob.label + " n'importe où (sauf au Sanctuaire).";
            case QUIZ -> "Répondez simplement dans le chat !";
            default -> "Cet événement est actif partout : profitez-en !";
        };
    }

    /** Résumé envoyé par /event. */
    public static void info(ServerPlayer player) {
        player.sendSystemMessage(Msg.header("Événements"));
        if (current != null) {
            player.sendSystemMessage(Msg.text("En cours : ", ChatFormatting.GRAY).append(Msg.text(current.label, current.color, ChatFormatting.BOLD))
                    .append(Msg.text("  (" + Msg.clock(timeLeft()) + " restantes)", ChatFormatting.GRAY)));
            player.sendSystemMessage(Msg.text("  " + current.description, ChatFormatting.WHITE));
            if (current == Type.CONCOURS) {
                List<Map.Entry<UUID, Double>> podium = podium();
                player.sendSystemMessage(Msg.text("  Métier : " + contestJob.icon + " " + contestJob.label
                        + " · votre score : " + Math.round(SCORES.getOrDefault(player.getUUID(), 0.0)) + " XP", contestJob.color));
                for (int i = 0; i < Math.min(3, podium.size()); i++)
                    player.sendSystemMessage(Msg.text("  " + (i + 1) + ". " + NAMES.get(podium.get(i).getKey()) + " — " + Math.round(podium.get(i).getValue()) + " XP", ChatFormatting.YELLOW));
            }
            if (current == Type.QUIZ && roundOpen) player.sendSystemMessage(Msg.text("  Question : " + question, ChatFormatting.YELLOW));
            if (joinable(current)) player.sendSystemMessage(Msg.text("  ", ChatFormatting.GRAY).append(Msg.button("Participer", "/event rejoindre", "Y aller", ChatFormatting.GREEN)));
        } else {
            player.sendSystemMessage(Msg.text("Aucun événement en cours.", ChatFormatting.GRAY));
        }
        Settings s = GodData.settings();
        if (s.eventsAuto && current == null && nextAt > 0) {
            long in = Math.max(0, nextAt - System.currentTimeMillis());
            player.sendSystemMessage(Msg.text("Prochain événement dans " + Msg.time(in) + (upcoming != null ? " : " + upcoming.label : " (tiré au sort)")
                    + " · au moins " + s.eventMinPlayers + " joueur(s) connecté(s)", ChatFormatting.GRAY));
        } else if (!s.eventsAuto) {
            player.sendSystemMessage(Msg.text("Les événements automatiques sont désactivés (les admins peuvent en lancer).", ChatFormatting.GRAY));
        }
        MutableComponent all = Msg.text("Possibles : ", ChatFormatting.DARK_GRAY);
        for (Type t : Type.values()) {
            all.append(Msg.suggest(t.label, "/event", t.description + " (" + t.minutes + " min)", t.color)).append(Msg.text(" · ", ChatFormatting.DARK_GRAY));
        }
        player.sendSystemMessage(all);
    }

    // ------------------------------------------------------------------ Concours de métier

    private static String setupContest() {
        Job[] jobs = Job.values();
        contestJob = jobs[RANDOM.nextInt(jobs.length)];
        SCORES.clear();
        NAMES.clear();
        return null;
    }

    /** Appelé par les métiers à chaque gain d'XP. */
    public static void onJobXp(ServerPlayer player, Job job, double amount) {
        if (current != Type.CONCOURS || job != contestJob || amount <= 0) return;
        SCORES.merge(player.getUUID(), amount, Double::sum);
        NAMES.put(player.getUUID(), player.getName().getString());
    }

    private static Map.Entry<UUID, Double> leader() {
        List<Map.Entry<UUID, Double>> p = podium();
        return p.isEmpty() ? null : p.get(0);
    }

    private static List<Map.Entry<UUID, Double>> podium() {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(SCORES.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    private static void finishContest(MinecraftServer server) {
        List<Map.Entry<UUID, Double>> podium = podium();
        if (podium.isEmpty()) {
            Msg.broadcast(server, "Personne n'a participé au concours de " + contestJob.label + ".", ChatFormatting.GRAY);
            return;
        }
        long[] money = {4000, 2000, 1000};
        long[] essence = {400, 200, 100};
        CrateSystem.Crate[] keys = {CrateSystem.Crate.RARE, CrateSystem.Crate.COMMUNE, CrateSystem.Crate.COMMUNE};
        String[] medals = {"🥇", "🥈", "🥉"};
        Msg.broadcast(server, "Résultats du concours de " + contestJob.label + " :", ChatFormatting.GOLD);
        for (int i = 0; i < podium.size(); i++) {
            Map.Entry<UUID, Double> e = podium.get(i);
            PlayerProfile p = GodData.find(e.getKey().toString());
            if (p == null) continue;
            if (i < 3) {
                reward(server, p, money[i], essence[i], keys[i], 1);
                if (i == 0) p.eventsWon++;
                Msg.broadcast(server, medals[i] + " " + p.name + " — " + Math.round(e.getValue()) + " XP : +" + Msg.money(money[i])
                        + ", +" + essence[i] + " Essence, clé " + keys[i].label, i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            } else {
                reward(server, p, 200, 20, null, 0); // lot de participation
            }
        }
        if (podium.size() > 3) Msg.broadcast(server, "Les autres participants reçoivent 200 pièces. Merci à tous !", ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------ Titan

    private static String setupTitan(MinecraftServer server) {
        if (TournamentSystem.current() != null) return "Un tournoi occupe l'Arène.";
        if (RebellionSystem.anyFighting()) return "Une rébellion occupe l'Arène.";
        ServerLevel l = Hub.level(server);
        double[] c = TournamentSystem.arenaCenter(server);
        var boss = EntityType.WITHER_SKELETON.create(l, EntitySpawnReason.EVENT);
        if (boss == null) return "Impossible de faire apparaître le Titan.";
        int players = Math.max(1, server.getPlayerList().getPlayerCount());
        titanMax = 250 + 150.0 * players;
        boss.snapTo(c[0], c[1] + 0.5, c[2]);
        setAttr(boss, Attributes.MAX_HEALTH, titanMax);
        setAttr(boss, Attributes.ARMOR, 12);
        setAttr(boss, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        setAttr(boss, Attributes.ATTACK_DAMAGE, 11);
        setAttr(boss, Attributes.FOLLOW_RANGE, 48);
        setAttr(boss, Attributes.MOVEMENT_SPEED, 0.3);
        setAttr(boss, Attributes.SCALE, 2.2);
        boss.setHealth((float) titanMax);
        boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        boss.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        for (EquipmentSlot slot : EquipmentSlot.values()) boss.setDropChance(slot, 0F);
        boss.setCustomName(Component.literal("☠ Titan déchu ☠").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        boss.setCustomNameVisible(true);
        boss.setPersistenceRequired();
        boss.setGlowingTag(true);
        EVENT_MOBS.add(boss.getUUID());
        if (!l.addFreshEntity(boss)) {
            EVENT_MOBS.remove(boss.getUUID());
            return "Impossible de faire apparaître le Titan.";
        }
        titanId = boss.getUUID();
        titanSlain = false;
        SCORES.clear();
        NAMES.clear();
        nextMinions = System.currentTimeMillis() + 25_000;
        l.sendParticles(ParticleTypes.LARGE_SMOKE, c[0], c[1] + 2, c[2], 120, 1.5, 2, 1.5, 0.05);
        l.playSound((net.minecraft.world.entity.player.Player) null, c[0], c[1], c[2], SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.8F);
        return null;
    }

    private static void setAttr(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static Entity titan() {
        if (titanId == null || GodData.server() == null) return null;
        return Hub.level(GodData.server()).getEntity(titanId);
    }

    private static void tickTitan(MinecraftServer server, long now) {
        Entity e = titan();
        if (e == null || !e.isAlive()) {
            if (!titanSlain) end(server, "le Titan a disparu.");
            return;
        }
        ServerLevel l = (ServerLevel) e.level();
        double[] c = TournamentSystem.arenaCenter(server);
        double dx = e.getX() - c[0], dz = e.getZ() - c[2];
        if (dx * dx + dz * dz > (WorldBuilder.ARENA_R + 2) * (WorldBuilder.ARENA_R + 2) || e.getY() < c[1] - 4) {
            e.teleportTo(c[0], c[1] + 0.5, c[2]); // il ne quitte pas l'Arène
        }
        if (now >= nextMinions) {
            nextMinions = now + 25_000;
            EVENT_MOBS.removeIf(id -> {
                Entity m = l.getEntity(id);
                return m == null || !m.isAlive();
            });
            if (EVENT_MOBS.size() < 10) {
                for (int i = 0; i < 3; i++) {
                    var minion = (i == 2 ? EntityType.SPIDER : EntityType.HUSK).create(l, EntitySpawnReason.EVENT);
                    if (minion == null) continue;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    minion.snapTo(e.getX() + Math.cos(a) * 4, e.getY(), e.getZ() + Math.sin(a) * 4);
                    minion.setPersistenceRequired();
                    for (EquipmentSlot slot : EquipmentSlot.values()) minion.setDropChance(slot, 0F);
                    EVENT_MOBS.add(minion.getUUID());
                    l.addFreshEntity(minion);
                }
                l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + 1, e.getZ(), 60, 3, 1, 3, 0.02);
                for (ServerPlayer sp : l.players())
                    if (sp.distanceTo(e) < 48) sp.displayClientMessage(Msg.text("Le Titan appelle ses serviteurs !", ChatFormatting.RED), true);
            }
        }
    }

    /** Dégâts infligés à une créature (événement Forge). */
    public static void onDamage(Entity victim, Entity attacker, float amount) {
        if (current != Type.TITAN || titanId == null || !victim.getUUID().equals(titanId)) return;
        if (!(attacker instanceof ServerPlayer sp)) return;
        SCORES.merge(sp.getUUID(), (double) amount, Double::sum);
        NAMES.put(sp.getUUID(), sp.getName().getString());
    }

    /** Mort d'une créature : victoire contre le Titan. */
    public static void onDeath(MinecraftServer server, Entity victim) {
        if (!EVENT_MOBS.remove(victim.getUUID())) return;
        if (current == Type.TITAN && victim.getUUID().equals(titanId)) {
            titanSlain = true;
            ServerLevel l = (ServerLevel) victim.level();
            l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY() + 2, victim.getZ(), 200, 2, 2, 2, 0.5);
            end(server, "le Titan est vaincu !");
        }
    }

    private static void finishTitan(MinecraftServer server) {
        ServerLevel l = Hub.level(server);
        for (UUID id : new ArrayList<>(EVENT_MOBS)) {
            Entity m = l.getEntity(id);
            if (m != null) m.discard();
        }
        EVENT_MOBS.clear();
        titanId = null;
        if (!titanSlain) return;
        double total = 0;
        for (double d : SCORES.values()) total += d;
        if (total <= 0) return;
        List<Map.Entry<UUID, Double>> list = podium();
        Msg.broadcast(server, "Le Titan déchu est terrassé ! Récompenses selon les dégâts infligés :", ChatFormatting.GOLD);
        for (int i = 0; i < list.size(); i++) {
            Map.Entry<UUID, Double> e = list.get(i);
            double share = e.getValue() / total;
            if (share < 0.01) continue;
            PlayerProfile p = GodData.find(e.getKey().toString());
            if (p == null) continue;
            long money = 300 + Math.round(6000 * share);
            long essence = 50 + Math.round(800 * share);
            CrateSystem.Crate key = i == 0 ? CrateSystem.Crate.EPIQUE : i < 3 ? CrateSystem.Crate.RARE : CrateSystem.Crate.COMMUNE;
            reward(server, p, money, essence, key, 1);
            if (i == 0) p.eventsWon++;
            if (i < 5) Msg.broadcast(server, (i + 1) + ". " + p.name + " — " + Math.round(share * 100) + " % des dégâts : +" + Msg.money(money)
                    + ", +" + essence + " Essence, clé " + key.label, i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
        }
    }

    // ------------------------------------------------------------------ Chasse au trésor

    private static String setupTreasure(MinecraftServer server) {
        ServerLevel l = Hub.resources(server);
        if (l == null || !Hub.isResources(l)) return "Le monde ressource n'est pas chargé.";
        for (int attempt = 0; attempt < 12; attempt++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            int dist = 250 + RANDOM.nextInt(500);
            int x = (int) Math.round(Math.cos(a) * dist), z = (int) Math.round(Math.sin(a) * dist);
            l.getChunk(x >> 4, z >> 4); // génère la zone si besoin
            BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (top.getY() <= l.getMinY() + 1 || !l.getFluidState(top.below()).isEmpty() || !l.getFluidState(top).isEmpty()) continue;
            l.setBlockAndUpdate(top, Blocks.CHEST.defaultBlockState());
            PointSystem.set(l, top, "tresor");
            treasure = top;
            return null;
        }
        return "Aucun emplacement sec trouvé pour le trésor, réessayez.";
    }

    private static void tickTreasure(MinecraftServer server) {
        if (treasure == null) return;
        ServerLevel l = Hub.resources(server);
        for (ServerPlayer sp : l.players()) {
            double dx = treasure.getX() + 0.5 - sp.getX(), dz = treasure.getZ() + 0.5 - sp.getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            String dir = direction(dx, dz);
            String txt = dist < 6 ? "✦ Le trésor est juste là !" : "✦ Trésor : " + (dist > 50 ? (dist / 10) * 10 : dist) + " blocs " + dir
                    + (Math.abs(treasure.getY() - sp.getY()) > 8 ? (treasure.getY() > sp.getY() ? " (plus haut)" : " (plus bas)") : "");
            sp.displayClientMessage(Msg.text(txt, ChatFormatting.AQUA), true);
        }
    }

    private static void treasureFx(MinecraftServer server) {
        if (treasure == null) return;
        ServerLevel l = Hub.resources(server);
        if (l == null) return;
        for (int i = 0; i < 6; i++)
            l.sendParticles(ParticleTypes.END_ROD, treasure.getX() + 0.5, treasure.getY() + 1.5 + i * 3, treasure.getZ() + 0.5, 2, 0.1, 1.2, 0.1, 0.01);
    }

    private static String direction(double dx, double dz) {
        // Minecraft : nord = -Z, est = +X.
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String[] dirs = {"au nord", "au nord-est", "à l'est", "au sud-est", "au sud", "au sud-ouest", "à l'ouest", "au nord-ouest"};
        return dirs[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    /** Clic droit sur le coffre au trésor. */
    public static void openTreasure(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.level().getServer();
        if (current != Type.TRESOR || treasure == null || !treasure.equals(pos) || !Hub.isResources(player.level())) {
            PointSystem.remove(player.level(), pos);
            player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            return;
        }
        PlayerProfile p = GodData.get(player);
        reward(server, p, 3000, 300, CrateSystem.Crate.EPIQUE, 1);
        com.godmod.system.Shop.give(player, new ItemStack(Items.DIAMOND, 8));
        p.eventsWon++;
        GodData.dirty();
        ServerLevel l = (ServerLevel) player.level();
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 120, 0.5, 1, 0.5, 0.4);
        Fx.title(player, Component.literal("TRÉSOR TROUVÉ !").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("+3 000 pièces · +300 Essence · clé Épique · 8 diamants").withStyle(ChatFormatting.YELLOW));
        Msg.broadcast(server, player.getName().getString() + " a trouvé le trésor divin !", ChatFormatting.AQUA);
        end(server, "trésor trouvé par " + player.getName().getString() + ".");
    }

    private static void finishTreasure(MinecraftServer server) {
        if (treasure == null) return;
        ServerLevel l = Hub.resources(server);
        if (l != null) {
            PointSystem.remove(l, treasure);
            if (l.getBlockState(treasure).is(Blocks.CHEST)) l.setBlockAndUpdate(treasure, Blocks.AIR.defaultBlockState());
        }
        treasure = null;
    }

    // ------------------------------------------------------------------ Quiz

    private record Question(String text, String... answers) {
    }

    private static final Question[] TRIVIA = {
            new Question("Combien de blocs d'obsidienne faut-il au minimum pour un portail du Nether ?", "10", "dix"),
            new Question("Quel monstre vert explose en sifflant ?", "creeper"),
            new Question("Combien de lingots de fer faut-il pour fabriquer une enclume ?", "31", "trente et un"),
            new Question("Quel animal donne de la laine ?", "mouton", "moutons"),
            new Question("Quel objet permet de planer, trouvé dans les cités de l'End ?", "elytres", "elytra", "elytre"),
            new Question("Quel boss garde les cités antiques des profondeurs ?", "warden"),
            new Question("Combien de paliers divins existe-t-il sur ce serveur ?", "5", "cinq"),
            new Question("Comment s'appelle le spawn du serveur ?", "sanctuaire", "le sanctuaire"),
            new Question("Quel métier gagne de l'XP en coupant des bûches ?", "bucheron"),
            new Question("Combien de quêtes peut-on avoir en même temps ?", "5", "cinq"),
            new Question("Combien de cœurs a un joueur au départ ?", "10", "dix"),
            new Question("Quel boss vit sur l'île principale de l'End ?", "dragon", "ender dragon", "dragon de l'ender", "le dragon"),
            new Question("Quelle commande ouvre le Panthéon ?", "/dieu", "dieu"),
            new Question("Quel minerai faut-il miner avec une pioche en fer au minimum pour obtenir des diamants ?", "diamant", "minerai de diamant"),
            new Question("Combien de secondes dure un jour complet dans Minecraft ? (en minutes)", "20", "vingt"),
            new Question("Quelle plante pousse sur le sable et pique ?", "cactus"),
            new Question("Quel objet faut-il pour dompter un loup ?", "os", "un os"),
            new Question("Quel bloc explose quand on l'allume ?", "tnt"),
            new Question("De quelle couleur est la laine d'un mouton qui a mangé de l'herbe ? (par défaut)", "blanc", "blanche"),
            new Question("Quel métal sert à fabriquer un paratonnerre ?", "cuivre")};

    private static final String[] WORDS = {
            "DIAMANT", "EMERAUDE", "OBSIDIENNE", "CREEPER", "ENDERMAN", "SQUELETTE", "PIOCHE", "LANTERNE", "BIBLIOTHEQUE",
            "ENCLUME", "ALAMBIC", "TRIDENT", "BALISE", "CITROUILLE", "PASTEQUE", "REDSTONE", "NETHERITE", "AMETHYSTE",
            "SANCTUAIRE", "PANTHEON", "ARTEFACT", "BUCHERON", "PECHEUR", "BOUSSOLE", "FOURNEAU", "TONNEAU", "VILLAGEOIS"};

    private static String setupQuiz() {
        round = 0;
        roundOpen = false;
        nextRound = System.currentTimeMillis() + 3000;
        return null;
    }

    private static void newQuestion() {
        int kind = RANDOM.nextInt(3);
        if (kind == 0) {
            int a, b;
            String op;
            int res;
            switch (RANDOM.nextInt(3)) {
                case 0 -> {
                    a = 12 + RANDOM.nextInt(88);
                    b = 12 + RANDOM.nextInt(88);
                    op = "+";
                    res = a + b;
                }
                case 1 -> {
                    a = 50 + RANDOM.nextInt(150);
                    b = 10 + RANDOM.nextInt(a - 10);
                    op = "−";
                    res = a - b;
                }
                default -> {
                    a = 3 + RANDOM.nextInt(17);
                    b = 3 + RANDOM.nextInt(10);
                    op = "×";
                    res = a * b;
                }
            }
            question = "Combien font " + a + " " + op + " " + b + " ?";
            answers = List.of(String.valueOf(res));
        } else if (kind == 1) {
            String w = WORDS[RANDOM.nextInt(WORDS.length)];
            List<Character> chars = new ArrayList<>();
            for (char ch : w.toCharArray()) chars.add(ch);
            String mixed;
            do {
                java.util.Collections.shuffle(chars, RANDOM);
                StringBuilder b = new StringBuilder();
                for (char ch : chars) b.append(ch);
                mixed = b.toString();
            } while (mixed.equals(w));
            question = "Remettez les lettres dans l'ordre : " + mixed;
            answers = List.of(normalize(w));
        } else {
            Question q = TRIVIA[RANDOM.nextInt(TRIVIA.length)];
            question = q.text();
            List<String> list = new ArrayList<>();
            for (String s : q.answers()) list.add(normalize(s));
            answers = list;
        }
    }

    private static void tickQuiz(MinecraftServer server, long now) {
        if (!roundOpen && now >= nextRound) {
            if (round >= ROUNDS) {
                end(server, null);
                return;
            }
            round++;
            newQuestion();
            roundOpen = true;
            roundEnds = now + 45_000;
            Component c = Msg.prefix().append(Msg.text("❓ Question " + round + "/" + ROUNDS + " : ", ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Msg.text(question, ChatFormatting.YELLOW));
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                sp.sendSystemMessage(c);
                Fx.sound(sp, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.5F);
            }
        } else if (roundOpen && now >= roundEnds) {
            roundOpen = false;
            nextRound = now + 6000;
            Msg.broadcast(server, "Personne n'a trouvé ! La réponse était : " + answers.get(0), ChatFormatting.GRAY);
        }
    }

    /** Message de chat (déjà autorisé) : bonne réponse au quiz ? */
    public static void onChat(ServerPlayer player, String raw) {
        if (current != Type.QUIZ || !roundOpen) return;
        String n = normalize(raw);
        if (n.isEmpty() || !answers.contains(n)) return;
        MinecraftServer server = player.level().getServer();
        server.execute(() -> {
            if (current != Type.QUIZ || !roundOpen) return; // quelqu'un a été plus rapide
            roundOpen = false;
            nextRound = System.currentTimeMillis() + 6000;
            PlayerProfile p = GodData.get(player);
            boolean lastRound = round >= ROUNDS;
            reward(server, p, 600, 60, lastRound ? CrateSystem.Crate.COMMUNE : null, 1);
            SCORES.merge(player.getUUID(), 1.0, Double::sum);
            Msg.broadcast(server, player.getName().getString() + " a trouvé la bonne réponse (" + answers.get(0) + ") : +600 pièces, +60 Essence"
                    + (lastRound ? ", clé Commune" : "") + " !", ChatFormatting.GREEN);
            Fx.sound(player, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.6F);
        });
    }

    /** Minuscule, sans accents ni ponctuation : « Élytres ! » → « elytres ». */
    public static String normalize(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^a-z0-9/ ]", "").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ Faveur divine

    private static void tickFavour(MinecraftServer server, long now) {
        if (now < nextFavour) return;
        nextFavour = now + 30_000;
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) if (!AuthSystem.isLocked(sp) && !sp.isSpectator()) pool.add(sp);
        if (pool.isEmpty()) return;
        java.util.Collections.shuffle(pool, RANDOM);
        int n = Math.max(1, pool.size() / 3);
        for (int i = 0; i < n; i++) {
            ServerPlayer sp = pool.get(i);
            PlayerProfile p = GodData.get(sp);
            int r = RANDOM.nextInt(100);
            String gift;
            if (r < 55) {
                CrateSystem.addKey(p, CrateSystem.Crate.COMMUNE, 1);
                gift = "une clé Commune";
            } else if (r < 75) {
                CrateSystem.addKey(p, CrateSystem.Crate.RARE, 1);
                gift = "une clé Rare";
            } else if (r < 80) {
                CrateSystem.addKey(p, CrateSystem.Crate.EPIQUE, 1);
                gift = "une clé Épique !";
            } else {
                p.money += 750;
                gift = "750 pièces";
            }
            GodData.dirty();
            ServerLevel l = (ServerLevel) sp.level();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(sp.getX(), sp.getY(), sp.getZ());
                bolt.setVisualOnly(true);
                l.addFreshEntity(bolt);
            }
            Fx.title(sp, Component.literal("Faveur divine !").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                    Component.literal("Vous recevez " + gift).withStyle(ChatFormatting.YELLOW));
            Msg.broadcast(server, "⚡ La faveur divine frappe " + sp.getName().getString() + " : " + gift, ChatFormatting.LIGHT_PURPLE);
        }
    }

    // ------------------------------------------------------------------ Récompenses

    private static void reward(MinecraftServer server, PlayerProfile p, long money, long essence, CrateSystem.Crate key, int keys) {
        p.money += money;
        if (key != null && keys > 0) CrateSystem.addKey(p, key, keys);
        ServerPlayer sp = PowerSystem.online(server, p.uuid);
        if (sp != null) PowerSystem.addEssence(sp, essence, "événement");
        else p.essence += essence;
        GodData.dirty();
    }

    /** Liste des types (suggestions de commande). */
    public static List<String> typeNames() {
        List<String> l = new ArrayList<>();
        for (Type t : Type.values()) l.add(t.name().toLowerCase());
        return l;
    }

    /** Pour /event liste. */
    public static Map<String, String> catalog() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Type t : Type.values()) m.put(t.name().toLowerCase(), t.label + " (" + t.minutes + " min) — " + t.description);
        return m;
    }
}
