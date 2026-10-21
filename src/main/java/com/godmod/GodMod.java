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
        // Le même .jar sert au serveur et, facultativement, aux joueurs (chat à onglets).
        // Sur un client, seule la partie interface est active : le jeu du mod ne tourne que sur un serveur dédié.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.godmod.client.ClientInit.init();
            LOGGER.info("God Civilisation : interface client chargée.");
            return;
        }
        GameEvents.register();
        LOGGER.info("God Mod chargé : que les dieux s'affrontent !");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
