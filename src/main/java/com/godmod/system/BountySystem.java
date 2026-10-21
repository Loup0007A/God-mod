package com.godmod.system;

import com.godmod.data.Bounty;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Primes (/prime) : n'importe qui peut mettre de l'argent sur la tête d'un joueur (taxe de 10 %).
 * Celui qui le tue en combat touche la prime (sa propre participation lui est simplement rendue).
 * Une prime non réclamée expire au bout de 7 jours : les participants sont remboursés.
 * Le joueur recherché porte un ☠ rouge devant son nom.
 */
public final class BountySystem {
    private BountySystem() {
    }

    public static long amount(String uuid) {
        Bounty b = GodData.state().bounties.get(uuid);
        return b == null ? 0 : b.total;
    }

    public static String place(ServerPlayer from, String targetName, long amount) {
        Settings st = GodData.settings();
        PlayerProfile me = GodData.get(from);
        PlayerProfile t = GodData.byName(targetName);
        if (t == null) return "Joueur inconnu : " + targetName + ".";
        if (t.uuid.equals(me.uuid)) return "Vous ne pouvez pas mettre une prime sur votre propre tête.";
        if (amount < st.bountyMin) return "Prime minimale : " + Msg.money(st.bountyMin) + ".";
        if (me.money < amount) return "Solde insuffisant : vous avez " + Msg.money(me.money) + ".";
        long tax = amount * st.bountyTaxPercent / 100;
        long net = amount - tax;
        me.money -= amount;
        Bounty b = GodData.state().bounties.computeIfAbsent(t.uuid, k -> {
            Bounty nb = new Bounty();
            nb.target = t.uuid;
            nb.created = System.currentTimeMillis();
            return nb;
        });
        b.targetName = t.name;
        b.total += net;
        b.updated = System.currentTimeMillis();
        b.contributors.merge(me.uuid, net, Long::sum);
        GodData.dirty();
        GodData.log("PRIME : " + me.name + " met " + net + " (taxe " + tax + ") sur " + t.name + " → " + b.total);
        Msg.broadcast(from.level().getServer(), "☠ " + me.name + " met " + Msg.money(net) + " de prime sur la tête de " + t.name
                + " ! Total : " + Msg.money(b.total) + ". (/prime)", ChatFormatting.RED);
        ServerPlayer online = from.level().getServer().getPlayerList().getPlayer(UUID.fromString(t.uuid));
        if (online != null) {
            Msg.err(online, "Votre tête est mise à prix : " + Msg.money(b.total) + ". Méfiez-vous des autres joueurs !");
            Ranks.refresh(online);
        }
        return null;
    }

    /** Un joueur en tue un autre en combat. */
    public static void onKill(ServerPlayer killer, ServerPlayer victim) {
        Bounty b = GodData.state().bounties.get(victim.getUUID().toString());
        if (b == null || b.total <= 0) return;
        String kid = killer.getUUID().toString();
        long own = b.contributors.getOrDefault(kid, 0L);
        long reward = b.total - own;
        GodData.state().bounties.remove(victim.getUUID().toString());
        PlayerProfile kp = GodData.get(killer);
        kp.money += b.total; // sa propre participation lui revient, le reste est la prime
        GodData.dirty();
        GodData.log("PRIME : " + kp.name + " tue " + b.targetName + " et touche " + reward + (own > 0 ? " (+" + own + " remboursés)" : ""));
        MinecraftServer server = killer.level().getServer();
        Msg.broadcast(server, "☠ " + kp.name + " a abattu " + b.targetName + " et touche sa prime de " + Msg.money(reward) + " !", ChatFormatting.GOLD);
        com.godmod.util.Fx.sound(killer, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1F, 0.6F);
        Ranks.refresh(victim);
    }

    public static String list(ServerPlayer p) {
        List<Bounty> all = new ArrayList<>(GodData.state().bounties.values());
        all.sort((a, b) -> Long.compare(b.total, a.total));
        Msg.send(p, Msg.header("Primes"));
        if (all.isEmpty()) {
            Msg.info(p, "Aucune prime en cours. /prime <joueur> <montant> pour en lancer une (taxe de " + GodData.settings().bountyTaxPercent + " %).");
            return null;
        }
        MinecraftServer server = p.level().getServer();
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            Bounty b = all.get(i);
            boolean on = server.getPlayerList().getPlayer(UUID.fromString(b.target)) != null;
            long left = b.updated + GodData.settings().bountyDays * 86_400_000L - now;
            Msg.send(p, Msg.text((i + 1) + ". ☠ " + b.targetName, ChatFormatting.RED)
                    .append(Msg.text(" — " + Msg.money(b.total) + " · " + b.contributors.size() + " participant(s) · " + (on ? "en ligne" : "hors ligne")
                            + " · expire dans " + Msg.time(Math.max(0, left)) + " ", ChatFormatting.GRAY))
                    .append(Msg.suggest("[+]", "/prime " + b.targetName + " ", "Augmenter la prime", ChatFormatting.GOLD)));
        }
        return null;
    }

    /** Chaque minute : expiration des primes (remboursement des participants). */
    public static void tickMinute(MinecraftServer server) {
        long limit = GodData.settings().bountyDays * 86_400_000L;
        long now = System.currentTimeMillis();
        var it = GodData.state().bounties.entrySet().iterator();
        while (it.hasNext()) {
            Bounty b = it.next().getValue();
            if (now - b.updated < limit) continue;
            it.remove();
            for (Map.Entry<String, Long> c : b.contributors.entrySet()) {
                PlayerProfile pr = GodData.find(c.getKey());
                if (pr != null) pr.money += c.getValue();
            }
            GodData.dirty();
            GodData.log("PRIME : expirée sur " + b.targetName + ", " + b.total + " remboursés");
            ServerPlayer t = server.getPlayerList().getPlayer(UUID.fromString(b.target));
            if (t != null) Ranks.refresh(t);
        }
    }
}
