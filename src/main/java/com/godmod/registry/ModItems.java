package com.godmod.registry;

import com.godmod.GodMod;
import com.godmod.data.Specialization;
import com.godmod.item.ArtifactItem;
import com.godmod.item.CodexItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GodMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GodMod.MODID);

    public static final RegistryObject<Item> CODEX = ITEMS.register("codex", () -> new CodexItem(
            new Item.Properties().setId(ITEMS.key("codex")).stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> TIDE_TRIDENT = ITEMS.register("tide_trident", () -> new ArtifactItem(
            new Item.Properties().setId(ITEMS.key("tide_trident")).stacksTo(1).rarity(Rarity.EPIC),
            Specialization.OCEAN));

    public static final RegistryObject<Item> THUNDER_SCEPTER = ITEMS.register("thunder_scepter", () -> new ArtifactItem(
            new Item.Properties().setId(ITEMS.key("thunder_scepter")).stacksTo(1).rarity(Rarity.EPIC),
            Specialization.CIEL));

    public static final RegistryObject<Item> VOID_ORB = ITEMS.register("void_orb", () -> new ArtifactItem(
            new Item.Properties().setId(ITEMS.key("void_orb")).stacksTo(1).rarity(Rarity.EPIC),
            Specialization.COSMOS));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.literal("God Mod"))
            .icon(() -> new ItemStack(CODEX.get()))
            .displayItems((params, output) -> {
                output.accept(CODEX.get());
                output.accept(TIDE_TRIDENT.get());
                output.accept(THUNDER_SCEPTER.get());
                output.accept(VOID_ORB.get());
            })
            .build());

    private ModItems() {
    }

    public static Item artifactFor(Specialization s) {
        return switch (s) {
            case OCEAN -> TIDE_TRIDENT.get();
            case CIEL -> THUNDER_SCEPTER.get();
            case COSMOS -> VOID_ORB.get();
            default -> CODEX.get();
        };
    }
}
