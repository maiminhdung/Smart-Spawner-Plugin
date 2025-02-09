package me.nighter.smartSpawner.hooks.shops.api.economyshopgui;

import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.prices.AdvancedSellPrice;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.EcoType;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.utils.OptimizedVirtualInventory;
import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import me.nighter.smartSpawner.SmartSpawner;

import static me.gypopo.economyshopgui.util.EconomyType.*;

public class EconomyShopGUI implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;

    public EconomyShopGUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
    }

    private Map<EcoType, Double> applyTax(Map<EcoType, Double> originalPrices) {
        double taxPercentage = plugin.getConfigManager().getTaxPercentage();
        Map<EcoType, Double> taxedPrices = new HashMap<>();
        for (Map.Entry<EcoType, Double> entry : originalPrices.entrySet()) {
            double originalPrice = entry.getValue();
            double taxAmount = originalPrice * (taxPercentage / 100.0);
            double afterTaxPrice = originalPrice - taxAmount;
            taxedPrices.put(entry.getKey(), afterTaxPrice);
        }
        return taxedPrices;
    }

    public synchronized boolean sellAllItems(Player player, SpawnerData spawner) {
        OptimizedVirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<OptimizedVirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        // Prevent processing if inventory is empty
        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        int totalAmount = 0;
        boolean foundSellableItem = false;
        Map<ShopItem, Integer> soldItems = new HashMap<>();
        Map<EcoType, Double> prices = new HashMap<>();
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Calculate sellable items and prices
        for (Map.Entry<OptimizedVirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            if (amount > 0) {
                ShopItem shopItem = EconomyShopGUIHook.getShopItem(player, template);
                if (shopItem == null || !EconomyShopGUIHook.isSellAble(shopItem)) continue;

                foundSellableItem = true;

                int limit = getSellLimit(shopItem, player.getUniqueId(), (int)amount);
                if (limit == -1) continue;

                limit = getMaxSell(shopItem, limit, soldItems.getOrDefault(shopItem, 0));
                if (limit == -1) continue;

                // Create ItemStack with correct amount for removal
                ItemStack itemToRemove = template.clone();
                itemToRemove.setAmount(limit);
                itemsToRemove.add(itemToRemove);

                calculateSellPrice(prices, shopItem, player, template, limit, totalAmount);
                totalAmount += limit;
                soldItems.put(shopItem, soldItems.getOrDefault(shopItem, 0) + limit);
            }
        }

        if (!foundSellableItem) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        if (totalAmount > 0) {
            double taxPercentage = plugin.getConfigManager().getTaxPercentage();
            Map<EcoType, Double> afterTaxPrices = prices;

            if (taxPercentage > 0) {
                afterTaxPrices = applyTax(prices);
            }

            // Remove items all at once
            virtualInv.removeItems(itemsToRemove);

            updateShopStats(soldItems, player.getUniqueId());

            for (Map.Entry<EcoType, Double> entry : afterTaxPrices.entrySet()) {
                if (isClaimableCurrency(entry.getKey())) {
                    EconomyShopGUIHook.getEcon(entry.getKey()).depositBalance(player, entry.getValue());
                }
            }

            StringBuilder priceBuilder = new StringBuilder();
            afterTaxPrices.forEach((type, value) -> {
                if (priceBuilder.length() > 0) priceBuilder.append(", ");
                priceBuilder.append(formatMonetaryValue(value, type));
            });

            if (taxPercentage > 0) {
                plugin.getLanguageManager().sendMessage(player, "messages.sell-all-tax",
                        "%amount%", String.valueOf(languageManager.formatNumber(totalAmount)),
                        "%price%", priceBuilder.toString(),
                        "%tax%", String.format("%.2f", taxPercentage));
            } else {
                plugin.getLanguageManager().sendMessage(player, "messages.sell-all",
                        "%amount%", String.valueOf(languageManager.formatNumber(totalAmount)),
                        "%price%", priceBuilder.toString());
            }
            return true;
        }

        plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
        return false;
    }


    private int getSellLimit(ShopItem shopItem, UUID playerUUID, int amount) {
        if (shopItem.getLimitedSellMode() != 0) {
            int stock = EconomyShopGUIHook.getSellLimit(shopItem, playerUUID);
            if (stock <= 0) {
                return -1;
            } else if (stock < amount) {
                amount = stock;
            }
        }
        return amount;
    }

    private int getMaxSell(ShopItem shopItem, int qty, int alreadySold) {
        if (shopItem.isMaxSell(alreadySold + qty)) {
            if (alreadySold >= shopItem.getMaxSell())
                return -1;
            qty = shopItem.getMaxSell() - alreadySold;
        }
        return qty;
    }

    private void calculateSellPrice(Map<EcoType, Double> prices, ShopItem shopItem, Player player, ItemStack item, int amount, int sold) {
        if (EconomyShopGUIHook.hasMultipleSellPrices(shopItem)) {
            AdvancedSellPrice sellPrice = EconomyShopGUIHook.getMultipleSellPrices(shopItem);
            sellPrice.getSellPrices(sellPrice.giveAll() ? null : sellPrice.getSellTypes().get(0), player, item, amount, sold)
                    .forEach((type, price) -> prices.put(type, prices.getOrDefault(type, 0d) + price));
        } else {
            double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, item, player, amount, sold);
            prices.put(shopItem.getEcoType(), prices.getOrDefault(shopItem.getEcoType(), 0d) + sellPrice);
        }
    }

    private void updateShopStats(Map<ShopItem, Integer> items, UUID playerUUID) {
        plugin.task(task -> {
            for (Map.Entry<ShopItem, Integer> entry : items.entrySet()) {
                ShopItem item = entry.getKey();
                int amount = entry.getValue();

                if (item.isRefillStock()) {
                    EconomyShopGUIHook.sellItemStock(item, playerUUID, amount);
                }
                if (item.getLimitedSellMode() != 0) {
                    EconomyShopGUIHook.sellItemLimit(item, playerUUID, amount);
                }
                if (item.isDynamicPricing()) {
                    EconomyShopGUIHook.sellItem(item, amount);
                }
            }
        });
    }

    private boolean isClaimableCurrency(EcoType ecoType) {
        return switch (ecoType.getType().name()) {
            case "ITEM", "LEVELS", "EXP", "VAULT", "PLAYER_POINTS", "COINS" -> true;
            default -> false;
        };
    }

    private String formatMonetaryValue(double value, EcoType ecoType) {
        if (ecoType == null) {
            return formatPrice(value, false);
        }

        try {
            boolean useLanguageManager = ecoType.getType() == VAULT && configManager.isFormatedPrice()
                    || ecoType.getType() == PLAYER_POINTS;
            String formattedValue = formatPrice(value, useLanguageManager);

            // Add name type for EXP, LEVELS và ITEM
            if (ecoType.getType() == EXP || ecoType.getType() == LEVELS || ecoType.getType() == ITEM) {
                return formattedValue + " " + ecoType.getType().name();
            }

            return formattedValue;
        } catch (Exception e) {
            return formatPrice(value, false);
        }
    }

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}