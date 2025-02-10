package me.nighter.smartSpawner.dataMigration;

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
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        int dataVersion = config.getInt(MIGRATION_FLAG, 1);

        if (dataVersion >= CURRENT_VERSION) {
            plugin.getLogger().info("Data format is up to date.");
            return false;
        }

        plugin.getLogger().info("Old data format detected. Starting migration process...");

        try {
            if (!createBackup(dataFile)) {
                plugin.getLogger().severe("Failed to create backup. Migration aborted.");
                return false;
            }

            boolean success = migrateData(config, dataFile);

            if (success) {
                plugin.getLogger().info("Data migration completed successfully!");
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