package com.godmod.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Interface « coffre » pilotée par le serveur : chaque case est un bouton.
 * Les objets ne peuvent jamais être pris : tout clic est intercepté puis l'affichage est resynchronisé.
 */
public class GodMenu extends ChestMenu {
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;

    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, Click click);
    }

    @FunctionalInterface
    public interface InventoryAction {
        void run(ServerPlayer player, Slot slot, Click click);
    }

    /** Type de clic simplifié. */
    public enum Click { LEFT, RIGHT, SHIFT }

    private final Map<Integer, Action> actions;
    private final InventoryAction inventoryAction;

    public GodMenu(int id, Inventory inventory, SimpleContainer box, Map<Integer, Action> actions, InventoryAction inventoryAction) {
        super(MenuType.GENERIC_9x6, id, inventory, box, ROWS);
        this.actions = new HashMap<>(actions);
        this.inventoryAction = inventoryAction;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player instanceof ServerPlayer sp) {
            Click click = clickType == ClickType.QUICK_MOVE ? Click.SHIFT : (button == 1 ? Click.RIGHT : Click.LEFT);
            boolean simple = clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE;
            if (simple && slotId >= 0 && slotId < SIZE) {
                Action a = actions.get(slotId);
                if (a != null) {
                    try {
                        a.run(sp, click);
                    } catch (Exception e) {
                        com.godmod.GodMod.LOGGER.error("Erreur dans l'interface du Panthéon", e);
                    }
                }
            } else if (simple && slotId >= SIZE && slotId < this.slots.size() && inventoryAction != null) {
                inventoryAction.run(sp, this.slots.get(slotId), click);
            }
        }
        this.sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
