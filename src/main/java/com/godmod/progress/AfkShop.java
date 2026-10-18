package com.godmod.progress;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.menu.Menus;
import com.godmod.menu.Page;
import com.godmod.system.AfkSystem;
import com.godmod.system.Perks;
import com.godmod.system.PowerSystem;
import com.godmod.system.Shop;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** /afkshop : ce que l'on achète avec les jetons gagnés dans la zone AFK. */
public final class AfkShop {
    private AfkShop() {
    }

    public record Offer(String label, String description, long cost, Item icon, int count, Consumer<ServerPlayer> give) {
    }

    public static final List<Offer> OFFERS = new ArrayList<>();

    private static void o(String label, String desc, long cost, Item icon, int count, Consumer<ServerPlayer> give) {
        OFFERS.add(new Offer(label, desc, cost, icon, count, give));
    }

    static {
        o("1 000 pièces", "De l'argent pour la boutique", 40, Items.GOLD_INGOT, 1, sp -> GodData.get(sp).money += 1000);
        o("250 Essence", "Pour monter de palier", 60, Items.EXPERIENCE_BOTTLE, 1, sp -> PowerSystem.addEssence(sp, 250, "boutique AFK"));
        o("Clé Commune", "Pour la caisse Commune du Sanctuaire", 60, Items.TRIPWIRE_HOOK, 1, sp -> CrateSystem.addKey(GodData.get(sp), CrateSystem.Crate.COMMUNE, 1));
        o("Clé Rare", "Pour la caisse Rare du Sanctuaire", 240, Items.TRIPWIRE_HOOK, 2, sp -> CrateSystem.addKey(GodData.get(sp), CrateSystem.Crate.RARE, 1));
        o("Clé Épique", "Pour la caisse Épique du Sanctuaire", 900, Items.TRIPWIRE_HOOK, 3, sp -> CrateSystem.addKey(GodData.get(sp), CrateSystem.Crate.EPIQUE, 1));
        o("Boost des métiers (1 h)", "XP de métier ×1,5 pendant une heure (cumulable)", 150, Items.GOLDEN_PICKAXE, 1, sp -> {
            PlayerProfile p = GodData.get(sp);
            p.jobBoostUntil = Math.max(System.currentTimeMillis(), p.jobBoostUntil) + 3600_000L;
        });
        o("32 fioles d'expérience", "Pour enchanter", 30, Items.EXPERIENCE_BOTTLE, 32, sp -> Shop.give(sp, new ItemStack(Items.EXPERIENCE_BOTTLE, 32)));
        o("2 étiquettes", "Pour nommer vos animaux", 30, Items.NAME_TAG, 2, sp -> Shop.give(sp, new ItemStack(Items.NAME_TAG, 2)));
        o("Pomme dorée enchantée", "Le fruit des dieux", 300, Items.ENCHANTED_GOLDEN_APPLE, 1, sp -> Shop.give(sp, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
        o("Générateur de poulets", "Un spawner de poulets", 350, Items.SPAWNER, 1, sp -> Shop.give(sp, SpawnerSystem.item("minecraft:chicken")));
        o("Générateur de vaches", "Un spawner de vaches", 500, Items.SPAWNER, 1, sp -> Shop.give(sp, SpawnerSystem.item("minecraft:cow")));
    }

    public static String buy(ServerPlayer player, Offer offer) {
        PlayerProfile p = GodData.get(player);
        if (p.afkTokens < offer.cost()) return "Il faut " + offer.cost() + " jetons AFK (vous en avez " + p.afkTokens + "). Passez du temps dans la zone AFK (/afk).";
        p.afkTokens -= offer.cost();
        offer.give().accept(player);
        GodData.dirty();
        GodData.log("AFKSHOP : " + p.name + " achète " + offer.label() + " (" + offer.cost() + " jetons)");
        Fx.sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.3F);
        Msg.ok(player, "Acheté : " + offer.label() + " (−" + offer.cost() + " jetons, reste " + p.afkTokens + ").");
        return null;
    }

    public static void open(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        Page page = new Page("Boutique AFK — " + p.afkTokens + " jeton(s)").frame(Items.PURPLE_STAINED_GLASS_PANE);
        page.set(4, Page.icon(Items.CLOCK, "Jetons AFK : " + p.afkTokens, ChatFormatting.LIGHT_PURPLE,
                "§7Gagnés dans la zone AFK (/afk) :", "§71 jeton et 5 pièces par minute,", "§7" + AfkSystem.DAILY_CAP + " jetons par jour au plus.",
                "§7Aujourd'hui : " + (QuestSystem.today().equals(p.afkDay) ? p.afkTokensToday : 0) + "/" + AfkSystem.DAILY_CAP,
                p.jobBoostUntil > System.currentTimeMillis() ? "§aBoost des métiers : encore " + Msg.time(p.jobBoostUntil - System.currentTimeMillis()) : "§7Aucun boost actif"));
        int i = 0;
        for (Offer o : OFFERS) {
            if (i >= 14) break;
            int slot = Menus.INNER[i++];
            boolean can = p.afkTokens >= o.cost();
            page.set(slot, Page.icon(o.icon(), o.count(), o.label(), can ? ChatFormatting.GREEN : ChatFormatting.GRAY, List.of(
                    Page.line("§7" + o.description()), Page.line("§dPrix : " + o.cost() + " jetons"),
                    Page.line(""), Page.line(can ? "§eCliquez pour acheter" : "§cPas assez de jetons")), false), (sp, c) -> {
                String err = buy(sp, o);
                if (err != null) Msg.err(sp, err);
                sp.level().getServer().execute(() -> open(sp));
            });
        }
        // Privilèges achetables aussi en jetons (rangées 3 et 4).
        int j = 14;
        for (Perks.Perk k : Perks.Perk.values()) {
            if (j >= Menus.INNER.length) break;
            boolean owned = Perks.ownsProfile(p, k);
            page.set(Menus.INNER[j++], Page.icon(owned ? Items.LIME_DYE : k.icon, 1, "Privilège " + k.usage, owned ? ChatFormatting.GREEN : ChatFormatting.GOLD, List.of(
                    Page.line("§7" + k.description), Page.line(owned ? "§aDéjà débloqué" : "§dPrix : " + k.tokens + " jetons"),
                    Page.line(owned ? "" : "§8(ou " + Msg.money(k.price) + " dans /shop)"),
                    Page.line(owned ? "" : "§eMaj + clic pour acheter")), owned), (sp, c) -> {
                if (owned || c != com.godmod.menu.GodMenu.Click.SHIFT) {
                    if (!owned) Msg.info(sp, "Maj + clic pour confirmer l'achat de " + k.usage + " (" + k.tokens + " jetons).");
                    return;
                }
                String err = Perks.buy(sp, k, true);
                if (err != null) Msg.err(sp, err);
                sp.level().getServer().execute(() -> open(sp));
            });
        }
        page.set(49, Page.icon(Items.ENDER_PEARL, "Aller dans la zone AFK", ChatFormatting.LIGHT_PURPLE, "§eCliquez (/afk)"), (sp, c) -> {
            sp.closeContainer();
            String err = AfkSystem.toggle(sp);
            if (err != null) Msg.err(sp, err);
        });
        page.set(53, Page.icon(Items.BARRIER, "Fermer", ChatFormatting.RED), (sp, c) -> sp.closeContainer());
        page.open(player);
    }
}
