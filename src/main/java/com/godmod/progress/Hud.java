package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Tier;
import com.godmod.system.PowerSystem;
import com.godmod.system.Ranks;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Affichage permanent de la progression :
 * <ul>
 *   <li>une barre de boss « Palier actuel → palier suivant » qui se remplit avec l'Essence ;</li>
 *   <li>un tableau latéral personnel (argent, Foi, métier, quêtes, clés, fidèles…).</li>
 * </ul>
 * /hud pour l'afficher ou le masquer.
 */
public final class Hud {
    private static final String OBJECTIVE = "godmod_hud";
    private static final Scoreboard SCOREBOARD = new Scoreboard();
    private static final Objective SIDEBAR = SCOREBOARD.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
            Component.literal("✦ PANTHÉON ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<UUID, Integer> SENT_LINES = new HashMap<>();

    private Hud() {
    }

    public static void update(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (!p.hud) {
            hide(player);
            return;
        }
        updateBar(player, p);
        updateSidebar(player, p);
    }

    // ------------------------------------------------------------------ Barre de boss

    private static void updateBar(ServerPlayer player, PlayerProfile p) {
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> {
            ServerBossEvent b = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
            b.addPlayer(player);
            return b;
        });
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        Tier next = p.tier.next();
        float progress;
        MutableComponent name;
        if (next == null) {
            progress = 1F;
            name = Component.literal("✦ " + p.tier.label + " ✦  —  palier maximum atteint").withStyle(p.tier.color, ChatFormatting.BOLD);
        } else {
            long from = PowerSystem.threshold(p.tier), to = PowerSystem.threshold(next);
            progress = (float) Math.max(0, Math.min(1, (p.essence - from) / (double) Math.max(1, to - from)));
            name = Component.literal(p.tier.label).withStyle(p.tier.color, ChatFormatting.BOLD)
                    .append(Component.literal("  →  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(next.label).withStyle(next.color, ChatFormatting.BOLD))
                    .append(Component.literal("   Essence " + p.essence + " / " + to).withStyle(ChatFormatting.LIGHT_PURPLE));
            int need = next.followers;
            if (need > 0) {
                int have = next.countsAllFollowers() ? GodData.totalFollowers(p.uuid) : GodData.followersOf(p.uuid).size();
                name.append(Component.literal("   Fidèles " + have + "/" + need).withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (next.isTranscendent()) {
                boolean ready = com.godmod.system.Transcendence.ready(p, next);
                name.append(Component.literal(ready ? "   ✦ /transcender !" : "   (+ métiers, générateur, offrande)").withStyle(ready ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            }
        }
        bar.setName(name);
        bar.setProgress(progress);
        bar.setColor(switch (p.tier) {
            case MORTEL -> BossEvent.BossBarColor.WHITE;
            case EVEILLE -> BossEvent.BossBarColor.GREEN;
            case DEMI_DIEU -> BossEvent.BossBarColor.BLUE;
            case DIEU -> BossEvent.BossBarColor.YELLOW;
            case DIEU_SUPREME -> BossEvent.BossBarColor.PINK;
            case TITAN -> BossEvent.BossBarColor.RED;
            case PRIMORDIAL -> BossEvent.BossBarColor.BLUE;
            case ASTRAL -> BossEvent.BossBarColor.PURPLE;
            case ETERNEL -> BossEvent.BossBarColor.YELLOW;
            case CREATEUR -> BossEvent.BossBarColor.WHITE;
        });
    }

    // ------------------------------------------------------------------ Tableau latéral

    private static void updateSidebar(ServerPlayer player, PlayerProfile p) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(" "));
        lines.add(kv("Palier", Component.literal(Ranks.tierTag(p.tier)).withStyle(p.tier.color)));
        lines.add(kv("Essence", Component.literal(String.valueOf(p.essence)).withStyle(ChatFormatting.LIGHT_PURPLE)));
        lines.add(kv("Argent", Component.literal(String.format(java.util.Locale.FRANCE, "%,d", p.money) + " ⛃").withStyle(ChatFormatting.GOLD)));
        if (p.tier.atLeast(Tier.EVEILLE)) lines.add(kv("Foi", Component.literal((int) p.faith + " / " + (int) p.maxFaith()).withStyle(ChatFormatting.AQUA)));
        lines.add(Component.literal("  "));
        Job best = JobSystem.best(p);
        lines.add(kv("Métier", Component.literal(best.icon + " " + best.label + " " + JobSystem.level(p, best)
                + " (" + JobSystem.totalLevels(p) + " niv.)").withStyle(best.color)));
        String tracked = QuestSystem.tracked(p);
        lines.add(kv("Quêtes " + p.quests.size() + "/" + QuestSystem.MAX_ACTIVE, Component.literal(tracked == null ? "aucune (/quetes)" : tracked).withStyle(ChatFormatting.YELLOW)));
        String asc = AscensionQuests.hud(p);
        if (asc != null) lines.add(kv("Ascension", Component.literal(asc).withStyle(ChatFormatting.LIGHT_PURPLE)));
        lines.add(kv("Clés", Component.literal(String.valueOf(CrateSystem.totalKeys(p))).withStyle(ChatFormatting.GREEN)));
        if (p.master != null) {
            PlayerProfile m = GodData.find(p.master);
            lines.add(kv("Dieu", Component.literal(m == null ? "?" : m.name).withStyle(ChatFormatting.GOLD)));
        }
        int followers = GodData.followersOf(p.uuid).size();
        if (followers > 0 || p.tier.atLeast(Tier.DEMI_DIEU)) lines.add(kv("Fidèles", Component.literal(String.valueOf(followers)).withStyle(ChatFormatting.YELLOW)));
        EventSystem.Type ev = EventSystem.current();
        if (ev != null) lines.add(kv("Événement", Component.literal(ev.label).withStyle(ev.color)));
        if (DailyReward.available(p)) lines.add(Component.literal("★ /recompense disponible").withStyle(ChatFormatting.GOLD));
        long clear = com.godmod.system.LagControl.secondsLeft();
        if (clear >= 0) lines.add(Component.literal("❄ Nettoyage : ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(Msg.clock(clear * 1000)).withStyle(clear <= 10 ? ChatFormatting.RED : ChatFormatting.WHITE)));
        else lines.add(Component.literal("/dieu · /aide · /event").withStyle(ChatFormatting.DARK_GRAY));

        boolean fresh = !SENT_LINES.containsKey(player.getUUID());
        if (fresh) {
            player.connection.send(new ClientboundSetObjectivePacket(SIDEBAR, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, SIDEBAR));
        }
        int previous = SENT_LINES.getOrDefault(player.getUUID(), 0);
        for (int i = 0; i < lines.size(); i++) {
            player.connection.send(new ClientboundSetScorePacket("l" + i, OBJECTIVE, lines.size() - i,
                    Optional.of(lines.get(i)), Optional.<net.minecraft.network.chat.numbers.NumberFormat>of(BlankFormat.INSTANCE)));
        }
        for (int i = lines.size(); i < previous; i++) player.connection.send(new ClientboundResetScorePacket("l" + i, OBJECTIVE));
        SENT_LINES.put(player.getUUID(), lines.size());
    }

    private static Component kv(String key, Component value) {
        return Component.literal(key + " : ").withStyle(ChatFormatting.GRAY).append(value);
    }

    public static void hide(ServerPlayer player) {
        ServerBossEvent bar = BARS.remove(player.getUUID());
        if (bar != null) bar.removeAllPlayers();
        if (SENT_LINES.remove(player.getUUID()) != null) {
            player.connection.send(new ClientboundSetObjectivePacket(SIDEBAR, ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
    }

    public static void onLogout(ServerPlayer player) {
        ServerBossEvent bar = BARS.remove(player.getUUID());
        if (bar != null) bar.removeAllPlayers();
        SENT_LINES.remove(player.getUUID());
    }
}
