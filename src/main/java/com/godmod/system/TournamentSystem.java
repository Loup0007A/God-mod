package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.data.Tier;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tournois en duels 1v1.
 * <ul>
 *   <li>≤ 6 inscrits : une seule poule, tout le monde s'affronte.</li>
 *   <li>Plus : poules de 3 à 5, le premier de chaque poule passe en élimination directe.</li>
 * </ul>
 * Les morts en duel sont annulées (pas de perte d'objets) ; un duel dure au plus 3 minutes.
 */
public final class TournamentSystem {
    public static final long REGISTER_MS = 120_000;
    public static final long MATCH_MS = 180_000;
    public static final long BETWEEN_MS = 6_000;
    public static final int SINGLE_POOL_MAX = 6;

    public enum Phase { INSCRIPTIONS, POULES, ELIMINATION }

    public static final class Match {
        public final String a, b;
        public String winner;

        Match(String a, String b) {
            this.a = a;
            this.b = b;
        }
    }

    public static final class Tournament {
        public Phase phase = Phase.INSCRIPTIONS;
        public long phaseEnd = System.currentTimeMillis() + REGISTER_MS;
        public final List<String> participants = new ArrayList<>();
        public final List<List<String>> pools = new ArrayList<>();
        public final Map<String, Integer> wins = new HashMap<>();
        public final Deque<Match> queue = new ArrayDeque<>();
        public final List<String> bracket = new ArrayList<>();
        public final List<String> nextRound = new ArrayList<>();
        public Match current;
        public long matchEnd;
        public long nextMatchAt;
        /** Si non nul : tournoi pour le trône d'un dieu inactif. */
        public String throneOf;
        public long prize;
        public final Map<String, double[]> returnPos = new HashMap<>();
    }

    private static Tournament current;

    private TournamentSystem() {
    }

    public static Tournament current() {
        return current;
    }

    public static String create(MinecraftServer server, long prize, String throneOf) {
        if (current != null) return "Un tournoi est déjà en cours.";
        current = new Tournament();
        current.prize = prize;
        current.throneOf = throneOf;
        PlayerProfile god = GodData.find(throneOf);
        String title = god != null ? "pour le trône de " + god.name : "divin";
        GodData.log("TOURNOI créé (" + title + ", prix " + prize + ")");
        Msg.broadcast(server, "🏆 Un tournoi " + title + " ouvre ses inscriptions pour 2 minutes ! Prix : "
                + prize + " pièces" + (god != null ? " + le trône et ses fidèles" : "") + ".", ChatFormatting.GOLD);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Msg.send(sp, Msg.button("S'inscrire", "/tournoi rejoindre", "Duels 1v1 en poules", ChatFormatting.GREEN));
        }
        return null;
    }

    /** Un dieu absent depuis longtemps peut être détrôné par un tournoi. */
    public static String claimThrone(ServerPlayer player, PlayerProfile god) {
        Settings s = GodData.settings();
        if (!god.tier.isGod()) return god.name + " n'est pas un dieu.";
        if (PowerSystem.online(player.level().getServer(), god.uuid) != null) return god.name + " est connecté : défiez-le directement.";
        long idle = System.currentTimeMillis() - god.lastSeen;
        long need = s.inactiveGodDays * 24L * 3600 * 1000;
        if (idle < need) return god.name + " n'est absent que depuis " + Msg.time(idle) + " (il faut " + s.inactiveGodDays + " jours).";
        String err = create(player.level().getServer(), 500, god.uuid);
        if (err != null) return err;
        join(player);
        return null;
    }

    public static String join(ServerPlayer player) {
        if (current == null) return "Aucun tournoi en cours.";
        if (current.phase != Phase.INSCRIPTIONS) return "Les inscriptions sont closes.";
        PlayerProfile p = GodData.get(player);
        if (p.uuid.equals(current.throneOf)) return "Vous ne pouvez pas concourir pour votre propre trône.";
        if (current.participants.contains(p.uuid)) return "Vous êtes déjà inscrit.";
        current.participants.add(p.uuid);
        Msg.broadcast(player.level().getServer(), p.name + " s'inscrit au tournoi (" + current.participants.size() + " inscrits).", ChatFormatting.YELLOW);
        return null;
    }

    public static String leave(ServerPlayer player) {
        if (current == null) return "Aucun tournoi en cours.";
        if (current.phase != Phase.INSCRIPTIONS) return "Le tournoi a commencé : vous déclarez forfait à votre prochain duel en restant absent.";
        if (!current.participants.remove(player.getUUID().toString())) return "Vous n'êtes pas inscrit.";
        Msg.ok(player, "Désinscription prise en compte.");
        return null;
    }

    public static String cancel(MinecraftServer server) {
        if (current == null) return "Aucun tournoi en cours.";
        if (current.current != null) endMatchTeleport(server, current.current);
        current = null;
        Msg.broadcast(server, "Le tournoi a été annulé.", ChatFormatting.GRAY);
        return null;
    }

    public static boolean isFighting(String uuid) {
        return current != null && current.current != null && current.current.winner == null
                && (current.current.a.equals(uuid) || current.current.b.equals(uuid));
    }

    // ------------------------------------------------------------------ Déroulement

    public static void tick(MinecraftServer server) {
        if (current == null) return;
        long now = System.currentTimeMillis();
        Tournament t = current;

        if (t.phase == Phase.INSCRIPTIONS) {
            if (now < t.phaseEnd) return;
            if (t.participants.size() < 2) {
                Msg.broadcast(server, "Tournoi annulé : pas assez d'inscrits.", ChatFormatting.GRAY);
                current = null;
                return;
            }
            buildPools(server, t);
            return;
        }

        if (t.current != null) {
            Match m = t.current;
            ServerPlayer pa = PowerSystem.online(server, m.a), pb = PowerSystem.online(server, m.b);
            if (pa == null && pb == null) finishMatch(server, t, m, m.a);
            else if (pa == null) finishMatch(server, t, m, m.b);
            else if (pb == null) finishMatch(server, t, m, m.a);
            else if (now >= t.matchEnd) {
                String w = pa.getHealth() / pa.getMaxHealth() >= pb.getHealth() / pb.getMaxHealth() ? m.a : m.b;
                Msg.broadcast(server, "Temps écoulé : victoire aux points.", ChatFormatting.GRAY);
                finishMatch(server, t, m, w);
            }
            return;
        }

        if (now < t.nextMatchAt) return;
        if (!t.queue.isEmpty()) {
            startMatch(server, t, t.queue.poll());
            return;
        }
        advance(server, t);
    }

    private static void buildPools(MinecraftServer server, Tournament t) {
        List<String> players = new ArrayList<>(t.participants);
        Collections.shuffle(players);
        int poolCount = players.size() <= SINGLE_POOL_MAX ? 1 : (int) Math.ceil(players.size() / 4.0);
        for (int i = 0; i < poolCount; i++) t.pools.add(new ArrayList<>());
        for (int i = 0; i < players.size(); i++) t.pools.get(i % poolCount).add(players.get(i));
        for (List<String> pool : t.pools) {
            for (int i = 0; i < pool.size(); i++)
                for (int j = i + 1; j < pool.size(); j++)
                    t.queue.add(new Match(pool.get(i), pool.get(j)));
        }
        t.phase = Phase.POULES;
        t.nextMatchAt = System.currentTimeMillis() + 3000;
        StringBuilder sb = new StringBuilder("Le tournoi commence ! ");
        for (int i = 0; i < t.pools.size(); i++) {
            sb.append("Poule ").append((char) ('A' + i)).append(" : ");
            List<String> names = new ArrayList<>();
            for (String u : t.pools.get(i)) names.add(name(u));
            sb.append(String.join(", ", names)).append(". ");
        }
        Msg.broadcast(server, sb.toString(), ChatFormatting.GOLD);
    }

    /** Fin d'une phase : poules → élimination → vainqueur. */
    private static void advance(MinecraftServer server, Tournament t) {
        if (t.phase == Phase.POULES) {
            List<String> qualified = new ArrayList<>();
            for (List<String> pool : t.pools) {
                String best = null;
                for (String u : pool) {
                    if (best == null || t.wins.getOrDefault(u, 0) > t.wins.getOrDefault(best, 0)) best = u;
                }
                if (best != null) qualified.add(best);
            }
            if (qualified.size() <= 1) {
                crown(server, t, qualified.isEmpty() ? null : qualified.get(0));
                return;
            }
            t.phase = Phase.ELIMINATION;
            t.bracket.clear();
            t.bracket.addAll(qualified);
            Msg.broadcast(server, "Phase éliminatoire : " + names(qualified), ChatFormatting.GOLD);
            queueRound(t);
            return;
        }
        // ELIMINATION : le tour est fini, les vainqueurs passent.
        t.bracket.clear();
        t.bracket.addAll(t.nextRound);
        t.nextRound.clear();
        if (t.bracket.size() <= 1) {
            crown(server, t, t.bracket.isEmpty() ? null : t.bracket.get(0));
            return;
        }
        queueRound(t);
    }

    private static void queueRound(Tournament t) {
        List<String> b = t.bracket;
        for (int i = 0; i + 1 < b.size(); i += 2) t.queue.add(new Match(b.get(i), b.get(i + 1)));
        if (b.size() % 2 == 1) t.nextRound.add(b.get(b.size() - 1)); // exempté
    }

    private static void startMatch(MinecraftServer server, Tournament t, Match m) {
        ServerPlayer pa = PowerSystem.online(server, m.a), pb = PowerSystem.online(server, m.b);
        if (pa == null || pb == null) {
            String w = pa != null ? m.a : pb != null ? m.b : m.a;
            Msg.broadcast(server, "Forfait : " + name(pa == null ? m.a : m.b) + " est absent.", ChatFormatting.GRAY);
            finishMatch(server, t, m, w);
            return;
        }
        t.current = m;
        t.matchEnd = System.currentTimeMillis() + MATCH_MS;
        ServerLevel arena = server.overworld();
        double[] c = arenaCenter(server);
        save(t, pa);
        save(t, pb);
        prepare(pa);
        prepare(pb);
        Teleport.to(pa, arena, c[0] - 6, c[1], c[2]);
        Teleport.to(pb, arena, c[0] + 6, c[1], c[2]);
        Msg.broadcast(server, "⚔ Duel : " + name(m.a) + " contre " + name(m.b) + " ! (3 min max)", ChatFormatting.YELLOW);
    }

    private static void prepare(ServerPlayer p) {
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.clearFire();
    }

    private static void save(Tournament t, ServerPlayer p) {
        if (p.level() == p.level().getServer().overworld()) {
            t.returnPos.put(p.getUUID().toString(), new double[]{p.getX(), p.getY(), p.getZ()});
        } else {
            t.returnPos.remove(p.getUUID().toString());
        }
    }

    /** Appelé quand un combattant devrait mourir : la mort est annulée et il perd le duel. */
    public static void onDuelDefeat(ServerPlayer loser) {
        Tournament t = current;
        if (t == null || t.current == null) return;
        Match m = t.current;
        String l = loser.getUUID().toString();
        String w = m.a.equals(l) ? m.b : m.a;
        loser.setHealth(loser.getMaxHealth());
        finishMatch(loser.level().getServer(), t, m, w);
    }

    private static void finishMatch(MinecraftServer server, Tournament t, Match m, String winner) {
        m.winner = winner;
        t.wins.merge(winner, 1, Integer::sum);
        if (t.phase == Phase.ELIMINATION) t.nextRound.add(winner);
        Msg.broadcast(server, "Victoire de " + name(winner) + " !", ChatFormatting.GREEN);
        endMatchTeleport(server, m);
        t.current = null;
        t.nextMatchAt = System.currentTimeMillis() + BETWEEN_MS;
    }

    private static void endMatchTeleport(MinecraftServer server, Match m) {
        for (String u : new String[]{m.a, m.b}) {
            ServerPlayer sp = PowerSystem.online(server, u);
            if (sp == null) continue;
            prepare(sp);
            double[] pos = current == null ? null : current.returnPos.get(u);
            if (pos != null) Teleport.to(sp, server.overworld(), pos[0], pos[1], pos[2]);
        }
    }

    private static void crown(MinecraftServer server, Tournament t, String winnerUuid) {
        current = null;
        if (winnerUuid == null) {
            Msg.broadcast(server, "Le tournoi se termine sans vainqueur.", ChatFormatting.GRAY);
            return;
        }
        PlayerProfile w = GodData.find(winnerUuid);
        if (w == null) return;
        w.tournamentsWon++;
        w.money += t.prize;
        w.essence += Math.round(300 * GodData.settings().essenceMultiplier);
        GodData.dirty();
        Msg.broadcast(server, "🏆 " + w.name + " remporte le tournoi et " + t.prize + " pièces !", ChatFormatting.GOLD);
        GodData.log("TOURNOI gagné par " + w.name);
        if (t.throneOf != null) {
            PlayerProfile old = GodData.find(t.throneOf);
            if (old != null) {
                for (PlayerProfile f : GodData.followersOf(old.uuid)) {
                    if (!f.uuid.equals(w.uuid)) f.master = w.uuid;
                }
                w.master = null;
                FaithSystem.dethrone(server, old, w, "détrôné pour inactivité");
                old.master = w.uuid;
                if (!w.tier.isGod()) PowerSystem.setTier(server, w, Tier.DIEU, "gagne le trône et devient");
                Msg.broadcast(server, w.name + " hérite du trône et des fidèles de " + old.name + " !", ChatFormatting.GOLD);
            }
        }
        ServerPlayer sp = PowerSystem.online(server, w.uuid);
        if (sp != null) PowerSystem.checkPromotion(sp, w);
    }

    // ------------------------------------------------------------------ Utilitaires

    public static double[] arenaCenter(MinecraftServer server) {
        Settings s = GodData.settings();
        if (s.arenaSet) return new double[]{s.arenaX, s.arenaY, s.arenaZ};
        BlockPos top = server.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
        return new double[]{0.5, top.getY() + 1, 0.5};
    }

    public static String name(String uuid) {
        PlayerProfile p = GodData.find(uuid);
        return p == null ? "?" : p.name;
    }

    private static String names(List<String> uuids) {
        List<String> n = new ArrayList<>();
        for (String u : uuids) n.add(name(u));
        return String.join(", ", n);
    }

    /** Classement provisoire pour l'affichage. */
    public static Map<String, Integer> standings() {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (current == null) return m;
        for (String u : current.participants) m.put(name(u), current.wins.getOrDefault(u, 0));
        return m;
    }
}
