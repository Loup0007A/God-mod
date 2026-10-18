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
    private Ranks() {
    }

    public static String tierTag(Tier t) {
        return switch (t) {
            case MORTEL -> "Mortel";
            case EVEILLE -> "Éveillé";
            case DEMI_DIEU -> "Demi-dieu";
            case DIEU -> "✦ Dieu";
            case DIEU_SUPREME -> "✦✦ Dieu suprême";
        };
    }

    /** Ex. : « [Admin] [✦ Dieu] Pseudo ». */
    public static MutableComponent decorate(ServerPlayer player, Component name) {
        PlayerProfile p = GodData.get(player);
        MutableComponent c = Component.empty();
        Role role = Moderation.roleOf(player);
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
