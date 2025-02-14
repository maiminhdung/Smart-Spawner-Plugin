package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.SmartSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ConfigManager {
    private final SmartSpawner plugin;
    private final Logger logger;
    private FileConfiguration config;
    private FileConfiguration lootConfig;
    private File configFile;
    private File lootConfigFile;
    private Map<String, Object> configCache;

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
    public void saveConfigs() {
        saveMainConfig();
        saveLootConfig();
    }
    public void reloadConfigs() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        lootConfig = YamlConfiguration.loadConfiguration(lootConfigFile);
        mergeLootDefaults();
        initializeCache();
    }

    private void setDefaultIfNotExists(String path, Object defaultValue) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            plugin.saveConfig();
        }
    }

    public void debug(String message) {
        if (isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    // ===============================================================
    //                      Main Configs
    // ===============================================================

    public FileConfiguration getMainConfig() {
        return config;
    }

    public void loadMainConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        boolean hasChanges = mergeDefaultConfig();

        if (hasChanges) {
            try {
                config.save(configFile);
                logger.info("Updated config.yml with new default values");
            } catch (IOException e) {
                logger.severe("Could not save updated config.yml: " + e.getMessage());
            }
        }
    }

    private boolean mergeDefaultConfig() {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : defaultConfig.entrySet()) {
            String path = entry.getKey();
            Object defaultValue = entry.getValue();

            if (!config.contains(path)) {
                config.set(path, defaultValue);
                changed = true;
                logger.info("Added missing config value: " + path);
            }
        }
        return changed;
    }

    private void saveMainConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save main config: " + e.getMessage());
        }
    }

    private final Map<String, Object> defaultConfig = new HashMap<String, Object>() {{
        // Global Settings
        put("settings.language", "en");
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
        put("shop-integration", "EconomyShopGUI");
        put("formated-price", true);
        put("tax.enabled", false);
        put("tax.rate", 10.0);

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

        // Cache global settings
        configCache.put("settings.language", config.getString("settings.language"));
        configCache.put("settings.debug", config.getBoolean("settings.debug"));
        configCache.put("settings.save-interval", config.getInt("settings.save-interval"));

        // Cache spawner settings
        configCache.put("spawner.default-entity", config.getString("spawner.default-entity"));
        configCache.put("spawner.min-mobs", config.getInt("spawner.min-mobs"));
        configCache.put("spawner.max-mobs", config.getInt("spawner.max-mobs"));
        configCache.put("spawner.range", config.getInt("spawner.range"));
        configCache.put("spawner.delay", config.getInt("spawner.delay"));
        configCache.put("spawner.max-storage-pages", config.getInt("spawner.max-storage-pages"));
        configCache.put("spawner.max-stack-size", config.getInt("spawner.max-stack-size"));
        configCache.put("spawner.max-stored-exp", config.getInt("spawner.max-stored-exp"));
        configCache.put("spawner.allow-exp-mending", config.getBoolean("spawner.allow-exp-mending"));
        configCache.put("spawner.allow-toggle-equipment-drops", config.getBoolean("spawner.allow-toggle-equipment-drops"));
        configCache.put("spawner.allow-grief", config.getBoolean("spawner.allow-grief"));
        configCache.put("spawner.activate-on-place", config.getBoolean("spawner.activate-on-place"));

        // Cache break settings
        configCache.put("spawner-break.enabled", config.getBoolean("spawner-break.enabled"));
        configCache.put("spawner-break.durability-loss", config.getInt("spawner-break.durability-loss-per-spawner"));
        configCache.put("spawner-break.silk-touch.required", config.getBoolean("spawner-break.silk-touch.required"));
        configCache.put("spawner-break.silk-touch.level", config.getInt("spawner-break.silk-touch.level"));
        configCache.put("spawner-break.drop-stack.amount", config.getInt("spawner-break.drop-stack.amount"));

        // Cache hologram settings
        configCache.put("hologram.enabled", config.getBoolean("hologram.enabled"));
        configCache.put("hologram.see-through", config.getBoolean("hologram.see-through"));
        configCache.put("hologram.shadowed-text", config.getBoolean("hologram.shadowed-text"));
        configCache.put("hologram.height", config.getDouble("hologram.height"));
        configCache.put("hologram.offset.x", config.getDouble("hologram.offset.x"));
        configCache.put("hologram.offset.z", config.getDouble("hologram.offset.z"));

        // Cache particle settings
        configCache.put("particles.loot-spawn", config.getBoolean("particles.loot-spawn"));
        configCache.put("particles.spawner-stack", config.getBoolean("particles.spawner-stack"));
        configCache.put("particles.spawner-activate", config.getBoolean("particles.spawner-activate"));

        // Cache shop config
        configCache.put("shop-integration", config.getString("shop-integration"));
        configCache.put("formated-price", config.getString("formated-price"));
        configCache.put("tax.enabled", config.getBoolean("tax.enabled"));
        configCache.put("tax.rate", config.getDouble("tax.rate"));

        // Cache hopper settings
        configCache.put("hopper.enabled", config.getBoolean("hopper.enabled"));
        configCache.put("hopper.transfer-cooldown", config.getInt("hopper.transfer-cooldown"));
        configCache.put("hopper.check-interval", config.getInt("hopper.check-interval"));

        // Cache performance settings
        configCache.put("performance.batch-size", config.getInt("performance.batch-size"));

        // Cache update checker settings
        configCache.put("update-checker.enabled", config.getBoolean("update-checker.enabled"));
        configCache.put("update-checker.check-interval", config.getInt("update-checker.check-interval"));
        configCache.put("update-checker.notify-ops", config.getBoolean("update-checker.notify-ops"));
        configCache.put("update-checker.notify-on-join", config.getBoolean("update-checker.notify-on-join"));
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
    //                      Global Settings
    // ===============================================================

    public boolean isDebugMode() {
        return (boolean) configCache.computeIfAbsent("settings.debug", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getSaveInterval() {
        return (int) configCache.computeIfAbsent("settings.save-interval", key -> {
            int defaultValue = 6000;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
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
            setDefaultIfNotExists("spawner.default-entity", defaultValue);
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
        return (int) configCache.computeIfAbsent("spawner.min-mobs", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxMobs() {
        return (int) configCache.computeIfAbsent("spawner.max-mobs", key -> {
            int defaultValue = 4;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getSpawnerRange() {
        return (int) configCache.computeIfAbsent("spawner.range", key -> {
            int defaultValue = 16;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getSpawnerDelay() {
        return (int) configCache.computeIfAbsent("spawner.delay", key -> {
            int defaultValue = 16;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxStoragePages() {
        return (int) configCache.computeIfAbsent("spawner.max-storage-pages", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxStackSize() {
        return (int) configCache.computeIfAbsent("spawner.max-stack-size", key -> {
            int defaultValue = 100;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxStoredExp() {
        return (int) configCache.computeIfAbsent("spawner.max-stored-exp", key -> {
            int defaultValue = 1000;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean isAllowExpMending() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-exp-mending", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean isAllowToggleEquipmentItems() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-toggle-equipment-drops", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean isAllowGrief() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-grief", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean getActivateOnPlace() {
        return (boolean) configCache.computeIfAbsent("spawner.activate-on-place", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    // ===============================================================
    //                   Spawner Breaking Mechanics
    // ===============================================================

    public boolean isSpawnerBreakEnabled() {
        return (boolean) configCache.computeIfAbsent("spawner-break.enabled", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    @SuppressWarnings("unchecked")
    public List<String> getRequiredTools() {
        return (List<String>) configCache.computeIfAbsent("spawner-break.required-tools", key -> {
            List<String> defaultValue = Arrays.asList(
                    "IRON_PICKAXE",
                    "GOLDEN_PICKAXE",
                    "DIAMOND_PICKAXE",
                    "NETHERITE_PICKAXE"
            );
            setDefaultIfNotExists(key, defaultValue);
            return config.getStringList(key);
        });
    }

    public int getDurabilityLossPerSpawner() {
        return (int) configCache.computeIfAbsent("spawner-break.durability-loss-per-spawner", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean isSilkTouchRequired() {
        return (boolean) configCache.computeIfAbsent("spawner-break.silk-touch.required", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getSilkTouchLevel() {
        return (int) configCache.computeIfAbsent("spawner-break.silk-touch.level", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getDropStackAmount() {
        return (int) configCache.computeIfAbsent("spawner-break.drop-stack.amount", key -> {
            int defaultValue = 64;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    // ===============================================================
    //                   Spawner Hologram Settings
    // ===============================================================

    public boolean isHologramEnabled() {
        return (boolean) configCache.computeIfAbsent("hologram.enabled", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean isHologramSeeThrough() {
        return (boolean) configCache.computeIfAbsent("hologram.see-through", key -> {
            setDefaultIfNotExists(key, defaultConfig.get(key));
            return config.getBoolean(key, (boolean) defaultConfig.get(key));
        });
    }

    public boolean isHologramShadowed() {
        return (boolean) configCache.computeIfAbsent("hologram.shadowed-text", key -> {
            setDefaultIfNotExists(key, defaultConfig.get(key));
            return config.getBoolean(key, (boolean) defaultConfig.get(key));
        });
    }

    public double getHologramHeight() {
        return (double) configCache.computeIfAbsent("hologram.height", key -> {
            double defaultValue = 1.6;
            setDefaultIfNotExists(key, defaultValue);
            return config.getDouble(key, defaultValue);
        });
    }

    public double getHologramOffsetX() {
        return (double) configCache.computeIfAbsent("hologram.offset.x", key -> {
            double defaultValue = 0.5;
            setDefaultIfNotExists(key, defaultValue);
            return config.getDouble(key, defaultValue);
        });
    }

    public double getHologramOffsetZ() {
        return (double) configCache.computeIfAbsent("hologram.offset.z", key -> {
            double defaultValue = 0.5;
            setDefaultIfNotExists(key, defaultValue);
            return config.getDouble(key, defaultValue);
        });
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
        ECONOMY_SHOP_GUI,
        ECONOMY_SHOP_GUI_PREMIUM,
        SHOP_GUI_PLUS;

        public static ShopType fromString(String value) {
            if (value == null) return DISABLED;

            return switch (value.toLowerCase()) {
                case "economyshopgui" -> ECONOMY_SHOP_GUI;
                case "economyshopgui-premium" -> ECONOMY_SHOP_GUI_PREMIUM;
                case "shopguiplus" -> SHOP_GUI_PLUS;
                case "disabled" -> DISABLED;
                default -> DISABLED;
            };
        }

        @Override
        public String toString() {
            return switch (this) {
                case ECONOMY_SHOP_GUI -> "economyshopgui";
                case ECONOMY_SHOP_GUI_PREMIUM -> "economyshopgui-premium";
                case SHOP_GUI_PLUS -> "shopguiplus";
                case DISABLED -> "disabled";
            };
        }
    }
    public ShopType getShopType() {
        String shopType = (String) configCache.computeIfAbsent("shop-integration",
                key -> config.getString(key, "economyshopgui"));
        return ShopType.fromString(shopType);
    }

    public boolean isFormatedPrice() {
        Object value = configCache.computeIfAbsent("formated-price", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);

            return config.getBoolean(key, defaultValue);
        });

        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        } else {
            throw new IllegalStateException("Invalid value type for 'formated-price': " + value);
        }
    }

    public boolean isTaxEnabled() {
        return (boolean) configCache.computeIfAbsent("tax.enabled", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public double getTaxPercentage() {
        if (!isTaxEnabled()) return 0.0;
        return (double) configCache.computeIfAbsent("tax.rate", key -> {
            double defaultValue = 10.0;
            setDefaultIfNotExists(key, defaultValue);
            return config.getDouble(key, defaultValue);
        });
    }

    // ===============================================================
    //                Hopper Mechanics (Experimental)
    // ===============================================================

    public boolean isHopperEnabled() {
        return (boolean) configCache.computeIfAbsent("hopper.enabled", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getHopperItemsPerTransfer() {
        return (int) configCache.computeIfAbsent("hopper.items-per-transfer", key -> {
            int defaultValue = 64;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getHopperCheckInterval() {
        return (int) configCache.computeIfAbsent("hopper.check-interval", key -> {
            int defaultValue = 20;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    // ===============================================================
    //                    Plugin Update Checker
    // ===============================================================

    public boolean isUpdateCheckerEnabled() {
        return (boolean) configCache.computeIfAbsent("update-checker.enabled", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getUpdateCheckInterval() {
        return (int) configCache.computeIfAbsent("update-checker.check-interval", key -> {
            int defaultValue = 24;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean shouldNotifyOps() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-ops", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean shouldNotifyOnJoin() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-on-join", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }
}
