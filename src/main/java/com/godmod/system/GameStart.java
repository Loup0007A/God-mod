package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.data.Settings;
import com.godmod.util.DivineItems;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/** Accueil d'un joueur une fois son identité vérifiée. */
public final class GameStart {
    private GameStart() {
    }

    public static void afterAuth(ServerPlayer sp, PlayerProfile p) {
        Settings s = GodData.settings();
        if (!p.codexGiven) {
            p.codexGiven = true; // le Codex est placé dans sa case par CodexGuard
            if (s.hubSet) Hub.teleport(sp);
            Msg.send(sp, Msg.text("Bienvenue au Sanctuaire, mortel. Votre ascension commence. Ouvrez le Codex divin (clic droit) ou tapez ", ChatFormatting.GOLD)
                    .append(Msg.button("/dieu", "/dieu", "Ouvrir le Panthéon", ChatFormatting.YELLOW))
                    .append(Msg.text(" — toutes les commandes : ", ChatFormatting.GOLD))
                    .append(Msg.button("/aide", "/aide", "Liste des commandes", ChatFormatting.AQUA)));
            GodData.log("NOUVEAU JOUEUR : " + p.name);
        } else {
            Msg.send(sp, Msg.text("Bon retour, " + p.tier.label + " " + p.name + ". ", ChatFormatting.WHITE)
                    .append(Msg.button("Panthéon", "/dieu", "Ouvrir le menu principal", ChatFormatting.YELLOW))
                    .append(Msg.text(" ", ChatFormatting.WHITE))
                    .append(Msg.button("Aide", "/aide", "Toutes les commandes", ChatFormatting.AQUA)));
        }
        Role role = Moderation.roleOf(sp);
        if (role.atLeast(Role.MODERATEUR)) {
            Msg.send(sp, Msg.text("Rang : ", ChatFormatting.GRAY).append(Msg.text(role.label, role.color))
                    .append(Msg.text(" — /mod pour la modération, /sc pour le chat d'équipe.", ChatFormatting.GRAY)));
        }
        GodData.dirty();
        com.godmod.progress.EventSystem.onLogin(sp);
        com.godmod.util.Scheduler.later(60, () -> {
            if (!sp.hasDisconnected()) com.godmod.progress.DailyReward.remind(sp);
        });
        PowerSystem.applyAttributes(sp);
        Ranks.refresh(sp);
    }
}
