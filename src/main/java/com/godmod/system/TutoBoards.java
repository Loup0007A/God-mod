package com.godmod.system;

import com.godmod.data.GodData;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tableaux du tutoriel : /tuto tableau &lt;n&gt; affiche la page n sur un panneau de 5 × 4 blocs,
 * plaqué sur le mur visé (centré sur le point regardé). Un nouveau tableau du même numéro remplace l'ancien.
 * Mise en forme des pages : §6 or, §e jaune, §f blanc, §7 gris, §b cyan, §d rose, §a vert, §c rouge, §l gras.
 */
public final class TutoBoards {
    public static final String TAG = "godmod_tuto";

    private TutoBoards() {
    }

    public static final List<String[]> PAGES = List.of(
            new String[]{"§6§l1 · BIENVENUE AU PANTHÉON",
                    "§fIci, chaque joueur commence §emortel§f",
                    "§fet peut devenir un §6dieu§f.",
                    "",
                    "§e/dieu§7 : le menu principal, tout y est",
                    "§e/aide§7 : toutes les commandes",
                    "§e/progression§7 : comment avancer",
                    "",
                    "§fLe §dCodex divin§f (case 9) ouvre",
                    "§faussi le menu d'un clic droit.",
                    "",
                    "§bSuivez les panneaux du tutoriel !"},
            new String[]{"§6§l2 · L'ESSENCE ET LES PALIERS",
                    "§fL'§dEssence§f fait monter de palier :",
                    "",
                    "§7Mortel → §aÉveillé§7 → §bDemi-dieu",
                    "§7→ §6Dieu§7 → §dDieu suprême",
                    "§7puis 5 paliers de §ftranscendance§7.",
                    "",
                    "§fOn la gagne en jouant : métiers,",
                    "§fquêtes, combats, événements…",
                    "",
                    "§fLa barre en haut de l'écran montre",
                    "§fvotre progression en direct."},
            new String[]{"§6§l3 · LES MÉTIERS",
                    "§e⛏ Mineur  §6♣ Bûcheron  §a✿ Fermier",
                    "§c⚔ Chasseur  §b≈ Pêcheur",
                    "",
                    "§fChaque action donne de l'§aXP§f",
                    "§fet de l'§dEssence§f. 50 niveaux,",
                    "§fdes §bclés§f tous les 5 niveaux et",
                    "§fdes §dtalents§f : filon, abattage,",
                    "§fmoisson 3×3, trésors…",
                    "",
                    "§e/metiers§7 : niveaux et talents",
                    "§7Un métier §6en vogue§7 chaque jour (×1,5)"},
            new String[]{"§6§l4 · LES QUÊTES",
                    "§e/quetes§7 : 7 propositions par jour",
                    "§7de ★ facile à ★★★★ épique.",
                    "§f5 quêtes en même temps au maximum.",
                    "",
                    "§fBonus à §e3§f et §e5§f quêtes par jour.",
                    "§bDéfis de la semaine§f : 3 grands",
                    "§fobjectifs renouvelés le lundi.",
                    "",
                    "§d/ascension§7 : le fil rouge, de",
                    "§7mortel jusqu'à Créateur."},
            new String[]{"§6§l5 · CAISSES ET CLÉS",
                    "§fQuatre caisses vous attendent ici :",
                    "§fCommune, §9Rare§f, §5Épique§f, §6Divine§f.",
                    "",
                    "§fClic droit avec une clé : roulette !",
                    "§fSans clé : aperçu des gains.",
                    "",
                    "§fClés : métiers, quêtes, événements,",
                    "§frécompense du jour, boutique AFK.",
                    "",
                    "§e/cles§7 : vos clés et leur achat"},
            new String[]{"§6§l6 · LE MONDE RESSOURCE",
                    "§e/mine§7 : un monde sauvage pour",
                    "§7miner, couper et chasser.",
                    "",
                    "§fVous arrivez §ben parachute§f :",
                    "§faucun dégât de chute jusqu'au sol.",
                    "§7(accroupi : replier le parachute)",
                    "",
                    "§e/spawn§7 : retour au Sanctuaire",
                    "§e/setwarp§7 · §e/warp§7 : vos points de",
                    "§7téléportation publics"},
            new String[]{"§6§l7 · POUVOIRS ET FOI",
                    "§fDès §aÉveillé§f, vous avez de la §bFoi§f",
                    "§fet des §6pouvoirs divins§f :",
                    "§7bénédiction, foudre, miracle,",
                    "§7météores, jugement…",
                    "",
                    "§e/pouvoir§7 : la liste et les délais",
                    "§e/dieu stats§7 : Force, Vitalité,",
                    "§7Agilité et Esprit",
                    "",
                    "§fCodex §7accroupi + clic droit§f : pouvoirs"},
            new String[]{"§6§l8 · FIDÈLES ET CONFLITS",
                    "§fUn §bdemi-dieu§f qui bat un joueur",
                    "§fplus faible en fait son §efidèle§f.",
                    "",
                    "§cUn mortel qui tue un dieu le fait",
                    "§cchuter§f et peut prendre son trône !",
                    "",
                    "§e/rebellion§7 : se soulever contre",
                    "§7son dieu, dans l'Arène",
                    "§e/tournoi§7 · §e/guerre§7 : duels et",
                    "§7batailles entre panthéons"},
            new String[]{"§6§l9 · VOTRE MONDE DIVIN",
                    "§fDevenu §6Dieu§f, vous recevez votre",
                    "§fîle : un seul bloc de terre au début !",
                    "",
                    "§e/monde§7 : y aller",
                    "§e/ile§7 : qui peut entrer, construire…",
                    "",
                    "§fLave + eau = un §6générateur§f.",
                    "§e/generateur§7 : 20 prestiges pour",
                    "§7obtenir fer, diamants, débris…",
                    "",
                    "§fL'île grandit avec vos fidèles."},
            new String[]{"§6§l10 · ÉCONOMIE",
                    "§e/shop§7 : objets du Nether et de",
                    "§7l'End, ressources, artefacts",
                    "§e/shop vendre tout§7 : vendre",
                    "",
                    "§e/payer§7 : donner de l'argent",
                    "§e/baltop§7 : les plus riches",
                    "",
                    "§d/privileges§7 : /craft, /ec,",
                    "§7/enclume, /vol… à vie",
                    "§e/afk§7 : jetons pour §e/afkshop"},
            new String[]{"§6§l11 · ÉVÉNEMENTS ET CADEAUX",
                    "§fToutes les 45 minutes : §bXP ×2§f,",
                    "§fTitan à abattre, chasse au trésor,",
                    "§fconcours, quiz, pluie de clés…",
                    "§e/event§7 : l'événement en cours",
                    "",
                    "§e/recompense§7 : un cadeau par jour,",
                    "§7de plus en plus gros avec la série.",
                    "",
                    "§e/stats§7 : toutes vos statistiques"},
            new String[]{"§6§l12 · À VOUS DE JOUER !",
                    "§fCommencez par :",
                    "§a1.§f /recompense",
                    "§a2.§f /quetes (acceptez-en 5)",
                    "§a3.§f /mine pour récolter",
                    "",
                    "§fRespectez les autres joueurs.",
                    "§e/signaler§7 : prévenir l'équipe",
                    "",
                    "§6§lBonne ascension, mortel !"});

    /** Texte JSON-SNBT d'une page (liste de morceaux colorés). */
    private static String snbt(String[] lines) {
        List<String> parts = new ArrayList<>();
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li] + (li < lines.length - 1 ? "\n" : "");
            String color = "white";
            boolean bold = false;
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '§' && i + 1 < line.length()) {
                    if (cur.length() > 0) {
                        parts.add(part(cur.toString(), color, bold));
                        cur.setLength(0);
                    }
                    char k = line.charAt(++i);
                    if (k == 'l') {
                        bold = true;
                        continue;
                    }
                    bold = false;
                    color = switch (k) {
                        case '6' -> "gold";
                        case 'e' -> "yellow";
                        case '7' -> "gray";
                        case 'b' -> "aqua";
                        case 'd' -> "light_purple";
                        case 'a' -> "green";
                        case 'c' -> "red";
                        case '9' -> "blue";
                        case '5' -> "dark_purple";
                        default -> "white";
                    };
                } else {
                    cur.append(c);
                }
            }
            if (cur.length() > 0) parts.add(part(cur.toString(), color, bold));
        }
        return "[" + String.join(",", parts) + "]";
    }

    private static String part(String text, String color, boolean bold) {
        String t = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{text:\"" + t + "\",color:\"" + color + "\"" + (bold ? ",bold:true" : "") + "}";
    }

    /** Largeur approximative d'un caractère de la police Minecraft, en pixels (avance comprise). */
    private static int charWidth(char c, boolean bold) {
        int w;
        if ("i!.,:;|'".indexOf(c) >= 0) w = 2;
        else if (c == 'l' || c == '`') w = 3;
        else if (c == 't' || c == 'I' || c == ' ' || c == '[' || c == ']' || c == '(' || c == ')' || c == '*') w = 4;
        else if (c == 'f' || c == 'k' || c == '<' || c == '>' || c == '{' || c == '}') w = 5;
        else if (c > 0x2000) w = 9; // symboles (★ ⛏ ✿ ≈ →…)
        else w = 6;
        return w + (bold ? 1 : 0);
    }

    /** Largeur en pixels de la ligne la plus longue d'une page. */
    private static int maxLineWidth(String[] lines) {
        int max = 1;
        for (String line : lines) {
            int w = 0;
            boolean bold = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '§' && i + 1 < line.length()) {
                    char k = line.charAt(++i);
                    bold = k == 'l' || (bold && k == 'l');
                    if (k != 'l') bold = false;
                    continue;
                }
                w += charWidth(c, bold);
            }
            max = Math.max(max, w);
        }
        return max;
    }

    /** Panneau : 5 blocs de large, 4 de haut. Le texte est mis à l'échelle pour remplir l'intérieur. */
    public static final double WIDTH = 5, HEIGHT = 4, MARGIN = 0.3, FRAME = 0.08;

    private static String transform(double tx, double ty, double tz, double sx, double sy, double sz) {
        return String.format(java.util.Locale.ROOT,
                "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[%.4ff,%.4ff,%.4ff],scale:[%.4ff,%.4ff,%.4ff]}",
                tx, ty, tz, sx, sy, sz);
    }

    private static void blockPanel(MinecraftServer server, ServerLevel level, int n, double x, double y, double z, float yaw,
                                   String block, double tx, double ty, double tz, double sx, double sy, double sz) {
        WorldBuilder.run(server, level, String.format(java.util.Locale.ROOT,
                "summon minecraft:block_display %.3f %.3f %.3f {Tags:[\"%s\",\"%s_%d\"],Rotation:[%.1ff,0f],brightness:{sky:15,block:15},"
                        + "block_state:{Name:\"%s\"},%s}",
                x, y, z, TAG, TAG, n, yaw, block, transform(tx, ty, tz, sx, sy, sz)));
    }

    /**
     * Pose le tableau n. {@code here} : devant le joueur, tourné vers lui ; sinon plaqué sur le mur visé,
     * centré sur le point regardé. Renvoie une erreur ou null.
     */
    public static String place(ServerPlayer p, int n, boolean here) {
        if (n < 1 || n > PAGES.size()) return "Il y a " + PAGES.size() + " tableaux (1 à " + PAGES.size() + ").";
        MinecraftServer server = p.level().getServer();
        ServerLevel level = (ServerLevel) p.level();
        double x, cy, z;
        float yaw;
        if (here) {
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            x = p.getX() + flat.x * 3;
            z = p.getZ() + flat.z * 3;
            cy = p.getY() + 2.5;
            yaw = Math.round((p.getYRot() + 180) / 90F) * 90F; // face au joueur
        } else {
            Vec3 eye = p.getEyePosition();
            var hit = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(32)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.BLOCK) return "Regardez le mur (à moins de 32 blocs) où mettre le tableau : visez le centre du rectangle.";
            BlockHitResult bh = (BlockHitResult) hit;
            Direction face = bh.getDirection();
            if (face.getAxis().isVertical()) return "Visez un mur (une face verticale), pas le sol ni le plafond.";
            Vec3 at = bh.getLocation().add(face.getStepX() * 0.06, 0, face.getStepZ() * 0.06);
            x = at.x;
            z = at.z;
            cy = at.y;
            yaw = face.toYRot();
        }
        remove(server, n);
        String[] page = PAGES.get(n - 1);
        String bg = GodData.settings().tutoBackground;

        // 1) Fond plein de 5 × 4 blocs, toujours éclairé, et un fin cadre doré.
        blockPanel(server, level, n, x, cy, z, yaw, bg, -WIDTH / 2, -HEIGHT / 2, -0.04, WIDTH, HEIGHT, 0.02);
        String frame = GodData.settings().tutoFrame;
        if (frame != null && !frame.isEmpty()) {
            double f = FRAME;
            blockPanel(server, level, n, x, cy, z, yaw, frame, -WIDTH / 2 - f, HEIGHT / 2, -0.045, WIDTH + 2 * f, f, 0.03);
            blockPanel(server, level, n, x, cy, z, yaw, frame, -WIDTH / 2 - f, -HEIGHT / 2 - f, -0.045, WIDTH + 2 * f, f, 0.03);
            blockPanel(server, level, n, x, cy, z, yaw, frame, -WIDTH / 2 - f, -HEIGHT / 2, -0.045, f, HEIGHT, 0.03);
            blockPanel(server, level, n, x, cy, z, yaw, frame, WIDTH / 2, -HEIGHT / 2, -0.045, f, HEIGHT, 0.03);
        }

        // 2) Texte : taille calculée pour remplir l'intérieur (sans retour à la ligne automatique).
        double px = 0.025; // 1 pixel de police = 1/40 de bloc à l'échelle 1
        double textW = maxLineWidth(page) * px, textH = page.length * 10 * px;
        double scale = Math.min((WIDTH - 2 * MARGIN) / textW, (HEIGHT - 2 * MARGIN) / textH);
        double h = textH * scale;
        WorldBuilder.run(server, level, String.format(java.util.Locale.ROOT,
                "summon minecraft:text_display %.3f %.3f %.3f {Tags:[\"%s\",\"%s_%d\"],Rotation:[%.1ff,0f],billboard:\"fixed\",alignment:\"center\","
                        + "line_width:2000,background:0,shadow:1b,see_through:0b,brightness:{sky:15,block:15},%s,text:%s}",
                x, cy - h / 2, z, TAG, TAG, n, yaw, transform(0, 0, 0, scale, scale, scale), snbt(page)));
        GodData.log("TUTO : tableau " + n + " posé par " + p.getName().getString());
        return null;
    }

    public static void remove(MinecraftServer server, int n) {
        WorldBuilder.run(server, Hub.level(server), "kill @e[type=minecraft:text_display,tag=" + TAG + "_" + n + "]");
        WorldBuilder.run(server, Hub.level(server), "kill @e[type=minecraft:block_display,tag=" + TAG + "_" + n + "]");
    }

    public static void removeAll(MinecraftServer server) {
        WorldBuilder.run(server, Hub.level(server), "kill @e[type=minecraft:text_display,tag=" + TAG + "]");
        WorldBuilder.run(server, Hub.level(server), "kill @e[type=minecraft:block_display,tag=" + TAG + "]");
    }
}
