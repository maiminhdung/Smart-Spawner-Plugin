package me.nighter.smartSpawner.hooks.shops.api.zshop;

import fr.maxlego08.zshop.api.ShopManager;
import fr.maxlego08.zshop.api.buttons.ItemButton;
import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.hooks.shops.SaleLogger;
import me.nighter.smartSpawner.spawner.properties.VirtualInventory;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ZShop implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private ShopManager shopManager;
    private Economy vaultEconomy;

    public ZShop(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
        setupVaultEconomy();
    }

    private boolean setupVaultEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        vaultEconomy = rsp.getProvider();
        return vaultEconomy != null;
    }

    private Optional<ItemButton> getItemButton(Player player, ItemStack itemStack) {
        try {
            ShopManager shopManager = this.getShopManager();
            return shopManager.getItemButton(player, itemStack);
        } catch (Exception exception) {
            exception.printStackTrace();
            return Optional.empty();
        }
    }

    public ShopManager getShopManager() {
        if (this.shopManager != null) return this.shopManager;
        return this.shopManager = this.plugin.getServer().getServicesManager().getRegistration(ShopManager.class).getProvider();
    }

    private double calculateNetAmount(double grossAmount, double taxPercentage) {
        if (taxPercentage <= 0) {
            return grossAmount;
        }
        return grossAmount * (1 - taxPercentage / 100.0);
    }

    @Override
    public boolean sellAllItems(Player player, SpawnerData spawner) {
        if (!isEnabled() || vaultEconomy == null) {
            plugin.getLogger().warning("Support for zShop requires Vault as a currency provider");
            return false;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        double totalGrossPrice = 0.0;
        int totalAmount = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();
        boolean foundSellableItem = false;

        // Process each distinct item type
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            if (amount <= 0) continue;

            Optional<ItemButton> sellButtonOpt = getItemButton(player, template);
            if (sellButtonOpt.isEmpty()) continue;

            ItemButton sellButton = sellButtonOpt.get();
            double sellPrice = sellButton.getSellPrice(player, (int) amount);
            if (sellPrice <= 0) continue;

            foundSellableItem = true;

            // Create item for removal
            ItemStack itemToRemove = template.clone();
            int removeAmount = (int) Math.min(amount, Integer.MAX_VALUE);
            itemToRemove.setAmount(removeAmount);
            itemsToRemove.add(itemToRemove);

            totalGrossPrice += sellPrice;
            totalAmount += removeAmount;
        }

        if (!foundSellableItem) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        // Remove all items first
        boolean itemsRemoved = virtualInv.removeItems(itemsToRemove);
        if (!itemsRemoved) {
            plugin.getLogger().warning("Failed to remove items from virtual inventory for player " + player.getName());
            return false;
        }

        // Process payment with Vault only
        double taxPercentage = plugin.getConfigManager().getTaxPercentage();
        double netAmount = calculateNetAmount(totalGrossPrice, taxPercentage);

        if (!vaultEconomy.depositPlayer(player, netAmount).transactionSuccess()) {
            plugin.getLogger().warning("Failed to deposit " + netAmount + " to player " + player.getName());
        }

        if (itemsRemoved && configManager.isLoggingEnabled()) {
            // Log each item separately
            for (ItemStack item : itemsToRemove) {
                Optional<ItemButton> sellButtonOpt = getItemButton(player, item);
                if (sellButtonOpt.isPresent()) {
                    ItemButton sellButton = sellButtonOpt.get();
                    double itemSellPrice = sellButton.getSellPrice(player, item.getAmount());
                    SaleLogger.getInstance().logSale(
                            player.getName(),
                            item.getType().name(),
                            item.getAmount(),
                            itemSellPrice,
                            "VAULT" // ZShop uses Vault economy
                    );
                }
            }
        }

        // Send success message
        String formattedPrice = formatPrice(netAmount, configManager.isFormatedPrice());
        if (taxPercentage > 0) {
            plugin.getLanguageManager().sendMessage(player, "messages.sell-all-tax",
                    "%amount%", String.valueOf(languageManager.formatNumber(totalAmount)),
                    "%price%", formattedPrice,
                    "%tax%", String.format("%.2f", taxPercentage)
            );
        } else {
            plugin.getLanguageManager().sendMessage(player, "messages.sell-all",
                    "%amount%", String.valueOf(languageManager.formatNumber(totalAmount)),
                    "%price%", formattedPrice);
        }
        return true;
    }

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("zShop");
    }
}