package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.hologram.SpawnerHologram;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.concurrent.locks.ReentrantLock;

public class SpawnerData {
    private final SmartSpawner plugin;
    private static final Logger logger = Logger.getLogger("SmartSpawnerConfig");
    private final LanguageManager languageManager;
    private final ConfigManager configManager;

    // Spawner properties
    private final String spawnerId;
    private final Location spawnerLocation;
    private Integer spawnerExp;
    private Boolean spawnerActive;
    private Integer spawnerRange;
    private Boolean spawnerStop;
    private Boolean isAtCapacity;
    private Long lastSpawnTime;
    private Integer spawnDelay;
    private EntityType entityType;
    private int maxSpawnerLootSlots;
    private int maxStoredExp;
    private int minMobs;
    private int maxMobs;
    private int stackSize;

    // Inventory properties
    private VirtualInventory virtualInventory;
    private boolean allowEquipmentItems;

    // Hologram properties
    private SpawnerHologram hologram;

    // Other properties
    private final ReentrantLock lock = new ReentrantLock();

    // Cached values
    private long cachedSpawnDelay = 0;
    private Map<String, Object> cachedValues = new ConcurrentHashMap<>();

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = true;
        this.isAtCapacity = false;
        this.stackSize = 1;
        this.maxSpawnerLootSlots = 45;
        this.allowEquipmentItems = true;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        loadConfigValues();
        this.virtualInventory = new VirtualInventory(maxSpawnerLootSlots);
        if (configManager.getBoolean("hologram-enabled")) {
            this.hologram = new SpawnerHologram(location);
            this.hologram.createHologram();
            updateHologramData();
        }
    }

    private void loadConfigValues() {
        int baseMaxStoredExp = configManager.getInt("max-stored-exp");
        int baseMinMobs = configManager.getInt("min-mobs");
        int baseMaxMobs = configManager.getInt("max-mobs");
        int baseSpawnerDelay = configManager.getInt("delay");
        int maxStoragePages = configManager.getInt("max-storage-pages");

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

        this.spawnDelay = baseSpawnerDelay * 20; // Convert seconds to ticks
        if (this.spawnDelay <= 0) {
            logger.warning("Invalid delay value. Setting to default: 20");
            this.spawnDelay = 20;
        }

        this.spawnerRange = configManager.getInt("range");
        if (this.spawnerRange <= 0) {
            logger.warning("Invalid range value. Setting to default: 16");
            this.spawnerRange = 16;
        }
    }

    public void setStackSize(int stackSize) {
        lock.lock();
        try {
            int maxAllowedStack = configManager.getInt("max-stack-size");
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
            Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();

            // Calculate new max slots
            int maxStoragePages = configManager.getInt("max-storage-pages");
            int newMaxSlots = (45 * maxStoragePages) * stackSize;

            // Create new inventory with new size
            VirtualInventory newInventory = new VirtualInventory(newMaxSlots);

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
        } finally {
            lock.unlock();
        }
    }

    public void setStackSize(int stackSize, Player player) {
        lock.lock();
        try {
            int maxAllowedStack = configManager.getInt("max-stack-size");
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
            Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();

            // Calculate new max slots
            int maxStoragePages = configManager.getInt("max-storage-pages");
            int newMaxSlots = (45 * maxStoragePages) * stackSize;

            // Create new inventory with new size
            VirtualInventory newInventory = new VirtualInventory(newMaxSlots);

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
        } finally {
            lock.unlock();
        }
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

    public boolean isAtCapacity() {
        return isAtCapacity;
    }

    public void setAtCapacity(boolean isAtCapacity) {
        this.isAtCapacity = isAtCapacity;
    }

// ===============================================================
//                    Virtual Inventory
// ===============================================================

    public VirtualInventory getVirtualInventory() {
        return virtualInventory;
    }

    public void setVirtualInventory(VirtualInventory inventory) {
        this.virtualInventory = inventory;
    }

    public Map<Integer, ItemStack> getDisplayInventory() {
        return virtualInventory.getDisplayInventory();
    }

// ===============================================================
//                    Spawner Hologram
// ===============================================================

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
        if (configManager.getBoolean("hologram-enabled")) {
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
        if (hologram != null && configManager.getBoolean("hologram-enabled")) {
            hologram.cleanupExistingHologram();
        }
    }
// ===============================================================
//                    Locking Mechanism
// ===============================================================

    public ReentrantLock getLock() {
        return lock;
    }

// ===============================================================
//                    Cached Values
// ===============================================================

    public long getCachedSpawnDelay() {
        return cachedSpawnDelay;
    }

    public void setCachedSpawnDelay(long delay) {
        this.cachedSpawnDelay = delay;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCachedValue(String key, Supplier<T> supplier) {
        return (T) cachedValues.computeIfAbsent(key, k -> supplier.get());
    }

    public void invalidateCache() {
        cachedSpawnDelay = 0;
        cachedValues.clear();
    }

}