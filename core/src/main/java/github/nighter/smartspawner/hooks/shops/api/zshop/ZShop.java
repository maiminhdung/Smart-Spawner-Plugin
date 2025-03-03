package github.nighter.smartspawner.hooks.shops.api.zshop;

import fr.maxlego08.zshop.api.ShopManager;
import fr.maxlego08.zshop.api.buttons.ItemButton;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.hooks.shops.IShopIntegration;
import github.nighter.smartspawner.hooks.shops.SaleLogger;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class ZShop implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private ShopManager shopManager;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private Economy vaultEconomy;

    // Transaction timeout
    private static final long TRANSACTION_TIMEOUT_MS = 5000; // 5 seconds timeout

    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<UUID, CompletableFuture<Boolean>> pendingSales = new ConcurrentHashMap<>();

    public ZShop(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiManager();
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
            plugin.getLogger().log(Level.SEVERE, "Error getting item button", exception);
            return Optional.empty();
        }
    }

    public ShopManager getShopManager() {
        if (this.shopManager != null) return this.shopManager;
        return this.shopManager = this.plugin.getServer().getServicesManager()
                .getRegistration(ShopManager.class).getProvider();
    }

    private double calculateNetAmount(double grossAmount, double taxPercentage) {
        if (configManager.getBoolean("tax-enabled")) {
            return grossAmount * (1 - taxPercentage / 100.0);
        }
        return grossAmount;
    }

    @Override
    public boolean sellAllItems(Player player, SpawnerData spawner) {
        if (!isEnabled() || vaultEconomy == null) {
            plugin.getLogger().warning("Support for zShop requires Vault as a currency provider");
            return false;
        }

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

        // Calculate prices and validate items
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
            // Force inventory update
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof StoragePageHolder) {
                int newTotalPages = calculateTotalPages(spawner);
                spawnerGuiViewManager.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
            }
        });

        // Process payment
        double taxPercentage = configManager.getDouble("tax-rate");
        double netAmount = calculateNetAmount(calculation.getTotalGrossPrice(), taxPercentage);

        try {
            CompletableFuture<Boolean> depositFuture = new CompletableFuture<>();

            // Process economy transaction on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                boolean success = vaultEconomy.depositPlayer(player, netAmount).transactionSuccess();
                depositFuture.complete(success);
            });

            boolean success = depositFuture.get(TRANSACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (!success) {
                // Restore items if payment fails
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    virtualInv.addItems(calculation.getItemsToRemove());
                    languageManager.sendMessage(player, "messages.sell-failed");
                    // Force inventory update
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof StoragePageHolder) {
                        int newTotalPages = calculateTotalPages(spawner);
                        spawnerGuiViewManager.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
                    }
                });
                return false;
            }

            // Log sales asynchronously
            if (configManager.getBoolean("logging-enabled")) {
                logSalesAsync(calculation, player.getName());
            }

            // Send success message on main thread
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sendSuccessMessage(player, calculation.getTotalAmount(), netAmount, taxPercentage));

            return true;

        } catch (Exception e) {
            // Restore items on timeout/error
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                virtualInv.addItems(calculation.getItemsToRemove());
                int newTotalPages = calculateTotalPages(spawner);
                spawnerGuiViewManager.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
                languageManager.sendMessage(player, "messages.sell-failed");
            });
            return false;
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void logSalesAsync(SaleCalculationResult calculation, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<ItemStack, Double> entry : calculation.getItemPrices().entrySet()) {
                ItemStack item = entry.getKey();
                double price = entry.getValue();
                SaleLogger.getInstance().logSale(
                        playerName,
                        item.getType().name(),
                        item.getAmount(),
                        price,
                        "VAULT"
                );
            }
        });
    }

    private String formatMonetaryValue(double value) {
        return formatPrice(value, configManager.getBoolean("formated-price"));
    }

    private void sendSuccessMessage(Player player, int totalAmount, double netAmount, double taxPercentage) {
        if (configManager.getBoolean("tax-enabled")) {
            double netPrice = calculateNetAmount(netAmount, taxPercentage);
            languageManager.sendMessage(player, "messages.sell-all-tax",
                    "%amount%", String.valueOf(totalAmount),
                    "%price%", formatMonetaryValue(netPrice),
                    "%gross%", formatMonetaryValue(netAmount),
                    "%tax%", String.format("%.2f", taxPercentage)
            );
        } else {
            languageManager.sendMessage(player, "messages.sell-all",
                    "%amount%", String.valueOf(totalAmount),
                    "%price%", formatMonetaryValue(netAmount));
        }
    }

    private SaleCalculationResult calculateSalePrices(Player player, Map<VirtualInventory.ItemSignature, Long> items) {
        double totalGrossPrice = 0.0;
        int totalAmount = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();
        Map<ItemStack, Double> itemPrices = new HashMap<>();
        boolean foundSellableItem = false;

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

            ItemStack itemToRemove = template.clone();
            int removeAmount = (int) Math.min(amount, Integer.MAX_VALUE);
            itemToRemove.setAmount(removeAmount);
            itemsToRemove.add(itemToRemove);

            totalGrossPrice += sellPrice;
            totalAmount += removeAmount;
            itemPrices.put(itemToRemove, sellPrice);
        }

        return new SaleCalculationResult(totalGrossPrice, totalAmount, itemsToRemove, itemPrices, foundSellableItem);
    }

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("zShop");
    }

    private static class SaleCalculationResult {
        private final double totalGrossPrice;
        private final int totalAmount;
        private final List<ItemStack> itemsToRemove;
        private final Map<ItemStack, Double> itemPrices;
        private final boolean valid;

        public SaleCalculationResult(double totalGrossPrice, int totalAmount,
                                     List<ItemStack> itemsToRemove,
                                     Map<ItemStack, Double> itemPrices,
                                     boolean valid) {
            this.totalGrossPrice = totalGrossPrice;
            this.totalAmount = totalAmount;
            this.itemsToRemove = itemsToRemove;
            this.itemPrices = itemPrices;
            this.valid = valid;
        }

        public double getTotalGrossPrice() {
            return totalGrossPrice;
        }

        public int getTotalAmount() {
            return totalAmount;
        }

        public List<ItemStack> getItemsToRemove() {
            return itemsToRemove;
        }

        public Map<ItemStack, Double> getItemPrices() {
            return itemPrices;
        }

        public boolean isValid() {
            return valid;
        }
    }
}