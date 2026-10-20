package com.godmod.command;

import com.godmod.data.Role;
import com.godmod.system.Moderation;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /aide : liste des commandes, rangée par thème et filtrée selon le rang.
 * Chaque commande est cliquable (elle se pré-remplit dans le chat) et son survol affiche l'explication.
 */
public final class Help {
    private Help() {
    }

    private record Cmd(String usage, String description, Role role) {
    }

    private record Category(String id, String label, ChatFormatting color, List<Cmd> commands) {
    }

    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();

    private static void cat(String id, String label, ChatFormatting color, Cmd... cmds) {
        CATEGORIES.put(id, new Category(id, label, color, List.of(cmds)));
    }

    private static Cmd p(String usage, String desc) {
        return new Cmd(usage, desc, Role.JOUEUR);
    }

    private static Cmd m(String usage, String desc) {
        return new Cmd(usage, desc, Role.MODERATEUR);
    }

    private static Cmd a(String usage, String desc) {
        return new Cmd(usage, desc, Role.ADMIN);
    }

    static {
        cat("base", "Essentiel", ChatFormatting.GOLD,
                p("/dieu", "Ouvre le Panthéon : tout le jeu est accessible depuis ce menu"),
                p("/progression", "Le guide : comment gagner de l'Essence et monter de palier"),
                p("/recompense", "Récompense quotidienne (série de 7 jours, de plus en plus généreuse)"),
                p("/event", "Événement en cours et prochain événement"),
                p("/event rejoindre", "Rejoindre l'événement en cours (Titan, chasse au trésor)"),
                p("/hud [on|off]", "Affiche ou masque la barre de progression et le tableau latéral"),
                p("/stats [joueur|serveur]", "Panneau de statistiques (combat, récolte, artisanat, temps) et records"),
                p("/generateur", "Prestige du générateur lave + eau de votre île (I à XX)"),
                p("/afk", "Zone AFK : 1 jeton et 5 pièces par minute, sans être expulsé"),
                p("/afkshop", "Dépenser ses jetons AFK : clés, boosts, générateurs, privilèges"),
                p("/dieu codex", "Récupérer un Codex divin perdu"));
        cat("progression", "Progression", ChatFormatting.GREEN,
                p("/metiers [métier]", "Vos 5 métiers, leurs talents, le barème d'XP et le classement"),
                p("/quetes", "Quêtes du jour (5 en même temps), défis de la semaine, ascension"),
                p("/quetes liste", "Résumé de toutes vos quêtes dans le chat"),
                p("/quetes renouveler", "Nouvelles propositions (gratuit une fois par jour)"),
                p("/quetes semaine", "Les 3 défis de la semaine"),
                p("/transcender", "Les 5 paliers au-delà du Dieu suprême : conditions et offrande"),
                p("/ascension", "Le fil rouge de mortel à Créateur (21 étapes)"),
                p("/cles", "Vos clés de caisse et l'achat de clés"),
                p("/dieu stats [caractéristique]", "Dépenser vos points : force, vitalite, agilite, esprit"),
                p("/dieu profil [joueur]", "Profil détaillé d'un joueur"),
                p("/classement [puissance|argent|metiers|quetes|evenements]", "Les meilleurs joueurs du serveur"));
        cat("divin", "Pouvoirs et fidèles", ChatFormatting.LIGHT_PURPLE,
                p("/pouvoir", "Menu des pouvoirs divins (Foi et temps de recharge)"),
                p("/pouvoir <nom>", "Lancer un pouvoir : benir, ailes, foudre, miracle, aura, meteores, jugement…"),
                p("/pouvoir chatier <fidèle>", "Châtier un de vos fidèles"),
                p("/pouvoir temps <jour|nuit|pluie|orage|clair>", "Maîtrise du temps (Dieu suprême)"),
                p("/pouvoir espace <joueur>", "Se téléporter auprès d'un joueur (Dieu suprême)"),
                p("/dieu specialisation <ocean|ciel|cosmos>", "Choisir votre domaine (palier Dieu)"),
                p("/dieu fideles", "Liste de vos fidèles"),
                p("/dieu liberer <fidèle>", "Libérer un fidèle"),
                p("/dieu choix <trone|refuser>", "Après un déicide : prendre le trône ou refuser"));
        cat("monde", "Mondes et voyages", ChatFormatting.AQUA,
                p("/spawn", "Retour au Sanctuaire"),
                p("/mine", "Monde ressource (endroit aléatoire et sûr)"),
                p("/monde", "Votre monde divin (palier Dieu)"),
                p("/monde visiter <joueur>", "Visiter le monde divin d'un autre joueur"),
                p("/ile", "Permissions de votre monde divin"),
                p("/ile ajouter|retirer <joueur>", "Donner ou retirer tous les droits sur votre île"),
                p("/ile perm <joueur|fideles|visiteurs> <permission> <on|off>", "Permission précise : entrer, casser, poser, interagir, coffres"),
                p("/ile pvp <on|off>", "Combat entre joueurs sur votre île"),
                p("/warp [nom]", "Liste des warps ou s'y rendre"),
                p("/setwarp <nom>", "Créer ou déplacer votre warp"),
                p("/delwarp <nom>", "Supprimer un de vos warps"),
                p("/tpa <joueur>", "Demander à aller auprès d'un joueur"),
                p("/tpahere <joueur>", "Demander à un joueur de venir"),
                p("/tpaccept [joueur]", "Accepter une demande"),
                p("/tpdeny [joueur]", "Refuser une demande"),
                p("/tpatoggle", "Bloquer ou autoriser les demandes de téléportation"));
        cat("economie", "Économie", ChatFormatting.YELLOW,
                p("/argent", "Votre solde"),
                p("/payer <joueur> <montant>", "Donner de l'argent à un joueur"),
                p("/baltop", "Les joueurs les plus riches"),
                p("/shop", "La boutique divine (Nether, End, ressources, artefacts)"),
                p("/shop vendre", "Vendre la pile tenue en main"),
                p("/shop vendre un", "Vendre un seul exemplaire"),
                p("/shop vendre tout", "Vendre tous les exemplaires de cet objet dans l'inventaire"));
        cat("conflits", "Conflits", ChatFormatting.RED,
                p("/rebellion [declarer|rejoindre]", "Se soulever contre son dieu (tous les 3 jours)"),
                p("/tournoi [rejoindre|quitter]", "Tournoi 1v1 en cours"),
                p("/trone reclamer <dieu>", "Lancer un tournoi pour le trône d'un dieu inactif"),
                p("/guerre [declarer <dieu>]", "Guerre entre panthéons (30 min)"));
        cat("privileges", "Privilèges", ChatFormatting.LIGHT_PURPLE,
                p("/privileges", "Acheter des privilèges (aussi dans /shop et /afkshop)"),
                p("/craft", "Établi portable"),
                p("/ec", "Coffre de l'Ender portable"),
                p("/enclume", "Enclume portable"),
                p("/manger", "Se rassasier (toutes les 10 min)"),
                p("/soin", "Se soigner (toutes les 30 min, hors combat)"),
                p("/chapeau", "Porter l'objet en main sur la tête"),
                p("/tete [joueur]", "Tête d'un joueur (toutes les heures)"),
                p("/reparer", "Réparer l'objet en main (toutes les 2 h)"),
                p("/vol", "Voler dans son propre monde divin"));
        cat("compte", "Compte", ChatFormatting.GRAY,
                p("/motdepasse <ancien> <nouveau>", "Changer de mot de passe"),
                p("/signaler <joueur> <raison>", "Prévenir l'équipe d'un problème"));
        cat("moderation", "Modération", ChatFormatting.DARK_AQUA,
                m("/mod info <joueur>", "Fiche complète : rang, IP, sanctions, statistiques"),
                m("/mod invsee <joueur>", "Voir l'inventaire d'un joueur connecté"),
                m("/mod ecsee <joueur>", "Voir le coffre de l'Ender d'un joueur connecté"),
                m("/mod kick <joueur> [raison]", "Expulser"),
                m("/mod mute <joueur> <minutes> [raison]", "Rendre muet (chat et messages privés)"),
                m("/mod unmute <joueur>", "Rendre la parole"),
                m("/mod ban <joueur> <heures> [raison]", "Bannir (72 h max pour un modérateur, 0 = définitif)"),
                m("/mod unban <joueur>", "Lever un bannissement"),
                m("/mod warn <joueur> <raison>", "Avertir"),
                m("/mod freeze <joueur>", "Immobiliser / libérer"),
                m("/mod tp <joueur>", "Se téléporter auprès d'un joueur"),
                m("/mod journal [lignes]", "Journal des actions"),
                m("/sc <message>", "Chat d'équipe"),
                m("/tournoi creer [prix] · /tournoi annuler", "Gérer les tournois"));
        cat("admin", "Administration", ChatFormatting.DARK_RED,
                a("/modo [ajouter|admin|retirer|liste] <joueur>", "Nommer l'équipe (ou le menu Équipe de /dieu)"),
                a("/event lancer <type> · /event stop", "Lancer ou arrêter un événement"),
                a("/event auto <on|off> · /event intervalle <min> · /event joueurs <n>", "Réglages des événements automatiques"),
                a("/cle donner <joueur> <type> [n] · /cle tous <type> [n]", "Donner des clés de caisse"),
                a("/godadmin voir <joueur>", "Tout sur un joueur"),
                a("/godadmin argent|essence|gemmes <joueur> <definir|ajouter|retirer> <valeur>", "Modifier un profil"),
                a("/godadmin palier <joueur> <palier>", "Changer de palier"),
                a("/godadmin teleport <secondes>", "Délai avant les téléportations (0 = immédiat)"),
                a("/godadmin spawn reconstruire|vider|definir|edition", "Gérer le Sanctuaire"),
                a("/godadmin point placer <type> · retirer", "Caisses et pupitres (bloc visé)"),
                a("/godadmin clearile <joueur> · clearzone <rayon> [hauteur]", "Nettoyer une île ou une zone"),
                a("/mine reset [maintenant]", "Régénérer le monde ressource"),
                a("/zone creer commande <nom> <console|joueur> <commande>", "Zone qui lance une commande quand on y entre ({joueur} = pseudo)"),
                a("/zone baton · /zone creer <afk|spawn|libre> <nom> [toutehauteur] · /zone supprimer [nom] · /zone liste", "Tracer des zones AFK, protégées ou libres avec un bâton"),
                a("/donner <objet> [nombre] [joueur]", "Clés de caisse, artefacts, Codex, générateurs, jetons AFK"),
                a("/trailer_lancer [adresse] · /trailer_stop", "Cinématique de 2 min 30 pour filmer un trailer"),
                a("/antibot [noms|ip|vague on|off · autoriser · scan · expulser · purge · bannirip]", "Protection contre les bots (nouveaux comptes)"),
                a("/clearlag · /clearlag intervalle <s> · /clearlag entites <max>", "Nettoyer les objets au sol, régler le délai et la limite de créatures par chunk"),
                a("/auth on|off|session <h>|reset <joueur>", "Mots de passe"),
                a("/pay enable|disable|status", "Boutique premium"),
                a("/godadmin mode serveur|petit", "Équilibrage (gros serveur ou petite survie)"));
    }

    public static List<String> categoryIds() {
        return new ArrayList<>(CATEGORIES.keySet());
    }

    /** /aide : sommaire des thèmes. */
    public static void summary(CommandSourceStack src) {
        summary(src::sendSystemMessage, Moderation.roleOf(src));
    }

    public static void summary(net.minecraft.server.level.ServerPlayer player) {
        summary(player::sendSystemMessage, Moderation.roleOf(player));
    }

    private static void summary(java.util.function.Consumer<Component> out, Role role) {
        out.accept(Msg.header("Aide du Panthéon"));
        out.accept(Msg.text("Presque tout se fait depuis ", ChatFormatting.GRAY)
                .append(Msg.button("/dieu", "/dieu", "Ouvrir le Panthéon", ChatFormatting.YELLOW))
                .append(Msg.text(". Choisissez un thème :", ChatFormatting.GRAY)));
        MutableComponent line = Component.empty();
        int n = 0;
        for (Category c : CATEGORIES.values()) {
            if (!visible(c, role)) continue;
            if (n > 0 && n % 4 == 0) {
                out.accept(line);
                line = Component.empty();
            }
            line.append(Msg.button(c.label(), "/aide " + c.id(), count(c, role) + " commande(s)", c.color())).append(Component.literal(" "));
            n++;
        }
        out.accept(line);
        out.accept(Msg.text("Astuce : cliquez sur une commande pour la préparer dans le chat.", ChatFormatting.DARK_GRAY));
    }

    /** /aide <thème>. Renvoie faux si le thème n'existe pas. */
    public static boolean show(CommandSourceStack src, String id) {
        Category c = CATEGORIES.get(id.toLowerCase());
        if (c == null) {
            for (Category k : CATEGORIES.values()) if (k.label().equalsIgnoreCase(id)) c = k;
        }
        Role role = Moderation.roleOf(src);
        if (c == null || !visible(c, role)) return false;
        src.sendSystemMessage(Msg.header(c.label()));
        for (Cmd cmd : c.commands()) {
            if (!role.atLeast(cmd.role())) continue;
            String suggestion = cmd.usage().split(" [<\\[]")[0].split(" · ")[0].replaceAll("\\|\\S*", "") + " ";
            src.sendSystemMessage(Msg.suggest(cmd.usage(), suggestion, cmd.description() + "\n§7Cliquez pour l'écrire", c.color())
                    .append(Msg.text(" — " + cmd.description(), ChatFormatting.GRAY)));
        }
        src.sendSystemMessage(Msg.text("« ", ChatFormatting.DARK_GRAY).append(Msg.button("Retour au sommaire", "/aide", "Tous les thèmes", ChatFormatting.YELLOW)));
        return true;
    }

    private static boolean visible(Category c, Role role) {
        return count(c, role) > 0;
    }

    private static int count(Category c, Role role) {
        int n = 0;
        for (Cmd cmd : c.commands()) if (role.atLeast(cmd.role())) n++;
        return n;
    }
}
