package com.godmod.progress;

import com.godmod.GodMod;
import com.godmod.data.GodData;
import com.godmod.util.DivineItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Générateurs de créatures (spawners) obtenus dans les caisses ou à la boutique.
 * Posés, ils produisent le mob indiqué ; cassés par quelqu'un qui en a le droit, ils sont rendus intacts.
 */
public final class SpawnerSystem {
    public static final String KIND = "spawner";
    public static final String MOB_TAG = "godmod_mob";

    public static final Map<String, String> MOBS = new LinkedHashMap<>();

    static {
        MOBS.put("minecraft:chicken", "Poulet");
        MOBS.put("minecraft:pig", "Cochon");
        MOBS.put("minecraft:cow", "Vache");
        MOBS.put("minecraft:sheep", "Mouton");
        MOBS.put("minecraft:zombie", "Zombie");
        MOBS.put("minecraft:skeleton", "Squelette");
        MOBS.put("minecraft:spider", "Araignée");
        MOBS.put("minecraft:cave_spider", "Araignée venimeuse");
        MOBS.put("minecraft:creeper", "Creeper");
        MOBS.put("minecraft:blaze", "Blaze");
        MOBS.put("minecraft:enderman", "Enderman");
        MOBS.put("minecraft:iron_golem", "Golem de fer");
    }

    private SpawnerSystem() {
    }

    public static String mobName(String mob) {
        return MOBS.getOrDefault(mob, mob);
    }

    public static ItemStack item(String mob) {
        ItemStack stack = new ItemStack(Items.SPAWNER);
        CompoundTag tag = new CompoundTag();
        tag.putString(DivineItems.TAG, KIND);
        tag.putString(MOB_TAG, mob);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Générateur : " + mobName(mob))
                .withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false).withBold(true)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                com.godmod.menu.Page.line("§7Posez-le pour faire apparaître des " + mobName(mob).toLowerCase()),
                com.godmod.menu.Page.line("§7Cassez-le pour le récupérer"))));
        return stack;
    }

    /** Identifiant du mob d'un objet générateur, ou null. */
    public static String mobOf(ItemStack stack) {
        if (!KIND.equals(DivineItems.kind(stack))) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String mob = data == null ? "" : data.copyTag().getStringOr(MOB_TAG, "");
        return mob.isEmpty() ? null : mob;
    }

    /** Un joueur vient de poser un bloc : si c'est un de nos générateurs, on lui attribue son mob. */
    public static void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState placed) {
        if (!placed.is(Blocks.SPAWNER)) return;
        String mob = mobOf(player.getMainHandItem());
        if (mob == null) mob = mobOf(player.getOffhandItem());
        if (mob == null) return;
        String finalMob = mob;
        level.getServer().execute(() -> configure(level, pos, finalMob));
    }

    public static void configure(ServerLevel level, BlockPos pos, String mob) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SpawnerBlockEntity spawner)) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(mob));
        spawner.getSpawner().setEntityId(type, level, level.random, pos);
        spawner.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        GodData.state().spawners.put(JobSystem.key(level, pos), mob);
        GodData.dirty();
        GodMod.LOGGER.debug("[God Mod] Générateur {} posé en {}", mob, pos);
    }

    /** Un générateur posé par un joueur est cassé : on rend l'objet. */
    public static void onBroken(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.SPAWNER)) return;
        String mob = GodData.state().spawners.remove(JobSystem.key(level, pos));
        if (mob == null) return;
        GodData.dirty();
        Block.popResource(level, pos, item(mob));
    }
}
