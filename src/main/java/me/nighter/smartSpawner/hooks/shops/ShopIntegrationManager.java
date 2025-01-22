package me.nighter.smartSpawner.hooks.shops;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.api.economyshopgui.EconomyShopGUI;
import me.nighter.smartSpawner.hooks.shops.api.shopguiplus.ShopGuiPlus;
import me.nighter.smartSpawner.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class ShopIntegrationManager {
    private final SmartSpawner plugin;
    private IShopIntegration shopIntegration;
    private boolean hasShopIntegration = false;
    private boolean isShopGUIPlusEnabled = false;

    public ShopIntegrationManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        setupShopIntegration();
    }

    private void setupShopIntegration() {
        // Ensure only one shop integration is active
        if (hasShopIntegration) {
            plugin.getLogger().warning("Another shop integration is already enabled. Skipping additional shop setup.");
            return;
        }

        ConfigManager.ShopType shopType = plugin.getConfigManager().getShopType();
        switch (shopType) {
            case ECONOMY_SHOP_GUI_PREMIUM:
                plugin.getLogger().info("Checking for EconomyShopGUI-Premium...");
                setupEconomyShopGUI("EconomyShopGUI-Premium");
                break;
            case ECONOMY_SHOP_GUI:
                plugin.getLogger().info("Checking for EconomyShopGUI...");
                setupEconomyShopGUI("EconomyShopGUI");
                break;
            case SHOP_GUI_PLUS:
                plugin.getLogger().info("Checking for ShopGUIPlus...");
                setupShopGuiPlus();
                break;
            case DISABLED:
                plugin.getLogger().info("Shop integration is disabled by configuration");
                break;
        }
    }

    private void setupShopGuiPlus() {
        Plugin shopGuiPlus = Bukkit.getPluginManager().getPlugin("ShopGUIPlus");
        if (shopGuiPlus != null && shopGuiPlus.isEnabled()) {
            try {
                shopIntegration = new ShopGuiPlus(plugin);
                hasShopIntegration = true;
                isShopGUIPlusEnabled = true;
                plugin.getLogger().info("ShopGUIPlus integration enabled successfully!");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to setup ShopGUIPlus integration: " + e.getMessage());
                hasShopIntegration = false;
                isShopGUIPlusEnabled = false;
            }
        } else {
            plugin.getLogger().info("ShopGUIPlus not found - integration disabled");
        }
    }

    private void setupEconomyShopGUI(String pluginName) {
        Plugin shop = Bukkit.getPluginManager().getPlugin(pluginName);
        if (shop != null && shop.isEnabled()) {
            try {
                shopIntegration = new EconomyShopGUI(plugin);
                hasShopIntegration = true;
                plugin.getLogger().info(pluginName + " integration enabled successfully!");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to setup " + pluginName + " integration: " + e.getMessage());
                hasShopIntegration = false;
            }
        } else {
            plugin.getLogger().info(pluginName + " not found - integration disabled");
        }
    }

    // Getters
    public IShopIntegration getShopIntegration() {
        if (shopIntegration != null && shopIntegration.isEnabled()) {
            return shopIntegration;
        }
        return null;
    }

    public boolean hasShopIntegration() {
        return hasShopIntegration;
    }

    public boolean isShopGUIPlusEnabled() {
        return isShopGUIPlusEnabled;
    }
}