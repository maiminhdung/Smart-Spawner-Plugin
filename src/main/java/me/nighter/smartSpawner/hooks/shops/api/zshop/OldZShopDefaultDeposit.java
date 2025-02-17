package me.nighter.smartSpawner.hooks.shops.api.zshop;

import fr.maxlego08.zshop.api.ShopManager;
import fr.maxlego08.zshop.api.buttons.ItemButton;
import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.spawner.properties.VirtualInventory;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class OldZShopDefaultDeposit implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private ShopManager shopManager;

    public OldZShopDefaultDeposit(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
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

    @Override
    public boolean sellAllItems(Player player, SpawnerData spawner) {
        if (!isEnabled()) {
            return false;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        AtomicReference<Double> totalPrice = new AtomicReference<>(0.0);
        AtomicInteger totalAmount = new AtomicInteger(0);
        List<ItemStack> itemsToRemove = new ArrayList<>();
        List<ItemButton> processedButtons = new ArrayList<>();
        Map<ItemButton, Integer> buttonAmounts = new java.util.HashMap<>();
        Map<ItemButton, Double> buttonPrices = new java.util.HashMap<>();

        // First pass: collect all sellable items and calculate prices
        boolean foundSellableItem = items.entrySet().stream().anyMatch(entry -> {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            if (amount <= 0) return false;

            Optional<ItemButton> sellButtonOpt = getItemButton(player, template);
            if (sellButtonOpt.isEmpty()) return false;

            ItemButton sellButton = sellButtonOpt.get();
            double sellPrice = sellButton.getSellPrice(player, (int) amount);
            if (sellPrice <= 0) return false;

            // Create item for removal
            ItemStack itemToRemove = template.clone();
            int removeAmount = (int) Math.min(amount, Integer.MAX_VALUE);
            itemToRemove.setAmount(removeAmount);
            itemsToRemove.add(itemToRemove);

            // Track button data for batch processing
            processedButtons.add(sellButton);
            buttonAmounts.merge(sellButton, removeAmount, Integer::sum);
            buttonPrices.merge(sellButton, sellPrice, Double::sum);

            totalPrice.updateAndGet(current -> current + sellPrice);
            totalAmount.addAndGet(removeAmount);

            return true;
        });

        if (!foundSellableItem) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        // Remove all items first (to prevent exploitation if a later operation fails)
        virtualInv.removeItems(itemsToRemove);

        // Process all economy transactions as a batch
        processedButtons.forEach(button -> {
            double price = buttonPrices.getOrDefault(button, 0.0);
            if (price > 0) {

                // NoSuchMethodError: 'net.milkbowl.vault.economy.Economy.depositPlayer(org.bukkit.OfflinePlayer, double)'
                button.getEconomy().depositMoney(player, price);
            }
        });

        // Apply tax
        double taxPercentage = plugin.getConfigManager().getTaxPercentage();
        double finalPrice = taxPercentage > 0
                ? totalPrice.get() * (1 - taxPercentage / 100.0)
                : totalPrice.get();

        // Send success message
        String formattedPrice = formatPrice(finalPrice, configManager.isFormatedPrice());
        if (taxPercentage > 0) {
            plugin.getLanguageManager().sendMessage(player, "messages.sell-all-tax",
                    "%amount%", String.valueOf(languageManager.formatNumber(totalAmount.get())),
                    "%price%", formattedPrice,
                    "%tax%", String.format("%.2f", taxPercentage)
            );
        } else {
            plugin.getLanguageManager().sendMessage(player, "messages.sell-all",
                    "%amount%", String.valueOf(languageManager.formatNumber(totalAmount.get())),
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