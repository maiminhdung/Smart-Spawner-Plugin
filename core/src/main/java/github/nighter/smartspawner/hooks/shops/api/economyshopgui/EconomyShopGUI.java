package github.nighter.smartspawner.hooks.shops.api.economyshopgui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.hooks.shops.IShopIntegration;
import github.nighter.smartspawner.hooks.shops.SaleLogger;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import static me.gypopo.economyshopgui.util.EconomyType.*;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.prices.AdvancedSellPrice;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.EcoType;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class EconomyShopGUI implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    // Transaction timeout
    private static final long TRANSACTION_TIMEOUT_MS = 5000; // 5 seconds timeout

    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<UUID, CompletableFuture<Boolean>> pendingSales = new ConcurrentHashMap<>();

    public EconomyShopGUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiManager();
    }

    @Override
    public boolean sellAllItems(Player player, SpawnerData spawner) {
        // Prevent multiple concurrent sales for the same player
        if (pendingSales.containsKey(player.getUniqueId())) {
            languageManager.sendMessage(player, "messages.transaction-in-progress");
            return false;
        }

        // Get lock with timeout
        ReentrantLock lock = spawner.getLock();
        if (!lock.tryLock()) {
            languageManager.sendMessage(player, "messages.transaction-in-progress");
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
                            languageManager.sendMessage(player, "messages.sell-failed"));
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
                    languageManager.sendMessage(player, "messages.no-items"));
            return false;
        }

        // Calculate prices and prepare items
        SaleCalculationResult calculation = calculateSalePrices(player, items);
        if (!calculation.isValid()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    languageManager.sendMessage(player, "messages.no-sellable-items"));
            return false;
        }

        int oldTotalPages = calculateTotalPages(spawner);
        // Pre-remove items to improve UX
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            virtualInv.removeItems(calculation.getItemsToRemove());
            if (virtualInv.isDirty()) {
                int newTotalPages = calculateTotalPages(spawner);
                spawnerGuiViewManager.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
            }
        });

        try {
            // Process transactions
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
                    languageManager.sendMessage(player, "messages.sell-failed");
                    int newTotalPages = calculateTotalPages(spawner);
                    spawnerGuiViewManager.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
                });
                return false;
            }

            // Update shop stats asynchronously
            updateShopStats(calculation.getSoldItems(), player.getUniqueId());

            // Send success message
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sendSuccessMessage(player, calculation));

            return true;

        } catch (Exception e) {
            // Restore items on timeout/error
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                virtualInv.addItems(calculation.getItemsToRemove());
                languageManager.sendMessage(player, "messages.sell-failed");
                int newTotalPages = calculateTotalPages(spawner);
                spawnerGuiViewManager.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
            });
            return false;
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private boolean processTransactions(Player player, SaleCalculationResult calculation) {
        Map<EcoType, Double> afterTaxPrices = calculation.getTaxedPrices();

        try {
            for (Map.Entry<EcoType, Double> entry : afterTaxPrices.entrySet()) {
                if (isClaimableCurrency(entry.getKey())) {
                    EconomyShopGUIHook.getEcon(entry.getKey()).depositBalance(player, entry.getValue());
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing transaction", e);
            return false;
        }
    }

    private void sendSuccessMessage(Player player, SaleCalculationResult calculation) {
        StringBuilder taxedPriceBuilder = new StringBuilder();
        calculation.getTaxedPrices().forEach((type, value) -> {
            if (!taxedPriceBuilder.isEmpty()) taxedPriceBuilder.append(", ");
            taxedPriceBuilder.append(formatMonetaryValue(value, type));
        });

        StringBuilder originalPriceBuilder = new StringBuilder();
        if (configManager.getBoolean("tax-enabled")) {
            calculation.getOriginalPrices().forEach((type, value) -> {
                if (!originalPriceBuilder.isEmpty()) originalPriceBuilder.append(", ");
                originalPriceBuilder.append(formatMonetaryValue(value, type));
            });
        }

        double taxPercentage = configManager.getDouble("tax-rate");
        if (configManager.getBoolean("tax-enabled")) {
            languageManager.sendMessage(player, "messages.sell-all-tax",
                    "%amount%", String.valueOf(languageManager.formatNumberTenThousand(calculation.getTotalAmount())),
                    "%price%", taxedPriceBuilder.toString(),
                    "%gross%", originalPriceBuilder.toString(),
                    "%tax%", String.format("%.2f", taxPercentage));
        } else {
            languageManager.sendMessage(player, "messages.sell-all",
                    "%amount%", String.valueOf(languageManager.formatNumberTenThousand(calculation.getTotalAmount())),
                    "%price%", taxedPriceBuilder.toString());
        }
    }

    private SaleCalculationResult calculateSalePrices(Player player, Map<VirtualInventory.ItemSignature, Long> items) {
        Map<EcoType, Double> prices = new HashMap<>();
        Map<ShopItem, Integer> soldItems = new HashMap<>();
        List<ItemStack> itemsToRemove = new ArrayList<>();
        int totalAmount = 0;
        boolean foundSellableItem = false;

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
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

                ItemStack itemToRemove = template.clone();
                itemToRemove.setAmount(limit);
                itemsToRemove.add(itemToRemove);

                calculateSellPrice(prices, shopItem, player, template, limit, totalAmount);
                totalAmount += limit;
                soldItems.put(shopItem, soldItems.getOrDefault(shopItem, 0) + limit);
            }
        }

        Map<EcoType, Double> taxedPrices = configManager.getBoolean("tax-enabled")
                ? applyTax(prices)
                : prices;

        return new SaleCalculationResult(prices, taxedPrices, totalAmount, itemsToRemove, soldItems, foundSellableItem);
    }

    private Map<EcoType, Double> applyTax(Map<EcoType, Double> originalPrices) {
        double taxPercentage = plugin.getConfigManager().getDouble("tax-rate");
        Map<EcoType, Double> taxedPrices = new HashMap<>();
        for (Map.Entry<EcoType, Double> entry : originalPrices.entrySet()) {
            double originalPrice = entry.getValue();
            double taxAmount = originalPrice * (taxPercentage / 100.0);
            double afterTaxPrice = originalPrice - taxAmount;
            taxedPrices.put(entry.getKey(), afterTaxPrice);
        }
        return taxedPrices;
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
                    .forEach((type, price) -> {
                        prices.put(type, prices.getOrDefault(type, 0d) + price);
                        // Log sale for each type of currency
                        if (configManager.getBoolean("logging-enabled")) {
                            SaleLogger.getInstance().logSale(
                                    player.getName(),
                                    item.getType().name(),
                                    amount,
                                    price,
                                    type.getType().name()
                            );
                        }
                    });
        } else {
            double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, item, player, amount, sold);
            prices.put(shopItem.getEcoType(), prices.getOrDefault(shopItem.getEcoType(), 0d) + sellPrice);
            // Log sale for single currency
            if (configManager.getBoolean("logging-enabled")) {
                SaleLogger.getInstance().logSale(
                        player.getName(),
                        item.getType().name(),
                        amount,
                        sellPrice,
                        shopItem.getEcoType().getType().name()
                );
            }
        }
    }

    private void updateShopStats(Map<ShopItem, Integer> items, UUID playerUUID) {
        plugin.runTaskAsync(task -> {
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
            boolean useLanguageManager = ecoType.getType() == VAULT && configManager.getBoolean("formated-price")
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

    private static class SaleCalculationResult {
        private final Map<EcoType, Double> originalPrices;
        private final Map<EcoType, Double> taxedPrices;
        private final int totalAmount;
        private final List<ItemStack> itemsToRemove;
        private final Map<ShopItem, Integer> soldItems;
        private final boolean valid;

        public SaleCalculationResult(
                Map<EcoType, Double> originalPrices,
                Map<EcoType, Double> taxedPrices,
                int totalAmount,
                List<ItemStack> itemsToRemove,
                Map<ShopItem, Integer> soldItems,
                boolean valid) {
            this.originalPrices = originalPrices;
            this.taxedPrices = taxedPrices;
            this.totalAmount = totalAmount;
            this.itemsToRemove = itemsToRemove;
            this.soldItems = soldItems;
            this.valid = valid;
        }

        public Map<EcoType, Double> getOriginalPrices() {
            return originalPrices;
        }

        public Map<EcoType, Double> getTaxedPrices() {
            return taxedPrices;
        }

        public int getTotalAmount() {
            return totalAmount;
        }

        public List<ItemStack> getItemsToRemove() {
            return itemsToRemove;
        }

        public Map<ShopItem, Integer> getSoldItems() {
            return soldItems;
        }

        public boolean isValid() {
            return valid;
        }
    }
}