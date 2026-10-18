package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Rôles de l'équipe (modérateur, admin) et sanctions. */
public final class Moderation {
    /** Durée maximale d'un bannissement prononcé par un modérateur (les admins n'ont pas de limite). */
    public static final int MOD_MAX_BAN_HOURS = 72;

    private static final Predicate<CommandSourceStack> OP = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    private static final Map<UUID, double[]> FROZEN = new HashMap<>();

    private Moderation() {
    }

    // ------------------------------------------------------------------ Permissions

    /** Rang effectif : les opérateurs du serveur sont admins. */
    public static Role roleOf(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (p.role == Role.ADMIN) return Role.ADMIN;
        if (OP.test(player.createCommandSourceStack())) return Role.ADMIN;
        return p.role == null ? Role.JOUEUR : p.role;
    }

    /** Condition de commande : la console a tous les droits. */
    public static Predicate<CommandSourceStack> require(Role role) {
        return src -> {
            Entity e = src.getEntity();
            if (!(e instanceof ServerPlayer sp)) return true;
            if (!GodData.ready()) return OP.test(src);
            return roleOf(sp).atLeast(role) || OP.test(src);
        };
    }

    public static Role roleOf(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer sp ? roleOf(sp) : Role.ADMIN;
    }

    public static String setRole(MinecraftServer server, PlayerProfile target, Role role, String by) {
        target.role = role;
        GodData.dirty();
        GodData.log("STAFF : " + by + " donne le rang " + role.label + " à " + target.name);
        ServerPlayer sp = PowerSystem.online(server, target.uuid);
        if (sp != null) {
            server.getCommands().sendCommands(sp);
            Msg.ok(sp, "Votre rang est désormais : " + role.label + ".");
        }
        return target.name + " est désormais " + role.label + ".";
    }

    // ------------------------------------------------------------------ Sanctions

    public static String kick(MinecraftServer server, PlayerProfile target, String reason, String by) {
        ServerPlayer sp = PowerSystem.online(server, target.uuid);
        if (sp == null) return "!" + target.name + " n'est pas connecté.";
        GodData.log("KICK : " + by + " expulse " + target.name + " (" + reason + ")");
        sp.connection.disconnect(Component.literal("Expulsé par " + by + " : " + reason));
        staffAlert(server, by + " a expulsé " + target.name + " (" + reason + ").");
        return target.name + " a été expulsé.";
    }

    public static String mute(MinecraftServer server, PlayerProfile target, int minutes, String reason, String by) {
        target.mutedUntil = System.currentTimeMillis() + minutes * 60_000L;
        target.muteReason = reason;
        GodData.dirty();
        GodData.log("MUTE : " + by + " rend muet " + target.name + " " + minutes + " min (" + reason + ")");
        ServerPlayer sp = PowerSystem.online(server, target.uuid);
        if (sp != null) Msg.err(sp, "Vous êtes muet pendant " + minutes + " min : " + reason);
        staffAlert(server, by + " a rendu muet " + target.name + " pour " + minutes + " min.");
        return target.name + " est muet pour " + minutes + " min.";
    }

    public static String unmute(MinecraftServer server, PlayerProfile target, String by) {
        target.mutedUntil = 0;
        GodData.dirty();
        GodData.log("UNMUTE : " + by + " -> " + target.name);
        ServerPlayer sp = PowerSystem.online(server, target.uuid);
        if (sp != null) Msg.ok(sp, "Vous pouvez de nouveau parler.");
        return target.name + " peut de nouveau parler.";
    }

    /** heures = 0 : définitif. */
    public static String ban(MinecraftServer server, PlayerProfile target, int hours, String reason, String by, Role byRole) {
        if (!byRole.atLeast(Role.ADMIN) && (hours == 0 || hours > MOD_MAX_BAN_HOURS))
            return "!Un modérateur ne peut bannir que " + MOD_MAX_BAN_HOURS + " h maximum. Demandez à un admin.";
        if (target.role != null && target.role.atLeast(Role.MODERATEUR) && !byRole.atLeast(Role.ADMIN))
            return "!Vous ne pouvez pas bannir un membre de l'équipe.";
        target.bannedUntil = hours == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + hours * 3600_000L;
        target.banReason = reason;
        target.bannedBy = by;
        GodData.dirty();
        String duration = hours == 0 ? "définitivement" : "pour " + hours + " h";
        GodData.log("BAN : " + by + " bannit " + target.name + " " + duration + " (" + reason + ")");
        ServerPlayer sp = PowerSystem.online(server, target.uuid);
        if (sp != null) sp.connection.disconnect(banMessage(target));
        staffAlert(server, by + " a banni " + target.name + " " + duration + ".");
        return target.name + " est banni " + duration + ".";
    }

    public static String unban(PlayerProfile target, String by) {
        target.bannedUntil = 0;
        target.banReason = null;
        GodData.dirty();
        GodData.log("UNBAN : " + by + " -> " + target.name);
        return target.name + " n'est plus banni.";
    }

    public static String warn(MinecraftServer server, PlayerProfile target, String reason, String by) {
        target.warnings.add(new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date()) + " " + by + " : " + reason);
        GodData.dirty();
        GodData.log("AVERTISSEMENT : " + by + " -> " + target.name + " (" + reason + ")");
        ServerPlayer sp = PowerSystem.online(server, target.uuid);
        if (sp != null) Msg.err(sp, "⚠ Avertissement de l'équipe : " + reason + " (" + target.warnings.size() + " au total)");
        return target.name + " averti (" + target.warnings.size() + " avertissement(s)).";
    }

    public static String toggleFreeze(MinecraftServer server, PlayerProfile target, String by) {
        ServerPlayer sp = PowerSystem.online(server, target.uuid);
        if (sp == null) return "!" + target.name + " n'est pas connecté.";
        if (FROZEN.remove(sp.getUUID()) != null) {
            Msg.ok(sp, "Vous pouvez de nouveau bouger.");
            return target.name + " est libéré.";
        }
        FROZEN.put(sp.getUUID(), new double[]{sp.getX(), sp.getY(), sp.getZ()});
        Msg.err(sp, "Vous êtes immobilisé par l'équipe de modération. Restez disponible dans le chat.");
        GodData.log("FREEZE : " + by + " immobilise " + target.name);
        return target.name + " est immobilisé.";
    }

    public static boolean isFrozen(ServerPlayer player) {
        return FROZEN.containsKey(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            double[] pos = FROZEN.get(sp.getUUID());
            if (pos == null) continue;
            if (Math.abs(sp.getX() - pos[0]) > 0.3 || Math.abs(sp.getZ() - pos[2]) > 0.3 || Math.abs(sp.getY() - pos[1]) > 1.5) {
                Teleport.to(sp, (ServerLevel) sp.level(), pos[0], pos[1], pos[2]);
            }
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (FROZEN.remove(player.getUUID()) != null) {
            staffAlert(player.level().getServer(), player.getName().getString() + " s'est déconnecté pendant qu'il était immobilisé.");
        }
    }

    // ------------------------------------------------------------------ Contrôles

    public static boolean isBanned(PlayerProfile p) {
        if (p.bannedUntil == 0) return false;
        if (System.currentTimeMillis() >= p.bannedUntil) {
            p.bannedUntil = 0;
            GodData.dirty();
            return false;
        }
        return true;
    }

    public static Component banMessage(PlayerProfile p) {
        String until = p.bannedUntil == Long.MAX_VALUE ? "définitivement"
                : "encore " + Msg.time(p.bannedUntil - System.currentTimeMillis());
        return Component.literal("Vous êtes banni " + until + ".\nRaison : " + (p.banReason == null ? "—" : p.banReason)
                + "\nPar : " + (p.bannedBy == null ? "—" : p.bannedBy));
    }

    public static boolean isMuted(PlayerProfile p) {
        return System.currentTimeMillis() < p.mutedUntil;
    }

    /** Message visible uniquement par les modérateurs et admins connectés (et la console). */
    public static void staffAlert(MinecraftServer server, String text) {
        com.godmod.GodMod.LOGGER.info("[Équipe] " + text);
        Component c = Msg.text("[Équipe] ", ChatFormatting.DARK_AQUA).append(Msg.text(text, ChatFormatting.AQUA));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (roleOf(sp).atLeast(Role.MODERATEUR)) sp.sendSystemMessage(c);
        }
    }

    public static void staffChat(MinecraftServer server, String from, Role role, String message) {
        com.godmod.GodMod.LOGGER.info("[Équipe] " + from + " : " + message);
        Component c = Msg.text("[Équipe] ", ChatFormatting.DARK_AQUA)
                .append(Component.literal(from).withStyle(role.color))
                .append(Msg.text(" : " + message, ChatFormatting.WHITE));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (roleOf(sp).atLeast(Role.MODERATEUR)) sp.sendSystemMessage(c);
        }
    }
}
