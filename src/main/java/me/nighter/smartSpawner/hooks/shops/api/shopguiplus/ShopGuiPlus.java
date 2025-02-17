package me.nighter.smartSpawner.hooks.shops.api.shopguiplus;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.hooks.shops.SaleLogger;
import me.nighter.smartSpawner.spawner.properties.VirtualInventory;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyManager;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ShopGuiPlus implements IShopIntegration{
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final boolean isLoggingEnabled;

    public ShopGuiPlus(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
        this.isLoggingEnabled = configManager.isLoggingEnabled();
    }

    @Override
    public synchronized boolean sellAllItems(Player player, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        Map<EconomyType, Map<ItemStack, Integer>> itemsByEconomy = new HashMap<>();
        Map<EconomyType, Double> totalPriceByEconomy = new HashMap<>();
        Map<String, Integer> itemAmounts = new HashMap<>(); // For logging
        int totalAmount = 0;
        boolean foundSellableItem = false;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Group items by economy type and calculate prices
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            if (amount > 0) {
                double sellPrice = ShopGuiPlusApi.getItemStackPriceSell(player, template);
                if (sellPrice <= 0) continue;

                // Get economy type for this specific item
                EconomyType itemEconomyType = getEconomyType(template);
                foundSellableItem = true;

                // Create item for removal
                ItemStack itemToRemove = template.clone();
                itemToRemove.setAmount((int)Math.min(amount, Integer.MAX_VALUE));
                itemsToRemove.add(itemToRemove);

                // Group items by economy type
                Map<ItemStack, Integer> itemsForEconomy = itemsByEconomy.computeIfAbsent(
                        itemEconomyType,
                        k -> new HashMap<>()
                );
                itemsForEconomy.merge(template, (int)amount, Integer::sum);

                // Track total price for each economy type
                totalPriceByEconomy.merge(itemEconomyType, sellPrice * amount, Double::sum);
                totalAmount += amount;

                // Store item amounts for logging
                if (isLoggingEnabled) {
                    String itemName = template.getType().name();
                    itemAmounts.merge(itemName, (int)amount, Integer::sum);
                }
            }
        }

        if (!foundSellableItem || itemsByEconomy.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        double taxPercentage = plugin.getConfigManager().getTaxPercentage();

        // Process transactions for each economy type
        boolean allTransactionsSuccessful = true;
        for (Map.Entry<EconomyType, Double> entry : totalPriceByEconomy.entrySet()) {
            EconomyType economyType = entry.getKey();
            double totalPrice = entry.getValue();
            double finalPrice = taxPercentage > 0
                    ? totalPrice * (1 - taxPercentage / 100.0)
                    : totalPrice;

            try {
                EconomyProvider economyProvider = ShopGuiPlusApi.getPlugin().getEconomyManager()
                        .getEconomyProvider(economyType);

                if (economyProvider == null) {
                    plugin.getLogger().severe("No economy provider found for type: " + economyType);
                    allTransactionsSuccessful = false;
                    continue;
                }

                economyProvider.deposit(player, finalPrice);
                // Log transaction if enabled
                if (configManager.isLoggingEnabled()) {
                    for (Map.Entry<String, Integer> itemEntry : itemAmounts.entrySet()) {
                        SaleLogger.getInstance().logSale(
                                player.getName(),
                                itemEntry.getKey(),
                                itemEntry.getValue(),
                                finalPrice,
                                economyType.name()
                        );
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing transaction for economy " +
                        economyType + ": " + e.getMessage());
                allTransactionsSuccessful = false;
            }
        }

        if (!allTransactionsSuccessful) {
            plugin.getLanguageManager().sendMessage(player, "messages.sell-failed");
            return false;
        }

        // Remove all sold items at once
        virtualInv.removeItems(itemsToRemove);

        // Calculate total final price across all economies for message
        double totalFinalPrice = totalPriceByEconomy.values().stream()
                .mapToDouble(price -> taxPercentage > 0
                        ? price * (1 - taxPercentage / 100.0)
                        : price)
                .sum();

        // Send notification
        String formattedPrice = formatPrice(totalFinalPrice, configManager.isFormatedPrice());
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

    private EconomyType getEconomyType(ItemStack material) {
        EconomyType economyType = ShopGuiPlusApi.getItemStackShop(material).getEconomyType();
        if(economyType != null) {
            return economyType;
        }

        EconomyManager economyManager = ShopGuiPlusApi.getPlugin().getEconomyManager();
        EconomyProvider defaultEconomyProvider = economyManager.getDefaultEconomyProvider();
        if(defaultEconomyProvider != null) {
            String defaultEconomyTypeName = defaultEconomyProvider.getName().toUpperCase(Locale.US);
            try {
                return EconomyType.valueOf(defaultEconomyTypeName);
            } catch(IllegalArgumentException ex) {
                return EconomyType.CUSTOM;
            }
        }

        return EconomyType.CUSTOM;
    }

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public boolean isEnabled() {
        return ShopGuiPlusApi.getPlugin().getShopManager().areShopsLoaded();
    }
}