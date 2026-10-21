package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.system.WorldBuilder;
import com.godmod.util.Glyphs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Caisses en 3D : au-dessus de chaque caisse (le bloc existant sert de socle, il n'est jamais modifié),
 * un coffre modélisé dans le pack de ressources — corps, couvercle articulé et joyau flottant — avec son nom.
 * <ul>
 *   <li>au repos : le couvercle « respire » (s'entrouvre légèrement), le joyau tourne et flotte, anneau de particules ;</li>
 *   <li>à l'ouverture d'une caisse : le couvercle s'ouvre grand, le joyau s'élève en tournant vite, colonne de lumière,
 *       puis tout se referme ;</li>
 *   <li>une entité d'interaction couvre le coffre : un clic droit dessus ouvre la caisse.</li>
 * </ul>
 * Uniquement des entités : /caisses decor retirer les enlève toutes.
 */
public final class CrateDecor {
    public static final String TAG = "godmod_crate";
    public static final String HIT = "godmod_crate_hit";
    private static final float SCALE = 0.9F;
    private static int tick = 0;

    private record Spot(int index, ServerLevel level, BlockPos pos, CrateSystem.Crate crate, float yaw) {
        double cx() {
            return pos.getX() + 0.5;
        }

        double cz() {
            return pos.getZ() + 0.5;
        }

        /** Centre du corps du coffre. */
        double cy() {
            return pos.getY() + 1 + SCALE / 2 + 0.01 + GodData.settings().crateModelOffset;
        }
    }

    /** Ouvertures en cours : numéro de caisse → tick de fin. */
    private static final Map<Integer, Integer> OPEN = new HashMap<>();

    private CrateDecor() {
    }

    private static List<Spot> spots(MinecraftServer server) {
        List<Spot> out = new ArrayList<>();
        Map<String, String> sorted = new TreeMap<>(GodData.state().points);
        int i = 0;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!e.getValue().startsWith("crate:")) continue;
            CrateSystem.Crate c = CrateSystem.Crate.parse(e.getValue().substring(6));
            String[] k = e.getKey().split("\\|");
            if (c == null || k.length != 4) continue;
            ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(k[0])));
            if (l == null) continue;
            BlockPos pos = new BlockPos(Integer.parseInt(k[1]), Integer.parseInt(k[2]), Integer.parseInt(k[3]));
            float yaw = 0;
            BlockState st = l.getBlockState(pos);
            if (st.hasProperty(HorizontalDirectionalBlock.FACING)) yaw = st.getValue(HorizontalDirectionalBlock.FACING).toYRot();
            out.add(new Spot(i++, l, pos, c, yaw));
        }
        return out;
    }

    private static String kind(CrateSystem.Crate c) {
        return switch (c) {
            case COMMUNE -> "commune";
            case RARE -> "rare";
            case EPIQUE -> "epique";
            case DIVINE -> "divine";
        };
    }

    private static int rgb(CrateSystem.Crate c) {
        return switch (c) {
            case COMMUNE -> 0xE8E6F0;
            case RARE -> 0x4F8CFF;
            case EPIQUE -> 0xB45CFF;
            case DIVINE -> 0xFFC93C;
        };
    }

    private static String tags(Spot s, String part) {
        return String.format(Locale.ROOT, "Tags:[\"%s\",\"%s_%d\",\"%s_%s\",\"%s_%d_%s\"]", TAG, TAG, s.index(), TAG, part, TAG, s.index(), part);
    }

    private static String item(Spot s, String part) {
        return String.format("item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"godciv:crate_%s_%s\"}}", kind(s.crate()), part);
    }

    private static String transform(float rx, float ry, double ty, float scale) {
        // Rotation (axe X pour le couvercle, axe Y pour le joyau) en quaternion.
        float qx = (float) Math.sin(Math.toRadians(rx) / 2), qy = (float) Math.sin(Math.toRadians(ry) / 2);
        float qw = (float) (Math.cos(Math.toRadians(rx) / 2) * Math.cos(Math.toRadians(ry) / 2));
        if (rx != 0 && ry != 0) qw = (float) Math.sqrt(Math.max(0, 1 - qx * qx - qy * qy));
        return String.format(Locale.ROOT, "transformation:{left_rotation:[%.4ff,%.4ff,0f,%.4ff],right_rotation:[0f,0f,0f,1f],translation:[0f,%.3ff,0f],scale:[%.3ff,%.3ff,%.3ff]}",
                qx, qy, qw, ty, scale, scale, scale);
    }

    /** Position de la charnière du couvercle (arrière du coffre, en haut du corps). */
    private static double[] hinge(Spot s) {
        double dz = -6 / 16.0 * SCALE, dy = 1 / 16.0 * SCALE;
        double yaw = Math.toRadians(s.yaw());
        return new double[]{s.cx() - Math.sin(yaw) * dz, s.cy() + dy, s.cz() + Math.cos(yaw) * dz};
    }

    /** Pose (ou repose) les coffres 3D sur toutes les caisses. Renvoie leur nombre. */
    public static int place(MinecraftServer server) {
        remove(server);
        List<Spot> spots = spots(server);
        for (Spot s : spots) {
            String common = "billboard:\"fixed\",brightness:{sky:15,block:12},view_range:1.5f";
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "summon minecraft:item_display %.3f %.3f %.3f {%s,Rotation:[%.1ff,0f],%s,%s,%s}",
                    s.cx(), s.cy(), s.cz(), tags(s, "body"), s.yaw(), item(s, "body"), common, transform(0, 0, 0, SCALE)));
            double[] h = hinge(s);
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "summon minecraft:item_display %.3f %.3f %.3f {%s,Rotation:[%.1ff,0f],%s,%s,%s}",
                    h[0], h[1], h[2], tags(s, "lid"), s.yaw(), item(s, "lid"), common, transform(0, 0, 0, SCALE)));
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "summon minecraft:item_display %.3f %.3f %.3f {%s,%s,%s,Glowing:1b,glow_color_override:%d,%s}",
                    s.cx(), s.cy() + SCALE / 2 + 0.6, s.cz(), tags(s, "gem"), item(s, "gem"), common, rgb(s.crate()), transform(0, 0, 0, 0.55F)));
            String key = Glyphs.on() ? Glyphs.KEY + " " : "";
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "summon minecraft:text_display %.3f %.3f %.3f {%s,billboard:\"center\",alignment:\"center\",shadow:1b,line_width:200,"
                            + "background:%d,brightness:{sky:15,block:15},"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.9f,0.9f,0.9f]},"
                            + "text:[{text:\"✦ CAISSE %s ✦\\n\",color:\"#%06X\",bold:true},{text:\"%sClic droit avec une clé\",color:\"gray\",bold:false}]}",
                    s.cx(), s.cy() + SCALE / 2 + 1.15, s.cz(), tags(s, "label"), 0x7015172A,
                    s.crate().label.toUpperCase(Locale.ROOT), rgb(s.crate()), key));
            // Zone cliquable qui couvre le coffre.
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "summon minecraft:interaction %.3f %.3f %.3f {Tags:[\"%s\",\"%s\",\"%s_type_%s\"],width:%.2ff,height:%.2ff,response:1b}",
                    s.cx(), s.pos().getY() + 1.0, s.cz(), TAG, HIT, TAG, s.crate().name(), SCALE + 0.1, SCALE + 0.35));
        }
        OPEN.clear();
        GodData.settings().crateDecor = true;
        GodData.dirty();
        return spots.size();
    }

    public static void remove(MinecraftServer server) {
        WorldBuilder.run(server, server.overworld(), "kill @e[tag=" + TAG + "]");
        OPEN.clear();
        GodData.settings().crateDecor = false;
        GodData.dirty();
    }

    /** Anciens panneaux posés automatiquement juste au-dessus des caisses (« Caisse … » / « clic droit »). */
    public static int removeOldLabels(MinecraftServer server) {
        int n = 0;
        for (Spot s : spots(server)) {
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "kill @e[type=minecraft:text_display,tag=%s,x=%.2f,y=%.2f,z=%.2f,distance=..3.2]",
                    WorldBuilder.LABEL_TAG, s.cx(), s.pos().getY() + 2.0, s.cz()));
            n++;
        }
        return n;
    }

    /** Clic droit sur la zone d'un coffre 3D : renvoie le type de caisse, ou null. */
    public static CrateSystem.Crate crateOf(Entity e) {
        if (!e.getTags().contains(HIT)) return null;
        for (String t : e.getTags()) {
            if (t.startsWith(TAG + "_type_")) return CrateSystem.Crate.parse(t.substring((TAG + "_type_").length()));
        }
        return null;
    }

    private static void merge(MinecraftServer server, ServerLevel level, String selector, String nbt) {
        WorldBuilder.run(server, level, "execute as @e[" + selector + "] run data merge entity @s {" + nbt + "}");
    }

    /** Lance l'animation d'ouverture sur le coffre du bon type le plus proche du joueur. */
    public static void onOpen(ServerPlayer player, CrateSystem.Crate c) {
        if (!GodData.settings().crateDecor) return;
        MinecraftServer server = player.level().getServer();
        Spot best = null;
        double bd = 12 * 12;
        for (Spot s : spots(server)) {
            if (s.crate() != c || s.level() != player.level()) continue;
            double d = player.distanceToSqr(s.cx(), s.cy(), s.cz());
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        if (best == null) return;
        Spot s = best;
        OPEN.put(s.index(), tick + 90);
        merge(server, s.level(), "tag=" + TAG + "_" + s.index() + "_lid", "start_interpolation:0,interpolation_duration:6," + transform(-105, 0, 0, SCALE));
        merge(server, s.level(), "tag=" + TAG + "_" + s.index() + "_gem", "start_interpolation:0,interpolation_duration:30," + transform(0, 179, 0.7, 0.75F));
        s.level().playSound(null, s.pos().above(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
        s.level().playSound(null, s.pos().above(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    private static void close(MinecraftServer server, Spot s) {
        merge(server, s.level(), "tag=" + TAG + "_" + s.index() + "_lid", "start_interpolation:0,interpolation_duration:10," + transform(0, 0, 0, SCALE));
        merge(server, s.level(), "tag=" + TAG + "_" + s.index() + "_gem", "start_interpolation:0,interpolation_duration:20," + transform(0, 0, 0, 0.55F));
        s.level().playSound(null, s.pos().above(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.8F, 0.9F);
    }

    private static ParticleOptions particle(CrateSystem.Crate c) {
        return switch (c) {
            case COMMUNE -> ParticleTypes.END_ROD;
            case RARE -> ParticleTypes.ENCHANT;
            case EPIQUE -> ParticleTypes.WITCH;
            case DIVINE -> ParticleTypes.TOTEM_OF_UNDYING;
        };
    }

    /** Chaque tick : animations au repos, ouvertures, particules. */
    public static void tick(MinecraftServer server) {
        if (!GodData.settings().crateDecor) return;
        tick++;
        boolean second = tick % 20 == 0;
        boolean fast = tick % 4 == 0;
        if (!second && !fast) return;
        List<Spot> spots = spots(server);
        int step = (tick / 20) % 4;
        float gemAngle = step == 3 ? 359.9F : step * 90 + 90;
        float bob = (float) (Math.sin(tick / 20.0) * 0.06);
        for (Spot s : spots) {
            Integer end = OPEN.get(s.index());
            boolean open = end != null;
            if (open && tick >= end) {
                OPEN.remove(s.index());
                close(server, s);
                open = false;
            }
            if (second && !open) {
                // Joyau : un quart de tour par seconde et léger flottement.
                merge(server, s.level(), "tag=" + TAG + "_" + s.index() + "_gem",
                        "start_interpolation:0,interpolation_duration:20," + transform(0, gemAngle, bob, 0.55F));
            }
            if (second && !open && (tick / 20) % 3 == 0) {
                // Le couvercle « respire » : il s'entrouvre puis se referme.
                merge(server, s.level(), "tag=" + TAG + "_" + s.index() + "_lid",
                        "start_interpolation:0,interpolation_duration:15," + transform((tick / 60) % 2 == 0 ? -9 : 0, 0, 0, SCALE));
            }
            if (!fast) continue;
            if (s.level().players().stream().noneMatch(p -> p.distanceToSqr(s.cx(), s.cy(), s.cz()) < 48 * 48)) continue;
            double t = tick / 10.0;
            ParticleOptions part = particle(s.crate());
            for (int k = 0; k < 2; k++) {
                double ang = t + k * Math.PI;
                s.level().sendParticles(part, s.cx() + Math.cos(ang) * 0.8, s.pos().getY() + 1.1, s.cz() + Math.sin(ang) * 0.8, 1, 0, 0, 0, 0);
            }
            if (open) { // colonne de lumière pendant l'ouverture
                s.level().sendParticles(ParticleTypes.END_ROD, s.cx(), s.cy() + 0.5, s.cz(), 4, 0.12, 0.6, 0.12, 0.02);
                s.level().sendParticles(part, s.cx(), s.cy() + 0.8, s.cz(), 3, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }
}
