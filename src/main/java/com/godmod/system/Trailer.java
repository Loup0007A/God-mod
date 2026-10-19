package com.godmod.system;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/**
 * /trailer_lancer : cinématique de 2 min 30 pour filmer un trailer.
 * La caméra du joueur qui lance la commande suit une série de plans (travellings, orbites, grues) à travers
 * les lieux et fonctionnalités du serveur ; un mannequin portant son skin joue son personnage.
 * Pendant le tournage, le chat est coupé pour tout le monde et le réalisateur ne reçoit plus aucun message.
 * Il suffit d'enregistrer l'écran (F1 pour masquer l'interface).
 */
public final class Trailer {
    private static final int CY = WorldBuilder.CY;

    /** Un plan : durée, monde, titres, trajectoires de la caméra, du point visé et de l'acteur (t de 0 à 1). */
    private record Shot(int ticks, Function<MinecraftServer, ServerLevel> level, String title, String subtitle,
                        DoubleFunction<Vec3> cam, DoubleFunction<Vec3> look, DoubleFunction<Vec3> actor,
                        Fx2 fx, boolean trident) {
    }

    @FunctionalInterface
    private interface Fx2 {
        void run(ServerLevel level, int tick, Vec3 actorPos);
    }

    private static final class Session {
        final ServerPlayer viewer;
        final GameType oldMode;
        final ResourceKey<Level> oldDim;
        final double ox, oy, oz;
        final float oyaw, opitch;
        final String address;
        final List<Shot> shots = new ArrayList<>();
        int shot = -1, tick;
        Entity camera;
        LivingEntity actor;
        ServerLevel level;
        Vec3 lastActor;

        Session(ServerPlayer viewer, String address) {
            this.viewer = viewer;
            this.oldMode = viewer.gameMode.getGameModeForPlayer();
            this.oldDim = viewer.level().dimension();
            this.ox = viewer.getX();
            this.oy = viewer.getY();
            this.oz = viewer.getZ();
            this.oyaw = viewer.getYRot();
            this.opitch = viewer.getXRot();
            this.address = address;
        }
    }

    private static Session session;

    private Trailer() {
    }

    public static boolean active() {
        return session != null;
    }

    public static boolean isViewer(ServerPlayer p) {
        return session != null && session.viewer == p;
    }

    // ------------------------------------------------------------------ Outils de trajectoire

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static DoubleFunction<Vec3> fixed(double x, double y, double z) {
        Vec3 v = new Vec3(x, y, z);
        return t -> v;
    }

    private static DoubleFunction<Vec3> line(double x1, double y1, double z1, double x2, double y2, double z2) {
        return t -> {
            double e = ease(t);
            return new Vec3(x1 + (x2 - x1) * e, y1 + (y2 - y1) * e, z1 + (z2 - z1) * e);
        };
    }

    /** Orbite autour de (cx, cz) à la hauteur y, de l'angle a0 à a1 (degrés). */
    private static DoubleFunction<Vec3> orbit(double cx, double y, double cz, double r, double a0, double a1) {
        return t -> {
            double a = Math.toRadians(a0 + (a1 - a0) * ease(t));
            return new Vec3(cx + Math.cos(a) * r, y, cz + Math.sin(a) * r);
        };
    }

    private static DoubleFunction<Vec3> follow(DoubleFunction<Vec3> target, double dx, double dy, double dz) {
        return t -> target.apply(t).add(dx, dy, dz);
    }

    private static ServerLevel hub(MinecraftServer s) {
        return Hub.level(s);
    }

    // ------------------------------------------------------------------ Scénario

    private static void script(Session s, MinecraftServer server) {
        List<Shot> l = s.shots;
        int spawnZ = 17;
        // 1. Ouverture : grande orbite aérienne autour du Sanctuaire.
        l.add(new Shot(240, Trailer::hub, "✦ LE PANTHÉON ✦", "Un monde où les joueurs deviennent des dieux",
                orbit(0, CY + 45, -8, 80, 200, 300), fixed(0, CY + 6, -12), fixed(0.5, CY + 1, spawnZ + 0.5), null, false));
        // 2. Arrivée : travelling derrière le personnage qui remonte l'avenue.
        DoubleFunction<Vec3> walk1 = line(0.5, CY + 1, 34, 0.5, CY + 1, 9.5);
        l.add(new Shot(240, Trailer::hub, "Commencez simple mortel…", "",
                follow(walk1, 2.5, 3.2, 6.5), follow(walk1, 0, 1.6, -6), walk1, null, false));
        // 3. Le temple : plan grue qui s'élève.
        DoubleFunction<Vec3> walk2 = line(0.5, CY + 1, -8.5, 0.5, CY + 4, -27.5);
        l.add(new Shot(240, Trailer::hub, "…et élevez-vous au rang de dieu", "10 paliers, du Mortel au Créateur",
                line(16, CY + 3, -6, 11, CY + 22, -16), t -> walk2.apply(t).add(0, 6, -6), walk2, null, false));
        // 4. Caisses : travelling latéral, explosion de lumière sur la caisse.
        DoubleFunction<Vec3> walk3 = line(-4.5, CY + 1, 11.5, -12.5, CY + 1, 7.5);
        l.add(new Shot(260, Trailer::hub, "Caisses, clés et générateurs", "Des récompenses à chaque pas",
                line(-2, CY + 3.5, 1, -7, CY + 3, 0), follow(walk3, 0, 1.4, 0), walk3, (lv, tk, a) -> {
            if (tk > 150 && tk % 12 == 0) {
                lv.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, -14.5, CY + 3, 6.5, 40, 0.4, 0.6, 0.4, 0.35);
                lv.sendParticles(ParticleTypes.END_ROD, -11.5, CY + 3, 11.5, 15, 0.3, 0.5, 0.3, 0.05);
            }
        }, false));
        // 5. Métiers et quêtes : orbite serrée autour du pupitre.
        DoubleFunction<Vec3> walk4 = line(4.5, CY + 1, 17.5, 1.5, CY + 1, 20.5);
        l.add(new Shot(220, Trailer::hub, "5 métiers · quêtes · défis", "Talents, prestige et ascension",
                orbit(0.5, CY + 4, 21.5, 7, -30, 110), fixed(0.5, CY + 2, 21.5), walk4, (lv, tk, a) -> {
            if (tk % 6 == 0) lv.sendParticles(ParticleTypes.ENCHANT, 0.5, CY + 2.5, 22.5, 20, 0.6, 0.6, 0.6, 0.5);
        }, false));
        // 6. Boutique : travelling avant vers le pavillon.
        DoubleFunction<Vec3> walk5 = line(-22.5, CY + 1, -12.5, -28.5, CY + 1, -19.5);
        l.add(new Shot(200, Trailer::hub, "Boutique · privilèges · zone AFK", "Tout s'achète, tout se mérite",
                line(-14, CY + 7, -4, -22, CY + 4, -11), fixed(-31, CY + 4, -22), walk5, null, false));
        // 7. Arène : orbite autour du personnage, foudre divine.
        double ax = WorldBuilder.ARENA_DX + 0.5;
        l.add(new Shot(320, Trailer::hub, "Pouvoirs divins", "Rébellions · tournois · Titan",
                orbit(ax, CY + 6, 0.5, 15, 180, 400), fixed(ax, CY + 2.2, 0.5), fixed(ax, CY + 1, 0.5), (lv, tk, a) -> {
            if (tk % 45 == 20) {
                for (int i = 0; i < 3; i++) {
                    double ang = Math.toRadians(tk * 7 + i * 120);
                    LightningBolt b = EntityType.LIGHTNING_BOLT.create(lv, EntitySpawnReason.TRIGGERED);
                    if (b == null) continue;
                    b.snapTo(a.x + Math.cos(ang) * 6, a.y, a.z + Math.sin(ang) * 6);
                    b.setVisualOnly(true);
                    lv.addFreshEntity(b);
                }
            }
            if (tk % 4 == 0) Fx.ring(lv, ParticleTypes.SOUL_FIRE_FLAME, a.x, a.y + 0.2, a.z, 2.5 + (tk % 40) / 10.0, 30);
        }, true));
        // 8. Monde divin du réalisateur (s'il en a un).
        PlayerProfile p = GodData.get(s.viewer);
        if (DivineWorld.hasWorld(p) && DivineWorld.level(server) != null) {
            ServerLevel dl = DivineWorld.level(server);
            DivineWorld.preparePlot(dl, p);
            BlockPos core = DivineWorld.core(p);
            int top = Math.max(dl.getHeight(Heightmap.Types.MOTION_BLOCKING, core.getX(), core.getZ()), core.getY() + 1);
            double cx = core.getX() + 0.5, cz = core.getZ() + 0.5;
            l.add(new Shot(260, sv -> DivineWorld.level(sv), "Votre propre monde divin", "Une île qui grandit avec vos fidèles",
                    orbit(cx, top + 10, cz, 16, 0, 160), fixed(cx, top + 1, cz), fixed(cx, top, cz), (lv, tk, a) -> {
                if (tk % 5 == 0) lv.sendParticles(ParticleTypes.END_ROD, a.x, a.y + 1, a.z, 6, 3, 2, 3, 0.02);
            }, false));
        }
        // 9. Monde ressource : descente en parachute.
        ServerLevel res = Hub.resources(server);
        if (res != null) {
            int x = 520, z = 520;
            res.getChunk(x >> 4, z >> 4);
            int ground = res.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            DoubleFunction<Vec3> drop = t -> new Vec3(x + 0.5, ground + 40 - 39 * t, z + 0.5);
            l.add(new Shot(300, sv -> Hub.resources(sv), "Un monde sauvage à conquérir", "Minage · chasse au trésor · événements",
                    follow(drop, 7, 3, 7), follow(drop, 0, 0.8, 0), drop, (lv, tk, a) -> {
                if (tk % 3 == 0) {
                    for (int i = 0; i < 8; i++) {
                        double ang = Math.PI * 2 * i / 8;
                        lv.sendParticles(ParticleTypes.CLOUD, a.x + Math.cos(ang) * 1.2, a.y + 2.6, a.z + Math.sin(ang) * 1.2, 1, 0, 0, 0, 0);
                    }
                }
            }, false));
        }
        // 10. Événements : feu d'artifice au-dessus de la fontaine.
        l.add(new Shot(240, Trailer::hub, "Des événements toutes les 45 minutes", "Concours · trésors · pluie de clés",
                line(-30, CY + 14, 26, 30, CY + 14, 26), fixed(0, CY + 14, 0), fixed(0.5, CY + 1, 9.5), (lv, tk, a) -> {
            if (tk % 14 == 0) {
                int i = tk / 14;
                String[] colors = {"16711680", "65280", "255", "16766720", "16711935", "65535"};
                String c1 = colors[i % colors.length], c2 = colors[(i + 2) % colors.length];
                double fx = Math.sin(i * 1.7) * 9, fz = Math.cos(i * 1.3) * 9;
                WorldBuilder.run(lv.getServer(), lv, String.format(java.util.Locale.ROOT,
                        "summon minecraft:firework_rocket %.1f %d %.1f {LifeTime:%d,FireworksItem:{id:\"minecraft:firework_rocket\",count:1,"
                                + "components:{\"minecraft:fireworks\":{flight_duration:1,explosions:[{shape:\"large_ball\",colors:[I;%s,%s],has_twinkle:true,has_trail:true}]}}}}",
                        fx, CY + 2, fz, 26 + (i % 3) * 4, c1, c2));
            }
        }, false));
        // 11. Final : face au personnage sur les marches du temple, puis grand recul vers le ciel.
        l.add(new Shot(320, Trailer::hub, "✦ LE PANTHÉON ✦", s.address == null ? "Rejoins-nous et deviens un dieu" : "Rejoins-nous : " + s.address,
                t -> {
                    double e = ease(t);
                    return new Vec3(0.5, CY + 5.6 + 55 * e * e, -23.5 + 70 * e);
                }, t -> new Vec3(0.5, CY + 5.6 - 4 * ease(t), -28.5 + 8 * ease(t)), fixed(0.5, CY + 4, -28.5), (lv, tk, a) -> {
            if (tk % 8 == 0) lv.sendParticles(ParticleTypes.END_ROD, a.x, a.y + 2, a.z, 12, 1.5, 2, 1.5, 0.05);
        }, true));
    }

    // ------------------------------------------------------------------ Lancement / arrêt

    public static String start(ServerPlayer viewer, String address) {
        if (session != null) return "Un tournage est déjà en cours (/trailer_stop).";
        MinecraftServer server = viewer.level().getServer();
        Session s = new Session(viewer, address);
        script(s, server);
        session = s;
        com.godmod.progress.Hud.hide(viewer);
        viewer.closeContainer();
        viewer.setGameMode(GameType.SPECTATOR);
        Msg.broadcast(server, "🎬 Tournage du trailer en cours : le chat est en pause pendant environ " + Msg.time(totalTicks(s) * 50L) + ".", ChatFormatting.GOLD);
        WorldBuilder.run(server, Hub.level(server), "playsound minecraft:music_disc.otherside record " + viewer.getName().getString() + " ~ ~ ~ 1 1 1");
        GodData.log("TRAILER : lancé par " + viewer.getName().getString());
        return null;
    }

    private static int totalTicks(Session s) {
        int n = 0;
        for (Shot sh : s.shots) n += sh.ticks();
        return n;
    }

    public static void stop(MinecraftServer server, String why) {
        Session s = session;
        if (s == null) return;
        session = null;
        try {
            if (s.camera != null) s.camera.discard();
            if (s.actor != null) s.actor.discard();
            ServerPlayer v = s.viewer;
            if (!v.hasDisconnected()) {
                v.setCamera(v);
                v.setGameMode(s.oldMode);
                ServerLevel back = server.getLevel(s.oldDim);
                if (back != null) Teleport.to(v, back, s.ox, s.oy, s.oz);
                v.setYRot(s.oyaw);
                v.setXRot(s.opitch);
                WorldBuilder.run(server, Hub.level(server), "stopsound " + v.getName().getString() + " record");
                Msg.ok(v, "Tournage terminé" + (why == null ? "." : " : " + why));
            }
        } catch (Exception e) {
            GodMod.LOGGER.error("[God Mod] Fin du trailer", e);
        }
        Msg.broadcast(server, "🎬 Tournage terminé : le chat est de nouveau ouvert. Merci !", ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ Boucle (chaque tick)

    public static void tick(MinecraftServer server) {
        Session s = session;
        if (s == null) return;
        if (s.viewer.hasDisconnected()) {
            stop(server, "le réalisateur s'est déconnecté");
            return;
        }
        try {
            if (s.shot < 0 || s.tick >= s.shots.get(s.shot).ticks()) {
                s.shot++;
                s.tick = 0;
                if (s.shot >= s.shots.size()) {
                    stop(server, null);
                    return;
                }
                beginShot(server, s);
            }
            Shot sh = s.shots.get(s.shot);
            double t = s.tick / (double) Math.max(1, sh.ticks() - 1);
            Vec3 cam = sh.cam().apply(t), look = sh.look().apply(t);
            Vec3 act = sh.actor() == null ? null : sh.actor().apply(t);
            double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            if (s.camera != null) {
                s.camera.setPos(cam.x, cam.y - s.camera.getEyeHeight(), cam.z);
                s.camera.setYRot(yaw);
                s.camera.setXRot(pitch);
                s.camera.setYHeadRot(yaw);
            }
            if (s.actor != null && act != null) {
                float ayaw;
                if (s.lastActor != null && s.lastActor.distanceToSqr(act) > 0.0004) {
                    ayaw = (float) Math.toDegrees(Math.atan2(-(act.x - s.lastActor.x), act.z - s.lastActor.z));
                } else {
                    ayaw = (float) Math.toDegrees(Math.atan2(-(cam.x - act.x), cam.z - act.z)); // regarde la caméra
                }
                s.actor.setPos(act.x, act.y, act.z);
                s.actor.setYRot(ayaw);
                s.actor.setYHeadRot(ayaw);
                s.actor.setYBodyRot(ayaw);
                s.lastActor = act;
            }
            // La caméra est (re)fixée quelques ticks après l'apparition, quand le client connaît l'entité.
            if (s.camera != null && (s.tick == 3 || (s.tick > 3 && s.viewer.getCamera() != s.camera))) s.viewer.setCamera(s.camera);
            if (sh.fx() != null && act != null) sh.fx().run(s.level, s.tick, act);
            if (s.tick == 15 && sh.title() != null) {
                Fx.title(s.viewer, Component.literal(sh.title()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal(sh.subtitle()).withStyle(ChatFormatting.WHITE));
            }
            s.tick++;
        } catch (Exception e) {
            GodMod.LOGGER.error("[God Mod] Erreur pendant le trailer", e);
            stop(server, "erreur (voir la console)");
        }
    }

    private static void beginShot(MinecraftServer server, Session s) {
        Shot sh = s.shots.get(s.shot);
        ServerLevel lv = sh.level().apply(server);
        if (lv == null) lv = Hub.level(server);
        Vec3 cam = sh.cam().apply(0);
        Vec3 look0 = sh.look().apply(0);
        Vec3 act = sh.actor() == null ? cam : sh.actor().apply(0);
        double ldx = look0.x - cam.x, ldy = look0.y - cam.y, ldz = look0.z - cam.z;
        float yaw0 = (float) Math.toDegrees(Math.atan2(-ldx, ldz));
        float pitch0 = (float) -Math.toDegrees(Math.atan2(ldy, Math.sqrt(ldx * ldx + ldz * ldz)));
        if (lv != s.level) {
            // Nouveau monde : on recrée la caméra et l'acteur sur place.
            s.viewer.setCamera(s.viewer);
            if (s.camera != null) s.camera.discard();
            if (s.actor != null) s.actor.discard();
            s.camera = null;
            s.actor = null;
            s.level = lv;
            s.viewer.teleportTo(lv, cam.x, cam.y - 1.62, cam.z, java.util.Set.of(), yaw0, pitch0, true);
            lv.getChunk(BlockPos.containing(act).getX() >> 4, BlockPos.containing(act).getZ() >> 4);
            s.camera = spawnCamera(lv, cam);
            s.actor = spawnActor(server, lv, act, s.viewer.getName().getString());
        } else {
            // Même monde : la vue passe 3 ticks par les yeux du réalisateur, placé exactement au bon endroit.
            s.viewer.setCamera(s.viewer);
            s.viewer.teleportTo(lv, cam.x, cam.y - 1.62, cam.z, java.util.Set.of(), yaw0, pitch0, true);
            if (s.camera != null) {
                s.camera.setPos(cam.x, cam.y - s.camera.getEyeHeight(), cam.z);
                s.camera.setYRot(yaw0);
                s.camera.setXRot(pitch0);
            }
        }
        if (s.actor != null) {
            s.actor.setPos(act.x, act.y, act.z);
            s.actor.setItemSlot(EquipmentSlot.MAINHAND, sh.trident() ? new ItemStack(Items.TRIDENT) : ItemStack.EMPTY);
        }
        s.lastActor = null;
    }

    private static Entity spawnCamera(ServerLevel lv, Vec3 at) {
        var stand = EntityType.ARMOR_STAND.create(lv, EntitySpawnReason.COMMAND);
        if (stand == null) return null;
        stand.snapTo(at.x, at.y - stand.getEyeHeight(), at.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        lv.addFreshEntity(stand);
        return stand;
    }

    /** Mannequin portant le skin du réalisateur (entité « mannequin » de Minecraft 1.21.9+). */
    private static LivingEntity spawnActor(MinecraftServer server, ServerLevel lv, Vec3 at, String name) {
        String pos = String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", at.x, at.y, at.z);
        WorldBuilder.run(server, lv, "summon minecraft:mannequin " + pos + " {profile:\"" + name + "\",NoGravity:1b,Invulnerable:1b,Silent:1b,immovable:1b}");
        LivingEntity found = findMannequin(lv, at);
        if (found == null) {
            WorldBuilder.run(server, lv, "summon minecraft:mannequin " + pos + " {NoGravity:1b,Invulnerable:1b,Silent:1b}");
            found = findMannequin(lv, at);
        }
        if (found == null) GodMod.LOGGER.warn("[God Mod] Trailer : impossible de faire apparaître le mannequin (tournage sans acteur).");
        return found;
    }

    private static LivingEntity findMannequin(ServerLevel lv, Vec3 at) {
        List<LivingEntity> list = lv.getEntitiesOfClass(LivingEntity.class, new AABB(at.x - 2, at.y - 2, at.z - 2, at.x + 2, at.y + 3, at.z + 2),
                e -> "minecraft:mannequin".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()));
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }
}
