package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.system.WorldBuilder;
import com.godmod.util.Glyphs;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Décor des caisses : au-dessus de chaque caisse (là où elle est, sans toucher aux blocs), un joyau qui tourne
 * et brille à la couleur de la caisse, un panneau flottant avec son nom, et un anneau de particules.
 * Uniquement des entités d'affichage : /caisses decor retirer les enlève toutes.
 */
public final class CrateDecor {
    public static final String TAG = "godmod_crate";
    private static int tick = 0;

    private CrateDecor() {
    }

    private record Spot(ServerLevel level, double x, double y, double z, CrateSystem.Crate crate) {
    }

    private static List<Spot> spots(MinecraftServer server) {
        List<Spot> out = new ArrayList<>();
        for (Map.Entry<String, String> e : GodData.state().points.entrySet()) {
            if (!e.getValue().startsWith("crate:")) continue;
            CrateSystem.Crate c = CrateSystem.Crate.parse(e.getValue().substring(6));
            String[] k = e.getKey().split("\\|");
            if (c == null || k.length != 4) continue;
            ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(k[0])));
            if (l == null) continue;
            out.add(new Spot(l, Integer.parseInt(k[1]) + 0.5, Integer.parseInt(k[2]), Integer.parseInt(k[3]) + 0.5, c));
        }
        return out;
    }

    private static String gem(CrateSystem.Crate c) {
        return switch (c) {
            case COMMUNE -> "minecraft:quartz";
            case RARE -> "minecraft:heart_of_the_sea";
            case EPIQUE -> "minecraft:end_crystal";
            case DIVINE -> "minecraft:nether_star";
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

    private static String color(CrateSystem.Crate c) {
        return String.format("#%06X", rgb(c));
    }

    /** Pose (ou repose) le décor de toutes les caisses. Renvoie le nombre de caisses décorées. */
    public static int place(MinecraftServer server) {
        remove(server);
        List<Spot> spots = spots(server);
        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i);
            String tags = String.format("Tags:[\"%s\",\"%s_%d\"]", TAG, TAG, i);
            // Joyau tournant, avec un contour lumineux de la couleur de la caisse.
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "summon minecraft:item_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s_%d\",\"%s_spin\"],item:{id:\"%s\",count:1},billboard:\"fixed\","
                            + "Glowing:1b,glow_color_override:%d,brightness:{sky:15,block:15},"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.65f,0.65f,0.65f]}}",
                    s.x(), s.y() + 1.55, s.z(), TAG, TAG, i, TAG, gem(s.crate()), rgb(s.crate())));
            // Panneau flottant.
            String key = Glyphs.on() ? Glyphs.KEY + " " : "";
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "summon minecraft:text_display %.2f %.2f %.2f {%s,billboard:\"center\",alignment:\"center\",shadow:1b,line_width:200,"
                            + "background:%d,brightness:{sky:15,block:15},"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1f,1f,1f]},"
                            + "text:[{text:\"✦ CAISSE %s ✦\\n\",color:\"%s\",bold:true},{text:\"%sClic droit avec une clé\",color:\"gray\",bold:false}]}",
                    s.x(), s.y() + 2.15, s.z(), tags, 0x7015172A, s.crate().label.toUpperCase(Locale.ROOT), color(s.crate()), key));
        }
        GodData.settings().crateDecor = true;
        GodData.dirty();
        return spots.size();
    }

    public static void remove(MinecraftServer server) {
        ServerLevel any = server.overworld();
        WorldBuilder.run(server, any, "kill @e[tag=" + TAG + "]");
        GodData.settings().crateDecor = false;
        GodData.dirty();
    }

    /** Anciens panneaux posés automatiquement juste au-dessus des caisses (« Caisse … » / « clic droit »). */
    public static int removeOldLabels(MinecraftServer server) {
        int n = 0;
        for (Spot s : spots(server)) {
            WorldBuilder.run(server, s.level(), String.format(Locale.ROOT,
                    "kill @e[type=minecraft:text_display,tag=%s,x=%.2f,y=%.2f,z=%.2f,distance=..3.2]",
                    WorldBuilder.LABEL_TAG, s.x(), s.y() + 2.0, s.z()));
            n++;
        }
        return n;
    }

    /** Chaque tick : rotation douce du joyau (toutes les secondes) et anneau de particules. */
    public static void tick(MinecraftServer server) {
        if (!GodData.settings().crateDecor) return;
        tick++;
        if (tick % 20 == 0) {
            double a = Math.toRadians((tick / 20 % 4) * 90 + 90);
            float sy = (float) Math.sin(a / 2), cw = (float) Math.cos(a / 2);
            float bob = (float) (Math.sin(tick / 20.0) * 0.08);
            WorldBuilder.run(server, server.overworld(), String.format(Locale.ROOT,
                    "execute as @e[type=minecraft:item_display,tag=%s_spin] run data merge entity @s {start_interpolation:0,interpolation_duration:20,"
                            + "transformation:{left_rotation:[0f,%.4ff,0f,%.4ff],right_rotation:[0f,0f,0f,1f],translation:[0f,%.3ff,0f],scale:[0.65f,0.65f,0.65f]}}",
                    TAG, sy, cw, bob));
        }
        if (tick % 4 != 0) return;
        double t = tick / 10.0;
        for (Spot s : spots(server)) {
            if (s.level().players().stream().noneMatch(p -> p.distanceToSqr(s.x(), s.y(), s.z()) < 48 * 48)) continue;
            ParticleOptions part = switch (s.crate()) {
                case COMMUNE -> ParticleTypes.END_ROD;
                case RARE -> ParticleTypes.ENCHANT;
                case EPIQUE -> ParticleTypes.WITCH;
                case DIVINE -> ParticleTypes.TOTEM_OF_UNDYING;
            };
            for (int k = 0; k < 2; k++) {
                double ang = t + k * Math.PI;
                s.level().sendParticles(part, s.x() + Math.cos(ang) * 0.75, s.y() + 1.15, s.z() + Math.sin(ang) * 0.75, 1, 0, 0, 0, 0);
            }
        }
    }
}
