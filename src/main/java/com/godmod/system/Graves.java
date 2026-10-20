package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.Grave;
import com.godmod.data.PlayerProfile;
import com.godmod.util.ItemCodec;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tombes : à la mort d'un joueur, ses objets sont rangés dans une tombe à l'endroit du décès
 * (un crâne flottant et son nom) au lieu de tomber par terre, où le nettoyage les effacerait.
 * <ul>
 *   <li>le propriétaire récupère tout en marchant dessus ;</li>
 *   <li>le tueur (en JcJ) peut la piller tout de suite, tout le monde après 10 minutes ;</li>
 *   <li>/tombe : vos tombes, et la récupération à distance (500 pièces, gratuite après une chute dans le vide) ;</li>
 *   <li>au bout de 2 heures, la tombe tombe en poussière.</li>
 * </ul>
 */
public final class Graves {
    public static final String TAG = "godmod_grave";

    private Graves() {
    }

    private static List<Grave> graves() {
        return GodData.state().graves;
    }

    private static long openMs() {
        return GodData.settings().graveOpenMinutes * 60_000L;
    }

    private static long expireMs() {
        return GodData.settings().graveExpireMinutes * 60_000L;
    }

    // ------------------------------------------------------------------ Création

    /** Appelé depuis LivingDropsEvent : range les objets du joueur dans une tombe et vide la liste des objets à lâcher. */
    public static void onPlayerDrops(ServerPlayer sp, Collection<ItemEntity> drops, DamageSource source) {
        if (!GodData.settings().graves || drops.isEmpty()) return;
        if (TournamentSystem.isFighting(sp.getUUID().toString())) return;
        MinecraftServer server = sp.level().getServer();
        Grave g = new Grave();
        List<ItemEntity> taken = new ArrayList<>();
        for (ItemEntity ie : drops) {
            ItemStack st = ie.getItem();
            if (st.isEmpty()) continue;
            try {
                g.items.add(ItemCodec.encode(server, st));
                g.count += st.getCount();
                taken.add(ie);
            } catch (Exception e) {
                com.godmod.GodMod.LOGGER.error("Tombe : objet non enregistré, il tombe au sol", e);
            }
        }
        if (taken.isEmpty()) return;
        drops.removeAll(taken);
        ServerLevel level = (ServerLevel) sp.level();
        g.id = GodData.state().nextGraveId++;
        g.owner = sp.getUUID().toString();
        g.ownerName = sp.getName().getString();
        Entity killer = source == null ? null : source.getEntity();
        if (killer instanceof ServerPlayer k && k != sp) {
            g.killer = k.getUUID().toString();
            g.killerName = k.getName().getString();
        }
        g.dimension = level.dimension().identifier().toString();
        g.voidDeath = sp.getY() < level.getMinY() + 1;
        g.x = sp.getX();
        g.y = g.voidDeath ? level.getMinY() + 1 : sp.getY();
        g.z = sp.getZ();
        g.created = System.currentTimeMillis();
        graves().add(g);
        GodData.dirty();
        GodData.log("TOMBE #" + g.id + " : " + g.ownerName + " (" + g.count + " objets) à " + (int) g.x + " " + (int) g.y + " " + (int) g.z + " " + g.dimension);
        if (!g.voidDeath) spawnMarker(server, level, g);
        // Le message arrive après l'écran de mort.
        server.execute(() -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(sp.getUUID());
            if (owner == null) return;
            Msg.send(owner, Msg.prefix().append(Msg.text("⚰ Vos " + g.count + " objets sont dans votre tombe"
                            + (g.voidDeath ? " (chute dans le vide : récupération gratuite). " : " en " + (int) g.x + " " + (int) g.y + " " + (int) g.z
                            + ". Marchez dessus pour tout reprendre. "), ChatFormatting.GRAY))
                    .append(Msg.button("[Mes tombes]", "/tombe", "Liste et récupération à distance", ChatFormatting.YELLOW)));
        });
    }

    private static void spawnMarker(MinecraftServer server, ServerLevel level, Grave g) {
        String tags = String.format("Tags:[\"%s\",\"%s_%d\"]", TAG, TAG, g.id);
        WorldBuilder.run(server, level, String.format(Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {%s,billboard:\"vertical\",brightness:{sky:15,block:15},item:{id:\"minecraft:skeleton_skull\",count:1},"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}",
                g.x, g.y + 0.6, g.z, tags));
        WorldBuilder.run(server, level, String.format(Locale.ROOT,
                "summon minecraft:text_display %.2f %.2f %.2f {%s,billboard:\"center\",shadow:1b,background:1073741824,brightness:{sky:15,block:15},"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]},"
                        + "text:[{text:\"⚰ Tombe de %s\\n\",color:\"gray\",bold:true},{text:\"%d objet(s)\",color:\"dark_gray\"}]}",
                g.x, g.y + 1.3, g.z, tags, g.ownerName.replaceAll("[^A-Za-z0-9_]", ""), g.count));
    }

    private static void remove(MinecraftServer server, Grave g) {
        graves().remove(g);
        GodData.dirty();
        ServerLevel level = level(server, g);
        if (level != null) WorldBuilder.run(server, level, "kill @e[tag=" + TAG + "_" + g.id + "]");
    }

    private static ServerLevel level(MinecraftServer server, Grave g) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(g.dimension)));
    }

    /** Un affichage de tombe chargé depuis le disque alors que la tombe n'existe plus : on le retire. */
    public static boolean isStaleMarker(Entity e) {
        if (!e.getTags().contains(TAG) || !GodData.ready()) return false;
        for (String t : e.getTags()) {
            if (!t.startsWith(TAG + "_")) continue;
            try {
                long id = Long.parseLong(t.substring(TAG.length() + 1));
                for (Grave g : graves()) if (g.id == id) return false;
            } catch (NumberFormatException ignored) {
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Récupération

    private static boolean canLoot(ServerPlayer p, Grave g) {
        String id = p.getUUID().toString();
        return id.equals(g.owner) || id.equals(g.killer) || System.currentTimeMillis() - g.created >= openMs();
    }

    private static void loot(ServerPlayer p, Grave g) {
        MinecraftServer server = p.level().getServer();
        remove(server, g);
        int n = 0;
        for (String s : g.items) {
            ItemStack st = ItemCodec.decode(server, s);
            if (st.isEmpty()) continue;
            n += st.getCount();
            Shop.give(p, st);
        }
        boolean own = p.getUUID().toString().equals(g.owner);
        GodData.log("TOMBE #" + g.id + " de " + g.ownerName + " récupérée par " + p.getName().getString());
        com.godmod.util.Fx.sound(p, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1F, 0.8F);
        Msg.ok(p, own ? "Vous récupérez vos " + n + " objet(s)." : "Vous pillez la tombe de " + g.ownerName + " : " + n + " objet(s).");
        if (!own) {
            ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(g.owner));
            if (owner != null) Msg.err(owner, p.getName().getString() + " a pillé votre tombe.");
        }
    }

    /** /tombe recuperer : à distance, payant (sauf mort dans le vide). */
    public static String remote(ServerPlayer p, long id) {
        Grave g = null;
        for (Grave x : graves()) if (x.id == id) g = x;
        if (g == null) return "Cette tombe n'existe plus.";
        if (!p.getUUID().toString().equals(g.owner)) return "Ce n'est pas votre tombe.";
        long cost = g.voidDeath ? 0 : GodData.settings().graveRemoteCost;
        PlayerProfile pr = GodData.get(p);
        if (pr.money < cost) return "Il faut " + Msg.money(cost) + " pour récupérer à distance (ou allez-y à pied : " + (int) g.x + " " + (int) g.y + " " + (int) g.z + ").";
        pr.money -= cost;
        GodData.dirty();
        loot(p, g);
        if (cost > 0) Msg.info(p, "Récupération à distance : " + Msg.money(cost) + ".");
        return null;
    }

    public static String list(ServerPlayer p) {
        String me = p.getUUID().toString();
        List<Grave> mine = new ArrayList<>();
        for (Grave g : graves()) if (g.owner.equals(me)) mine.add(g);
        Msg.send(p, Msg.header("Vos tombes"));
        if (mine.isEmpty()) {
            Msg.info(p, "Aucune tombe : vous n'avez rien perdu.");
            return null;
        }
        long now = System.currentTimeMillis();
        for (Grave g : mine) {
            long left = Math.max(0, g.created + expireMs() - now);
            long cost = g.voidDeath ? 0 : GodData.settings().graveRemoteCost;
            String where = g.dimension.endsWith("divine") ? "Monde divin" : g.dimension.endsWith("ressources") ? "Monde ressource" : "Monde principal";
            Msg.send(p, Msg.text("⚰ " + g.count + " objet(s) · " + where + " " + (int) g.x + " " + (int) g.y + " " + (int) g.z
                            + " · disparaît dans " + Msg.time(left)
                            + (now - g.created < openMs() ? " · protégée encore " + Msg.time(g.created + openMs() - now) : " · pillable") + "  ", ChatFormatting.GRAY)
                    .append(Msg.button(cost == 0 ? "[Récupérer gratuitement]" : "[Récupérer : " + Msg.money(cost) + "]", "/tombe recuperer " + g.id,
                            "Récupérer à distance", ChatFormatting.GREEN)));
        }
        return null;
    }

    // ------------------------------------------------------------------ Boucle (chaque seconde)

    public static void tickSecond(MinecraftServer server, int counter) {
        if (graves().isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Grave g : new ArrayList<>(graves())) {
            if (now - g.created > expireMs()) {
                remove(server, g);
                GodData.log("TOMBE #" + g.id + " de " + g.ownerName + " disparue (" + g.count + " objets)");
                ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(g.owner));
                if (owner != null) Msg.err(owner, "Votre tombe (" + g.count + " objets) est tombée en poussière.");
                continue;
            }
            if (g.voidDeath) continue;
            ServerLevel level = level(server, g);
            if (level == null) continue;
            boolean seen = false;
            for (ServerPlayer p : level.players()) {
                double d = p.distanceToSqr(g.x, g.y, g.z);
                if (d < 64 * 64) seen = true;
                if (d > 2.0 * 2.0 || !p.isAlive() || p.isSpectator()) continue;
                if (canLoot(p, g)) {
                    loot(p, g);
                    break;
                }
                p.displayClientMessage(Msg.text("⚰ Tombe de " + g.ownerName + " : pillable dans " + Msg.time(g.created + openMs() - now), ChatFormatting.GRAY), true);
            }
            if (seen && counter % 2 == 0 && graves().contains(g)) {
                level.sendParticles(ParticleTypes.SOUL, g.x, g.y + 0.6, g.z, 2, 0.25, 0.3, 0.25, 0.01);
            }
        }
    }
}
