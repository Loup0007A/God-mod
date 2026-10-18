package com.godmod.item;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Specialization;
import com.godmod.system.ArtifactEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Artefact divin lié à une spécialisation. Clic droit pour l'utiliser. */
public class ArtifactItem extends Item {
    private final Specialization specialization;

    public ArtifactItem(Properties properties, Specialization specialization) {
        super(properties);
        this.specialization = specialization;
    }

    public Specialization specialization() {
        return specialization;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        boolean used;
        int baseCooldown;
        switch (specialization) {
            case OCEAN -> {
                used = ArtifactEffects.wave(sp, serverLevel);
                baseCooldown = 60;
            }
            case CIEL -> {
                used = ArtifactEffects.lightning(sp, serverLevel);
                baseCooldown = 80;
            }
            case COSMOS -> {
                used = ArtifactEffects.blackHole(sp, serverLevel);
                baseCooldown = 240;
            }
            default -> {
                used = false;
                baseCooldown = 0;
            }
        }
        if (!used) return InteractionResult.FAIL;
        PlayerProfile p = GodData.get(sp);
        player.getCooldowns().addCooldown(stack, ArtifactEffects.cooldown(p, baseCooldown));
        player.swing(hand, true);
        return InteractionResult.SUCCESS;
    }
}
