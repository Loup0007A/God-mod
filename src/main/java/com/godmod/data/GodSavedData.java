package com.godmod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.godmod.GodMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Stockage dans le monde Minecraft (fichier data/godmod.dat de l'overworld).
 * Le contenu est un JSON unique : simple à inspecter et robuste aux évolutions du mod.
 */
public class GodSavedData extends SavedData {
    private static final Gson GSON = new GsonBuilder().create();

    public static final Codec<GodSavedData> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.STRING.fieldOf("json").forGetter(GodSavedData::toJson)
    ).apply(b, GodSavedData::fromJson));

    @SuppressWarnings("deprecation")
    public static final SavedDataType<GodSavedData> TYPE = new SavedDataType<>(
            GodMod.MODID,
            GodSavedData::new,
            CODEC,
            null
    );

    public GodState state;

    public GodSavedData() {
        this.state = new GodState();
    }

    private static GodSavedData fromJson(String json) {
        GodSavedData data = new GodSavedData();
        try {
            GodState loaded = GSON.fromJson(json, GodState.class);
            if (loaded != null) {
                if (loaded.settings == null) loaded.settings = new Settings();
                if (loaded.players == null) loaded.players = new java.util.HashMap<>();
                if (loaded.log == null) loaded.log = new java.util.ArrayList<>();
                data.state = loaded;
            }
        } catch (Exception e) {
            GodMod.LOGGER.error("Impossible de lire les données du God Mod, nouvel état créé", e);
        }
        return data;
    }

    private String toJson() {
        return GSON.toJson(state);
    }
}
