package github.nighter.smartspawner;

import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerPlugin;
import github.nighter.smartspawner.api.SmartSpawnerAPIImpl;
import github.nighter.smartspawner.bstats.Metrics;
import github.nighter.smartspawner.commands.CommandHandler;
import github.nighter.smartspawner.commands.list.SpawnerListGUI;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.hooks.protections.api.Lands;
import github.nighter.smartspawner.hooks.shops.IShopIntegration;
import github.nighter.smartspawner.hooks.shops.SaleLogger;
import github.nighter.smartspawner.hooks.shops.ShopIntegrationManager;
import github.nighter.smartspawner.hooks.shops.api.shopguiplus.SpawnerHook;
import github.nighter.smartspawner.hooks.shops.api.shopguiplus.SpawnerProvider;
import github.nighter.smartspawner.migration.SpawnerDataMigration;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuFormUI;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuAction;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerHandler;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageAction;
import github.nighter.smartspawner.spawner.interactions.SpawnerClickManager;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerBreakListener;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerExplosionListener;
import github.nighter.smartspawner.spawner.interactions.place.SpawnerPlaceListener;
import github.nighter.smartspawner.spawner.interactions.stack.SpawnerStackHandler;
import github.nighter.smartspawner.spawner.interactions.type.SpawnEggHandler;
import github.nighter.smartspawner.spawner.lootgen.SpawnerRangeChecker;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.utils.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.lootgen.SpawnerLootGenerator;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import github.nighter.smartspawner.utils.UpdateChecker;
import github.nighter.smartspawner.nms.VersionInitializer;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Main class for the SmartSpawner plugin.
 * Handles initialization, dependencies, and manages the lifecycle of the plugin.
 *
 */
public class SmartSpawner extends JavaPlugin  implements SmartSpawnerPlugin {
    // Singleton instance
    private static SmartSpawner instance;

    // Core UI components
    private SpawnerMenuUI spawnerMenuUI;
    private SpawnerStorageUI spawnerStorageUI;
    private SpawnerStackerUI spawnerStackerUI;

    // Core handlers
    private SpawnEggHandler spawnEggHandler;
    private SpawnerClickManager spawnerClickManager;
    private SpawnerStackHandler spawnerStackHandler;

    // UI actions
    private SpawnerMenuAction spawnerMenuAction;
    private SpawnerStackerHandler spawnerStackerHandler;
    private SpawnerStorageAction spawnerStorageAction;

    // Core managers
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private SpawnerManager spawnerManager;
    private ShopIntegrationManager shopIntegrationManager;
    private HopperHandler hopperHandler;

    // Event handlers and utilities
    private GlobalEventHandlers globalEventHandlers;
    private SpawnerLootGenerator spawnerLootGenerator;
    private SpawnerListGUI spawnerListGUI;
    private SpawnerRangeChecker rangeChecker;
    private SpawnerGuiViewManager spawnerGuiViewManager;
    private SpawnerExplosionListener spawnerExplosionListener;
    private SpawnerBreakListener spawnerBreakListener;
    private SpawnerPlaceListener spawnerPlaceListener;
    private UpdateChecker updateChecker;

    // Integration flags - static for quick access
    public static boolean hasTowny = false;
    public static boolean hasLands = false;
    public static boolean hasWorldGuard = false;
    public static boolean hasGriefPrevention = false;
    public static boolean hasSuperiorSkyblock2 = false;

    // API implementation
    private SmartSpawnerAPIImpl apiImpl;

    /**
     * Called when the plugin is enabled.
     * Initializes all components, checks dependencies, and registers listeners.
     */
    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;

        // Initialize version-specific components
        initializeVersionComponents();

        // Check for data migration needs
        migrateDataIfNeeded();

        // Initialize core components async where possible
        initializeComponents()
                .thenRun(() -> {
                    setupCommand();
                    checkDependencies();
                    setupBtatsMetrics();
                    registerListeners();
                    initializeSaleLogging();

                    long loadTime = System.currentTimeMillis() - startTime;
                    getLogger().info("SmartSpawner has been enabled! (Loaded in " + loadTime + "ms)");
                });
    }

    /**
     * Get the API implementation for other plugins to use
     */
    @Override
    public SmartSpawnerAPI getAPI() {
        return apiImpl;
    }

    /**
     * Initializes version-specific components using the dedicated initializer class.
     */
    private void initializeVersionComponents() {
        try {
            new VersionInitializer(this).initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize version-specific components", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Checks for and performs data migration if needed.
     */
    private void migrateDataIfNeeded() {
        SpawnerDataMigration migration = new SpawnerDataMigration(this);
        if (migration.checkAndMigrateData()) {
            getLogger().info("Data migration completed. Loading with new format...");
        }
    }

    /**
     * Initializes all plugin components with appropriate async operations.
     *
     * @return CompletableFuture that completes when initialization is done
     */
    private CompletableFuture<Void> initializeComponents() {
        // Initialize core components in order
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.spawnerStorageUI = new SpawnerStorageUI(this);
        this.spawnerManager = new SpawnerManager(this);
        this.spawnerListGUI = new SpawnerListGUI(this);
        this.spawnerGuiViewManager = new SpawnerGuiViewManager(this);
        this.spawnerLootGenerator = new SpawnerLootGenerator(this);
        this.rangeChecker = new SpawnerRangeChecker(this);
        ;
        // Parallel initialization for components that can be initialized concurrently
        CompletableFuture<Void> asyncInit = CompletableFuture.runAsync(() -> {
            this.shopIntegrationManager = new ShopIntegrationManager(this);
            this.updateChecker = new UpdateChecker(this, "9tQwxSFr");
        });

        // Main thread initialization for components that need the main thread
        this.spawnerMenuUI = new SpawnerMenuUI(this);
        this.spawnerStackerUI = new SpawnerStackerUI(this);

        this.spawnEggHandler = new SpawnEggHandler(this);
        this.spawnerStackHandler = new SpawnerStackHandler(this);
        this.spawnerClickManager = new SpawnerClickManager(this);

        this.spawnerMenuAction = new SpawnerMenuAction(this);
        this.spawnerStackerHandler = new SpawnerStackerHandler(this);
        this.spawnerStorageAction = new SpawnerStorageAction(this);

        this.globalEventHandlers = new GlobalEventHandlers(this);
        this.spawnerExplosionListener = new SpawnerExplosionListener(this);
        this.spawnerBreakListener = new SpawnerBreakListener(this);
        this.spawnerPlaceListener = new SpawnerPlaceListener(this);

        // Initialize hopper handler if enabled in config
        if (configManager.getBoolean("hopper-enabled")) {
            this.hopperHandler = new HopperHandler(this);
        }

        // Complete initialization
        return asyncInit.thenRunAsync(() -> {
            Bukkit.getAsyncScheduler().runNow(this, task -> {
                updateChecker.initialize();
            });
        });
    }

    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        // Register core listeners
        pm.registerEvents(globalEventHandlers, this);
        pm.registerEvents(spawnerListGUI, this);
        pm.registerEvents(spawnerBreakListener, this);
        pm.registerEvents(spawnerPlaceListener, this);
        pm.registerEvents(spawnerStorageAction, this);
        pm.registerEvents(spawnerExplosionListener, this);
        pm.registerEvents(spawnerGuiViewManager, this);
        pm.registerEvents(spawnerClickManager, this);
        pm.registerEvents(spawnerMenuAction, this);
        pm.registerEvents(spawnerStackerHandler, this);

        // Register shop integration listeners if available
        if (isShopGUIPlusEnabled()) {
            pm.registerEvents(new SpawnerHook(this), this);
        }
    }

    /**
     * Sets up and registers plugin commands.
     */
    private void setupCommand() {
        CommandHandler commandHandler = new CommandHandler(this);
        getCommand("smartspawner").setExecutor(commandHandler);
        getCommand("smartspawner").setTabCompleter(commandHandler);
    }

    /**
     * Sets up bStats metrics for plugin usage tracking.
     */
    private void setupBtatsMetrics() {
        Metrics metrics = new Metrics(this, 24822);
        metrics.addCustomChart(new Metrics.SimplePie("players",
                () -> String.valueOf(Bukkit.getOnlinePlayers().size())));
    }

    /**
     * Initializes the sale logging system if enabled in config.
     */
    private void initializeSaleLogging() {
        if (configManager.getBoolean("logging-enabled")) {
            SaleLogger.getInstance();
        }
    }

    /**
     * Checks and initializes all plugin dependencies and integrations.
     */
    private void checkDependencies() {
        // Run protection plugin checks in parallel
        CompletableFuture.runAsync(this::checkProtectionPlugins);

        // Initialize shop integrations
        shopIntegrationManager.initialize();
    }

    /**
     * Checks for protection plugin integrations.
     */
    private void checkProtectionPlugins() {
        hasWorldGuard = checkPlugin("WorldGuard", () -> {
            try {
                Class.forName("com.sk89q.worldguard.WorldGuard");
                return com.sk89q.worldguard.WorldGuard.getInstance() != null;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }, true);

        hasGriefPrevention = checkPlugin("GriefPrevention", () -> {
            Plugin griefPlugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            return griefPlugin != null && griefPlugin instanceof GriefPrevention;
        }, true);

        hasLands = checkPlugin("Lands", () -> {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin != null) {
                new Lands(this);
                return true;
            }
            return false;
        }, true);

        hasTowny = checkPlugin("Towny", () -> {
            try {
                Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                return com.palmergames.bukkit.towny.TownyAPI.getInstance() != null;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }, true);

        hasSuperiorSkyblock2 = checkPlugin("SuperiorSkyblock2", () ->
                Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2") != null, true);
    }

    /**
     * Checks if a plugin is available and functional.
     *
     * @param pluginName The name of the plugin to check
     * @param checker A function that checks if the plugin is properly loaded
     * @param logSuccess Whether to log successful integration
     * @return true if the plugin is available and functional
     */
    private boolean checkPlugin(String pluginName, PluginCheck checker, boolean logSuccess) {
        try {
            if (checker.check()) {
                if (logSuccess) {
                    getLogger().info(pluginName + " integration enabled successfully!");
                }
                return true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            // Silent fail - plugin not available
        }
        return false;
    }

    /**
     * Called when the plugin is disabled.
     * Saves data and cleans up resources.
     */
    @Override
    public void onDisable() {
        saveAndCleanup();
        SpawnerMobHeadTexture.clearCache();
        shutdownSaleLogger();
        getLogger().info("SmartSpawner has been disabled!");
    }

    /**
     * Shuts down the sale logger if it was enabled.
     */
    private void shutdownSaleLogger() {
        if (configManager != null && configManager.getBoolean("logging-enabled")) {
            SaleLogger.getInstance().shutdown();
        }
    }

    /**
     * Saves all data and cleans up resources.
     */
    private void saveAndCleanup() {
        // Save spawner data
        if (spawnerManager != null) {
            spawnerManager.saveSpawnerData();
            spawnerManager.cleanupAllSpawners();
        }

        // Clean up other resources
        if (rangeChecker != null) rangeChecker.cleanup();
        if (spawnerGuiViewManager != null) spawnerGuiViewManager.cleanup();
        if (hopperHandler != null) hopperHandler.cleanup();
        if (spawnerClickManager != null) spawnerClickManager.cleanup();
        if (spawnerStackerHandler != null) spawnerStackerHandler.cleanupAll();
        if (spawnerStorageUI != null) spawnerStorageUI.cleanup();
        if (updateChecker != null) updateChecker.shutdown();
    }

    /**
     * Runs a task asynchronously.
     *
     * @param task The task to run
     * @return The ScheduledTask representing the scheduled task
     */
    public ScheduledTask runTaskAsync(Consumer<ScheduledTask> task) {
        return getServer().getAsyncScheduler().runNow(this, task);
    }

    /**
     * Functional interface for plugin availability checks.
     */
    @FunctionalInterface
    private interface PluginCheck {
        boolean check();
    }

    // Getters

    public static SmartSpawner getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public SpawnerMenuUI getSpawnerMenuUI() {
        return spawnerMenuUI;
    }

    public SpawnerMenuAction getSpawnerMenuAction() {
        return spawnerMenuAction;
    }

    public SpawnerGuiViewManager getSpawnerGuiManager() {
        return spawnerGuiViewManager;
    }

    public SpawnEggHandler getSpawnEggHandler() {
        return spawnEggHandler;
    }

    public SpawnerStackerUI getSpawnerStackerUI() {
        return spawnerStackerUI;
    }

    public SpawnerStackerHandler getSpawnerStackerHandler() {
        return spawnerStackerHandler;
    }

    public SpawnerStorageUI getSpawnerStorageUI() {
        return spawnerStorageUI;
    }

    public SpawnerLootGenerator getSpawnerLootGenerator() {
        return spawnerLootGenerator;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public SpawnerRangeChecker getRangeChecker() {
        return rangeChecker;
    }

    public SpawnerStackHandler getSpawnerStackHandler() {
        return spawnerStackHandler;
    }

    public HopperHandler getHopperHandler() {
        return hopperHandler;
    }

    public IShopIntegration getShopIntegration() {
        return shopIntegrationManager.getShopIntegration();
    }

    /**
     * Checks if any shop integration is available.
     *
     * @return true if a shop integration is available
     */
    public boolean hasShopIntegration() {
        return shopIntegrationManager.hasShopIntegration();
    }

    /**
     * Checks if ShopGUIPlus integration is enabled.
     *
     * @return true if ShopGUIPlus integration is enabled
     */
    public boolean isShopGUIPlusEnabled() {
        return shopIntegrationManager.isShopGUIPlusEnabled();
    }

    /**
     * Gets the spawner provider for shop integrations.
     *
     * @return A new SpawnerProvider instance
     */
    public SpawnerProvider getSpawnerProvider() {
        return new SpawnerProvider(this);
    }
}