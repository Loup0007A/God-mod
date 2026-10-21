package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chat du Panthéon :
 * <ul>
 *   <li><b>canaux</b> : Global, Local (100 blocs), Commerce, Groupe ; on écrit dans son canal courant
 *       (/canal, /g, /local, /commerce, /p) ; « ! » en début de message = Global ;</li>
 *   <li><b>onglets</b> (/chat) : le chat n'affiche plus qu'un canal ; l'écran est effacé puis l'historique du canal
 *       est réaffiché ; les messages des autres canaux sont comptés (« Global (3) ») ;</li>
 *   <li><b>messages privés</b> (/mp, /r), joueurs ignorés (/ignorer), espion pour l'équipe (/espion) ;</li>
 *   <li><b>groupes</b> (/groupe) de 8 joueurs avec leur canal ;</li>
 *   <li>mise en forme : rang et palier, fiche du joueur au survol (clic = message privé), mentions qui sonnent,
 *       [item] pour montrer l'objet en main, liens cliquables, couleurs &amp; pour les admins, anti-répétition.</li>
 * </ul>
 */
public final class ChatSystem {

    public enum Channel {
        GLOBAL("Global", "G", ChatFormatting.AQUA),
        LOCAL("Local", "L", ChatFormatting.YELLOW),
        COMMERCE("Commerce", "$", ChatFormatting.GOLD),
        GROUPE("Groupe", "P", ChatFormatting.GREEN),
        MP("Privé", "✉", ChatFormatting.LIGHT_PURPLE),
        SYSTEME("Serveur", "✦", ChatFormatting.GRAY);

        public final String label, tag;
        public final ChatFormatting color;

        Channel(String label, String tag, ChatFormatting color) {
            this.label = label;
            this.tag = tag;
            this.color = color;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Channel parse(String s) {
            String k = s.toLowerCase(Locale.ROOT);
            return switch (k) {
                case "global", "g", "general", "général" -> GLOBAL;
                case "local", "l", "proche" -> LOCAL;
                case "commerce", "c", "$", "marche", "marché" -> COMMERCE;
                case "groupe", "p", "party", "groupe_prive" -> GROUPE;
                case "mp", "prive", "privé", "messages" -> MP;
                default -> null;
            };
        }

        /** Canaux dans lesquels on peut écrire. */
        public boolean writable() {
            return this == GLOBAL || this == LOCAL || this == COMMERCE || this == GROUPE;
        }
    }

    /** Onglets : TOUT (null) ou un canal. */
    public static final List<Channel> TABS = List.of(Channel.GLOBAL, Channel.LOCAL, Channel.COMMERCE, Channel.GROUPE, Channel.MP);

    public static final int LOCAL_RADIUS = 100;
    private static final int HISTORY = 120;
    private static final int REPLAY = 60;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private record Entry(Channel channel, Component line) {
    }

    private static final class State {
        final Deque<Entry> history = new ArrayDeque<>();
        Channel tab; // null = Tout
        final Map<Channel, Integer> unread = new EnumMap<>(Channel.class);
        UUID replyTo;
        long lastMessage;
        String lastText = "";
        boolean replaying;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private ChatSystem() {
    }

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static Channel speaking(PlayerProfile p) {
        Channel c = p.chatChannel == null ? null : Channel.parse(p.chatChannel);
        return c == null || !c.writable() ? Channel.GLOBAL : c;
    }

    // ------------------------------------------------------------------ Groupes

    public static final int GROUP_MAX = 8;

    private static final class Group {
        UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
        final Map<UUID, Long> invites = new HashMap<>();
    }

    private static final Map<UUID, Group> GROUP_OF = new HashMap<>();

    private static Group group(ServerPlayer p) {
        return GROUP_OF.get(p.getUUID());
    }

    private static List<ServerPlayer> online(MinecraftServer server, Group g) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : g.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            if (sp != null) out.add(sp);
        }
        return out;
    }

    private static void groupNotice(MinecraftServer server, Group g, String text) {
        Component c = badge(Channel.GROUPE).append(Msg.text(text, ChatFormatting.GREEN));
        for (ServerPlayer sp : online(server, g)) deliver(sp, Channel.GROUPE, c, false, null);
    }

    public static String groupCreate(ServerPlayer p) {
        if (group(p) != null) return "Vous êtes déjà dans un groupe (/groupe quitter).";
        Group g = new Group();
        g.leader = p.getUUID();
        g.members.add(p.getUUID());
        GROUP_OF.put(p.getUUID(), g);
        Msg.ok(p, "Groupe créé. Invitez avec /groupe inviter <joueur>, parlez-y avec /p <message>.");
        return null;
    }

    public static String groupInvite(ServerPlayer p, ServerPlayer t) {
        if (t == null) return "Joueur introuvable ou hors ligne.";
        if (t == p) return "Vous êtes déjà avec vous-même.";
        Group g = group(p);
        if (g == null) {
            groupCreate(p);
            g = group(p);
        }
        if (!g.leader.equals(p.getUUID())) return "Seul le chef du groupe peut inviter.";
        if (g.members.size() >= GROUP_MAX) return "Le groupe est complet (" + GROUP_MAX + " joueurs).";
        if (GROUP_OF.containsKey(t.getUUID())) return t.getName().getString() + " est déjà dans un groupe.";
        if (ignores(t, p)) return t.getName().getString() + " ne reçoit pas vos invitations.";
        g.invites.put(t.getUUID(), System.currentTimeMillis());
        String n = p.getName().getString();
        Msg.send(t, Msg.text(n + " vous invite dans son groupe. ", ChatFormatting.GREEN)
                .append(Msg.button("Rejoindre", "/groupe accepter " + n, "Rejoindre le groupe de " + n, ChatFormatting.GREEN))
                .append(Msg.text(" ", ChatFormatting.GRAY))
                .append(Msg.button("Refuser", "/groupe refuser " + n, "Refuser", ChatFormatting.RED)));
        Msg.ok(p, "Invitation envoyée à " + t.getName().getString() + " (2 minutes pour accepter).");
        return null;
    }

    public static String groupAccept(ServerPlayer p, String leaderName) {
        if (group(p) != null) return "Quittez d'abord votre groupe (/groupe quitter).";
        MinecraftServer server = p.level().getServer();
        Group found = null;
        for (Group g : new HashSet<>(GROUP_OF.values())) {
            Long at = g.invites.get(p.getUUID());
            if (at == null || System.currentTimeMillis() - at > 120_000) continue;
            if (leaderName != null) {
                ServerPlayer l = server.getPlayerList().getPlayer(g.leader);
                if (l == null || !l.getName().getString().equalsIgnoreCase(leaderName)) continue;
            }
            found = g;
            break;
        }
        if (found == null) return "Aucune invitation de groupe en attente.";
        if (found.members.size() >= GROUP_MAX) return "Ce groupe est complet.";
        found.invites.remove(p.getUUID());
        found.members.add(p.getUUID());
        GROUP_OF.put(p.getUUID(), found);
        groupNotice(server, found, p.getName().getString() + " rejoint le groupe (" + found.members.size() + "/" + GROUP_MAX + ").");
        return null;
    }

    public static String groupDeny(ServerPlayer p) {
        boolean any = false;
        for (Group g : new HashSet<>(GROUP_OF.values())) any |= g.invites.remove(p.getUUID()) != null;
        if (!any) return "Aucune invitation en attente.";
        Msg.info(p, "Invitation refusée.");
        return null;
    }

    public static String groupLeave(ServerPlayer p) {
        Group g = group(p);
        if (g == null) return "Vous n'êtes dans aucun groupe.";
        leave(p.level().getServer(), p.getUUID(), p.getName().getString() + " quitte le groupe.");
        Msg.info(p, "Vous avez quitté le groupe.");
        return null;
    }

    private static void leave(MinecraftServer server, UUID id, String notice) {
        Group g = GROUP_OF.remove(id);
        if (g == null) return;
        g.members.remove(id);
        if (g.members.isEmpty()) return;
        if (g.leader.equals(id)) {
            g.leader = g.members.iterator().next();
            ServerPlayer l = server.getPlayerList().getPlayer(g.leader);
            notice += " Nouveau chef : " + (l == null ? "?" : l.getName().getString()) + ".";
        }
        if (g.members.size() == 1) {
            UUID last = g.members.iterator().next();
            GROUP_OF.remove(last);
            ServerPlayer lp = server.getPlayerList().getPlayer(last);
            if (lp != null) Msg.info(lp, notice + " Le groupe est dissous.");
            return;
        }
        groupNotice(server, g, notice);
    }

    public static String groupKick(ServerPlayer p, String name) {
        Group g = group(p);
        if (g == null) return "Vous n'êtes dans aucun groupe.";
        if (!g.leader.equals(p.getUUID())) return "Seul le chef peut exclure.";
        MinecraftServer server = p.level().getServer();
        for (UUID u : g.members) {
            PlayerProfile t = GodData.find(u.toString());
            if (t != null && t.name.equalsIgnoreCase(name) && !u.equals(p.getUUID())) {
                leave(server, u, t.name + " a été exclu du groupe.");
                ServerPlayer tp = server.getPlayerList().getPlayer(u);
                if (tp != null) Msg.err(tp, "Vous avez été exclu du groupe.");
                return null;
            }
        }
        return name + " n'est pas dans votre groupe.";
    }

    public static String groupLeader(ServerPlayer p, ServerPlayer t) {
        Group g = group(p);
        if (g == null) return "Vous n'êtes dans aucun groupe.";
        if (!g.leader.equals(p.getUUID())) return "Seul le chef peut passer la main.";
        if (t == null || !g.members.contains(t.getUUID())) return "Ce joueur n'est pas dans votre groupe.";
        g.leader = t.getUUID();
        groupNotice(p.level().getServer(), g, t.getName().getString() + " est le nouveau chef du groupe.");
        return null;
    }

    public static String groupList(ServerPlayer p) {
        Group g = group(p);
        if (g == null) {
            Msg.send(p, Msg.text("Vous n'êtes dans aucun groupe. ", ChatFormatting.GRAY)
                    .append(Msg.button("Créer un groupe", "/groupe creer", "Créer un groupe et inviter des amis", ChatFormatting.GREEN)));
            return null;
        }
        MinecraftServer server = p.level().getServer();
        Msg.send(p, Msg.header("Groupe (" + g.members.size() + "/" + GROUP_MAX + ")"));
        for (UUID u : g.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            PlayerProfile pr = GodData.find(u.toString());
            String n = pr == null ? "?" : pr.name;
            String where = sp == null ? "hors ligne" : worldName(sp) + " · " + (int) sp.getX() + " " + (int) sp.getY() + " " + (int) sp.getZ()
                    + " · " + (int) sp.getHealth() + " ❤";
            Msg.send(p, Msg.text((u.equals(g.leader) ? "♛ " : "• ") + n, u.equals(g.leader) ? ChatFormatting.GOLD : ChatFormatting.GREEN)
                    .append(Msg.text("  " + where, ChatFormatting.GRAY)));
        }
        Msg.send(p, Msg.text("/p <message> · /groupe inviter <joueur> · /groupe quitter", ChatFormatting.DARK_GRAY));
        return null;
    }

    private static String worldName(ServerPlayer sp) {
        if (Hub.isSpawn(sp.level())) return "Sanctuaire";
        if (Hub.isResources(sp.level())) return "Monde ressource";
        if (DivineWorld.isDivine(sp.level())) return "Monde divin";
        return "Monde principal";
    }

    // ------------------------------------------------------------------ Mise en forme

    private static String glyph(Channel c) {
        return switch (c) {
            case GLOBAL -> com.godmod.util.Glyphs.CH_GLOBAL;
            case LOCAL -> com.godmod.util.Glyphs.CH_LOCAL;
            case COMMERCE -> com.godmod.util.Glyphs.CH_COMMERCE;
            case GROUPE -> com.godmod.util.Glyphs.CH_GROUPE;
            case MP -> com.godmod.util.Glyphs.CH_MP;
            case SYSTEME -> com.godmod.util.Glyphs.STAR;
        };
    }

    private static MutableComponent badge(Channel c) {
        String hour = LocalTime.now(com.godmod.progress.QuestSystem.ZONE).format(HOUR);
        if (com.godmod.util.Glyphs.on()) {
            return com.godmod.util.Glyphs.of(glyph(c)).withStyle(s -> s
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Canal " + c.label + " · " + hour
                            + (c.writable() ? "\nCliquez pour écrire dans ce canal" : ""))))
                    .withClickEvent(c.writable() ? new ClickEvent.RunCommand("/canal " + c.id()) : null))
                    .append(Component.literal(" "));
        }
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(c.tag).withStyle(s -> s.withColor(c.color).withBold(true)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Canal " + c.label + " · " + hour
                                + (c.writable() ? "\nCliquez pour écrire dans ce canal" : ""))))
                        .withClickEvent(c.writable() ? new ClickEvent.RunCommand("/canal " + c.id()) : null)))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Pseudo décoré (rang, palier) avec sa fiche au survol ; clic = message privé. */
    private static MutableComponent name(ServerPlayer sp) {
        PlayerProfile p = GodData.get(sp);
        String n = sp.getName().getString();
        StringBuilder card = new StringBuilder();
        card.append(n).append('\n').append("Palier : ").append(p.tier.label);
        card.append("\nEssence : ").append(Msg.num(p.essence));
        if (p.master != null) {
            PlayerProfile m = GodData.find(p.master);
            card.append("\nDieu : ").append(m == null ? "?" : m.name);
        }
        int f = GodData.followersOf(p.uuid).size();
        if (f > 0) card.append("\nFidèles : ").append(f);
        card.append("\nMonde : ").append(worldName(sp));
        if (AfkSystem.isAfk(sp)) card.append("\n(AFK)");
        card.append("\n\nClic : message privé");
        String fc = card.toString();
        return Ranks.decorate(sp, sp.getName()).withStyle(s -> s
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(fc)))
                .withClickEvent(new ClickEvent.SuggestCommand("/mp " + n + " ")));
    }

    /** Corps du message : mentions, [item], liens, couleurs & pour les admins. */
    private static MutableComponent body(ServerPlayer from, String text, ChatFormatting color, Set<ServerPlayer> mentioned) {
        MinecraftServer server = from.level().getServer();
        boolean colors = Moderation.roleOf(from).atLeast(Role.ADMIN);
        MutableComponent out = Component.empty();
        String[] words = text.split(" ", -1);
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String sep = i < words.length - 1 ? " " : "";
            String lw = w.toLowerCase(Locale.ROOT);
            MutableComponent special = null;
            if (lw.equals("[item]") || lw.equals("[main]") || lw.equals("[objet]")) {
                ItemStack hand = from.getMainHandItem();
                if (!hand.isEmpty()) special = Component.empty().append(hand.getDisplayName())
                        .append(Component.literal(hand.getCount() > 1 ? " ×" + hand.getCount() : "").withStyle(ChatFormatting.GRAY));
            } else if (lw.startsWith("http://") || lw.startsWith("https://")) {
                try {
                    URI uri = URI.create(w);
                    special = Component.literal(w).withStyle(s -> s.withColor(ChatFormatting.BLUE).withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(uri)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Ouvrir le lien"))));
                } catch (Exception ignored) {
                }
            } else {
                String bare = w.replaceAll("^@", "").replaceAll("[^A-Za-z0-9_]", "");
                if (bare.length() >= 3) {
                    ServerPlayer t = server.getPlayerList().getPlayerByName(bare);
                    if (t != null && (w.startsWith("@") || w.replaceAll("[^A-Za-z0-9_]", "").equalsIgnoreCase(bare))) {
                        mentioned.add(t);
                        String shown = "@" + t.getName().getString();
                        special = Component.literal(shown).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)
                                .withClickEvent(new ClickEvent.SuggestCommand("/mp " + t.getName().getString() + " ")));
                        String rest = w.replaceAll("^@?[A-Za-z0-9_]+", "");
                        if (!rest.isEmpty()) special.append(Component.literal(rest).withStyle(color));
                    }
                }
            }
            if (special != null) {
                if (plain.length() > 0) {
                    out.append(styled(plain.toString(), color, colors));
                    plain.setLength(0);
                }
                out.append(special);
                plain.append(sep);
            } else {
                plain.append(w).append(sep);
            }
        }
        if (plain.length() > 0) out.append(styled(plain.toString(), color, colors));
        return out;
    }

    private static MutableComponent styled(String s, ChatFormatting color, boolean colors) {
        if (colors) s = s.replaceAll("&([0-9a-fk-or])", "§$1");
        else s = s.replace("§", "");
        return Component.literal(s).withStyle(color);
    }

    // ------------------------------------------------------------------ Envoi

    /** Remet un message à un joueur, en respectant son onglet, ses canaux masqués et sa liste d'ignorés. */
    private static void deliver(ServerPlayer to, Channel ch, Component line, boolean ping, ServerPlayer from) {
        if (Trailer.isViewer(to)) return;
        if (from != null && from != to && ignores(to, from)) return;
        State st = state(to);
        st.history.addLast(new Entry(ch, line));
        while (st.history.size() > HISTORY) st.history.removeFirst();
        PlayerProfile p = GodData.get(to);
        boolean hidden = st.tab == null && p.chatHidden.contains(ch.id()) && ch != Channel.MP;
        boolean visible = !hidden && (st.tab == null || st.tab == ch || ch == Channel.SYSTEME);
        if (visible) {
            st.replaying = true;
            try {
                to.sendSystemMessage(line);
            } finally {
                st.replaying = false;
            }
        } else {
            st.unread.merge(ch, 1, Integer::sum);
            if (ch == Channel.MP || ping) {
                to.displayClientMessage(Msg.text("✉ Nouveau message " + (ch == Channel.MP ? "privé" : "en " + ch.label) + " — /chat onglet "
                        + (ch == Channel.MP ? "mp" : ch.id()), ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        if (ping) ping(to);
    }

    private static void ping(ServerPlayer p) {
        p.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_PLING, SoundSource.PLAYERS,
                p.getX(), p.getY(), p.getZ(), 0.8F, 1.6F, p.getRandom().nextLong()));
    }

    /** Appelé par Msg pour les messages du serveur : gardés dans l'historique des onglets. */
    public static void recordSystem(ServerPlayer to, Component line) {
        if (!GodData.ready()) return;
        State st = STATES.get(to.getUUID());
        if (st == null || st.replaying) return;
        st.history.addLast(new Entry(Channel.SYSTEME, line));
        while (st.history.size() > HISTORY) st.history.removeFirst();
    }

    private static String spamCheck(ServerPlayer p, String text) {
        if (Moderation.roleOf(p).atLeast(Role.MODERATEUR)) return null;
        State st = state(p);
        long now = System.currentTimeMillis();
        if (now - st.lastMessage < 700) return "Doucement : un message toutes les secondes.";
        if (text.equalsIgnoreCase(st.lastText) && now - st.lastMessage < 6000) return "Évitez de répéter le même message.";
        st.lastMessage = now;
        st.lastText = text;
        return null;
    }

    /** Message tapé dans le chat (déjà autorisé : connecté, pas muet). */
    public static void onChat(ServerPlayer p, String raw) {
        String text = raw.trim();
        if (text.isEmpty()) return;
        PlayerProfile pr = GodData.get(p);
        State st = state(p);
        Channel ch = speaking(pr);
        if (text.startsWith("!") && text.length() > 1) {
            ch = Channel.GLOBAL;
            text = text.substring(1).trim();
        } else if (st.tab == Channel.MP) {
            if (st.replyTo == null) {
                Msg.err(p, "Onglet Privé : utilisez /mp <joueur> <message>, ou changez d'onglet (/chat).");
                return;
            }
            String err = reply(p, text);
            if (err != null) Msg.err(p, err);
            return;
        } else if (st.tab != null && st.tab.writable()) {
            ch = st.tab;
        }
        String err = say(p, ch, text);
        if (err != null) Msg.err(p, err);
    }

    /** Écrit dans un canal. Renvoie une erreur ou null. */
    public static String say(ServerPlayer p, Channel ch, String text) {
        if (text == null || text.isBlank()) return "Message vide.";
        if (text.length() > 256) text = text.substring(0, 256);
        String spam = spamCheck(p, text);
        if (spam != null) return spam;
        MinecraftServer server = p.level().getServer();
        List<ServerPlayer> targets = new ArrayList<>();
        switch (ch) {
            case GLOBAL, COMMERCE -> targets.addAll(server.getPlayerList().getPlayers());
            case LOCAL -> {
                for (ServerPlayer o : server.getPlayerList().getPlayers())
                    if (o.level() == p.level() && o.distanceToSqr(p) <= LOCAL_RADIUS * LOCAL_RADIUS) targets.add(o);
            }
            case GROUPE -> {
                Group g = group(p);
                if (g == null) return "Vous n'êtes dans aucun groupe. /groupe creer puis /groupe inviter <joueur>.";
                targets.addAll(online(server, g));
            }
            default -> {
                return "On ne peut pas écrire dans ce canal.";
            }
        }
        ChatFormatting color = switch (ch) {
            case LOCAL -> ChatFormatting.YELLOW;
            case COMMERCE -> ChatFormatting.GOLD;
            case GROUPE -> ChatFormatting.GREEN;
            default -> ChatFormatting.WHITE;
        };
        Set<ServerPlayer> mentioned = new HashSet<>();
        MutableComponent line = badge(ch).append(name(p)).append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                .append(body(p, text, color, mentioned));
        GodMod.LOGGER.info("[{}] {} : {}", ch.label, p.getName().getString(), text);
        for (ServerPlayer t : targets) deliver(t, ch, line, mentioned.contains(t) && t != p, p);
        if (ch == Channel.LOCAL && targets.size() <= 1) Msg.send(p, Msg.text("Personne ne vous entend à moins de " + LOCAL_RADIUS + " blocs (canal Local).", ChatFormatting.GRAY));
        EventSystemHook.onChat(p, text);
        return null;
    }

    /** Petit relais pour que les événements (quiz) voient aussi les messages des canaux. */
    private static final class EventSystemHook {
        static void onChat(ServerPlayer p, String text) {
            com.godmod.progress.EventSystem.onChat(p, text);
        }
    }

    // ------------------------------------------------------------------ Messages privés

    public static boolean ignores(ServerPlayer who, ServerPlayer other) {
        return GodData.get(who).ignored.contains(other.getUUID().toString())
                && !Moderation.roleOf(other).atLeast(Role.MODERATEUR);
    }

    public static String privateMessage(ServerPlayer from, ServerPlayer to, String text) {
        if (to == null) return "Joueur introuvable ou hors ligne.";
        if (to == from) return "Vous parlez tout seul ?";
        PlayerProfile fp = GodData.get(from);
        if (Moderation.isMuted(fp)) return "Vous êtes muet : messages privés impossibles.";
        if (ignores(to, from)) return to.getName().getString() + " ne reçoit pas vos messages.";
        if (GodData.get(to).mpBlocked && !Moderation.roleOf(from).atLeast(Role.MODERATEUR))
            return to.getName().getString() + " a désactivé les messages privés.";
        String spam = spamCheck(from, "mp:" + text);
        if (spam != null) return spam;
        String fn = from.getName().getString(), tn = to.getName().getString();
        Set<ServerPlayer> none = new HashSet<>();
        MutableComponent bodyC = body(from, text, ChatFormatting.LIGHT_PURPLE, none);
        MutableComponent toLine = badge(Channel.MP).append(Msg.text("De ", ChatFormatting.DARK_PURPLE)).append(name(from))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY)).append(bodyC.copy());
        MutableComponent fromLine = badge(Channel.MP).append(Msg.text("À ", ChatFormatting.DARK_PURPLE)).append(name(to))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY)).append(bodyC);
        deliver(to, Channel.MP, toLine, true, from);
        deliver(from, Channel.MP, fromLine, false, null);
        state(to).replyTo = from.getUUID();
        state(from).replyTo = to.getUUID();
        if (AfkSystem.isAfk(to)) Msg.send(from, Msg.text(tn + " est AFK : il lira votre message plus tard.", ChatFormatting.GRAY));
        GodMod.LOGGER.info("[MP] {} → {} : {}", fn, tn, text);
        Component spy = Msg.text("[Espion] " + fn + " → " + tn + " : " + text, ChatFormatting.DARK_GRAY);
        for (ServerPlayer s : from.level().getServer().getPlayerList().getPlayers()) {
            if (s != from && s != to && GodData.get(s).chatSpy && Moderation.roleOf(s).atLeast(Role.MODERATEUR)) s.sendSystemMessage(spy);
        }
        return null;
    }

    public static String reply(ServerPlayer from, String text) {
        UUID r = state(from).replyTo;
        if (r == null) return "Personne à qui répondre.";
        ServerPlayer to = from.level().getServer().getPlayerList().getPlayer(r);
        if (to == null) return "Votre correspondant s'est déconnecté.";
        return privateMessage(from, to, text);
    }

    public static String toggleIgnore(ServerPlayer p, String name) {
        PlayerProfile me = GodData.get(p);
        if (name == null) {
            List<String> names = new ArrayList<>();
            for (String u : me.ignored) {
                PlayerProfile o = GodData.find(u);
                names.add(o == null ? "?" : o.name);
            }
            Msg.info(p, names.isEmpty() ? "Vous n'ignorez personne. /ignorer <joueur>" : "Ignorés : " + String.join(", ", names) + ".");
            return null;
        }
        PlayerProfile t = GodData.byName(name);
        if (t == null) return "Joueur inconnu.";
        if (t.uuid.equals(me.uuid)) return "Impossible de s'ignorer soi-même.";
        if (me.ignored.remove(t.uuid)) Msg.ok(p, "Vous voyez de nouveau les messages de " + t.name + ".");
        else {
            me.ignored.add(t.uuid);
            Msg.ok(p, "Vous ne verrez plus les messages ni les invitations de " + t.name + ". (/ignorer " + t.name + " pour annuler)");
        }
        GodData.dirty();
        return null;
    }

    // ------------------------------------------------------------------ Onglets

    public static String setChannel(ServerPlayer p, Channel c) {
        if (c == null || !c.writable()) return "Canaux : global, local, commerce, groupe.";
        if (c == Channel.GROUPE && group(p) == null) return "Vous n'êtes dans aucun groupe (/groupe creer).";
        PlayerProfile pr = GodData.get(p);
        pr.chatChannel = c.id();
        GodData.dirty();
        State st = state(p);
        if (st.tab != null && st.tab != c) showTab(p, null);
        p.displayClientMessage(Msg.text("✎ Vous écrivez maintenant dans le canal " + c.label + (c == Channel.LOCAL ? " (" + LOCAL_RADIUS + " blocs)" : "")
                + ". « ! » au début d'un message = Global.", c.color), true);
        return null;
    }

    public static String toggleHidden(ServerPlayer p, Channel c) {
        if (c == null || c == Channel.MP || c == Channel.SYSTEME) return "Canaux masquables : global, local, commerce, groupe.";
        PlayerProfile pr = GodData.get(p);
        boolean now = !pr.chatHidden.remove(c.id());
        if (now) pr.chatHidden.add(c.id());
        GodData.dirty();
        Msg.info(p, "Canal " + c.label + (now ? " masqué dans l'onglet Tout (il reste lisible dans son onglet)." : " de nouveau visible."));
        return null;
    }

    /** Affiche un onglet : efface l'écran, rejoue l'historique filtré et dessine la barre d'onglets. */
    public static void showTab(ServerPlayer p, Channel tab) {
        State st = state(p);
        st.tab = tab;
        if (tab == null) st.unread.clear();
        else st.unread.remove(tab);
        if (tab != null && tab.writable() && !(tab == Channel.GROUPE && group(p) == null)) {
            PlayerProfile pr = GodData.get(p);
            pr.chatChannel = tab.id();
            GodData.dirty();
        }
        PlayerProfile pr = GodData.get(p);
        st.replaying = true;
        try {
            for (int i = 0; i < 100; i++) p.sendSystemMessage(Component.empty());
            List<Entry> shown = new ArrayList<>();
            for (Entry e : st.history) {
                boolean ok = tab == null ? !pr.chatHidden.contains(e.channel().id()) || e.channel() == Channel.MP : e.channel() == tab;
                if (ok) shown.add(e);
            }
            int from = Math.max(0, shown.size() - REPLAY);
            p.sendSystemMessage(Component.literal("━━━━━━━━  " + (tab == null ? "Tout" : tab.label) + "  ━━━━━━━━")
                    .withStyle(tab == null ? ChatFormatting.GRAY : tab.color));
            if (shown.isEmpty()) p.sendSystemMessage(Msg.text("Aucun message pour l'instant.", ChatFormatting.DARK_GRAY));
            for (int i = from; i < shown.size(); i++) p.sendSystemMessage(shown.get(i).line());
            p.sendSystemMessage(tabBar(p));
        } finally {
            st.replaying = false;
        }
    }

    /** Barre d'onglets cliquable. */
    public static MutableComponent tabBar(ServerPlayer p) {
        State st = state(p);
        PlayerProfile pr = GodData.get(p);
        MutableComponent bar = Component.literal("Onglets ").withStyle(ChatFormatting.DARK_GRAY);
        bar.append(tabButton("Tout", "tout", st.tab == null, ChatFormatting.WHITE, total(st)));
        for (Channel c : TABS) {
            if (c == Channel.GROUPE && group(p) == null) continue;
            bar.append(Component.literal(" ")).append(tabButton(c.label, c.id(), st.tab == c, c.color, st.unread.getOrDefault(c, 0)));
        }
        Channel sp = speaking(pr);
        bar.append(Component.literal("   ✎ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(sp.label).withStyle(s -> s.withColor(sp.color)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Canal où vous écrivez.\nCliquez pour changer.")))
                        .withClickEvent(new ClickEvent.RunCommand("/canal"))));
        return bar;
    }

    private static int total(State st) {
        int n = 0;
        for (int v : st.unread.values()) n += v;
        return n;
    }

    private static MutableComponent tabButton(String label, String id, boolean active, ChatFormatting color, int unread) {
        MutableComponent b = Component.literal(active ? "▶" + label : label).withStyle(s -> s
                .withColor(active ? color : ChatFormatting.GRAY).withBold(active).withUnderlined(active)
                .withClickEvent(new ClickEvent.RunCommand("/chat onglet " + id))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Afficher l'onglet " + label))));
        if (unread > 0 && !active) b.append(Component.literal(" (" + unread + ")").withStyle(ChatFormatting.YELLOW));
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY).append(b).append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** /canal sans argument : choix cliquable. */
    public static void channelPicker(ServerPlayer p) {
        MutableComponent line = Msg.text("Écrire dans : ", ChatFormatting.GRAY);
        Channel cur = speaking(GodData.get(p));
        for (Channel c : List.of(Channel.GLOBAL, Channel.LOCAL, Channel.COMMERCE, Channel.GROUPE)) {
            if (c == Channel.GROUPE && group(p) == null) continue;
            boolean on = c == cur;
            line.append(Component.literal((on ? "▶" : "") + c.label).withStyle(s -> s.withColor(on ? c.color : ChatFormatting.GRAY).withBold(on)
                    .withClickEvent(new ClickEvent.RunCommand("/canal " + c.id()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Écrire dans le canal " + c.label)))))
                    .append(Component.literal("  "));
        }
        Msg.send(p, line);
    }

    public static void onLogin(ServerPlayer p) {
        state(p);
        PlayerProfile pr = GodData.get(p);
        if (speaking(pr) == Channel.GROUPE) pr.chatChannel = Channel.GLOBAL.id();
        p.sendSystemMessage(tabBar(p));
    }

    public static void onLogout(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        leave(server, p.getUUID(), p.getName().getString() + " s'est déconnecté et quitte le groupe.");
        STATES.remove(p.getUUID());
        for (State s : STATES.values()) if (p.getUUID().equals(s.replyTo)) s.replyTo = null;
    }

    public static List<String> groupMembersNames(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        Group g = group(p);
        if (g == null) return out;
        for (UUID u : g.members) {
            PlayerProfile pr = GodData.find(u.toString());
            if (pr != null && !u.equals(p.getUUID())) out.add(pr.name);
        }
        return out;
    }
}
