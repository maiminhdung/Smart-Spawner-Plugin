package github.nighter.smartspawner.spawner.gui.storage.utils;

import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import java.util.*;

public class ItemMoveHelper {
    public static ItemMoveResult moveItems(ItemStack source, int amountToMove,
                                           PlayerInventory targetInv, VirtualInventory virtualInv) {
        List<ItemStack> movedItems = new ArrayList<>();
        int amountMoved = 0;

        for (int i = 0; i < 36 && amountToMove > 0; i++) {
            ItemStack current = targetInv.getItem(i);

            if (current == null || current.getType().isAir()) {
                ItemStack newStack = source.clone();
                newStack.setAmount(Math.min(amountToMove, source.getMaxStackSize()));
                targetInv.setItem(i, newStack);
                movedItems.add(newStack.clone());
                amountMoved += newStack.getAmount();
                amountToMove -= newStack.getAmount();
                break;
            } else if (current.isSimilar(source)) {
                int spaceInStack = current.getMaxStackSize() - current.getAmount();
                if (spaceInStack > 0) {
                    int addAmount = Math.min(spaceInStack, amountToMove);
                    current.setAmount(current.getAmount() + addAmount);
                    ItemStack moved = source.clone();
                    moved.setAmount(addAmount);
                    movedItems.add(moved);
                    amountMoved += addAmount;
                    amountToMove -= addAmount;
                    if (amountToMove <= 0) break;
                }
            }
        }

        return new ItemMoveResult(amountMoved, movedItems);
    }

    public static BatchItemMoveResult moveBatchItems(Map<Integer, ItemStack> items,
                                                     PlayerInventory targetInv,
                                                     VirtualInventory virtualInv) {
        Map<Integer, ItemUpdate> slotUpdates = new HashMap<>();
        List<ItemStack> movedItems = new ArrayList<>();
        int totalMoved = 0;
        boolean inventoryFull = false;

        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            ItemStack itemToMove = entry.getValue();
            int sourceSlot = entry.getKey();
            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack targetItem = targetInv.getItem(i);

                if (targetItem == null || targetItem.getType().isAir()) {
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    targetInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();

                    ItemStack moved = itemToMove.clone();
                    moved.setAmount(amountMoved);
                    movedItems.add(moved);
                } else if (targetItem.isSimilar(itemToMove)) {
                    int spaceInStack = targetItem.getMaxStackSize() - targetItem.getAmount();
                    if (spaceInStack > 0) {
                        int addAmount = Math.min(spaceInStack, amountToMove);
                        targetItem.setAmount(targetItem.getAmount() + addAmount);
                        amountMoved += addAmount;
                        amountToMove -= addAmount;

                        ItemStack moved = itemToMove.clone();
                        moved.setAmount(addAmount);
                        movedItems.add(moved);
                    }
                }
            }

            if (amountMoved > 0) {
                totalMoved += amountMoved;
                slotUpdates.put(sourceSlot, new ItemUpdate(itemToMove, amountMoved));
            }

            if (amountToMove > 0) {
                inventoryFull = true;
                break;
            }
        }

        return new BatchItemMoveResult(totalMoved, slotUpdates, movedItems, inventoryFull);
    }
}