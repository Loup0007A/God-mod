package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.Role;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Voyages par commande (/spawn, /warp, /mine, /monde, /tpa…) :
 * <ul>
 *   <li>3 secondes d'immobilité avant le départ (annulé si l'on bouge ou si l'on est blessé) ;</li>
 *   <li>impossible en plein combat contre un joueur (12 s après le dernier coup) ou pendant un duel/une rébellion.</li>
 * </ul>
 * Départ immédiat depuis le Sanctuaire (zone sûre) et pour l'équipe du serveur.
 */
public final class Travel {
    public static final long COMBAT_MS = 12_000;

    private static final class Pending {
        final String label;
        final Supplier<String> action;
        final ResourceKey<Level> dim;
        final double x, y, z;
        float health;
        int ticksLeft = warmup() * 20;

        Pending(ServerPlayer p, String label, Supplier<String> action) {
            this.label = label;
            this.action = action;
            this.dim = p.level().dimension();
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
            this.health = p.getHealth();
        }
    }

    /** Délai réglé par les admins (/godadmin teleport <secondes>). */
    public static int warmup() {
        return GodData.ready() ? Math.max(0, GodData.settings().teleportWarmupSeconds) : 0;
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> COMBAT = new HashMap<>();

    private Travel() {
    }

    // ------------------------------------------------------------------ Combat

    /** Un joueur en a frappé un autre : les deux sont « en combat ». */
    public static void tagCombat(ServerPlayer a, ServerPlayer b) {
        long until = System.currentTimeMillis() + COMBAT_MS;
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            COMBAT.put(p.getUUID(), until);
            if (PENDING.remove(p.getUUID()) != null) Msg.err(p, "Téléportation annulée : vous êtes en combat.");
        }
    }

    public static long combatLeft(ServerPlayer p) {
        Long until = COMBAT.get(p.getUUID());
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public static boolean inCombat(ServerPlayer p) {
        return combatLeft(p) > 0;
    }

    // ------------------------------------------------------------------ Départ

    /** Vrai si le joueur part sans attendre. */
    public static boolean instant(ServerPlayer p) {
        if (!GodData.ready() || warmup() <= 0) return true;
        if (p.isCreative() || p.isSpectator()) return true;
        if (Moderation.roleOf(p).atLeast(Role.MODERATEUR)) return true;
        return Hub.isSpawn(p.level());
    }

    /** Raison qui empêche de voyager maintenant, ou null. */
    public static String blocked(ServerPlayer p) {
        String id = p.getUUID().toString();
        if (TournamentSystem.isFighting(id)) return "Impossible de fuir un duel de tournoi.";
        if (RebellionSystem.involving(id) != null) return "Impossible de quitter une rébellion en cours.";
        long c = combatLeft(p);
        if (c > 0 && !Moderation.roleOf(p).atLeast(Role.MODERATEUR))
            return "Vous êtes en combat : téléportation possible dans " + Msg.time(c + 999) + ".";
        return null;
    }

    /**
     * Lance un voyage. L'action réalise la téléportation et renvoie un message d'erreur ou null.
     * Renvoie une erreur immédiate (voyage refusé) ou null.
     */
    public static String start(ServerPlayer p, String label, Supplier<String> action) {
        String why = blocked(p);
        if (why != null) return why;
        if (instant(p)) {
            PENDING.remove(p.getUUID());
            return action.get();
        }
        boolean replaced = PENDING.put(p.getUUID(), new Pending(p, label, action)) != null;
        p.displayClientMessage(Component.literal("✦ " + label + " dans " + warmup() + " s — ne bougez pas").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (!replaced) Msg.info(p, "Téléportation " + label.toLowerCase() + " dans " + warmup() + " secondes. Ne bougez pas.");
        Fx.sound(p, SoundEvents.BEACON_ACTIVATE, 0.5F, 1.8F);
        return null;
    }

    /** Raccourci pour une action qui ne peut pas échouer. */
    public static String startRun(ServerPlayer p, String label, Runnable action) {
        return start(p, label, () -> {
            action.run();
            return null;
        });
    }

    public static boolean pending(ServerPlayer p) {
        return PENDING.containsKey(p.getUUID());
    }

    // ------------------------------------------------------------------ Boucle

    public static void tick(MinecraftServer server) {
        if (!COMBAT.isEmpty() && server.getTickCount() % 200 == 0) {
            long now = System.currentTimeMillis();
            COMBAT.values().removeIf(t -> t < now);
        }
        if (PENDING.isEmpty()) return;
        for (Map.Entry<UUID, Pending> e : new ArrayList<>(PENDING.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Pending pd = e.getValue();
            if (p == null) {
                PENDING.remove(e.getKey());
                continue;
            }
            double dx = p.getX() - pd.x, dz = p.getZ() - pd.z, dy = p.getY() - pd.y;
            if (p.level().dimension() != pd.dim || dx * dx + dz * dz > 0.6 * 0.6 || Math.abs(dy) > 1.2) {
                PENDING.remove(e.getKey());
                p.displayClientMessage(Msg.text("✖ Téléportation annulée : vous avez bougé.", ChatFormatting.RED), true);
                Fx.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.5F);
                continue;
            }
            if (p.getHealth() < pd.health - 0.01F) {
                PENDING.remove(e.getKey());
                p.displayClientMessage(Msg.text("✖ Téléportation annulée : vous avez été blessé.", ChatFormatting.RED), true);
                continue;
            }
            pd.health = Math.max(pd.health, p.getHealth());
            pd.ticksLeft--;
            if (pd.ticksLeft > 0) {
                if (pd.ticksLeft % 20 == 0) {
                    p.displayClientMessage(Component.literal("✦ " + pd.label + " dans " + (pd.ticksLeft / 20) + " s").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    Fx.sound(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.0F + 0.2F * (warmup() - pd.ticksLeft / 20));
                }
                continue;
            }
            PENDING.remove(e.getKey());
            String err = blocked(p);
            if (err == null) {
                try {
                    err = pd.action.get();
                } catch (Exception ex) {
                    com.godmod.GodMod.LOGGER.error("[God Mod] Échec d'une téléportation", ex);
                    err = "La téléportation a échoué.";
                }
            }
            if (err != null && !err.isEmpty()) Msg.err(p, err);
            else Fx.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.2F);
        }
    }

    public static void onLogout(ServerPlayer p) {
        PENDING.remove(p.getUUID());
    }
}
