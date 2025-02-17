package me.nighter.smartSpawner.data.migration;

import me.nighter.smartSpawner.SmartSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SpawnerDataMigration {
    private final SmartSpawner plugin;
    private final File dataFolder;
    private static final String DATA_FILE = "spawners_data.yml";
    private static final String BACKUP_FILE = "spawners_data_backup.yml";
    private static final String MIGRATION_FLAG = "data_version";
    private static final int CURRENT_VERSION = 2;

    public SpawnerDataMigration(SmartSpawner plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
    }

    public boolean checkAndMigrateData() {
        File dataFile = new File(dataFolder, DATA_FILE);

        if (!dataFile.exists()) {
            plugin.getLogger().info("No spawner data file found. Creating new one...");
            try {
                FileConfiguration newConfig = new YamlConfiguration();
                newConfig.set(MIGRATION_FLAG, CURRENT_VERSION);
                newConfig.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create new data file: " + e.getMessage());
            }
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        // First, try to validate if the current format works
        boolean needsMigration;
        try {
            needsMigration = false;

            // Check if data_version exists
            if (!config.contains(MIGRATION_FLAG)) {
                config.set(MIGRATION_FLAG, CURRENT_VERSION);
                try {
                    config.save(dataFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save data_version flag: " + e.getMessage());
                }
            }

            // Validate the spawners section
            if (config.contains("spawners")) {
                for (String spawnerId : config.getConfigurationSection("spawners").getKeys(false)) {
                    String spawnerPath = "spawners." + spawnerId;
                    // Check if the spawner data is in the new format
                    if (!config.contains(spawnerPath + ".location") ||
                            !config.contains(spawnerPath + ".settings") ||
                            !config.contains(spawnerPath + ".inventory")) {
                        needsMigration = true;
                        break;
                    }
                }
            }

            if (!needsMigration) {
                //plugin.getLogger().info("Data format is up to date.");
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error validating current data format: " + e.getMessage());
            needsMigration = true;
        }

        // If we reach here, we need to migrate the data
        plugin.getLogger().info("Starting data migration process...");

        try {
            if (!createBackup(dataFile)) {
                plugin.getLogger().severe("Failed to create backup. Migration aborted.");
                return false;
            }

            boolean success = migrateData(config, dataFile);

            if (success) {
                return true;
            } else {
                restoreFromBackup(dataFile);
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error during data migration: " + e.getMessage());
            e.printStackTrace();
            restoreFromBackup(dataFile);
            return false;
        }
    }

    private boolean createBackup(File sourceFile) {
        try {
            File backupFile = new File(dataFolder, BACKUP_FILE);
            Files.copy(sourceFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Backup created successfully at: " + backupFile.getPath());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create backup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void restoreFromBackup(File dataFile) {
        try {
            File backupFile = new File(dataFolder, BACKUP_FILE);
            if (backupFile.exists()) {
                Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Data restored from backup.");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to restore from backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean migrateData(FileConfiguration oldConfig, File dataFile) {
        try {
            // Create new data file
            FileConfiguration newConfig = new YamlConfiguration();

            // Copy data version flag
            newConfig.set(MIGRATION_FLAG, CURRENT_VERSION);

            // Convert old data to new format
            SpawnerDataConverter converter = new SpawnerDataConverter(plugin, oldConfig, newConfig);
            converter.convertData();

            // Save new data to file
            newConfig.save(dataFile);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to migrate data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}