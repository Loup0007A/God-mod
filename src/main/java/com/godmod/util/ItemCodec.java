package com.godmod.util;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Conversion d'un objet (avec ses composants : enchantements, nom, contenu…) en texte SNBT et inversement. */
public final class ItemCodec {
    private ItemCodec() {
    }

    public static String encode(MinecraftServer server, ItemStack stack) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        return tag.toString();
    }

    public static ItemStack decode(MinecraftServer server, String snbt) {
        try {
            Tag tag = TagParser.parseCompoundFully(snbt);
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            com.godmod.GodMod.LOGGER.error("Objet illisible : " + snbt, e);
            return ItemStack.EMPTY;
        }
    }
}
