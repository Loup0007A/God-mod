package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Tier;
import com.godmod.menu.GodMenu;
import com.godmod.menu.Menus;
import com.godmod.menu.Page;
import com.godmod.progress.JobSystem;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Transcendance : les paliers au-delà du Dieu suprême (Titan, Primordial, Divinité astrale, Éternel, Créateur).
 * On n'y monte pas automatiquement : il faut réunir Essence, fidèles, niveaux de métier et prestige du générateur,
 * puis faire une offrande en pièces avec /transcender.
 * Chaque palier transcendant donne +5 % d'XP de métier et d'Essence, des caractéristiques plus hautes,
 * plus de Foi, un monde divin plus grand et plus de warps.
 */
public final class Transcendence {
    private Transcendence() {
    }

    public record Requirement(String label, long have, long need) {
        public boolean ok() {
            return have >= need;
        }
    }

    public static List<Requirement> requirements(PlayerProfile p, Tier t) {
        List<Requirement> r = new ArrayList<>();
        r.add(new Requirement("Palier précédent : " + (t.ordinal() > 0 ? Tier.values()[t.ordinal() - 1].label : "—"),
                p.tier.ordinal() + 1 >= t.ordinal() ? 1 : 0, 1));
        r.add(new Requirement("Essence", p.essence, PowerSystem.threshold(t)));
        r.add(new Requirement("Fidèles au total", GodData.totalFollowers(p.uuid), t.followers));
        r.add(new Requirement("Niveaux de métier cumulés", JobSystem.totalLevels(p), t.jobLevels));
        r.add(new Requirement("Prestige du générateur", GeneratorSystem.level(p), t.generator));
        r.add(new Requirement("Offrande (pièces)", p.money, t.offering));
        return r;
    }

    public static boolean ready(PlayerProfile p, Tier t) {
        if (t == null || !t.isTranscendent() || p.tier.next() != t) return false;
        for (Requirement r : requirements(p, t)) if (!r.ok()) return false;
        return true;
    }

    /** Bonus permanent d'XP de métier et d'Essence : +5 % par palier transcendant. */
    public static double bonus(PlayerProfile p) {
        return 1.0 + 0.05 * p.tier.transcendence();
    }

    public static String transcend(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Tier next = p.tier.next();
        if (next == null) return "Vous êtes déjà " + p.tier.label + " : il n'existe rien au-delà.";
        if (!next.isTranscendent()) return "La transcendance commence après le palier Dieu suprême. Prochain palier : " + next.label + ".";
        for (Requirement r : requirements(p, next)) {
            if (!r.ok()) return "Condition manquante pour devenir " + next.label + " : " + r.label() + " (" + Msg.num(r.have()) + " / " + Msg.num(r.need()) + "). /transcender pour le détail.";
        }
        p.money -= next.offering;
        p.transcendNotified = false;
        GodData.dirty();
        PowerSystem.setTier(player.level().getServer(), p, next, "a transcendé et devient");
        ServerLevel l = (ServerLevel) player.level();
        for (int i = 0; i < 4; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l, EntitySpawnReason.TRIGGERED);
            if (bolt == null) continue;
            double a = Math.PI / 2 * i;
            bolt.snapTo(player.getX() + Math.cos(a) * 3, player.getY(), player.getZ() + Math.sin(a) * 3);
            bolt.setVisualOnly(true);
            l.addFreshEntity(bolt);
        }
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 200, 1, 2, 1, 0.6);
        l.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 120, 0.5, 3, 0.5, 0.1);
        for (ServerPlayer sp : player.level().getServer().getPlayerList().getPlayers())
            Fx.sound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 0.6F);
        Fx.title(player, Component.literal("✦ " + next.label + " ✦").withStyle(next.color, ChatFormatting.BOLD),
                Component.literal("Transcendance " + next.transcendence() + "/5 · +" + (5 * next.transcendence()) + " % d'XP et d'Essence").withStyle(ChatFormatting.YELLOW));
        GodData.log("TRANSCENDANCE : " + p.name + " devient " + next.label + " (offrande " + next.offering + ")");
        return null;
    }

    // ------------------------------------------------------------------ Interface

    private static final Item[] ICONS = {Items.WITHER_SKELETON_SKULL, Items.HEART_OF_THE_SEA, Items.END_CRYSTAL, Items.TOTEM_OF_UNDYING, Items.NETHER_STAR};

    public static void open(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Tier next = p.tier.next();
        Page page = new Page("Transcendance — " + p.tier.label).frame(Items.WHITE_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.BEACON, "Au-delà du Dieu suprême", ChatFormatting.WHITE,
                "§7Cinq paliers transcendants : Titan, Primordial,", "§7Divinité astrale, Éternel et Créateur.",
                "§7Il faut tout réunir, puis faire une offrande.",
                "§7Chaque palier : +5 % d'XP de métier et d'Essence,", "§7caractéristiques plus hautes, plus de Foi,",
                "§7un monde divin plus grand, plus de warps.",
                "§dVotre bonus actuel : +" + (5 * p.tier.transcendence()) + " %"));
        Tier[] all = Tier.values();
        int[] slots = {20, 21, 22, 23, 24};
        int i = 0;
        for (Tier t : all) {
            if (!t.isTranscendent()) continue;
            boolean reached = p.tier.atLeast(t);
            boolean isNext = t == next;
            List<Component> lore = new ArrayList<>();
            for (Requirement r : requirements(p, t)) {
                if (r.label().startsWith("Palier")) continue;
                lore.add(Page.line((reached || r.ok() ? "§a✔ " : "§c✖ ") + "§7" + r.label() + " : §f" + Msg.num(r.need())
                        + (reached ? "" : " §8(" + Msg.num(Math.min(r.have(), r.need())) + ")")));
            }
            lore.add(Page.line("§7Plafond de caractéristique : §f" + t.statCap));
            lore.add(Page.line("§7Bonus : §d+" + (5 * t.transcendence()) + " % d'XP et d'Essence"));
            lore.add(Page.line(""));
            lore.add(Page.line(reached ? "§aAtteint" : isNext ? (ready(p, t) ? "§eMaj + clic pour transcender !" : "§7Prochain palier") : "§8À venir"));
            page.set(slots[i], Page.icon(reached ? Items.LIME_DYE : ICONS[i], t.transcendence(), Ranks.tierTag(t), t.color, lore, isNext && ready(p, t)), (sp, c) -> {
                if (!isNext) return;
                if (c != GodMenu.Click.SHIFT) {
                    Msg.info(sp, ready(GodData.get(sp), t) ? "Maj + clic pour confirmer : l'offrande de " + Msg.money(t.offering) + " sera dépensée."
                            : "Conditions non remplies pour devenir " + t.label + ".");
                    return;
                }
                String err = transcend(sp);
                if (err != null) Msg.err(sp, err);
                sp.level().getServer().execute(() -> open(sp));
            });
            i++;
        }
        if (!p.tier.atLeast(Tier.DIEU_SUPREME)) {
            page.set(40, Page.icon(Items.BARRIER, "Pas encore", ChatFormatting.GRAY, "§7Atteignez d'abord le palier Dieu suprême",
                    "§7(" + Msg.num(PowerSystem.threshold(Tier.DIEU_SUPREME)) + " Essence et " + Tier.DIEU_SUPREME.followers + " fidèles au total)."));
        }
        page.back((sp, c) -> sp.level().getServer().execute(() -> Menus.openMain(sp))).close();
        page.open(player);
    }
}
