package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.util.Msg;
import com.godmod.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Réinitialisation complète du monde ressource (/mine reset).
 * Un monde chargé ne peut pas être effacé à chaud : le reset est programmé, et le dossier du monde
 * ressource est supprimé au démarrage suivant, avant le chargement des mondes. Il est alors régénéré à neuf.
 */
public final class MineReset {
    private static final String FLAG = "godmod_reset_ressources.flag";

    private MineReset() {
    }

    private static Path worldDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    /** Appelé au tout début du démarrage du serveur (avant le chargement des mondes). */
    public static void onServerAboutToStart(MinecraftServer server) {
        Path world = worldDir(server);
        Path flag = world.resolve(FLAG);
        if (!Files.exists(flag)) return;
        Path dim = world.resolve("dimensions").resolve(GodMod.MODID).resolve("ressources");
        try {
            if (Files.exists(dim)) {
                try (Stream<Path> walk = Files.walk(dim)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            GodMod.LOGGER.warn("[God Mod] Impossible de supprimer {}", p);
                        }
                    });
                }
            }
            Files.deleteIfExists(flag);
            GodMod.LOGGER.info("[God Mod] Monde ressource réinitialisé : il sera régénéré à neuf.");
        } catch (IOException e) {
            GodMod.LOGGER.error("[God Mod] Échec de la réinitialisation du monde ressource", e);
        }
    }

    /** Programme le reset. Si {@code now}, le serveur s'arrête 15 s plus tard pour l'appliquer. */
    public static String schedule(MinecraftServer server, String by, boolean now) {
        try {
            Files.writeString(worldDir(server).resolve(FLAG), "reset demandé par " + by);
        } catch (IOException e) {
            return "!Impossible d'écrire le fichier de reset : " + e.getMessage();
        }
        // Les joueurs présents dans le monde ressource rentrent au Sanctuaire.
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (Hub.isResources(sp.level())) {
                Hub.teleport(sp);
                Msg.info(sp, "Le monde ressource va être réinitialisé : retour au Sanctuaire.");
            }
        }
        // Les warps et générateurs de ce monde disparaîtront avec lui.
        String dim = Hub.RESOURCES.identifier().toString();
        GodData.state().warps.values().removeIf(w -> dim.equals(w.dimension));
        GodData.state().spawners.keySet().removeIf(k -> k.startsWith(dim + "|"));
        GodData.dirty();
        GodData.log("MINE : reset programmé par " + by + (now ? " (redémarrage immédiat)" : ""));
        if (now) {
            Msg.broadcast(server, "⚠ Le serveur redémarre dans 15 secondes pour régénérer le monde ressource.", ChatFormatting.RED);
            Scheduler.later(200, () -> Msg.broadcast(server, "⚠ Redémarrage dans 5 secondes…", ChatFormatting.RED));
            Scheduler.later(300, () -> server.halt(false));
            return "Reset programmé : arrêt du serveur dans 15 s (relancez-le depuis le panel s'il ne redémarre pas seul).";
        }
        Msg.broadcast(server, "Le monde ressource sera entièrement régénéré au prochain redémarrage du serveur.", ChatFormatting.GOLD);
        return "Reset programmé : le monde ressource sera régénéré au prochain redémarrage. (/mine reset maintenant pour redémarrer tout de suite)";
    }

    public static boolean pending(MinecraftServer server) {
        return Files.exists(worldDir(server).resolve(FLAG));
    }
}
