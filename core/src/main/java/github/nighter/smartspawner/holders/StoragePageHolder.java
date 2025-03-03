package github.nighter.smartspawner.holders;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class StoragePageHolder implements InventoryHolder, SpawnerHolder {
    private final SpawnerData spawnerData;
    private int currentPage;
    private int totalPages;
    private int oldUsedSlots;

    // Make these public static final for better performance and accessibility
    public static final int ROWS_PER_PAGE = 5;
    public static final int SLOTS_PER_PAGE = ROWS_PER_PAGE * 9;
    public static final int MAX_ITEMS_PER_PAGE = 45; // Pre-calculated value

    // Cache the inventory reference
    private Inventory inventory;

    public StoragePageHolder(SpawnerData spawnerData, int currentPage, int totalPages) {
        this.spawnerData = spawnerData;
        this.currentPage = Math.max(1, Math.min(currentPage, totalPages)); // Ensure valid page range
        this.totalPages = Math.max(1, totalPages);
        this.oldUsedSlots = spawnerData.getVirtualInventory().getUsedSlots();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // Add setter for inventory to cache it
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(1, Math.min(page, totalPages));
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
    }

    public int getOldUsedSlots() {
        return oldUsedSlots;
    }

    public void updateOldUsedSlots() {
        this.oldUsedSlots = spawnerData.getVirtualInventory().getUsedSlots();
    }

    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    public int getSlotIndex(int itemIndex) {
        return itemIndex % MAX_ITEMS_PER_PAGE;
    }

    public int getItemIndex(int slotIndex) {
        return ((currentPage - 1) * MAX_ITEMS_PER_PAGE) + slotIndex;
    }
}