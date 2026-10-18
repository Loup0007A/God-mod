package com.godmod.data;

import net.minecraft.ChatFormatting;

/** Rang du joueur dans l'équipe du serveur. Les opérateurs (op) sont toujours considérés comme admins. */
public enum Role {
    JOUEUR("Joueur", ChatFormatting.GRAY),
    MODERATEUR("Modérateur", ChatFormatting.AQUA),
    ADMIN("Admin", ChatFormatting.RED);

    public final String label;
    public final ChatFormatting color;

    Role(String label, ChatFormatting color) {
        this.label = label;
        this.color = color;
    }

    public boolean atLeast(Role other) {
        return this.ordinal() >= other.ordinal();
    }

    public static Role parse(String s) {
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(s) || r.label.equalsIgnoreCase(s)) return r;
        }
        if (s.equalsIgnoreCase("modo") || s.equalsIgnoreCase("moderateur")) return MODERATEUR;
        return null;
    }
}
