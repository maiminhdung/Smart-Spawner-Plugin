package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Handles all file operations for spawner data, including saving and loading.
 * Implements efficient saving strategies to minimize I/O operations.
 */
public class SpawnerFileHandler {
    private final SmartSpawner plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private File spawnerDataFile;
    private FileConfiguration spawnerData;

    // Queue for managing individual spawner saves
    private final ConcurrentLinkedQueue<String> saveQueue = new ConcurrentLinkedQueue<>();

    // Track modified spawners for efficient batch saving
    private final Set<String> modifiedSpawners = ConcurrentHashMap.newKeySet();

    // Lock to prevent concurrent file operations
    private boolean isSaving = false;

    // Task ID for periodic save task
    private int saveTaskId = -1;

    /**
     * Creates a new file handler for spawner data
     *
     * @param plugin The SmartSpawner plugin instance
     */
    public SpawnerFileHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager();
        setupSpawnerDataFile();
        startSaveTask();
    }

    /**
     * Sets up the spawner data file, creating it if it doesn't exist
     */
    private void setupSpawnerDataFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        spawnerDataFile = new File(plugin.getDataFolder(), "spawners_data.yml");

        if (!spawnerDataFile.exists()) {
            try {
                spawnerDataFile.createNewFile();
                String header = """
                # File Format Example:
                #  spawners:
                #    spawnerId:
                #      location: world,x,y,z
                #      entityType: ENTITY_TYPE
                #      settings: exp,active,range,stop,delay,slots,maxExp,minMobs,maxMobs,stack,time,equipment
                #      inventory:
                #        - ITEM_TYPE:amount
                #        - ITEM_TYPE;durability:amount,durability:amount,...
                """;

                Files.write(spawnerDataFile.toPath(), header.getBytes(), StandardOpenOption.WRITE);
            } catch (IOException e) {
                logger.severe("Could not create spawners_data.yml!");
                e.printStackTrace();
            }
        }

        spawnerData = YamlConfiguration.loadConfiguration(spawnerDataFile);

        spawnerData.options().header("""
        File Format Example:
         spawners:
           spawnerId:
             location: world,x,y,z
             entityType: ENTITY_TYPE
             settings: exp,active,range,stop,delay,slots,maxExp,minMobs,maxMobs,stack,time,equipment
             inventory:
               - ITEM_TYPE:amount
               - ITEM_TYPE;durability:amount,durability:amount,...
        """);
    }

    /**
     * Starts periodic save task for all modified spawners
     */
    private void startSaveTask() {
        configManager.debug("Starting spawner data save task");
        int intervalSeconds = configManager.getInt("save-interval");

        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
        }

        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            configManager.debug("Running scheduled save task - interval: " + intervalSeconds + "s");
            saveModifiedSpawners();
        }, intervalSeconds * 20L, intervalSeconds * 20L).getTaskId();
    }

    /**
     * Saves a specific spawner to the data file
     *
     * @param spawnerId The ID of the spawner to save
     * @param spawner The spawner data to save
     * @return True if save was successful, false otherwise
     */
    public boolean saveIndividualSpawner(String spawnerId, SpawnerData spawner) {
        if (spawner == null) return false;

        try {
            String path = "spawners." + spawnerId;
            Location loc = spawner.getSpawnerLocation();

            // Ensure spawners section exists
            if (spawnerData.getConfigurationSection("spawners") == null) {
                spawnerData.createSection("spawners");
            }

            // Save basic spawner properties
            spawnerData.set(path + ".location", String.format("%s,%d,%d,%d",
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            spawnerData.set(path + ".entityType", spawner.getEntityType().name());

            // Build settings string efficiently
            String settings = String.valueOf(spawner.getSpawnerExp()) + ',' +
                    spawner.getSpawnerActive() + ',' +
                    spawner.getSpawnerRange() + ',' +
                    spawner.getSpawnerStop() + ',' +
                    spawner.getSpawnDelay() + ',' +
                    spawner.getMaxSpawnerLootSlots() + ',' +
                    spawner.getMaxStoredExp() + ',' +
                    spawner.getMinMobs() + ',' +
                    spawner.getMaxMobs() + ',' +
                    spawner.getStackSize() + ',' +
                    spawner.getLastSpawnTime() + ',' +
                    spawner.isAllowEquipmentItems();

            spawnerData.set(path + ".settings", settings);

            // Save VirtualInventory if available
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv != null) {
                Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();
                List<String> serializedItems = ItemStackSerializer.serializeInventory(items);
                spawnerData.set(path + ".inventory", serializedItems);
            }

            // Save file
            spawnerData.save(spawnerDataFile);
            return true;

        } catch (IOException e) {
            logger.severe("Could not save spawner " + spawnerId + " to file!");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Adds a spawner to the save queue for efficient batch processing
     *
     * @param spawnerId The ID of the spawner to save
     */
    public void queueSpawnerForSaving(String spawnerId) {
        saveQueue.add(spawnerId);
        processSaveQueue();
    }

    /**
     * Marks a spawner as modified without immediate saving
     * Will be saved during next batch save operation
     *
     * @param spawnerId The ID of the spawner that was modified
     */
    public void markSpawnerModified(String spawnerId) {
        modifiedSpawners.add(spawnerId);
    }

    /**
     * Processes the save queue, saving one spawner at a time
     * Runs asynchronously to prevent server lag
     */
    private synchronized void processSaveQueue() {
        if (isSaving || saveQueue.isEmpty()) return;

        isSaving = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String spawnerId = saveQueue.poll();
                if (spawnerId != null) {
                    // Get spawner from SpawnerManager and save it
                    SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerId);
                    if (spawner != null) {
                        saveIndividualSpawner(spawnerId, spawner);
                    }
                }
            } finally {
                isSaving = false;
                if (!saveQueue.isEmpty()) {
                    processSaveQueue(); // Process next spawner in queue
                }
            }
        });
    }

    /**
     * Saves all modified spawners in a single batch operation
     * Called periodically by the save task
     */
    public void saveModifiedSpawners() {
        if (modifiedSpawners.isEmpty()) {
            configManager.debug("No modified spawners to save");
            return;
        }

        Set<String> toSave = new HashSet<>(modifiedSpawners);
        modifiedSpawners.clear();

        if (!toSave.isEmpty()) {
            configManager.debug("Batch saving " + toSave.size() + " modified spawners");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                int savedCount = 0;
                for (String id : toSave) {
                    SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(id);
                    if (spawner != null) {
                        if (saveIndividualSpawner(id, spawner)) {
                            savedCount++;
                        }
                    }
                }
                configManager.debug("Batch save completed: " + savedCount + " spawners saved");
            });
        }
    }

    /**
     * Saves all spawners at once - use sparingly, preferably only on server shutdown
     *
     * @param spawners Map of all spawners to save
     * @return True if save was successful
     */
    public boolean saveAllSpawners(Map<String, SpawnerData> spawners) {
        try {
            // Preserve data_version if it exists, otherwise set default
            int dataVersion = spawnerData.getInt("data_version", 2);
            spawnerData.set("data_version", dataVersion);

            // Get existing spawners section or create new one
            ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
            if (spawnersSection == null) {
                spawnersSection = spawnerData.createSection("spawners");
            }

            // Clear save queue and modified set to prevent duplicate operations
            saveQueue.clear();
            modifiedSpawners.clear();

            // Track existing spawner IDs to remove only those that no longer exist
            Set<String> existingIds = new HashSet<>(spawnersSection.getKeys(false));
            Set<String> currentIds = spawners.keySet();

            // Remove only spawners that don't exist anymore
            ConfigurationSection finalSpawnersSection = spawnersSection;
            existingIds.stream()
                    .filter(id -> !currentIds.contains(id))
                    .forEach(id -> finalSpawnersSection.set(id, null));

            // Save all current spawners
            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                SpawnerData spawner = entry.getValue();
                Location loc = spawner.getSpawnerLocation();
                String path = "spawners." + spawnerId;

                // Save basic spawner properties
                spawnerData.set(path + ".location", String.format("%s,%d,%d,%d",
                        loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                spawnerData.set(path + ".entityType", spawner.getEntityType().name());

                // Save settings
                String settings = String.valueOf(spawner.getSpawnerExp()) + ',' +
                        spawner.getSpawnerActive() + ',' +
                        spawner.getSpawnerRange() + ',' +
                        spawner.getSpawnerStop() + ',' +
                        spawner.getSpawnDelay() + ',' +
                        spawner.getMaxSpawnerLootSlots() + ',' +
                        spawner.getMaxStoredExp() + ',' +
                        spawner.getMinMobs() + ',' +
                        spawner.getMaxMobs() + ',' +
                        spawner.getStackSize() + ',' +
                        spawner.getLastSpawnTime() + ',' +
                        spawner.isAllowEquipmentItems();

                spawnerData.set(path + ".settings", settings);

                // Save VirtualInventory
                VirtualInventory virtualInv = spawner.getVirtualInventory();
                if (virtualInv != null) {
                    Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();
                    List<String> serializedItems = ItemStackSerializer.serializeInventory(items);
                    spawnerData.set(path + ".inventory", serializedItems);
                }
            }

            spawnerData.save(spawnerDataFile);
            return true;
        } catch (IOException e) {
            logger.severe("Could not save spawners_data.yml!");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deletes a spawner from the data file
     *
     * @param spawnerId The ID of the spawner to delete
     * @return True if deletion was successful
     */
    public boolean deleteSpawnerFromFile(String spawnerId) {
        try {
            String path = "spawners." + spawnerId;
            spawnerData.set(path, null);
            spawnerData.save(spawnerDataFile);

            // Remove from tracking sets
            modifiedSpawners.remove(spawnerId);

            configManager.debug("Successfully deleted spawner " + spawnerId + " from data file");
            return true;
        } catch (IOException e) {
            logger.severe("Could not delete spawner " + spawnerId + " from spawners_data.yml!");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads all spawner data from the file
     *
     * @return Map of spawner IDs to SpawnerData objects
     */
    public Map<String, SpawnerData> loadAllSpawners() {
        Map<String, SpawnerData> loadedSpawners = new HashMap<>();

        ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
        if (spawnersSection == null) return loadedSpawners;

        int loadedCount = 0;
        int errorCount = 0;

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                SpawnerData spawner = loadSpawnerFromConfig(spawnerId);
                if (spawner != null) {
                    loadedSpawners.put(spawnerId, spawner);
                    loadedCount++;
                }
            } catch (Exception e) {
                logger.severe("Error loading spawner " + spawnerId);
                e.printStackTrace();
                errorCount++;
            }
        }

        logger.info("Loaded " + loadedCount + " spawners. Errors: " + errorCount);
        return loadedSpawners;
    }

    /**
     * Loads a single spawner from the configuration
     *
     * @param spawnerId The ID of the spawner to load
     * @return The loaded SpawnerData object, or null if loading failed
     */
    private SpawnerData loadSpawnerFromConfig(String spawnerId) {
        String path = "spawners." + spawnerId;

        // Load location
        String locationString = spawnerData.getString(path + ".location");
        if (locationString == null) {
            logger.warning("Invalid location for spawner " + spawnerId);
            return null;
        }

        String[] locParts = locationString.split(",");
        if (locParts.length != 4) {
            logger.warning("Invalid location format for spawner " + spawnerId);
            return null;
        }

        org.bukkit.World world = Bukkit.getWorld(locParts[0]);
        if (world == null) {
            logger.warning("World not found for spawner " + spawnerId + ": " + locParts[0]);
            return null;
        }

        Location location = new Location(world,
                Integer.parseInt(locParts[1]),
                Integer.parseInt(locParts[2]),
                Integer.parseInt(locParts[3]));

        // Check if this is a ghost spawner - the block at the location should be a MOB_SPAWNER
        if (!isSpawnerBlock(location)) {
            logger.warning("Ghost spawner detected at " + locationString + " with ID " + spawnerId +
                    " - No physical spawner block found.");
            return null;
        }

        // Load entity type
        String entityTypeString = spawnerData.getString(path + ".entityType");
        if (entityTypeString == null) {
            logger.warning("Missing entity type for spawner " + spawnerId);
            return null;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeString);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid entity type for spawner " + spawnerId + ": " + entityTypeString);
            return null;
        }

        // Create spawner instance
        SpawnerData spawner = new SpawnerData(spawnerId, location, entityType, plugin);

        // Load settings
        String settingsString = spawnerData.getString(path + ".settings");
        if (settingsString != null) {
            String[] settings = settingsString.split(",");
            if (settings.length >= 12) {
                try {
                    spawner.setSpawnerExp(Integer.parseInt(settings[0]));
                    spawner.setSpawnerActive(Boolean.parseBoolean(settings[1]));
                    spawner.setSpawnerRange(Integer.parseInt(settings[2]));
                    spawner.setSpawnerStop(Boolean.parseBoolean(settings[3]));
                    spawner.setSpawnDelay(Integer.parseInt(settings[4]));
                    spawner.setMaxSpawnerLootSlots(Integer.parseInt(settings[5]));
                    spawner.setMaxStoredExp(Integer.parseInt(settings[6]));
                    spawner.setMinMobs(Integer.parseInt(settings[7]));
                    spawner.setMaxMobs(Integer.parseInt(settings[8]));
                    spawner.setStackSize(Integer.parseInt(settings[9]));
                    spawner.setLastSpawnTime(Long.parseLong(settings[10]));
                    spawner.setAllowEquipmentItems(Boolean.parseBoolean(settings[11]));
                } catch (NumberFormatException e) {
                    logger.warning("Invalid settings format for spawner " + spawnerId);
                    return null;
                }
            }
        }

        // Load inventory
        List<String> inventoryData = spawnerData.getStringList(path + ".inventory");
        VirtualInventory virtualInv = new VirtualInventory(spawner.getMaxSpawnerLootSlots());

        if (inventoryData != null && !inventoryData.isEmpty()) {
            try {
                Map<ItemStack, Integer> items = ItemStackSerializer.deserializeInventory(inventoryData);
                for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
                    ItemStack item = entry.getKey();
                    int amount = entry.getValue();

                    if (item != null && amount > 0) {
                        while (amount > 0) {
                            int batchSize = Math.min(amount, item.getMaxStackSize());
                            ItemStack batch = item.clone();
                            batch.setAmount(batchSize);
                            virtualInv.addItems(Collections.singletonList(batch));
                            amount -= batchSize;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading inventory for spawner " + spawnerId);
                e.printStackTrace();
            }
        }

        spawner.setVirtualInventory(virtualInv);
        return spawner;
    }

    /**
     * Checks if the block at the given location is a MOB_SPAWNER
     *
     * @param location The location to check
     * @return true if the block is a MOB_SPAWNER, false otherwise
     */
    private boolean isSpawnerBlock(Location location) {
        // Make sure we run this on the main thread if needed
        if (!Bukkit.isPrimaryThread()) {
            // Return a default value when running async, will be checked properly during processing
            return true;
        }

        // Check if the chunk is loaded
        if (!location.getChunk().isLoaded()) {
            // We can't check unloaded chunks, so we'll assume it's valid for now
            return true;
        }

        return location.getBlock().getType() == Material.SPAWNER;
    }

    /**
     * Reloads spawner data from file
     */
    public void reloadSpawnerData() {
        spawnerData = YamlConfiguration.loadConfiguration(spawnerDataFile);
    }

    /**
     * Gets the current data file
     *
     * @return The spawner data file
     */
    public File getSpawnerDataFile() {
        return spawnerDataFile;
    }
}