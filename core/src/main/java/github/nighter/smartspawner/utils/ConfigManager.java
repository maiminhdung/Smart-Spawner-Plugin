package github.nighter.smartspawner.utils;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final Map<String, Object> configCache;
    private final String CURRENT_CONFIG_VERSION = "1.2.5.0";

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
        if (isDebugEnabled()) {
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
                    newLines.add("config-version: ' + CURRENT_CONFIG_VERSION + '");
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
                // Map old nested keys to new flattened keys
                String newKey = mapOldKeyToNewKey(key);

                // Only restore if the key exists in the new config
                if (newConfig.contains(newKey)) {
                    newConfig.set(newKey, value);
                    debug("Restored config value: " + key + " to " + newKey);
                } else {
                    debug("Skipped restoring non-existent key: " + newKey);
                }
            }
        }
    }

    private String mapOldKeyToNewKey(String oldKey) {
        // Map from old nested structure to new flattened structure
        Map<String, String> keyMapping = new HashMap<String, String>() {{
            // Plugin Settings
            put("settings.language", "language");
            put("settings.debug", "debug");
            put("settings.save-interval", "save-interval");

            // Spawner Core Mechanics
            put("spawner.default-entity", "default-entity");
            put("spawner.min-mobs", "min-mobs");
            put("spawner.max-mobs", "max-mobs");
            put("spawner.range", "range");
            put("spawner.delay", "delay");
            put("spawner.max-storage-pages", "max-storage-pages");
            put("spawner.max-stored-exp", "max-stored-exp");
            put("spawner.max-stack-size", "max-stack-size");
            put("spawner.allow-exp-mending", "allow-exp-mending");
            put("spawner.allow-toggle-equipment-drops", "allow-toggle-equipment-drops");
            put("spawner.allow-grief", "allow-grief");
            put("spawner.activate-on-place", "activate-on-place");

            // Spawner Breaking Mechanics
            put("spawner-break.enabled", "spawner-break-enabled");
            put("spawner-break.required-tools", "required-tools");
            put("spawner-break.durability-loss-per-spawner", "durability-loss-per-spawner");
            put("spawner-break.silk-touch.required", "silk-touch-required");
            put("spawner-break.silk-touch.level", "silk-touch-level");
            put("spawner-break.drop-stack.amount", "drop-stack-amount");

            // Hologram Settings
            put("hologram.enabled", "hologram-enabled");
            put("hologram.see-through", "hologram-see-through");
            put("hologram.shadowed-text", "hologram-shadowed-text");
            put("hologram.height", "hologram-height");
            put("hologram.offset.x", "hologram-offset-x");
            put("hologram.offset.z", "hologram-offset-z");

            // Particle Settings
            put("particles.loot-spawn", "particles-loot-spawn");
            put("particles.spawner-stack", "particles-spawner-stack");
            put("particles.spawner-activate", "particles-spawner-activate");

            // Economy Integration
            put("shop-integration", "shop-integration");
            put("formated-price", "formated-price");
            put("sell-cooldown", "sell-cooldown");
            put("tax.enabled", "tax-enabled");
            put("tax.rate", "tax-rate");

            // Logging
            put("logging.enabled", "logging-enabled");
            put("logging.file-path", "logging-file-path");

            // Hopper Mechanics
            put("hopper.enabled", "hopper-enabled");
            put("hopper.items-per-transfer", "hopper-items-per-transfer");
            put("hopper.check-interval", "hopper-check-interval");

            // Update Checker
            put("update-checker.enabled", "update-checker-enabled");
            put("update-checker.check-interval", "update-checker-interval");
            put("update-checker.notify-ops", "update-checker-notify-console");
            put("update-checker.notify-on-join", "update-checker-notify-on-join");
        }};

        return keyMapping.getOrDefault(oldKey, oldKey);
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

    // Updated to use flattened structure
    private final Map<String, Object> defaultConfig = new HashMap<String, Object>() {{
        put("config-version", CURRENT_CONFIG_VERSION);

        // Plugin Settings
        put("language", "en_US");
        put("debug", false);
        put("save-interval", 300);

        // Spawner Core Mechanics
        put("default-entity", "PIG");
        put("min-mobs", 1);
        put("max-mobs", 4);
        put("range", 16);
        put("delay", 20);
        put("max-storage-pages", 1);
        put("max-stored-exp", 1000);
        put("max-stack-size", 1000);
        put("allow-exp-mending", true);
        put("allow-toggle-equipment-drops", true);
        put("allow-grief", false);
        put("activate-on-place", true);

        // Extra & Custom Mechanics
        put("natural-spawner-interaction", false);

        // Spawner Breaking Mechanics
        put("spawner-break-enabled", true);
        put("required-tools", Arrays.asList(
                "IRON_PICKAXE",
                "GOLDEN_PICKAXE",
                "DIAMOND_PICKAXE",
                "NETHERITE_PICKAXE"
        ));
        put("durability-loss-per-spawner", 1);
        put("silk-touch-required", true);
        put("silk-touch-level", 1);
        put("drop-stack-amount", 64);

        // Hologram Settings
        put("hologram-enabled", false);
        put("hologram-see-through", false);
        put("hologram-shadowed-text", true);
        put("hologram-height", 1.6);
        put("hologram-offset-x", 0.5);
        put("hologram-offset-z", 0.5);

        // Particle Effect Settings
        put("particles-loot-spawn", true);
        put("particles-spawner-stack", true);
        put("particles-spawner-activate", true);

        // Economic Integration
        put("shop-integration", "auto");
        put("formated-price", true);
        put("sell-cooldown", 3);
        put("tax-enabled", false);
        put("tax-rate", 10.0);

        // Logging
        put("logging-enabled", true);
        put("logging-file-path", "shop-logs/sales.log");

        // Hopper Mechanics
        put("hopper-enabled", false);
        put("hopper-items-per-transfer", 4);
        put("hopper-check-interval", 1);

        // Update Checker
        put("update-checker-enabled", true);
        put("update-checker-interval", 24);
        put("update-checker-notify-console", true);
        put("update-checker-notify-on-join", true);
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
    //            Generic getter methods for different types
    // ===============================================================

    public String getString(String path) {
        // First check if the path exists directly (backward compatibility)
        if (config.contains(path)) {
            return (String) configCache.computeIfAbsent(path,
                    key -> config.getString(key, (String) defaultConfig.get(key)));
        }

        // Try with the mapped key for new flattened structure
        String mappedKey = mapOldKeyToNewKey(path);
        return (String) configCache.computeIfAbsent(mappedKey,
                key -> config.getString(key, (String) defaultConfig.get(key)));
    }

    public int getInt(String path) {
        // First check if the path exists directly (backward compatibility)
        if (config.contains(path)) {
            return (int) configCache.computeIfAbsent(path,
                    key -> config.getInt(key, defaultConfig.get(key) instanceof Number ?
                            ((Number) defaultConfig.get(key)).intValue() : 0));
        }

        // Try with the mapped key for new flattened structure
        String mappedKey = mapOldKeyToNewKey(path);
        return (int) configCache.computeIfAbsent(mappedKey,
                key -> config.getInt(key, defaultConfig.get(key) instanceof Number ?
                        ((Number) defaultConfig.get(key)).intValue() : 0));
    }

    public boolean getBoolean(String path) {
        // First check if the path exists directly (backward compatibility)
        if (config.contains(path)) {
            return (boolean) configCache.computeIfAbsent(path,
                    key -> config.getBoolean(key, defaultConfig.get(key) instanceof Boolean ?
                            (boolean) defaultConfig.get(key) : false));
        }

        // Try with the mapped key for new flattened structure
        String mappedKey = mapOldKeyToNewKey(path);
        return (boolean) configCache.computeIfAbsent(mappedKey,
                key -> config.getBoolean(key, defaultConfig.get(key) instanceof Boolean ?
                        (boolean) defaultConfig.get(key) : false));
    }

    public double getDouble(String path) {
        // First check if the path exists directly (backward compatibility)
        if (config.contains(path)) {
            return (double) configCache.computeIfAbsent(path,
                    key -> config.getDouble(key, defaultConfig.get(key) instanceof Number ?
                            ((Number) defaultConfig.get(key)).doubleValue() : 0.0));
        }

        // Try with the mapped key for new flattened structure
        String mappedKey = mapOldKeyToNewKey(path);
        return (double) configCache.computeIfAbsent(mappedKey,
                key -> config.getDouble(key, defaultConfig.get(key) instanceof Number ?
                        ((Number) defaultConfig.get(key)).doubleValue() : 0.0));
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        // First check if the path exists directly (backward compatibility)
        if (config.contains(path)) {
            return (List<String>) configCache.computeIfAbsent(path,
                    key -> config.getStringList(key));
        }

        // Try with the mapped key for new flattened structure
        String mappedKey = mapOldKeyToNewKey(path);
        List<String> defaultList = defaultConfig.get(mappedKey) instanceof List ?
                (List<String>) defaultConfig.get(mappedKey) : Collections.emptyList();

        return (List<String>) configCache.computeIfAbsent(mappedKey,
                key -> config.contains(key) ? config.getStringList(key) : defaultList);
    }

    public EntityType getDefaultEntityType() {
        Object cachedValue = configCache.get("default-entity");

        if (cachedValue instanceof EntityType) {
            return (EntityType) cachedValue;
        }

        String defaultValue = "PIG";
        String entityName;

        if (cachedValue instanceof String) {
            entityName = ((String) cachedValue).toUpperCase();
        } else {
            entityName = config.getString("default-entity", defaultValue).toUpperCase();
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

    public boolean isDebugEnabled() {
        return getBoolean("debug");
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
}
