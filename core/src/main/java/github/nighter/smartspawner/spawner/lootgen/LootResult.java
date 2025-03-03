package github.nighter.smartspawner.spawner.lootgen;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class LootResult {
    private final List<ItemStack> items;
    private final int experience;
    private final Map<String, Object> metadata; // For additional data if needed

    public LootResult(List<ItemStack> items, int experience) {
        this.items = new ArrayList<>(items); // Create defensive copy
        this.experience = experience;
        this.metadata = new HashMap<>();
    }

    public LootResult(List<ItemStack> items, int experience, Map<String, Object> metadata) {
        this.items = new ArrayList<>(items);
        this.experience = experience;
        this.metadata = new HashMap<>(metadata);
    }

    /**
     * Gets a copy of the items list to prevent external modification
     * @return List of ItemStacks representing the loot
     */
    public List<ItemStack> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Gets the raw items list for internal use
     * @return Direct reference to the items list
     */
    List<ItemStack> getRawItems() {
        return items;
    }

    /**
     * Gets the total experience from this loot
     * @return Amount of experience
     */
    public int getExperience() {
        return experience;
    }

    /**
     * Gets metadata value by key
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Sets metadata value
     * @param key The metadata key
     * @param value The metadata value
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Checks if this loot result has any items
     * @return true if there are any items, false otherwise
     */
    public boolean hasItems() {
        return !items.isEmpty();
    }

    /**
     * Checks if this loot result has any experience
     * @return true if there is any experience, false otherwise
     */
    public boolean hasExperience() {
        return experience > 0;
    }

    /**
     * Gets the total number of items in this loot result
     * @return Total number of items
     */
    public int getTotalItems() {
        return items.stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    /**
     * Combines two LootResults into a new one
     * @param other The other LootResult to combine with
     * @return A new LootResult containing items and experience from both
     */
    public LootResult combine(LootResult other) {
        List<ItemStack> combinedItems = new ArrayList<>(this.items);
        combinedItems.addAll(other.getItems());

        Map<String, Object> combinedMetadata = new HashMap<>(this.metadata);
        combinedMetadata.putAll(other.metadata);

        return new LootResult(
                combinedItems,
                this.experience + other.experience,
                combinedMetadata
        );
    }

    /**
     * Creates an empty LootResult
     * @return An empty LootResult with no items or experience
     */
    public static LootResult empty() {
        return new LootResult(Collections.emptyList(), 0);
    }

    /**
     * Combines multiple LootResults into a single one
     * @param results Collection of LootResults to combine
     * @return A new LootResult containing all items and experience
     */
    public static LootResult combine(Collection<LootResult> results) {
        List<ItemStack> allItems = new ArrayList<>();
        int totalExp = 0;
        Map<String, Object> combinedMetadata = new HashMap<>();

        for (LootResult result : results) {
            allItems.addAll(result.getItems());
            totalExp += result.getExperience();
            combinedMetadata.putAll(result.metadata);
        }

        return new LootResult(allItems, totalExp, combinedMetadata);
    }

    @Override
    public String toString() {
        return String.format("LootResult{items=%d, experience=%d, metadata=%s}",
                items.size(), experience, metadata);
    }

    /**
     * Serializes this LootResult to a ConfigurationSection
     * @param section The ConfigurationSection to serialize to
     */
    public void serialize(ConfigurationSection section) {
        section.set("experience", experience);

        List<Map<String, Object>> serializedItems = new ArrayList<>();
        for (ItemStack item : items) {
            serializedItems.add(item.serialize());
        }
        section.set("items", serializedItems);

        section.set("metadata", metadata);
    }

    /**
     * Deserializes a LootResult from a ConfigurationSection
     * @param section The ConfigurationSection to deserialize from
     * @return The deserialized LootResult
     */
    public static LootResult deserialize(ConfigurationSection section) {
        int exp = section.getInt("experience", 0);

        List<ItemStack> deserializedItems = new ArrayList<>();
        List<?> itemsList = section.getList("items", new ArrayList<>());
        for (Object obj : itemsList) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) obj;
                ItemStack item = ItemStack.deserialize(itemMap);
                deserializedItems.add(item);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) section.get("metadata", new HashMap<>());

        return new LootResult(deserializedItems, exp, metadata);
    }
}