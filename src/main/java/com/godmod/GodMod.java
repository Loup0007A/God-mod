package com.godmod;

import com.godmod.event.GameEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * God Mod — simulation de dieux multijoueur.
 * Point d'entrée du mod (côté serveur) : branche tous les systèmes sur les événements du jeu.
 * Tout démarre automatiquement avec le serveur : données, structuration du monde, authentification.
 */
@Mod(GodMod.MODID)
public final class GodMod {
    public static final String MODID = "godmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GodMod(FMLJavaModLoadingContext context) {
        // Mod 100 % serveur : aucun objet ni bloc ajouté, les joueurs gardent un Minecraft normal.
        GameEvents.register();
        LOGGER.info("God Mod chargé : que les dieux s'affrontent !");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
