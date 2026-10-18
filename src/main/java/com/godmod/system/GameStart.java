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
                    .append(Msg.button("/dieu", "/dieu", "Ouvrir le Panthéon", ChatFormatting.YELLOW)));
            GodData.log("NOUVEAU JOUEUR : " + p.name);
        } else {
            Msg.info(sp, "Bon retour, " + p.tier.label + " " + p.name + ". Tapez /dieu pour ouvrir le Panthéon.");
        }
        Role role = Moderation.roleOf(sp);
        if (role.atLeast(Role.MODERATEUR)) {
            Msg.send(sp, Msg.text("Rang : ", ChatFormatting.GRAY).append(Msg.text(role.label, role.color))
                    .append(Msg.text(" — /mod pour la modération, /sc pour le chat d'équipe.", ChatFormatting.GRAY)));
        }
        GodData.dirty();
        PowerSystem.applyAttributes(sp);
        Ranks.refresh(sp);
    }
}
