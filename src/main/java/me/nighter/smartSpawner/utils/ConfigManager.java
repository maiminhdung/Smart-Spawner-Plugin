package me.nighter.smartSpawner.utils;

import me.nighter.smartSpawner.SmartSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ConfigManager {
    private final SmartSpawner plugin;
    private final Logger logger;
    private FileConfiguration config;
    private FileConfiguration lootConfig;
    private File configFile;
    private File lootConfigFile;
    private Map<String, Object> configCache;
    private final String CURRENT_CONFIG_VERSION = "1.2.3.0";

    public ConfigManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configCache = new ConcurrentHashMap<>();
        loadConfigs();
    }

    // ===============================================================
    //                      Utility Methods
    // ===============================================================

    private void loadConfigs() {
        loadMainConfig();
        loadLootConfig();
        initializeCache();
    }

    public void reloadConfigs() {
        loadConfigs();
    }

    public void debug(String message) {
        if (isDebugMode()) {
            logger.info("[DEBUG] " + message);
        }
    }

    // ===============================================================
    //                      Main Config Management
    // ===============================================================

    public FileConfiguration getMainConfig() {
        return config;
    }

    private void loadMainConfig() {
        ensureDirectoryExists();
        configFile = new File(plugin.getDataFolder(), "config.yml");

        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            String configVersion = config.getString("config-version", "0.0.0.0");

            if (!hasAllConfigValues() || compareVersions(configVersion, CURRENT_CONFIG_VERSION) < 0) {
                logger.info("Config update needed - Missing values or outdated version detected");
                updateConfigWithComments();
            }
        } else {
            createNewConfig();
        }
    }

    private void updateConfigWithComments() {
        // Backup current values
        Map<String, Object> oldValues = backupCurrentConfig();

        // Create temporary file for the new config
        File tempFile = new File(plugin.getDataFolder(), "temp_config.yml");

        try {
            // Save the default config from resources to temp file
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    // Read all lines from the default config
                    List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.toList());

                    // Create new lines with the header format
                    List<String> newLines = new ArrayList<>();
                    newLines.add("# Configuration version - Do not modify this value");
                    newLines.add("config-version: '" + CURRENT_CONFIG_VERSION + "'");
                    newLines.add("");  // Add empty line for better readability
                    newLines.addAll(defaultLines);

                    // Write the formatted content to temp file
                    Files.write(tempFile.toPath(), newLines, StandardCharsets.UTF_8);
                }
            }

            // Load the new config with comments
            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);

            // Restore old values
            restoreConfigValues(oldValues, newConfig);

            // Set the new version
            newConfig.set("config-version", CURRENT_CONFIG_VERSION);

            // Save the new config
            newConfig.save(configFile);

            // Delete temp file
            tempFile.delete();

            // Reload the config
            config = YamlConfiguration.loadConfiguration(configFile);

            // Update cache
            initializeCache();

            logger.info("Config successfully updated to version " + CURRENT_CONFIG_VERSION);

        } catch (IOException e) {
            logger.severe("Failed to update config with comments: " + e.getMessage());
            handleConfigUpdateError(oldValues);
        }
    }


    private void handleConfigUpdateError(Map<String, Object> backup) {
        logger.warning("Attempting to restore from backup due to update failure...");
        try {
            // Create a new configuration
            FileConfiguration recoveryConfig = new YamlConfiguration();

            // Restore all backed up values
            for (Map.Entry<String, Object> entry : backup.entrySet()) {
                recoveryConfig.set(entry.getKey(), entry.getValue());
            }

            // Save the recovery config
            recoveryConfig.save(configFile);

            // Reload the config
            config = YamlConfiguration.loadConfiguration(configFile);

            logger.info("Successfully restored config from backup");
        } catch (IOException e) {
            logger.severe("Critical error: Failed to restore config from backup: " + e.getMessage());
        }
    }

    private boolean hasAllConfigValues() {
        List<String> missingValues = new ArrayList<>();

        // Check all default config keys
        for (String key : defaultConfig.keySet()) {
            if (!config.contains(key)) {
                missingValues.add(key);
            }
        }

        // If there are missing values, log them all
        if (!missingValues.isEmpty()) {
            logger.info("Found " + missingValues.size() + " missing config values:");
            for (String key : missingValues) {
                logger.info("- Missing config value: " + key + " (Default: " + defaultConfig.get(key) + ")");
            }

            // Log additional information for specific types of values
            missingValues.forEach(key -> {
                Object defaultValue = defaultConfig.get(key);
                if (defaultValue instanceof List) {
                    debug("  * List value for " + key + " should contain: " + defaultValue);
                } else if (defaultValue instanceof Map) {
                    debug("  * Map value for " + key + " should contain: " + defaultValue);
                } else if (defaultValue instanceof Boolean) {
                    debug("  * Boolean value for " + key + " should be: " + defaultValue);
                } else if (defaultValue instanceof Number) {
                    debug("  * Numeric value for " + key + " should be: " + defaultValue);
                }
            });

            return false;
        }
        return true;
    }

    private void createNewConfig() {
        try {
            // First get the default config content
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    // Read all lines from the default config
                    List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.toList());

                    // Create new lines with the header format
                    List<String> newLines = new ArrayList<>();
                    newLines.add("# Configuration version - Do not modify this value");
                    newLines.add("config-version: '" + CURRENT_CONFIG_VERSION + "'");
                    newLines.add("");  // Add empty line for better readability
                    newLines.addAll(defaultLines);

                    // Ensure the plugin folder exists
                    ensureDirectoryExists();

                    // Write the formatted content to config file
                    Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                } else {
                    // Fallback if resource not found
                    plugin.saveResource("config.yml", false);
                }
            }

            // Load the config
            config = YamlConfiguration.loadConfiguration(configFile);

        } catch (IOException e) {
            logger.severe("Error creating config file: " + e.getMessage());
            // Fallback to default saving method
            plugin.saveResource("config.yml", false);
            config = YamlConfiguration.loadConfiguration(configFile);
        }
    }

    private Map<String, Object> backupCurrentConfig() {
        Map<String, Object> oldValues = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                oldValues.put(key, config.get(key));
                debug("Backed up config value: " + key);
            }
        }
        return oldValues;
    }

    private void restoreConfigValues(Map<String, Object> oldValues, FileConfiguration newConfig) {
        for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Skip the version as we want to use the new one
            if (!key.equals("config-version")) {
                // Only restore if the key exists in the new config
                if (newConfig.contains(key)) {
                    newConfig.set(key, value);
                    debug("Restored config value: " + key);
                } else {
                    debug("Skipped restoring non-existent key: " + key);
                }
            }
        }
    }

    public void saveMainConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.severe("Failed to save config: " + e.getMessage());
        }
    }

    private void ensureDirectoryExists() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }

    private int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");

        for (int i = 0; i < Math.max(v1Parts.length, v2Parts.length); i++) {
            int v1 = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2 = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

            if (v1 != v2) {
                return v1 - v2;
            }
        }
        return 0;
    }

    private final Map<String, Object> defaultConfig = new HashMap<String, Object>() {{
        put("config-version", CURRENT_CONFIG_VERSION);

        // Global Settings
        put("settings.language", "en_US");
        put("settings.debug", false);
        put("settings.save-interval", 6000);

        // Spawner Core Mechanics
        put("spawner.default-entity", "PIG");
        put("spawner.min-mobs", 1);
        put("spawner.max-mobs", 4);
        put("spawner.range", 16);
        put("spawner.delay", 600);
        put("spawner.max-storage-pages", 1);
        put("spawner.max-stored-exp", 1000);
        put("spawner.max-stack-size", 1000);
        put("spawner.allow-exp-mending", true);
        put("spawner.allow-toggle-equipment-drops", true);
        put("spawner.allow-grief", false);
        put("spawner.activate-on-place", true);

        // Spawner Breaking Mechanics
        put("spawner-break.enabled", true);
        put("spawner-break.required-tools", Arrays.asList(
                "IRON_PICKAXE",
                "GOLDEN_PICKAXE",
                "DIAMOND_PICKAXE",
                "NETHERITE_PICKAXE"
        ));
        put("spawner-break.durability-loss-per-spawner", 1);
        put("spawner-break.silk-touch.required", true);
        put("spawner-break.silk-touch.level", 1);
        put("spawner-break.drop-stack.amount", 64);

        // Hologram Settings
        put("hologram.enabled", false);
        put("hologram.see-through", false);
        put("hologram.shadowed-text", true);
        put("hologram.height", 1.6);
        put("hologram.offset.x", 0.5);
        put("hologram.offset.z", 0.5);

        // Particle Effect Settings
        put("particles.loot-spawn", true);
        put("particles.spawner-stack", true);
        put("particles.spawner-activate", true);

        // Economic Integration
        put("shop-integration", "auto");
        put("formated-price", true);
        put("tax.enabled", false);
        put("tax.rate", 10.0);

        // Logging
        put("logging.enabled", true);
        put("logging.file-path", "shop-logs/sales.log");

        // Hopper Mechanics
        put("hopper.enabled", false);
        put("hopper.items-per-transfer", 1);
        put("hopper.check-interval", 20);

        // Update Checker
        put("update-checker.enabled", true);
        put("update-checker.check-interval", 24);
        put("update-checker.notify-ops", true);
        put("update-checker.notify-on-join", true);
    }};

    private void initializeCache() {
        configCache.clear();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                configCache.put(key, config.get(key));
            }
        }
    }

    // ===============================================================
    //                      Loot Configs
    // ===============================================================

    public FileConfiguration getLootConfig() {
        return lootConfig;
    }

    public void loadLootConfig() {
        lootConfigFile = new File(plugin.getDataFolder(), "mob_drops.yml");
        if (!lootConfigFile.exists()) {
            debug("Creating default mob_drops.yml");
            plugin.saveResource("mob_drops.yml", false);
        }
        lootConfig = YamlConfiguration.loadConfiguration(lootConfigFile);
        mergeLootDefaults();
    }

    private void mergeLootDefaults() {
        try (InputStream defaultLootConfigStream = plugin.getResource("mob_drops.yml")) {
            if (defaultLootConfigStream != null) {
                YamlConfiguration defaultLootConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultLootConfigStream, StandardCharsets.UTF_8)
                );

                for (String key : defaultLootConfig.getKeys(true)) {
                    if (!lootConfig.contains(key)) {
                        debug("Adding missing default config value: " + key);
                        lootConfig.set(key, defaultLootConfig.get(key));
                    }
                }
                saveLootConfig();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading default loot config: " + e.getMessage());
        }
    }

    private void saveLootConfig() {
        try {
            lootConfig.save(lootConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save loot config: " + e.getMessage());
        }
    }

    // ===============================================================
    //            Generic getter methods for different types
    // ===============================================================

    public String getString(String path) {
        return (String) configCache.computeIfAbsent(path,
                key -> config.getString(key, (String) defaultConfig.get(key)));
    }

    public int getInt(String path) {
        return (int) configCache.computeIfAbsent(path,
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public boolean getBoolean(String path) {
        return (boolean) configCache.computeIfAbsent(path,
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public double getDouble(String path) {
        return (double) configCache.computeIfAbsent(path,
                key -> config.getDouble(key, (double) defaultConfig.get(key)));
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        return (List<String>) configCache.computeIfAbsent(path,
                key -> config.getStringList(key));
    }

    // ===============================================================
    //                      Global Settings
    // ===============================================================

    public boolean isDebugMode() {
        return (boolean) configCache.computeIfAbsent("settings.debug",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public int getSaveInterval() {
        return (int) configCache.computeIfAbsent("settings.save-interval",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    // ===============================================================
    //                Spawner Core Mechanics Configs
    // ===============================================================

    public EntityType getDefaultEntityType() {
        Object cachedValue = configCache.get("spawner.default-entity");

        if (cachedValue instanceof EntityType) {
            return (EntityType) cachedValue;
        }

        String defaultValue = "PIG";
        String entityName;

        if (cachedValue instanceof String) {
            entityName = ((String) cachedValue).toUpperCase();
        } else {
            entityName = config.getString("spawner.default-entity", defaultValue).toUpperCase();
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid default entity type: " + entityName);
            entityType = EntityType.PIG;
        }
        configCache.put("spawner.default-entity", entityType);
        return entityType;
    }

    public int getMinMobs() {
        return (int) configCache.computeIfAbsent("spawner.min-mobs",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getMaxMobs() {
        return (int) configCache.computeIfAbsent("spawner.max-mobs",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getSpawnerRange() {
        return (int) configCache.computeIfAbsent("spawner.range",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getSpawnerDelay() {
        return (int) configCache.computeIfAbsent("spawner.delay",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getMaxStoragePages() {
        return (int) configCache.computeIfAbsent("spawner.max-storage-pages",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getMaxStackSize() {
        return (int) configCache.computeIfAbsent("spawner.max-stack-size",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getMaxStoredExp() {
        return (int) configCache.computeIfAbsent("spawner.max-stored-exp",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public boolean isAllowExpMending() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-exp-mending",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean isAllowToggleEquipmentItems() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-toggle-equipment-drops",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean isAllowGrief() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-grief",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean getActivateOnPlace() {
        return (boolean) configCache.computeIfAbsent("spawner.activate-on-place",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    // ===============================================================
    //                   Spawner Breaking Mechanics
    // ===============================================================

    public boolean isSpawnerBreakEnabled() {
        return (boolean) configCache.computeIfAbsent("spawner-break.enabled",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    @SuppressWarnings("unchecked")
    public List<String> getRequiredTools() {
        return (List<String>) configCache.computeIfAbsent("spawner-break.required-tools",
                key -> config.getStringList(key));
    }

    public int getDurabilityLossPerSpawner() {
        return (int) configCache.computeIfAbsent("spawner-break.durability-loss-per-spawner",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public boolean isSilkTouchRequired() {
        return (boolean) configCache.computeIfAbsent("spawner-break.silk-touch.required",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public int getSilkTouchLevel() {
        return (int) configCache.computeIfAbsent("spawner-break.silk-touch.level",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getDropStackAmount() {
        return (int) configCache.computeIfAbsent("spawner-break.drop-stack.amount",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    // ===============================================================
    //                   Spawner Hologram Settings
    // ===============================================================

    public boolean isHologramEnabled() {
        return (boolean) configCache.computeIfAbsent("hologram.enabled",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean isHologramSeeThrough() {
        return (boolean) configCache.computeIfAbsent("hologram.see-through",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean isHologramShadowed() {
        return (boolean) configCache.computeIfAbsent("hologram.shadowed-text",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public double getHologramHeight() {
        return (double) configCache.computeIfAbsent("hologram.height",
                key -> config.getDouble(key, (double) defaultConfig.get(key)));
    }

    public double getHologramOffsetX() {
        return (double) configCache.computeIfAbsent("hologram.offset.x",
                key -> config.getDouble(key, (double) defaultConfig.get(key)));
    }

    public double getHologramOffsetZ() {
        return (double) configCache.computeIfAbsent("hologram.offset.z",
                key -> config.getDouble(key, (double) defaultConfig.get(key)));
    }

    // ===============================================================
    //                     Particle Settings
    // ===============================================================

    public boolean isLootSpawnParticlesEnabled() {
        return (boolean) configCache.computeIfAbsent("particles.loot-spawn",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean isSpawnerStackParticlesEnabled() {
        return (boolean) configCache.computeIfAbsent("particles.spawner-stack",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean isSpawnerCreateParticlesEnabled() {
        return (boolean) configCache.computeIfAbsent("particles.spawner-activate",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    // ===============================================================
    //                   Economic Integration
    // ===============================================================

    public enum ShopType {
        DISABLED,
        AUTO,
        ECONOMY_SHOP_GUI,
        ECONOMY_SHOP_GUI_PREMIUM,
        SHOP_GUI_PLUS,
        ZSHOP;

        public static ShopType fromString(String value) {
            if (value == null) return DISABLED;

            return switch (value.toLowerCase()) {
                case "auto" -> AUTO;
                case "economyshopgui" -> ECONOMY_SHOP_GUI;
                case "economyshopgui-premium" -> ECONOMY_SHOP_GUI_PREMIUM;
                case "shopguiplus" -> SHOP_GUI_PLUS;
                case "zshop" -> ZSHOP;
                case "disabled" -> DISABLED;
                default -> DISABLED;
            };
        }

        @Override
        public String toString() {
            return switch (this) {
                case AUTO -> "auto";
                case ECONOMY_SHOP_GUI -> "economyshopgui";
                case ECONOMY_SHOP_GUI_PREMIUM -> "economyshopgui-premium";
                case SHOP_GUI_PLUS -> "shopguiplus";
                case ZSHOP -> "zshop";
                case DISABLED -> "disabled";
            };
        }
    }

    public ShopType getShopType() {
        String shopType = (String) configCache.computeIfAbsent("shop-integration",
                key -> config.getString(key, "auto"));
        return ShopType.fromString(shopType);
    }

    public boolean isFormatedPrice() {
        return (boolean) configCache.computeIfAbsent("formated-price",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean isTaxEnabled() {
        return (boolean) configCache.computeIfAbsent("tax.enabled",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public double getTaxPercentage() {
        if (!isTaxEnabled()) return 0.0;
        return (double) configCache.computeIfAbsent("tax.rate",
                key -> config.getDouble(key, (double) defaultConfig.get(key)));
    }

    public boolean isLoggingEnabled() {
        return (boolean) configCache.computeIfAbsent("logging.enabled",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public String getLogFilePath() {
        return (String) configCache.computeIfAbsent("logging.file-path",
                key -> config.getString(key, (String) defaultConfig.get(key)));
    }

    // ===============================================================
    //                Hopper Mechanics (Experimental)
    // ===============================================================

    public boolean isHopperEnabled() {
        return (boolean) configCache.computeIfAbsent("hopper.enabled",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public int getHopperItemsPerTransfer() {
        return (int) configCache.computeIfAbsent("hopper.items-per-transfer",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public int getHopperCheckInterval() {
        return (int) configCache.computeIfAbsent("hopper.check-interval",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    // ===============================================================
    //                    Plugin Update Checker
    // ===============================================================

    public boolean isUpdateCheckerEnabled() {
        return (boolean) configCache.computeIfAbsent("update-checker.enabled",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public int getUpdateCheckInterval() {
        return (int) configCache.computeIfAbsent("update-checker.check-interval",
                key -> config.getInt(key, (int) defaultConfig.get(key)));
    }

    public boolean shouldNotifyOps() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-ops",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }

    public boolean shouldNotifyOnJoin() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-on-join",
                key -> config.getBoolean(key, (boolean) defaultConfig.get(key)));
    }
}
