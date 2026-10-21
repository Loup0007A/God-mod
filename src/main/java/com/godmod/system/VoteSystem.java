package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.progress.CrateSystem;
import com.godmod.progress.QuestSystem;
import com.godmod.util.Msg;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /vote : liens vers les sites de classement, puis vérification du vote (par l'adresse IP du joueur, auprès
 * de l'API de chaque site) et récompense. Un même vote n'est récompensé qu'une fois (délai propre à chaque site).
 * <ul>
 *   <li>liste-serveurs-minecraft.org : identifiant du serveur (209022), sans clé ;</li>
 *   <li>top-serveurs.net : jeton du serveur (panel du site → API) ;</li>
 *   <li>serveur-prive.net : clé d'API (panel du site → API).</li>
 * </ul>
 */
public final class VoteSystem {
    public record Site(String id, String label, String url, long cooldownMs) {
    }

    public static final List<Site> SITES = List.of(
            new Site("top-serveurs", "Top-Serveurs", "https://top-serveurs.net/minecraft/vote/god-civilisation", 2 * 3600_000L),
            new Site("serveur-prive", "Serveur-Privé", "https://serveur-prive.net/minecraft/god-civilisation/vote", 90 * 60_000L),
            new Site("liste-serveurs", "Liste-Serveurs-Minecraft", "https://www.liste-serveurs-minecraft.org/vote/?idc=209022", 3 * 3600_000L));

    private static final Map<UUID, Long> LAST_CHECK = new HashMap<>();
    private static final long CHECK_COOLDOWN = 20_000;

    private VoteSystem() {
    }

    public static Site site(String id) {
        for (Site s : SITES) if (s.id().equalsIgnoreCase(id)) return s;
        return null;
    }

    /** Clé d'API configurée pour un site, ou null. */
    private static String key(Site s) {
        Settings st = GodData.settings();
        String k = switch (s.id()) {
            case "top-serveurs" -> st.voteTopServeursToken;
            case "serveur-prive" -> st.voteServeurPriveKey;
            case "liste-serveurs" -> st.voteListeServeursId;
            default -> null;
        };
        return k == null || k.isBlank() ? null : k.trim();
    }

    public static boolean configured(Site s) {
        return key(s) != null;
    }

    public static void setKey(String siteId, String value) {
        Settings st = GodData.settings();
        String v = value == null || value.equalsIgnoreCase("aucun") ? "" : value.trim();
        switch (siteId) {
            case "top-serveurs" -> st.voteTopServeursToken = v;
            case "serveur-prive" -> st.voteServeurPriveKey = v;
            case "liste-serveurs" -> st.voteListeServeursId = v;
            default -> {
            }
        }
        GodData.dirty();
    }

    private static long last(PlayerProfile p, Site s) {
        Long l = p.voteLast.get(s.id());
        return l == null ? 0 : l;
    }

    /** Temps avant de pouvoir revoter sur ce site (0 = maintenant). */
    public static long waitLeft(PlayerProfile p, Site s) {
        return Math.max(0, last(p, s) + s.cooldownMs() - System.currentTimeMillis());
    }

    public static int available(PlayerProfile p) {
        int n = 0;
        for (Site s : SITES) if (waitLeft(p, s) == 0) n++;
        return n;
    }

    private static MutableComponent link(String label, String url, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(color).withBold(true).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Ouvrir la page de vote\n" + url))));
    }

    // ------------------------------------------------------------------ /vote

    public static String show(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Settings st = GodData.settings();
        Msg.send(player, Msg.header("Voter pour God Civilisation"));
        Msg.send(player, Msg.text("Chaque vote fait monter le serveur dans les classements et vous rapporte "
                + Msg.money(st.voteMoney) + ", " + st.voteEssence + " Essence et une clé Commune.", ChatFormatting.GRAY));
        for (Site s : SITES) {
            long w = waitLeft(p, s);
            MutableComponent line = link(s.label(), s.url(), w == 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN)
                    .append(Msg.text(w == 0 ? "  ✔ disponible" : "  déjà voté · encore " + Msg.time(w), w == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            if (!configured(s)) line.append(Msg.text("  (récompense non vérifiable sur ce site pour l'instant)", ChatFormatting.DARK_GRAY));
            Msg.send(player, line);
        }
        Msg.send(player, Msg.text("Après avoir voté : ", ChatFormatting.GRAY)
                .append(Msg.button("J'ai voté : récupérer", "/vote recuperer", "Vérifie vos votes et donne les récompenses", ChatFormatting.GOLD))
                .append(Msg.text("  ", ChatFormatting.GRAY))
                .append(Msg.button("Classement", "/vote top", "Les meilleurs voteurs du mois", ChatFormatting.YELLOW)));
        Msg.send(player, Msg.text("Vos votes : " + p.votesMonth + " ce mois-ci · " + p.votesTotal + " au total. Votez depuis la même connexion que votre Minecraft.", ChatFormatting.DARK_GRAY));
        return null;
    }

    public static String claim(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PlayerProfile p = GodData.get(player);
        long now = System.currentTimeMillis();
        Long lc = LAST_CHECK.get(player.getUUID());
        if (lc != null && now - lc < CHECK_COOLDOWN) return "Patientez " + Msg.time(CHECK_COOLDOWN - (now - lc) + 999) + " avant une nouvelle vérification.";
        List<Site> todo = new ArrayList<>();
        for (Site s : SITES) if (configured(s) && waitLeft(p, s) == 0) todo.add(s);
        if (todo.isEmpty()) {
            boolean any = false;
            for (Site s : SITES) any |= configured(s);
            if (!any) return "La vérification des votes n'est pas encore configurée par l'équipe. Merci quand même pour vos votes !";
            return "Aucun vote à récupérer pour l'instant : revotez quand le délai de chaque site est écoulé (/vote).";
        }
        LAST_CHECK.put(player.getUUID(), now);
        String ip = AuthSystem.ip(player);
        String name = player.getName().getString();
        UUID id = player.getUUID();
        Msg.info(player, "Vérification de vos votes…");
        CompletableFuture.runAsync(() -> {
            List<Site> ok = new ArrayList<>();
            for (Site s : todo) {
                try {
                    if (check(s, key(s), ip, name)) ok.add(s);
                } catch (Exception e) {
                    GodMod.LOGGER.debug("[God Mod] Vérification du vote {} impossible : {}", s.id(), e.getMessage());
                }
            }
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                if (ok.isEmpty()) {
                    Msg.err(sp, "Aucun vote trouvé. Votez sur les sites (/vote), attendez quelques secondes puis réessayez. "
                            + "Votez depuis la même connexion internet que votre Minecraft.");
                    return;
                }
                for (Site s : ok) reward(sp, s);
            });
        });
        return null;
    }

    /** Appel de l'API du site (dans un fil séparé). */
    private static boolean check(Site s, String key, String ip, String name) throws Exception {
        String k = URLEncoder.encode(key, StandardCharsets.UTF_8), i = URLEncoder.encode(ip, StandardCharsets.UTF_8);
        String url = switch (s.id()) {
            case "top-serveurs" -> "https://api.top-serveurs.net/v1/votes/check-ip?server_token=" + k + "&ip=" + i;
            case "serveur-prive" -> "https://serveur-prive.net/api/v1/servers/" + k + "/votes/" + i;
            case "liste-serveurs" -> "https://api.liste-serveurs-minecraft.org/vote/vote_verification.php?server_id=" + k + "&ip=" + i + "&duration=180";
            default -> null;
        };
        if (url == null) return false;
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "GodCivilisation-Vote/1.0");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        String body;
        try (var in = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
            body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        return switch (s.id()) {
            case "liste-serveurs" -> body.equals("1");
            case "top-serveurs" -> jsonIs(body, "code", "200");
            case "serveur-prive" -> jsonIs(body, "success", "true");
            default -> false;
        };
    }

    private static boolean jsonIs(String body, String field, String expected) {
        try {
            JsonElement e = JsonParser.parseString(body);
            if (!e.isJsonObject() || !e.getAsJsonObject().has(field)) return false;
            return e.getAsJsonObject().get(field).getAsString().equalsIgnoreCase(expected);
        } catch (Exception ex) {
            return false;
        }
    }

    private static void reward(ServerPlayer sp, Site s) {
        PlayerProfile p = GodData.get(sp);
        if (waitLeft(p, s) > 0) return; // déjà récompensé entre-temps
        Settings st = GodData.settings();
        p.voteLast.put(s.id(), System.currentTimeMillis());
        String month = QuestSystem.today().substring(0, 7);
        if (!month.equals(p.voteMonth)) {
            p.voteMonth = month;
            p.votesMonth = 0;
        }
        String today = QuestSystem.today();
        if (!today.equals(p.voteDay)) {
            p.voteDay = today;
            p.votesToday = 0;
        }
        p.votesMonth++;
        p.votesTotal++;
        p.votesToday++;
        p.money += st.voteMoney;
        CrateSystem.addKey(p, CrateSystem.Crate.COMMUNE, 1);
        PowerSystem.addEssence(sp, st.voteEssence, "vote");
        boolean all = p.votesToday == SITES.size();
        if (all) CrateSystem.addKey(p, CrateSystem.Crate.RARE, 1);
        GodData.dirty();
        GodData.log("VOTE : " + p.name + " sur " + s.label() + " (" + p.votesMonth + " ce mois)");
        com.godmod.util.Fx.sound(sp, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.7F, 1.4F);
        Msg.ok(sp, "Merci pour votre vote sur " + s.label() + " ! +" + Msg.money(st.voteMoney) + ", +" + st.voteEssence + " Essence, +1 clé Commune"
                + (all ? ", et une clé Rare pour avoir voté sur les " + SITES.size() + " sites aujourd'hui !" : "."));
        Msg.broadcast(sp.level().getServer(), "★ " + p.name + " a voté pour le serveur sur " + s.label() + " ! Faites comme lui : /vote", ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ Classement et rappels

    public static String top(ServerPlayer player) {
        String month = QuestSystem.today().substring(0, 7);
        List<PlayerProfile> list = new ArrayList<>();
        for (PlayerProfile p : GodData.state().players.values()) if (month.equals(p.voteMonth) && p.votesMonth > 0) list.add(p);
        list.sort((a, b) -> Integer.compare(b.votesMonth, a.votesMonth));
        Msg.send(player, Msg.header("Meilleurs voteurs — " + month));
        if (list.isEmpty()) Msg.info(player, "Personne n'a encore voté ce mois-ci. Soyez le premier : /vote");
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            PlayerProfile p = list.get(i);
            Msg.send(player, Msg.text((i + 1) + ". " + p.name + " — " + p.votesMonth + " vote(s)", i == 0 ? ChatFormatting.GOLD : i < 3 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        return null;
    }

    public static void onLogin(ServerPlayer sp) {
        PlayerProfile p = GodData.get(sp);
        int n = available(p);
        if (n == 0) return;
        Msg.send(sp, Msg.prefix().append(Msg.text(n + " vote(s) disponible(s) pour soutenir le serveur. ", ChatFormatting.GOLD))
                .append(Msg.button("/vote", "/vote", "Voter et gagner des récompenses", ChatFormatting.YELLOW)));
    }

    public static String status() {
        StringBuilder b = new StringBuilder("Vérification des votes : ");
        for (Site s : SITES) b.append(s.label()).append(configured(s) ? " ✔  " : " ✖ (clé manquante)  ");
        return b.toString().trim();
    }

    public static void onLogout(ServerPlayer p) {
        LAST_CHECK.remove(p.getUUID());
    }
}
