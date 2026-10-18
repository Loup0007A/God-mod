package com.godmod.item;

import com.godmod.menu.Menus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Codex divin : ouvre l'interface du Panthéon. Donné à chaque joueur à sa première connexion. */
public class CodexItem extends Item {
    public CodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            Menus.openMain(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
