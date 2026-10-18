package com.godmod;

import com.godmod.event.GameEvents;
import com.godmod.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * God Mod — simulation de dieux multijoueur.
 * Point d'entrée du mod : enregistre les objets et les écouteurs d'événements.
 */
@Mod(GodMod.MODID)
public final class GodMod {
    public static final String MODID = "godmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GodMod(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        GameEvents.register();
        LOGGER.info("God Mod chargé : que les dieux s'affrontent !");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
