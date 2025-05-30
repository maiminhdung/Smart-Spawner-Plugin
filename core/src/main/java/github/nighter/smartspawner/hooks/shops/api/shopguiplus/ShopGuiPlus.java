package github.nighter.smartspawner.hooks.shops.api.shopguiplus;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.hooks.shops.IShopIntegration;
import github.nighter.smartspawner.hooks.shops.SaleLogger;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyManager;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class ShopGuiPlus implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final boolean isLoggingEnabled;

    // Transaction timeout
    private static final long TRANSACTION_TIMEOUT_MS = 5000; // 5 seconds timeout

    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<UUID, CompletableFuture<Boolean>> pendingSales = new ConcurrentHashMap<>();

    public ShopGuiPlus(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.isLoggingEnabled = plugin.getConfig().getBoolean("log_transactions.enabled", true);
    }

    @Override
    public boolean sellAllItems(Player player, SpawnerData spawner) {
        // Check if shop system is enabled
        if (!isEnabled()) {
            return false;
        }

        // Prevent multiple concurrent sales for the same player
        if (pendingSales.containsKey(player.getUniqueId())) {
            messageService.sendMessage(player, "shop.transaction_in_progress");
            return false;
        }

        // Get lock with timeout
        ReentrantLock lock = spawner.getLock();
        if (!lock.tryLock()) {
            messageService.sendMessage(player, "shop.transaction_in_progress");
            return false;
        }

        try {
            // Start async sale process
            CompletableFuture<Boolean> saleFuture = CompletableFuture.supplyAsync(() ->
                    processSaleAsync(player, spawner), executorService);

            pendingSales.put(player.getUniqueId(), saleFuture);

            // Handle completion
            saleFuture.whenComplete((success, error) -> {
                pendingSales.remove(player.getUniqueId());
                lock.unlock();

                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE, "Error processing sale", error);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            messageService.sendMessage(player, "shop.sale_failed"));
                }
            });

            // Wait for a very short time to get immediate result if possible
            try {
                Boolean result = saleFuture.get(100, TimeUnit.MILLISECONDS);
                return result != null && result;
            } catch (TimeoutException e) {
                // Sale is still processing, return true to keep inventory open
                return true;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            lock.unlock();
            plugin.getLogger().log(Level.SEVERE, "Error initiating sale", e);
            return false;
        }
    }

    private boolean processSaleAsync(Player player, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        if (items.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    messageService.sendMessage(player, "shop.no_items"));
            return false;
        }

        // Calculate prices and prepare items by economy type
        SaleCalculationResult calculation = calculateSalePrices(player, items);
        if (!calculation.isValid()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    messageService.sendMessage(player, "shop.no_sellable_items"));
            return false;
        }

        // Pre-remove items to improve UX
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            virtualInv.removeItems(calculation.getItemsToRemove());
            Scheduler.runTask(() -> plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner));
        });

        try {
            // Process transactions for each economy type
            CompletableFuture<Boolean> transactionFuture = new CompletableFuture<>();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                boolean success = processTransactions(player, calculation);
                transactionFuture.complete(success);
            });

            boolean success = transactionFuture.get(TRANSACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (!success) {
                // Restore items if payment fails
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    virtualInv.addItems(calculation.getItemsToRemove());
                    messageService.sendMessage(player, "shop.sale_failed");
                    Scheduler.runTask(() -> plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner));
                });
                return false;
            }

            // Log sales asynchronously
            if (isLoggingEnabled) {
                logSalesAsync(calculation, player.getName());
            }

            // Send success message
            double taxPercentage = plugin.getConfig().getDouble("tax.percentage", 10.0);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sendSuccessMessage(player, calculation.getTotalAmount(), calculation.getTotalPrice(), taxPercentage));

            return true;

        } catch (Exception e) {
            // Restore items on timeout/error
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                virtualInv.addItems(calculation.getItemsToRemove());
                Scheduler.runTask(() -> plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner));
            });
            return false;
        }
    }

    private boolean processTransactions(Player player, SaleCalculationResult calculation) {
        double taxPercentage = plugin.getConfig().getDouble("tax.percentage", 10.0);

        for (Map.Entry<EconomyType, Double> entry : calculation.getPricesByEconomy().entrySet()) {
            EconomyType economyType = entry.getKey();
            double totalPrice = entry.getValue();
            double finalPrice = calculateNetAmount(totalPrice, taxPercentage);

            try {
                EconomyProvider economyProvider = ShopGuiPlusApi.getPlugin().getEconomyManager()
                        .getEconomyProvider(economyType);

                if (economyProvider == null) {
                    plugin.getLogger().severe("No economy provider found for type: " + economyType);
                    return false;
                }

                economyProvider.deposit(player, finalPrice);
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing transaction for economy " +
                        economyType + ": " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    private double calculateNetAmount(double grossAmount, double taxPercentage) {
        if (plugin.getConfig().getBoolean("tax.enabled", false)) {
            return grossAmount * (1 - taxPercentage / 100.0);
        }
        return grossAmount;
    }

    private void logSalesAsync(SaleCalculationResult calculation, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<String, SaleInfo> entry : calculation.getItemSales().entrySet()) {
                SaleInfo saleInfo = entry.getValue();
                SaleLogger.getInstance().logSale(
                        playerName,
                        entry.getKey(),
                        saleInfo.getAmount(),
                        saleInfo.getPrice(),
                        saleInfo.getEconomyType().name()
                );
            }
        });
    }

    private String formatMonetaryValue(double value) {
        return formatPrice(value, true);
    }

    private void sendSuccessMessage(Player player, int totalAmount, double totalPrice, double taxPercentage) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(languageManager.formatNumber(totalAmount)));
        placeholders.put("price", formatMonetaryValue(totalPrice));

        if (plugin.getConfig().getBoolean("tax.enabled", false)) {
            double grossPrice = totalPrice / (1 - taxPercentage / 100.0);
            placeholders.put("gross", formatMonetaryValue(grossPrice));
            placeholders.put("tax", String.format("%.2f", taxPercentage));
            messageService.sendMessage(player, "shop.sell_all_with_tax", placeholders);
        } else {
            messageService.sendMessage(player, "shop.sell_all", placeholders);
        }
    }

    private SaleCalculationResult calculateSalePrices(Player player, Map<VirtualInventory.ItemSignature, Long> items) {
        Map<EconomyType, Double> pricesByEconomy = new HashMap<>();
        Map<String, SaleInfo> itemSales = new HashMap<>();
        List<ItemStack> itemsToRemove = new ArrayList<>();
        int totalAmount = 0;
        boolean foundSellableItem = false;

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            if (amount <= 0) continue;

            double sellPrice = ShopGuiPlusApi.getItemStackPriceSell(player, template);
            if (sellPrice <= 0) continue;

            EconomyType economyType = getEconomyType(template);
            foundSellableItem = true;

            ItemStack itemToRemove = template.clone();
            int removeAmount = (int) Math.min(amount, Integer.MAX_VALUE);
            itemToRemove.setAmount(removeAmount);
            itemsToRemove.add(itemToRemove);

            double totalItemPrice = sellPrice * amount;
            pricesByEconomy.merge(economyType, totalItemPrice, Double::sum);
            totalAmount += removeAmount;

            // Store sale info for logging
            String itemName = template.getType().name();
            itemSales.put(itemName, new SaleInfo(removeAmount, totalItemPrice, economyType));
        }

        return new SaleCalculationResult(pricesByEconomy, totalAmount, itemsToRemove, itemSales, foundSellableItem);
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

    private static class SaleCalculationResult {
        private final Map<EconomyType, Double> pricesByEconomy;
        private final int totalAmount;
        private final List<ItemStack> itemsToRemove;
        private final Map<String, SaleInfo> itemSales;
        private final boolean valid;

        public SaleCalculationResult(Map<EconomyType, Double> pricesByEconomy,
                                     int totalAmount,
                                     List<ItemStack> itemsToRemove,
                                     Map<String, SaleInfo> itemSales,
                                     boolean valid) {
            this.pricesByEconomy = pricesByEconomy;
            this.totalAmount = totalAmount;
            this.itemsToRemove = itemsToRemove;
            this.itemSales = itemSales;
            this.valid = valid;
        }

        public Map<EconomyType, Double> getPricesByEconomy() {
            return pricesByEconomy;
        }

        public double getTotalPrice() {
            return pricesByEconomy.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        public int getTotalAmount() {
            return totalAmount;
        }

        public List<ItemStack> getItemsToRemove() {
            return itemsToRemove;
        }

        public Map<String, SaleInfo> getItemSales() {
            return itemSales;
        }

        public boolean isValid() {
            return valid;
        }
    }

    private static class SaleInfo {
        private final int amount;
        private final double price;
        private final EconomyType economyType;

        public SaleInfo(int amount, double price, EconomyType economyType) {
            this.amount = amount;
            this.price = price;
            this.economyType = economyType;
        }

        public int getAmount() {
            return amount;
        }

        public double getPrice() {
            return price;
        }

        public EconomyType getEconomyType() {
            return economyType;
        }
    }
}