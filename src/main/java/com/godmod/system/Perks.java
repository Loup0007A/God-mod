package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.util.DivineItems;
import com.godmod.util.Fx;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Privilèges : commandes de confort achetées une fois pour toutes, très cher, à la boutique (/shop → Privilèges)
 * ou avec des jetons AFK (/afkshop). Les admins les ont toutes.
 */
public final class Perks {
    public enum Perk {
        HAT("chapeau", "/chapeau", "Porter l'objet tenu en main sur la tête", 15_000, 400, 0, Items.LEATHER_HELMET),
        CRAFT("craft", "/craft", "Établi portable, utilisable partout", 25_000, 600, 0, Items.CRAFTING_TABLE),
        FEED("manger", "/manger", "Rassasie complètement (toutes les 10 min)", 30_000, 700, 600, Items.COOKED_BEEF),
        ENDERCHEST("ec", "/ec", "Coffre de l'Ender portable", 40_000, 900, 0, Items.ENDER_CHEST),
        ANVIL("enclume", "/enclume", "Enclume portable (réparer, renommer, enchanter)", 50_000, 1200, 0, Items.ANVIL),
        HEAD("tete", "/tete [joueur]", "Obtenir la tête d'un joueur (toutes les heures)", 60_000, 1500, 3600, Items.PLAYER_HEAD),
        HEAL("soin", "/soin", "Soigne entièrement, éteint le feu (toutes les 30 min, hors combat)", 80_000, 2000, 1800, Items.GOLDEN_APPLE),
        REPAIR("reparer", "/reparer", "Répare l'objet tenu en main (toutes les 2 h)", 120_000, 3000, 7200, Items.ANVIL),
        FLY("vol", "/vol", "Voler dans votre propre monde divin", 200_000, 5000, 0, Items.ELYTRA);

        public final String id;
        public final String usage;
        public final String description;
        public final long price;
        public final long tokens;
        public final int cooldownSeconds;
        public final Item icon;

        Perk(String id, String usage, String description, long price, long tokens, int cooldownSeconds, Item icon) {
            this.id = id;
            this.usage = usage;
            this.description = description;
            this.price = price;
            this.tokens = tokens;
            this.cooldownSeconds = cooldownSeconds;
            this.icon = icon;
        }

        public static Perk parse(String s) {
            for (Perk k : values()) if (k.id.equalsIgnoreCase(s) || k.name().equalsIgnoreCase(s)) return k;
            return null;
        }
    }

    private Perks() {
    }

    public static boolean owns(ServerPlayer player, Perk perk) {
        return GodData.get(player).perks.contains(perk.id) || Moderation.roleOf(player).atLeast(Role.ADMIN);
    }

    public static boolean ownsProfile(PlayerProfile p, Perk perk) {
        return p.perks.contains(perk.id);
    }

    /** Achat avec des pièces (tokens = false) ou des jetons AFK. */
    public static String buy(ServerPlayer player, Perk perk, boolean tokens) {
        PlayerProfile p = GodData.get(player);
        if (p.perks.contains(perk.id)) return "Vous possédez déjà " + perk.usage + ".";
        if (tokens) {
            if (p.afkTokens < perk.tokens) return "Il faut " + perk.tokens + " jetons AFK (vous en avez " + p.afkTokens + ").";
            p.afkTokens -= perk.tokens;
        } else {
            if (p.money < perk.price) return "Il faut " + Msg.money(perk.price) + " (vous avez " + Msg.money(p.money) + ").";
            p.money -= perk.price;
        }
        p.perks.add(perk.id);
        GodData.dirty();
        GodData.log("PRIVILÈGE : " + p.name + " achète " + perk.usage + (tokens ? " (" + perk.tokens + " jetons AFK)" : " (" + perk.price + " pièces)"));
        Fx.title(player, Component.literal("Privilège débloqué !").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(perk.usage + " — " + perk.description).withStyle(ChatFormatting.YELLOW));
        Fx.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1.3F);
        Msg.ok(player, "Vous pouvez désormais utiliser " + perk.usage + " : " + perk.description + ".");
        server(player).getCommands().sendCommands(player);
        return null;
    }

    private static net.minecraft.server.MinecraftServer server(ServerPlayer p) {
        return p.level().getServer();
    }

    /** Vérifie la possession et le délai ; renvoie une erreur ou null (et démarre le délai). */
    private static String use(ServerPlayer player, Perk perk) {
        if (!owns(player, perk)) return perk.usage + " est un privilège : achetez-le dans /shop → Privilèges (ou /afkshop).";
        if (perk.cooldownSeconds <= 0 || Moderation.roleOf(player).atLeast(Role.ADMIN)) return null;
        PlayerProfile p = GodData.get(player);
        long left = p.perkCooldowns.getOrDefault(perk.id, 0L) - System.currentTimeMillis();
        if (left > 0) return perk.usage + " sera de nouveau disponible dans " + Msg.time(left) + ".";
        p.perkCooldowns.put(perk.id, System.currentTimeMillis() + perk.cooldownSeconds * 1000L);
        GodData.dirty();
        return null;
    }

    // ------------------------------------------------------------------ Commandes

    public static String craft(ServerPlayer player) {
        String err = use(player, Perk.CRAFT);
        if (err != null) return err;
        var access = ContainerLevelAccess.create(player.level(), player.blockPosition());
        player.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CraftingMenu(id, inv, access) {
            @Override
            public boolean stillValid(Player p) {
                return true;
            }
        }, Component.literal("Établi portable")));
        return null;
    }

    public static String anvil(ServerPlayer player) {
        String err = use(player, Perk.ANVIL);
        if (err != null) return err;
        var access = ContainerLevelAccess.create(player.level(), player.blockPosition());
        player.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AnvilMenu(id, inv, access) {
            @Override
            public boolean stillValid(Player p) {
                return true;
            }
        }, Component.literal("Enclume portable")));
        return null;
    }

    public static String enderChest(ServerPlayer player) {
        String err = use(player, Perk.ENDERCHEST);
        if (err != null) return err;
        var ec = player.getEnderChestInventory();
        player.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, ec), Component.literal("Coffre de l'Ender")));
        return null;
    }

    public static String feed(ServerPlayer player) {
        if (owns(player, Perk.FEED) && player.getFoodData().getFoodLevel() >= 20) return "Vous n'avez pas faim.";
        String err = use(player, Perk.FEED);
        if (err != null) return err;
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(10F);
        Fx.sound(player, SoundEvents.PLAYER_BURP, 0.8F, 1F);
        Msg.ok(player, "Vous êtes rassasié.");
        return null;
    }

    public static String heal(ServerPlayer player) {
        if (owns(player, Perk.HEAL) && Travel.inCombat(player)) return "Impossible en plein combat.";
        String err = use(player, Perk.HEAL);
        if (err != null) return err;
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        Fx.sound(player, SoundEvents.PLAYER_LEVELUP, 0.6F, 2F);
        Msg.ok(player, "Vous êtes entièrement soigné.");
        return null;
    }

    public static String hat(ServerPlayer player) {
        if (!owns(player, Perk.HAT)) return use(player, Perk.HAT);
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) return "Tenez en main l'objet à porter sur la tête.";
        if (DivineItems.isDivine(hand)) return "Les objets divins ne se portent pas sur la tête.";
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        ItemStack one = hand.copy();
        one.setCount(1);
        hand.shrink(1);
        player.setItemSlot(EquipmentSlot.HEAD, one);
        if (!head.isEmpty()) Shop.give(player, head);
        Msg.ok(player, "Joli chapeau !");
        return null;
    }

    public static String head(ServerPlayer player, String target) {
        String name = target == null ? player.getName().getString() : target;
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return "Pseudo invalide.";
        String err = use(player, Perk.HEAD);
        if (err != null) return err;
        var server = server(player);
        WorldBuilder.run(server, Hub.level(server), "give " + player.getName().getString() + " minecraft:player_head[minecraft:profile=\"" + name + "\"] 1");
        Msg.ok(player, "Voici la tête de " + name + " (le skin peut mettre quelques secondes à apparaître).");
        return null;
    }

    public static String repair(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        if (owns(player, Perk.REPAIR) && (hand.isEmpty() || !hand.isDamageableItem() || hand.getDamageValue() == 0))
            return "Tenez en main un objet abîmé.";
        String err = use(player, Perk.REPAIR);
        if (err != null) return err;
        hand.setDamageValue(0);
        Fx.sound(player, SoundEvents.ANVIL_USE, 0.6F, 1.2F);
        Msg.ok(player, hand.getHoverName().getString() + " est comme neuf.");
        return null;
    }

    /** /vol : uniquement dans son propre monde divin. */
    public static String fly(ServerPlayer player) {
        String err = use(player, Perk.FLY);
        if (err != null) return err;
        if (player.isCreative() || player.isSpectator()) return "Vous volez déjà.";
        PlayerProfile p = GodData.get(player);
        if (player.getAbilities().mayfly) {
            setFly(player, false);
            Msg.info(player, "Vol désactivé.");
            return null;
        }
        if (!canFlyHere(player, p)) return "Le vol n'est possible que dans votre propre monde divin (/monde).";
        setFly(player, true);
        Msg.ok(player, "Vol activé (double saut). Il se coupe si vous quittez votre monde divin.");
        return null;
    }

    private static boolean canFlyHere(ServerPlayer player, PlayerProfile p) {
        if (!DivineWorld.isDivine(player.level())) return false;
        PlayerProfile owner = DivineWorld.ownerAt(player.blockPosition());
        return owner != null && owner.uuid.equals(p.uuid);
    }

    private static void setFly(ServerPlayer player, boolean on) {
        player.getAbilities().mayfly = on;
        if (!on) player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    /** Chaque seconde : coupe le vol hors de son monde divin (avec chute ralentie). */
    public static void tickFly(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || !player.getAbilities().mayfly) return;
        if (Hub.canEditHub(player)) return;
        if (!canFlyHere(player, GodData.get(player))) {
            boolean wasFlying = player.getAbilities().flying;
            setFly(player, false);
            if (wasFlying) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0));
            Msg.info(player, "Vol désactivé : vous avez quitté votre monde divin.");
        }
    }
}
