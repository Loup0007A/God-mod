package com.godmod.util;

import com.godmod.data.GodData;
import com.godmod.data.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Images du pack de ressources, insérées dans le texte (caractères privés U+E000…).
 * Doit rester synchronisé avec pack/build_pack.py. Utilisées seulement si le pack est obligatoire :
 * sinon un joueur sans pack verrait des carrés.
 */
public final class Glyphs {
    public static final String ADMIN = "", MODO = "", AFK = "";
    public static final String CH_GLOBAL = "", CH_LOCAL = "", CH_COMMERCE = "", CH_GROUPE = "", CH_MP = "", STAR = "";
    public static final String COIN = "", ESSENCE = "", FAITH = "", KEY = "";
    public static final String PANTHEON = "";

    private Glyphs() {
    }

    public static boolean on() {
        return GodData.ready() && GodData.settings().packEnabled && GodData.settings().packRequired;
    }

    public static String tier(Tier t) {
        return String.valueOf((char) (0xE010 + t.ordinal()));
    }

    /** Image seule : en blanc (la couleur du texte teinterait l'image) et sans ombre portée (sinon elle paraît floue). */
    public static MutableComponent of(String glyph) {
        return Component.literal(glyph).withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(false).withItalic(false).withShadowColor(0));
    }
}
