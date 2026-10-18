package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demandes de téléportation entre joueurs :
 * /tpa joueur (aller vers lui), /tpahere joueur (le faire venir), /tpaccept, /tpdeny, /tpatoggle.
 */
public final class TpaSystem {
    public static final long EXPIRE_MS = 60_000;
    public static final long SPAM_MS = 15_000;

    private record Request(UUID from, String fromName, UUID to, boolean here, long expires) {
    }

    /** Destinataire -> (demandeur -> demande), dans l'ordre d'arrivée. */
    private static final Map<UUID, LinkedHashMap<UUID, Request>> INBOX = new HashMap<>();

    private TpaSystem() {
    }

    public static String request(ServerPlayer from, ServerPlayer to, boolean here) {
        if (from == to) return "Vous ne pouvez pas vous envoyer une demande à vous-même.";
        if (AuthSystem.isLocked(to)) return to.getName().getString() + " n'est pas encore connecté à son compte.";
        PlayerProfile target = GodData.get(to);
        if (target.tpaBlocked && !Moderation.roleOf(from).atLeast(com.godmod.data.Role.MODERATEUR))
            return to.getName().getString() + " n'accepte pas les demandes de téléportation.";
        String why = Travel.blocked(here ? to : from);
        if (why != null) return here ? to.getName().getString() + " ne peut pas voyager pour l'instant." : why;
        LinkedHashMap<UUID, Request> box = INBOX.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>());
        Request old = box.get(from.getUUID());
        long now = System.currentTimeMillis();
        if (old != null && old.expires - EXPIRE_MS + SPAM_MS > now) return "Demande déjà envoyée : patientez quelques secondes.";
        String fromName = from.getName().getString();
        box.remove(from.getUUID());
        box.put(from.getUUID(), new Request(from.getUUID(), fromName, to.getUUID(), here, now + EXPIRE_MS));

        Msg.send(to, Msg.text(fromName, ChatFormatting.YELLOW)
                .append(Msg.text(here ? " vous demande de venir auprès de lui. " : " souhaite se téléporter auprès de vous. ", ChatFormatting.WHITE))
                .append(Msg.button("Accepter", "/tpaccept " + fromName, "Accepter la demande", ChatFormatting.GREEN))
                .append(Msg.text(" ", ChatFormatting.WHITE))
                .append(Msg.button("Refuser", "/tpdeny " + fromName, "Refuser la demande", ChatFormatting.RED)));
        to.displayClientMessage(Msg.text("Demande de téléportation de " + fromName, ChatFormatting.YELLOW), true);
        Msg.ok(from, "Demande envoyée à " + to.getName().getString() + " (expire dans " + EXPIRE_MS / 1000 + " s).");
        return null;
    }

    private static Request pick(ServerPlayer to, String fromName) {
        LinkedHashMap<UUID, Request> box = INBOX.get(to.getUUID());
        if (box == null || box.isEmpty()) return null;
        long now = System.currentTimeMillis();
        box.values().removeIf(r -> r.expires < now);
        Request found = null;
        for (Request r : box.values()) {
            if (fromName == null || r.fromName.equalsIgnoreCase(fromName)) found = r; // le plus récent
        }
        return found;
    }

    public static String accept(ServerPlayer to, String fromName) {
        Request r = pick(to, fromName);
        if (r == null) return fromName == null ? "Aucune demande de téléportation en attente." : "Aucune demande de " + fromName + ".";
        INBOX.get(to.getUUID()).remove(r.from);
        MinecraftServer server = to.level().getServer();
        ServerPlayer from = server.getPlayerList().getPlayer(r.from);
        if (from == null) return r.fromName + " s'est déconnecté.";
        ServerPlayer mover = r.here ? to : from;
        ServerPlayer dest = r.here ? from : to;
        String access = canJoin(mover, dest);
        if (access != null) {
            Msg.err(from, "Téléportation impossible : " + access);
            return access;
        }
        UUID destId = dest.getUUID();
        String destName = dest.getName().getString();
        String err = Travel.start(mover, "Vers " + destName, () -> {
            ServerPlayer d = server.getPlayerList().getPlayer(destId);
            if (d == null) return destName + " s'est déconnecté.";
            String again = canJoin(mover, d);
            if (again != null) return again;
            DivineWorld.rememberReturn(mover);
            Teleport.to(mover, (ServerLevel) d.level(), d.getX(), d.getY(), d.getZ());
            Msg.ok(mover, "Vous voici auprès de " + destName + ".");
            return null;
        });
        if (err != null) {
            if (mover != to) Msg.err(to, mover.getName().getString() + " ne peut pas voyager : " + err);
            return mover == to ? err : null;
        }
        Msg.ok(to, "Demande de " + r.fromName + " acceptée.");
        if (mover != to) Msg.ok(from, to.getName().getString() + " a accepté votre demande.");
        return null;
    }

    /** Vérifie que le voyageur a le droit d'aller là où se trouve la destination. */
    private static String canJoin(ServerPlayer mover, ServerPlayer dest) {
        Level l = dest.level();
        if (l.dimension() == Level.NETHER || l.dimension() == Level.END) {
            if (GodData.settings().disableNetherEnd) return "cette dimension est scellée.";
        }
        if (DivineWorld.isDivine(l)) {
            PlayerProfile owner = DivineWorld.ownerAt(dest.blockPosition());
            if (owner == null) return "la destination est hors de tout monde divin.";
            if (!DivineWorld.canEnter(GodData.get(mover), owner))
                return mover.getName().getString() + " n'a pas accès au monde divin de " + owner.name + ".";
        }
        return null;
    }

    public static String deny(ServerPlayer to, String fromName) {
        Request r = pick(to, fromName);
        if (r == null) return "Aucune demande à refuser.";
        INBOX.get(to.getUUID()).remove(r.from);
        Msg.info(to, "Demande de " + r.fromName + " refusée.");
        ServerPlayer from = to.level().getServer().getPlayerList().getPlayer(r.from);
        if (from != null) Msg.err(from, to.getName().getString() + " a refusé votre demande de téléportation.");
        return null;
    }

    public static String toggle(ServerPlayer p) {
        PlayerProfile pr = GodData.get(p);
        pr.tpaBlocked = !pr.tpaBlocked;
        GodData.dirty();
        if (pr.tpaBlocked) INBOX.remove(p.getUUID());
        Msg.ok(p, pr.tpaBlocked ? "Vous refusez désormais toutes les demandes de téléportation." : "Vous acceptez de nouveau les demandes de téléportation.");
        return null;
    }

    /** Noms des joueurs qui ont une demande en attente pour ce destinataire (suggestions). */
    public static List<String> pendingNames(ServerPlayer to) {
        List<String> names = new ArrayList<>();
        LinkedHashMap<UUID, Request> box = INBOX.get(to.getUUID());
        if (box != null) for (Request r : box.values()) names.add(r.fromName);
        return names;
    }

    public static void tick(MinecraftServer server) {
        if (INBOX.isEmpty() || server.getTickCount() % 20 != 0) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, LinkedHashMap<UUID, Request>> e : INBOX.entrySet()) {
            e.getValue().values().removeIf(r -> {
                if (r.expires >= now) return false;
                ServerPlayer from = server.getPlayerList().getPlayer(r.from);
                if (from != null) Msg.info(from, "Votre demande de téléportation a expiré.");
                return true;
            });
        }
        INBOX.values().removeIf(Map::isEmpty);
    }

    public static void onLogout(ServerPlayer p) {
        INBOX.remove(p.getUUID());
        for (LinkedHashMap<UUID, Request> box : INBOX.values()) box.remove(p.getUUID());
    }
}
