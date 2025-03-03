package github.nighter.smartspawner.spawner.gui.storage.utils;

import org.bukkit.inventory.ItemStack;

public class ItemUpdate {
    private final ItemStack original;
    private final int amountMoved;

    public ItemUpdate(ItemStack original, int amountMoved) {
        this.original = original;
        this.amountMoved = amountMoved;
    }

    public ItemStack getUpdatedItem() {
        if (amountMoved >= original.getAmount()) {
            return null;
        }
        ItemStack updated = original.clone();
        updated.setAmount(original.getAmount() - amountMoved);
        return updated;
    }
}