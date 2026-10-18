package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.data.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Rang affiché devant le pseudo : dans la liste des joueurs (Tab) et dans le chat. */
public final class Ranks {
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

    private Ranks() {
    }

    public static String tierTag(Tier t) {
        return switch (t) {
            case MORTEL -> "Mortel";
            case EVEILLE -> "Éveillé";
            case DEMI_DIEU -> "Demi-dieu";
            case DIEU -> "✦ Dieu";
            case DIEU_SUPREME -> "✦✦ Dieu suprême";
            case TITAN -> "⚡ Titan";
            case PRIMORDIAL -> "✺ Primordial";
            case ASTRAL -> "✧ Divinité astrale";
            case ETERNEL -> "∞ Éternel";
            case CREATEUR -> "☀ Créateur";
        };
    }

    /** Ex. : « [Admin] [✦ Dieu] Pseudo ». */
    public static MutableComponent decorate(ServerPlayer player, Component name) {
        // Sécurité anti-boucle : si le calcul du rang redemande le nom affiché, on renvoie le nom brut.
        if (BUSY.get()) return name.copy();
        BUSY.set(true);
        try {
            return build(player, name);
        } catch (Exception e) {
            com.godmod.GodMod.LOGGER.error("[God Mod] Impossible d'afficher le rang de " + name.getString(), e);
            return name.copy();
        } finally {
            BUSY.set(false);
        }
    }

    private static MutableComponent build(ServerPlayer player, Component name) {
        PlayerProfile p = GodData.get(player);
        MutableComponent c = Component.empty();
        Role role = Moderation.roleOf(player);
        if (AfkSystem.isAfk(player)) c.append(Component.literal("[AFK] ").withStyle(ChatFormatting.DARK_GRAY));
        if (role == Role.ADMIN) c.append(Component.literal("[Admin] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        else if (role == Role.MODERATEUR) c.append(Component.literal("[Modo] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        c.append(Component.literal("[" + tierTag(p.tier) + "] ").withStyle(p.tier.color));
        c.append(name.copy().withStyle(p.tier.isGod() ? p.tier.color : ChatFormatting.WHITE));
        return c;
    }

    /** Met à jour l'affichage d'un joueur (à appeler quand son palier ou son rang change). */
    public static void refresh(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    public static void refresh(MinecraftServer server, String uuid) {
        ServerPlayer sp = PowerSystem.online(server, uuid);
        if (sp != null) refresh(sp);
    }
}
