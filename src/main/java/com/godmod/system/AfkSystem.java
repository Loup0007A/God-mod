package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.progress.QuestSystem;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AFK :
 * <ul>
 *   <li>/afk envoie dans la <b>zone AFK</b> du Sanctuaire : 1 jeton AFK et 5 pièces par minute (8 h par jour au plus),
 *       à dépenser dans /afkshop ;</li>
 *   <li>un joueur immobile 5 minutes est marqué [AFK] dans la liste des joueurs ;</li>
 *   <li>dans la zone AFK, le joueur n'est jamais expulsé pour inactivité : il reste connecté et compte comme
 *       joueur en ligne, ce qui garde le serveur allumé chez les hébergeurs qui l'éteignent quand il est vide.</li>
 * </ul>
 */
public final class AfkSystem {
    public static final int ZONE_X = -220, ZONE_Z = 0, ZONE_R = 8;
    public static final long IDLE_MS = 5 * 60_000L;
    public static final int DAILY_CAP = 480;

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTIVE = new HashMap<>();
    private static final Set<UUID> AFK = new HashSet<>();
    private static final Set<UUID> IN_ZONE = new HashSet<>();

    private AfkSystem() {
    }

    public static boolean isAfk(ServerPlayer p) {
        return AFK.contains(p.getUUID()) || inZone(p);
    }

    public static boolean inZone(ServerPlayer p) {
        if (!Hub.isSpawn(p.level())) return false;
        double dx = p.getX() - (ZONE_X + 0.5), dz = p.getZ() - (ZONE_Z + 0.5);
        return dx * dx + dz * dz <= (ZONE_R + 1) * (ZONE_R + 1) && Math.abs(p.getY() - (WorldBuilder.CY + 1)) < 6;
    }

    // ------------------------------------------------------------------ Zone

    /** Construit la zone AFK (une seule fois) : plateforme ronde vitrée à l'écart du Sanctuaire. */
    public static void ensureBuilt(MinecraftServer server) {
        if (GodData.settings().afkZoneBuilt) return;
        ServerLevel l = Hub.level(server);
        if (l == null || !Hub.isSpawn(l)) return;
        int cy = WorldBuilder.CY;
        for (int dx = -ZONE_R - 1; dx <= ZONE_R + 1; dx++) {
            for (int dz = -ZONE_R - 1; dz <= ZONE_R + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = ZONE_X + dx, z = ZONE_Z + dz;
                if (d <= ZONE_R + 0.5) {
                    Block floor = d < 1.5 ? Blocks.SEA_LANTERN : Math.abs(d - 5) < 0.5 ? Blocks.AMETHYST_BLOCK
                            : (Math.floorMod(dx + dz, 2) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES);
                    l.setBlock(new BlockPos(x, cy, z), floor.defaultBlockState(), 2);
                    l.setBlock(new BlockPos(x, cy - 1, z), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
                    for (int y = cy + 1; y <= cy + 5; y++) l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    l.setBlock(new BlockPos(x, cy + 6, z), (d < ZONE_R - 0.5 ? Blocks.PURPLE_STAINED_GLASS : Blocks.AMETHYST_BLOCK).defaultBlockState(), 2);
                } else if (d <= ZONE_R + 1.5) {
                    for (int y = cy; y <= cy + 6; y++)
                        l.setBlock(new BlockPos(x, y, z), (y == cy || y == cy + 6 ? Blocks.AMETHYST_BLOCK : Blocks.PURPLE_STAINED_GLASS).defaultBlockState(), 2);
                }
            }
        }
        WorldBuilder.label(server, l, ZONE_X + 0.5, cy + 4.2, ZONE_Z + 0.5, "☾ Zone AFK ☾", "light_purple", 1.4);
        WorldBuilder.label(server, l, ZONE_X + 0.5, cy + 3.6, ZONE_Z + 0.5, "1 jeton par minute · /afkshop", "gray", 0.8);
        WorldBuilder.label(server, l, ZONE_X + 0.5, cy + 3.2, ZONE_Z + 0.5, "/afk ou /spawn pour sortir", "gray", 0.7);
        GodData.settings().afkZoneBuilt = true;
        GodData.dirty();
    }

    /** /afk : entrer ou sortir de la zone AFK. */
    public static String toggle(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (inZone(player)) {
            Hub.teleport(player);
            Msg.ok(player, "Vous quittez la zone AFK. Jetons : " + GodData.get(player).afkTokens + " (/afkshop).");
            return null;
        }
        ensureBuilt(server);
        return Travel.startRun(player, "Vers la zone AFK", () -> {
            Teleport.to(player, Hub.level(server), ZONE_X + 0.5, WorldBuilder.CY + 1, ZONE_Z + 3.5);
            player.setYRot(180F);
            markActive(player);
            Msg.send(player, Msg.text("Zone AFK : vous gagnez 1 jeton AFK et 5 pièces par minute (8 h par jour au plus). ", ChatFormatting.LIGHT_PURPLE)
                    .append(Msg.button("Boutique AFK", "/afkshop", "Dépenser vos jetons", ChatFormatting.YELLOW)));
            Ranks.refresh(player);
        });
    }

    // ------------------------------------------------------------------ Boucle (chaque seconde)

    private static void markActive(ServerPlayer p) {
        LAST_ACTIVE.put(p.getUUID(), System.currentTimeMillis());
        if (AFK.remove(p.getUUID())) {
            Ranks.refresh(p);
            Msg.info(p, "Vous n'êtes plus AFK.");
        }
    }

    public static void tickSecond(MinecraftServer server, boolean minute) {
        long now = System.currentTimeMillis();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (AuthSystem.isLocked(sp)) continue;
            UUID id = sp.getUUID();
            double[] cur = {sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot()};
            double[] last = LAST_POS.put(id, cur);
            boolean moved = last == null || Math.abs(cur[0] - last[0]) > 0.1 || Math.abs(cur[2] - last[2]) > 0.1
                    || Math.abs(cur[3] - last[3]) > 1 || Math.abs(cur[4] - last[4]) > 1;
            if (moved && !inZone(sp)) markActive(sp);
            LAST_ACTIVE.putIfAbsent(id, now);
            if (!AFK.contains(id) && !inZone(sp) && now - LAST_ACTIVE.get(id) > IDLE_MS) {
                AFK.add(id);
                Ranks.refresh(sp);
            }
            boolean zone = inZone(sp);
            if (zone ? IN_ZONE.add(id) : IN_ZONE.remove(id)) Ranks.refresh(sp); // [AFK] dans la liste des joueurs
            if (zone) {
                sp.resetLastActionTime(); // jamais expulsé pour inactivité dans la zone AFK
                if (minute) reward(sp);
            }
        }
    }

    private static void reward(ServerPlayer sp) {
        PlayerProfile p = GodData.get(sp);
        String today = QuestSystem.today();
        if (!today.equals(p.afkDay)) {
            p.afkDay = today;
            p.afkTokensToday = 0;
        }
        p.afkMinutes++;
        if (p.afkTokensToday >= DAILY_CAP) {
            sp.displayClientMessage(Msg.text("Zone AFK : limite de 8 h atteinte aujourd'hui (" + p.afkTokens + " jetons).", ChatFormatting.GRAY), true);
            return;
        }
        p.afkTokensToday++;
        p.afkTokens++;
        p.money += 5;
        GodData.dirty();
        sp.displayClientMessage(Component.literal("☾ Zone AFK : +1 jeton (" + p.afkTokens + ") · +5 pièces · " + p.afkTokensToday + "/" + DAILY_CAP + " aujourd'hui")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    public static void onLogout(ServerPlayer p) {
        LAST_POS.remove(p.getUUID());
        LAST_ACTIVE.remove(p.getUUID());
        AFK.remove(p.getUUID());
        IN_ZONE.remove(p.getUUID());
    }

    /** Un message de chat ou une commande réveille aussi le joueur. */
    public static void onActivity(ServerPlayer p) {
        if (!inZone(p)) markActive(p);
    }
}
