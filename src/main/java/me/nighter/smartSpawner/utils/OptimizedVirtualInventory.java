package me.nighter.smartSpawner.utils;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OptimizedVirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems; // Store total quantities
    private final int maxSlots;
    private final Map<Integer, DisplayItem> displayCache; // Cache for display purposes
    private boolean displayCacheDirty;

    public OptimizedVirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.displayCache = new ConcurrentHashMap<>();
        this.displayCacheDirty = true;
    }

    public static class ItemSignature {
        private final ItemStack template;
        private final int hashCode;

        public ItemSignature(ItemStack item) {
            this.template = item.clone();
            this.template.setAmount(1);
            this.hashCode = calculateHashCode();
        }

        private int calculateHashCode() {
            return Objects.hash(
                    template.getType(),
                    template.getDurability(),
                    template.getItemMeta() != null ? template.getItemMeta().toString() : null
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemSignature)) return false;
            ItemSignature that = (ItemSignature) o;
            return template.isSimilar(that.template);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        public ItemStack getTemplate() {
            return template.clone();
        }
    }

    public static class DisplayItem {
        private final ItemSignature signature;
        private final int amount;

        public DisplayItem(ItemSignature signature, int amount) {
            this.signature = signature;
            this.amount = amount;
        }
    }

    // Add items in bulk
    public void addItems(List<ItemStack> items) {
        for (ItemStack item : items) {
            ItemSignature sig = new ItemSignature(item);
            consolidatedItems.merge(sig, (long) item.getAmount(), Long::sum);
        }
        displayCacheDirty = true;
    }

    // Remove items in bulk
    public boolean removeItems(List<ItemStack> items) {
        Map<ItemSignature, Long> toRemove = new HashMap<>();

        // Calculate total amounts to remove
        for (ItemStack item : items) {
            ItemSignature sig = new ItemSignature(item);
            toRemove.merge(sig, (long) item.getAmount(), Long::sum);
        }

        // Check if we have enough of each item
        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            Long currentAmount = consolidatedItems.getOrDefault(entry.getKey(), 0L);
            if (currentAmount < entry.getValue()) {
                return false;
            }
        }

        // Remove the items
        toRemove.forEach((sig, amount) -> {
            Long newAmount = consolidatedItems.get(sig) - amount;
            if (newAmount <= 0) {
                consolidatedItems.remove(sig);
            } else {
                consolidatedItems.put(sig, newAmount);
            }
        });

        displayCacheDirty = true;
        return true;
    }

    // Get display inventory
    public Map<Integer, ItemStack> getDisplayInventory() {
        if (!displayCacheDirty) {
            return convertDisplayCacheToItemStacks();
        }

        displayCache.clear();
        Map<ItemSignature, Long> remainingItems = new HashMap<>(consolidatedItems);
        Random random = new Random();
        int currentSlot = 0;

        while (!remainingItems.isEmpty() && currentSlot < maxSlots) {
            // Randomly select an item type
            List<ItemSignature> availableTypes = new ArrayList<>(remainingItems.keySet());
            ItemSignature selectedType = availableTypes.get(random.nextInt(availableTypes.size()));
            Long totalAmount = remainingItems.get(selectedType);

            // Calculate how much to put in this slot
            ItemStack template = selectedType.getTemplate();
            int maxStackSize = template.getMaxStackSize();
            int amountForThisSlot = Math.min(maxStackSize, totalAmount.intValue());

            // Update the display cache
            displayCache.put(currentSlot, new DisplayItem(selectedType, amountForThisSlot));

            // Update remaining amount
            long newAmount = totalAmount - amountForThisSlot;
            if (newAmount <= 0) {
                remainingItems.remove(selectedType);
            } else {
                remainingItems.put(selectedType, newAmount);
            }

            currentSlot++;
        }

        displayCacheDirty = false;
        return convertDisplayCacheToItemStacks();
    }

    private Map<Integer, ItemStack> convertDisplayCacheToItemStacks() {
        Map<Integer, ItemStack> result = new HashMap<>();
        displayCache.forEach((slot, displayItem) -> {
            ItemStack item = displayItem.signature.getTemplate();
            item.setAmount(displayItem.amount);
            result.put(slot, item);
        });
        return result;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public long getTotalItems() {
        return consolidatedItems.values().stream().mapToLong(Long::longValue).sum();
    }

    public Map<ItemSignature, Long> getConsolidatedItems() {
        return new HashMap<>(consolidatedItems);
    }

    public int getUsedSlots() {
        return getDisplayInventory().size();
    }
}