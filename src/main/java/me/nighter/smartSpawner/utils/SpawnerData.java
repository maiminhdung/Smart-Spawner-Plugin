package me.nighter.smartSpawner.utils;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Logger;

public class SpawnerData {
    private final SmartSpawner plugin;
    private final String spawnerId;
    private Location spawnerLocation;
    private Integer spawnerExp;
    private Boolean spawnerActive;
    private Integer spawnerRange;
    private Boolean spawnerStop;
    private Long lastSpawnTime;
    private Integer spawnDelay;
    private EntityType entityType;
    private int maxSpawnerLootSlots;
    private int maxStoredExp;
    private int minMobs;
    private int maxMobs;
    private int stackSize;
    private OptimizedVirtualInventory virtualInventory;
    private boolean allowEquipmentItems;
    private static final Logger logger = Logger.getLogger("SmartSpawnerConfig");
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private SpawnerHologram hologram;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = true;
        this.stackSize = 1;
        this.maxSpawnerLootSlots = 45;
        this.allowEquipmentItems = true;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        loadConfigValues();
        this.virtualInventory = new OptimizedVirtualInventory(maxSpawnerLootSlots);
        if (configManager.isHologramEnabled()) {
            this.hologram = new SpawnerHologram(location);
            this.hologram.createHologram();
            updateHologramData();
        }
    }

    public void updateHologramData() {
        if (hologram != null) {
            hologram.updateData(
                    stackSize,
                    entityType,
                    spawnerExp,
                    maxStoredExp,
                    virtualInventory.getUsedSlots(),
                    maxSpawnerLootSlots
            );
        }
    }

    public void reloadHologramData() {
        if (hologram != null) {
            hologram.remove();
            this.hologram = new SpawnerHologram(spawnerLocation);
            this.hologram.createHologram();
            hologram.updateData(
                    stackSize,
                    entityType,
                    spawnerExp,
                    maxStoredExp,
                    virtualInventory.getUsedSlots(),
                    maxSpawnerLootSlots
            );
        }
    }

    public void refreshHologram() {
        if (configManager.isHologramEnabled()) {
            if (hologram == null) {
                this.hologram = new SpawnerHologram(spawnerLocation);
                this.hologram.createHologram();
                updateHologramData();
            }
        } else {
            if (hologram != null) {
                hologram.remove();
                hologram = null;
            }
        }
    }

    public void removeHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
    }

    public void removeGhostHologram() {
        if (hologram != null && configManager.isHologramEnabled()) {
            hologram.cleanupExistingHologram();
        }
    }

    public OptimizedVirtualInventory getVirtualInventory() {
        return virtualInventory;
    }

    public void setVirtualInventory(OptimizedVirtualInventory inventory) {
        this.virtualInventory = inventory;
    }

    // Add method to get items for display in real inventory
    public Map<Integer, ItemStack> getDisplayInventory() {
        return virtualInventory.getDisplayInventory();
    }

    private void loadConfigValues() {
        int baseMaxStoredExp = configManager.getMaxStoredExp();
        int baseMinMobs = configManager.getMinMobs();
        int baseMaxMobs = configManager.getMaxMobs();
        int baseSpawnerDelay = configManager.getSpawnerDelay();
        int maxStoragePages = configManager.getMaxStoragePages();

        if (maxStoragePages <= 0) {
            logger.warning("Invalid max-storage-pages value. Setting to default: 1");
            maxStoragePages = 1;
        }

        this.maxSpawnerLootSlots = (45 * maxStoragePages) * stackSize;
        if (this.maxSpawnerLootSlots < 0) this.maxSpawnerLootSlots = 45;

        this.maxStoredExp = baseMaxStoredExp * stackSize;
        if (this.maxStoredExp <= 0) {
            logger.warning("Invalid max-stored-exp value after scaling. Setting to base value: " + baseMaxStoredExp);
            this.maxStoredExp = baseMaxStoredExp;
        }

        this.minMobs = baseMinMobs * stackSize;
        if (this.minMobs <= 0) {
            logger.warning("Invalid min-mobs value after scaling. Setting to base value: " + baseMinMobs);
            this.minMobs = baseMinMobs;
        }

        this.maxMobs = baseMaxMobs * stackSize;
        if (this.maxMobs <= 0 || this.maxMobs <= this.minMobs) {
            logger.warning("Invalid max-mobs value after scaling. Setting to: " + (this.minMobs + stackSize));
            this.maxMobs = this.minMobs + stackSize;
        }

        this.spawnDelay = baseSpawnerDelay;
        if (this.spawnDelay <= 0) {
            logger.warning("Invalid delay value. Setting to default: 600");
            this.spawnDelay = 600;
        }

        this.spawnerRange = configManager.getSpawnerRange();
        if (this.spawnerRange <= 0) {
            logger.warning("Invalid range value. Setting to default: 16");
            this.spawnerRange = 16;
        }
    }

    public void setStackSize(int stackSize) {
        int maxAllowedStack = configManager.getMaxStackSize();
        if (stackSize <= 0) {
            this.stackSize = 1;
            logger.warning("Invalid stack size. Setting to 1");
            return;
        }

        if (stackSize > maxAllowedStack) {
            this.stackSize = maxAllowedStack;
            logger.warning("Stack size exceeds maximum. Setting to " + maxAllowedStack);
            return;
        }

        // Get current consolidated items
        Map<OptimizedVirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();

        // Calculate new max slots
        int maxStoragePages = configManager.getMaxStoragePages();
        int newMaxSlots = (45 * maxStoragePages) * stackSize;

        // Create new inventory with new size
        OptimizedVirtualInventory newInventory = new OptimizedVirtualInventory(newMaxSlots);

        // Convert consolidated items to ItemStack list for adding to new inventory
        List<ItemStack> itemsToTransfer = new ArrayList<>();
        currentItems.forEach((signature, amount) -> {
            ItemStack template = signature.getTemplate();
            while (amount > 0) {
                int batchSize = (int) Math.min(amount, Integer.MAX_VALUE);
                ItemStack batch = template.clone();
                batch.setAmount(batchSize);
                itemsToTransfer.add(batch);
                amount -= batchSize;
            }
        });

        // Update stack size and config values
        this.stackSize = stackSize;
        loadConfigValues();
        this.lastSpawnTime = System.currentTimeMillis() + (long) this.spawnDelay;

        // Add items to new inventory
        newInventory.addItems(itemsToTransfer);
        this.virtualInventory = newInventory;
        updateHologramData();
    }

    public void setStackSize(int stackSize, Player player) {
        int maxAllowedStack = configManager.getMaxStackSize();
        if (stackSize <= 0) {
            this.stackSize = 1;
            configManager.debug("Invalid stack size. Setting to 1");
            return;
        }

        if (stackSize > maxAllowedStack) {
            this.stackSize = maxAllowedStack;
            configManager.debug("Stack size exceeds maximum. Setting to " + maxAllowedStack);
            return;
        }

        // Get current consolidated items
        Map<OptimizedVirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();

        // Calculate new max slots
        int maxStoragePages = configManager.getMaxStoragePages();
        int newMaxSlots = (45 * maxStoragePages) * stackSize;

        // Calculate total items currently stored
        long totalItems = virtualInventory.getTotalItems();
        long newCapacity = (long) newMaxSlots * 64; // Assuming 64 is max stack size

        // Check if we'll lose items
        if (totalItems > newCapacity) {
            languageManager.sendMessage(player, "messages.items-lost");
        }

        // Create new inventory with new size
        OptimizedVirtualInventory newInventory = new OptimizedVirtualInventory(newMaxSlots);

        // Convert consolidated items to ItemStack list for adding to new inventory
        List<ItemStack> itemsToTransfer = new ArrayList<>();
        currentItems.forEach((signature, amount) -> {
            ItemStack template = signature.getTemplate();
            while (amount > 0) {
                int batchSize = (int) Math.min(amount, Integer.MAX_VALUE);
                ItemStack batch = template.clone();
                batch.setAmount(batchSize);
                itemsToTransfer.add(batch);
                amount -= batchSize;
            }
        });

        // Update stack size and config values
        this.stackSize = stackSize;
        loadConfigValues();
        this.lastSpawnTime = System.currentTimeMillis() + (long) this.spawnDelay;

        // Add items to new inventory
        newInventory.addItems(itemsToTransfer);
        this.virtualInventory = newInventory;
        updateHologramData();
    }

    public int getStackSize() {
        return stackSize;
    }

    public void decreaseStackSizeByOne() {
        this.stackSize -= 1;
    }

    // Getters and Setters

    public String getSpawnerId() {
        return spawnerId;
    }

    public Location getSpawnerLocation() {
        return spawnerLocation;
    }

    public Integer getSpawnerExp() {
        return spawnerExp;
    }

    public void setSpawnerExp(int exp) {
        this.spawnerExp = Math.min(exp, maxStoredExp);
        updateHologramData();
    }

    public Integer getMaxStoredExp(){
        return maxStoredExp;
    }

    public void setMaxStoredExp(int maxStoredExp) {
        this.maxStoredExp = maxStoredExp;
    }

    public Boolean isSpawnerActive() {
        return spawnerActive;
    }

    public void setSpawnerActive(Boolean spawnerActive) {
        this.spawnerActive = spawnerActive;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public int getMaxSpawnerLootSlots() {
        return maxSpawnerLootSlots;
    }

    public void setMaxSpawnerLootSlots(int maxSpawnerLootSlots) {
        this.maxSpawnerLootSlots = maxSpawnerLootSlots;
    }

    public int getMinMobs() {
        return minMobs;
    }

    public void setMinMobs(int minMobs) {
        this.minMobs = minMobs;
    }

    public int getMaxMobs() {
        return maxMobs;
    }

    public void setMaxMobs(int maxMobs) {
        this.maxMobs = maxMobs;
    }

    public Integer getSpawnerRange() {
        return spawnerRange;
    }
    
    public void setSpawnerRange(Integer spawnerRange) {
        this.spawnerRange = spawnerRange;
    }

    public Boolean getSpawnerActive() {
        return spawnerActive;
    }

    public Boolean getSpawnerStop() {
        return spawnerStop;
    }

    public void setSpawnerStop(Boolean spawnerStop) {
        this.spawnerStop = spawnerStop;
    }

    public Long getLastSpawnTime() {
        return lastSpawnTime;
    }

    public void setLastSpawnTime(Long lastSpawnTime) {
        this.lastSpawnTime = lastSpawnTime;
    }

    public Integer getSpawnDelay() {
        return spawnDelay;
    }

    public void setSpawnDelay(Integer spawnDelay) {
        this.spawnDelay = spawnDelay;
    }

    public boolean isAllowEquipmentItems() {
        return allowEquipmentItems;
    }

    public void setAllowEquipmentItems(boolean allowEquipmentItems) {
        this.allowEquipmentItems = allowEquipmentItems;
    }
}