package github.nighter.smartspawner.api;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Main API interface for SmartSpawner plugin.
 * This API allows other plugins to interact with SmartSpawner functionality.
 */
public interface SmartSpawnerAPI {

    /**
     * Creates a spawner item with the specified entity type
     *
     * @param entityType The type of entity this spawner will spawn
     * @return An ItemStack representing the spawner
     */
    ItemStack createSpawnerItem(EntityType entityType);

    /**
     * Creates a spawner item with the specified entity type and a custom amount
     *
     * @param entityType The type of entity this spawner will spawn
     * @param amount The amount of the item stack
     * @return An ItemStack representing the spawner
     */
    ItemStack createSpawnerItem(EntityType entityType, int amount);

    /**
     * Gets the entity type from a spawner item
     *
     * @param item The spawner item to check
     * @return The EntityType of the spawner, or null if the item is not a valid spawner
     */
    EntityType getSpawnerEntityType(ItemStack item);

    /**
     * Checks if an item is a valid spawner created by SmartSpawner
     *
     * @param item The item to check
     * @return true if the item is a valid spawner, false otherwise
     */
    boolean isValidSpawner(ItemStack item);
}