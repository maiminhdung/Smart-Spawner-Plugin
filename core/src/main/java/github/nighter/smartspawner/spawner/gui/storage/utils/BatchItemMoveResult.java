package github.nighter.smartspawner.spawner.gui.storage.utils;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;

public class BatchItemMoveResult {
    private final int totalMoved;
    private final Map<Integer, ItemUpdate> slotUpdates;
    private final List<ItemStack> movedItems;
    private final boolean inventoryFull;

    public BatchItemMoveResult(int totalMoved, Map<Integer, ItemUpdate> slotUpdates,
                               List<ItemStack> movedItems, boolean inventoryFull) {
        this.totalMoved = totalMoved;
        this.slotUpdates = slotUpdates;
        this.movedItems = movedItems;
        this.inventoryFull = inventoryFull;
    }

    public int getTotalMoved() {
        return totalMoved;
    }

    public Map<Integer, ItemUpdate> getSlotUpdates() {
        return slotUpdates;
    }

    public List<ItemStack> getMovedItems() {
        return movedItems;
    }

    public boolean isInventoryFull() {
        return inventoryFull;
    }
}