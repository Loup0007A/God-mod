package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.util.DivineItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Le Codex divin vit toujours dans le dernier emplacement de la barre rapide (touche 9).
 * Il ne peut être ni jeté, ni rangé dans un coffre, ni perdu à la mort : il revient automatiquement.
 */
public final class CodexGuard {
    /** Emplacement réservé : dernière case de la barre rapide. */
    public static final int SLOT = 8;

    private CodexGuard() {
    }

    public static boolean isCodex(ItemStack stack) {
        return DivineItems.CODEX.equals(DivineItems.kind(stack));
    }

    /** Appelé chaque tick pour chaque joueur connecté et vérifié. */
    public static void tick(ServerPlayer player) {
        PlayerProfile p = GodData.get(player);
        if (!p.codexGiven || player.isSpectator()) return;

        // 1) Retirer les copies placées dans un coffre / conteneur ouvert.
        AbstractContainerMenu menu = player.containerMenu;
        Inventory inv = player.getInventory();
        if (menu != null && menu != player.inventoryMenu) {
            for (Slot slot : menu.slots) {
                if (slot.container != inv && isCodex(slot.getItem())) {
                    slot.set(ItemStack.EMPTY);
                    slot.setChanged();
                }
            }
        }

        // 2) Une seule copie, et dans la bonne case.
        boolean inPlace = isCodex(inv.getItem(SLOT));
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i == SLOT) continue;
            if (isCodex(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        if (isCodex(menu == null ? ItemStack.EMPTY : menu.getCarried())) return; // en cours de déplacement à la souris
        if (inPlace) return;

        ItemStack occupant = inv.getItem(SLOT);
        inv.setItem(SLOT, DivineItems.codex());
        if (!occupant.isEmpty() && !isCodex(occupant)) {
            if (!inv.add(occupant)) player.drop(occupant, false);
        }
        player.inventoryMenu.broadcastChanges();
    }
}
