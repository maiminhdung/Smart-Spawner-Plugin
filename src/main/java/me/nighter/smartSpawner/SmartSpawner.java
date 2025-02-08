package me.nighter.smartSpawner;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.hooks.shops.ShopIntegrationManager;
import me.nighter.smartSpawner.hooks.shops.api.shopguiplus.SpawnerHook;
import me.nighter.smartSpawner.hooks.shops.api.shopguiplus.SpawnerProvider;
import me.nighter.smartSpawner.managers.*;
import me.nighter.smartSpawner.listeners.*;
import me.nighter.smartSpawner.utils.UpdateChecker;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.nighter.smartSpawner.hooks.protections.api.Lands;
import me.nighter.smartSpawner.commands.SmartSpawnerCommand;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import com.palmergames.bukkit.towny.TownyAPI;
import com.sk89q.worldguard.WorldGuard;

import java.util.function.Consumer;
import java.util.stream.Stream;


public class SmartSpawner extends JavaPlugin {
    private static SmartSpawner instance;

    // Managers
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private SpawnerManager spawnerManager;
    private SpawnerLootManager lootManager;
    private HopperHandler hopperHandler;
    private ShopIntegrationManager shopIntegrationManager;

    // Handlers and Listeners
    private EventHandlers eventHandlers;
    private SpawnerRangeChecker rangeChecker;
    private SpawnerLootGenerator lootGenerator;
    private SpawnerListener spawnerListener;
    private SpawnerGuiListener spawnerGuiListener;
    private SpawnerStackHandler spawnerStackHandler;
    private SpawnerBreakHandler spawnerBreakHandler;
    private GUIClickHandler guiClickHandler;

    // Kyori/Adventure
    private static ComponentLogger prefixedlogger;

    // Integration flags
    public static boolean hasTowny = false;
    public static boolean hasLands = false;
    public static boolean hasWorldGuard = false;
    public static boolean hasGriefPrevention = false;

    @Override
    public void onEnable() {
        instance = this;
        String currentVersion = this.getDescription().getVersion();
        ComponentLogger prefixedLogger = ComponentLogger.logger(Bukkit.getLogger().getName());
        initializeVersionSpecificComponents();
        initializeComponents();
        setupCommand();
        checkDependencies();
        loadData();
        registerListeners();
        Stream.of(
                "╔══════════════════════════════════════════════════════════════╗",
                "║                                                              ║",
                " SmartSpawner " + currentVersion,
                " You are using Beta version of SmartSpawner,",
                " please report any bugs to the developer.",
                " ",
                " Discord: https://discord.gg/k7Sn2aynK6",
                " GitHub: https://github.com/maiminhdung/Smart-Spawner-Plugin",
                "║                                                              ║",
                "╚══════════════════════════════════════════════════════════════╝"
        ).forEach(prefixedLogger::warn);
    }

    private void initializeComponents() {
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.eventHandlers = new EventHandlers(this);
        this.lootGenerator = new SpawnerLootGenerator(this);
        this.lootManager = new SpawnerLootManager(this);
        this.spawnerManager = new SpawnerManager(this);
        this.rangeChecker = new SpawnerRangeChecker(this);
        this.spawnerListener = new SpawnerListener(this);
        this.spawnerGuiListener = new SpawnerGuiListener(this);
        this.spawnerBreakHandler = new SpawnerBreakHandler(this);
        this.spawnerStackHandler = new SpawnerStackHandler(this);
        this.guiClickHandler = new GUIClickHandler(this);
        this.shopIntegrationManager = new ShopIntegrationManager(this);

        if (configManager.isHopperEnabled()) {
            this.hopperHandler = new HopperHandler(this);
            this.hopperHandler.checkAllHoppers();
        } else {
            this.hopperHandler = null;
        }
    }

    private void setupCommand() {
        SmartSpawnerCommand smartSpawnerCommand = new SmartSpawnerCommand(this);
        getCommand("smartspawner").setExecutor(smartSpawnerCommand);
        getCommand("smartspawner").setTabCompleter(smartSpawnerCommand);
    }

    private void checkDependencies() {
        checkProtectionPlugins();
        shopIntegrationManager.initialize();
        checkFloodgate();
    }

    private void checkFloodgate() {
        checkPlugin("Floodgate", () -> FloodgateApi.getInstance() != null);
    }

    private void checkProtectionPlugins() {
        hasWorldGuard = checkPlugin("WorldGuard", () -> {
            WorldGuard.getInstance();
            return WorldGuard.getInstance() != null;
        });

        hasGriefPrevention = checkPlugin("GriefPrevention", () -> {
            GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getPluginManager().getPlugin("GriefPrevention");
            return griefPrevention != null;
        });

        hasLands = checkPlugin("Lands", () -> {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin != null) {
                new Lands(this);
                return true;
            }
            return false;
        });
        hasTowny = checkPlugin("Towny", () -> {
            TownyAPI.getInstance();
            return TownyAPI.getInstance() != null;
        });
    }

    private boolean checkPlugin(String pluginName, PluginCheck checker) {
        try {
            if (checker.check()) {
                getLogger().info(pluginName + " integration enabled successfully!");
                return true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            //getLogger().info(pluginName + " not detected, continuing without it");
        }
        return false;
    }

    private void loadData() {
        spawnerManager.loadSpawnerData();
        new UpdateChecker(this, "9tQwxSFr").initialize();
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new EventHandlers(this), this);
        pm.registerEvents(new SpawnerListener(this), this);
        pm.registerEvents(new SpawnerGuiListener(this), this);
        pm.registerEvents(new SpawnerBreakHandler(this), this);
        pm.registerEvents(new GUIClickHandler(this), this);
        pm.registerEvents(new SpawnerExplosionListener(this), this);
        if (isShopGUIPlusEnabled()) {
            pm.registerEvents(new SpawnerHook(this), this);
        }
    }

    @Override
    public void onDisable() {
        saveAndCleanup();
        SpawnerHeadManager.clearCache();
        getLogger().info("SmartSpawner has been disabled!");
    }

    private void saveAndCleanup() {
        if (spawnerManager != null) spawnerManager.saveSpawnerData();
        if (rangeChecker != null) rangeChecker.cleanup();
        if (configManager != null) configManager.saveConfigs();
        if (spawnerGuiListener != null) spawnerGuiListener.onDisable();
        if (hopperHandler != null) hopperHandler.cleanup();
        if (eventHandlers != null) eventHandlers.cleanup();
    }

    // Getters
    public static SmartSpawner getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public SpawnerLootGenerator getLootGenerator() { return lootGenerator; }
    public SpawnerLootManager getLootManager() { return lootManager; }
    public SpawnerManager getSpawnerManager() { return spawnerManager; }
    public SpawnerListener getSpawnerListener() { return spawnerListener; }
    public SpawnerRangeChecker getRangeChecker() { return rangeChecker; }
    public SpawnerStackHandler getSpawnerStackHandler() { return spawnerStackHandler; }
    public HopperHandler getHopperHandler() { return hopperHandler; }

    public ScheduledTask task(Consumer<ScheduledTask> task) {
        return getServer().getAsyncScheduler().runNow(this, task);
    }

    @FunctionalInterface
    private interface PluginCheck {
        boolean check();
    }

    // Getters for ShopIntegration
    public IShopIntegration getShopIntegration() {
        return shopIntegrationManager.getShopIntegration();
    }

    public boolean hasShopIntegration() {
        return shopIntegrationManager.hasShopIntegration();
    }

    public boolean isShopGUIPlusEnabled() {
        return shopIntegrationManager.isShopGUIPlusEnabled();
    }

    public SpawnerProvider getSpawnerProvider() {
        return new SpawnerProvider(this);
    }

    // Version specific implementations
    private void initializeVersionSpecificComponents() {
        String version = Bukkit.getServer().getBukkitVersion();
        String[][] versionSpecificComponents = {
                {"Particles", "me.nighter.smartSpawner.v1_20.ParticleInitializer", "me.nighter.smartSpawner.v1_21.ParticleInitializer"},
                {"Textures", "me.nighter.smartSpawner.v1_20.TextureInitializer", "me.nighter.smartSpawner.v1_21.TextureInitializer"},
                {"Spawners", "me.nighter.smartSpawner.v1_20.SpawnerInitializer", "me.nighter.smartSpawner.v1_21.SpawnerInitializer"}
        };
        for (String[] component : versionSpecificComponents) {
            try {
                String componentName = component[0];
                String v1_20Class = component[1];
                String v1_21Class = component[2];
                if (version.contains("1.20")) {
                    Class.forName(v1_20Class)
                            .getMethod("init")
                            .invoke(null);
                } else if (version.contains("1.21")) {
                    Class.forName(v1_21Class)
                            .getMethod("init")
                            .invoke(null);
                }
            } catch (Exception e) {
                getLogger().severe("Failed to initialize " + component[0] + " for version " + version);
                e.printStackTrace();
            }
        }
    }

    // Getters for kyori/adventure
    public static ComponentLogger prefixedlogger() { return prefixedlogger; }
}