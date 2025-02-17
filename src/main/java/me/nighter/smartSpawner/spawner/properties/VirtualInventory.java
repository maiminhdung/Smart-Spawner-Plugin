package me.nighter.smartSpawner.spawner.properties;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems;
    private final int maxSlots;
    private final Map<Integer, DisplayItem> displayCache;
    private boolean displayCacheDirty;
    private final Comparator<Map.Entry<ItemSignature, Long>> itemComparator;

    public VirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.displayCache = new ConcurrentHashMap<>();
        this.displayCacheDirty = true;

        // Create a comparator for sorting items
        this.itemComparator = (e1, e2) -> {
            ItemStack item1 = e1.getKey().getTemplate();
            ItemStack item2 = e2.getKey().getTemplate();

            // First, compare if items have durability
            boolean hasDurability1 = hasDurability(item1);
            boolean hasDurability2 = hasDurability(item2);

            if (hasDurability1 != hasDurability2) {
                return hasDurability1 ? 1 : -1;
            }

            // Then sort by material name
            int nameCompare = item1.getType().name().compareTo(item2.getType().name());
            if (nameCompare != 0) return nameCompare;

            // If same material, sort by amount (descending)
            return e2.getValue().compareTo(e1.getValue());
        };
    }

    private boolean hasDurability(ItemStack item) {
        return item.getType().getMaxDurability() > 0;
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
        if (items.isEmpty()) return;

        // Batch process items
        Map<ItemSignature, Long> batchUpdates = new HashMap<>();
        for (ItemStack item : items) {
            ItemSignature sig = new ItemSignature(item);
            batchUpdates.merge(sig, (long) item.getAmount(), Long::sum);
        }

        // Apply batch updates atomically
        batchUpdates.forEach((sig, amount) ->
                consolidatedItems.merge(sig, amount, Long::sum));

        displayCacheDirty = true;
    }

    // Remove items in bulk
    public boolean removeItems(List<ItemStack> items) {
        Map<ItemSignature, Long> toRemove = new HashMap<>();

        // Calculate total amounts to remove in a single pass
        for (ItemStack item : items) {
            if (item == null) continue;
            ItemSignature sig = new ItemSignature(item);
            toRemove.merge(sig, (long) item.getAmount(), Long::sum);
        }

        // Verify amounts in a single atomic check
        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            Long currentAmount = consolidatedItems.getOrDefault(entry.getKey(), 0L);
            if (currentAmount < entry.getValue()) {
                return false;
            }
        }

        // Perform removals in batch
        toRemove.forEach((sig, amount) -> {
            consolidatedItems.computeIfPresent(sig, (key, current) -> {
                long newAmount = current - amount;
                return newAmount <= 0 ? null : newAmount;
            });
        });

        displayCacheDirty = true;
        return true;
    }

    // Get display inventory
    public Map<Integer, ItemStack> getDisplayInventory() {
        if (!displayCacheDirty && !displayCache.isEmpty()) {
            return convertDisplayCacheToItemStacks();
        }

        displayCache.clear();

        // Sort items using our custom comparator
        List<Map.Entry<ItemSignature, Long>> sortedItems = new ArrayList<>(consolidatedItems.entrySet());
        sortedItems.sort(itemComparator);

        int currentSlot = 0;
        for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
            if (currentSlot >= maxSlots) break;

            ItemSignature sig = entry.getKey();
            long totalAmount = entry.getValue();

            while (totalAmount > 0 && currentSlot < maxSlots) {
                int stackSize = (int) Math.min(totalAmount, sig.getTemplate().getMaxStackSize());
                displayCache.put(currentSlot, new DisplayItem(sig, stackSize));
                totalAmount -= stackSize;
                currentSlot++;
            }
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