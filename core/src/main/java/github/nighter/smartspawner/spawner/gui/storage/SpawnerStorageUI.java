package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStorageUI {
    private static final int NAVIGATION_ROW = 5;
    private static final int INVENTORY_SIZE = 54;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    // Precomputed buttons to avoid repeated creation
    private final Map<String, ItemStack> staticButtons;
    private final Map<Boolean, ItemStack> equipmentToggleButtons;

    // Lightweight caches with better eviction strategies
    private final Map<String, ItemStack> navigationButtonCache;
    private final Map<String, ItemStack> pageIndicatorCache;

    // Cache expiry time reduced for more responsive updates
    private static final long CACHE_EXPIRY_TIME = 30000; // 30 seconds
    private static final int MAX_CACHE_SIZE = 100;

    public SpawnerStorageUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();

        // Initialize caches with appropriate initial capacity
        this.staticButtons = new HashMap<>(4);
        this.equipmentToggleButtons = new HashMap<>(2);
        this.navigationButtonCache = new ConcurrentHashMap<>(16);
        this.pageIndicatorCache = new ConcurrentHashMap<>(16);

        initializeStaticButtons();
        startCleanupTask();
    }

    private void initializeStaticButtons() {
        // Create return button
        staticButtons.put("return", createButton(
                Material.SPAWNER,
                languageManager.getMessage("return-button.name"),
                Collections.emptyList()
        ));

        // Create take all button
        staticButtons.put("takeAll", createButton(
                Material.CHEST,
                languageManager.getMessage("take-all-button.name"),
                Collections.emptyList()
        ));

        // Pre-create equipment toggle buttons
        equipmentToggleButtons.put(true, createEquipmentToggleButton(true));
        equipmentToggleButtons.put(false, createEquipmentToggleButton(false));
    }

    public Inventory createInventory(SpawnerData spawner, String title, int page, int totalPages) {

        // Get total pages efficiently
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Clamp page number to valid range
        page = Math.max(1, Math.min(page, totalPages));

        // Create inventory with title including page info
        Inventory pageInv = Bukkit.createInventory(
                new StoragePageHolder(spawner, page, totalPages),
                INVENTORY_SIZE,
                title + " - [" + page + "/" + totalPages + "]"
        );

        // Cache inventory reference in holder for better performance
        StoragePageHolder holder = (StoragePageHolder) pageInv.getHolder();
        holder.setInventory(pageInv);
        holder.updateOldUsedSlots();

        // Populate the inventory
        updateDisplay(pageInv, spawner, page, totalPages);
        return pageInv;
    }

    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page, int totalPages) {

        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Track both changes and slots that need to be emptied
        Map<Integer, ItemStack> updates = new HashMap<>();
        Set<Integer> slotsToEmpty = new HashSet<>();

        // Clear storage area slots first
        for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
            slotsToEmpty.add(i);
        }

        // Add items from virtual inventory
        addPageItems(updates, slotsToEmpty, spawner, page);

        // Add navigation buttons
        addNavigationButtons(updates, spawner, page, totalPages);

        // Apply all updates in a batch
        for (int slot : slotsToEmpty) {
            if (!updates.containsKey(slot)) {
                inventory.setItem(slot, null);
            }
        }

        for (Map.Entry<Integer, ItemStack> entry : updates.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }

        // Update hologram if enabled
        if (configManager.getBoolean("hologram-enabled")) {
            spawner.updateHologramData();
        }

        // Check if we need to update total pages
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();
        assert holder != null;
        int oldUsedSlots = holder.getOldUsedSlots();
        int currentUsedSlots = spawner.getVirtualInventory().getUsedSlots();

        // Only recalculate total pages if there's a significant change
        if (oldUsedSlots != currentUsedSlots) {
            int newTotalPages = calculateTotalPages(spawner);
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
        }
    }

    private void addPageItems(Map<Integer, ItemStack> updates, Set<Integer> slotsToEmpty,
                              SpawnerData spawner, int page) {
        // Get display items directly from virtual inventory
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();

        if (displayItems.isEmpty()) {
            return;
        }

        // Calculate start index for current page
        int startIndex = (page - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

        // Add items for this page
        for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
            int globalIndex = entry.getKey();

            // Check if item belongs on this page
            if (globalIndex >= startIndex && globalIndex < startIndex + StoragePageHolder.MAX_ITEMS_PER_PAGE) {
                int displaySlot = globalIndex - startIndex;
                updates.put(displaySlot, entry.getValue());
                slotsToEmpty.remove(displaySlot);
            }
        }
    }

    private void addNavigationButtons(Map<Integer, ItemStack> updates, SpawnerData spawner, int page, int totalPages) {
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }
        // Add previous page button if not on first page
        if (page > 1) {
            String cacheKey = "prev-" + (page - 1);
            ItemStack prevButton = navigationButtonCache.computeIfAbsent(
                    cacheKey, k -> createNavigationButton("previous", page - 1));
            updates.put(NAVIGATION_ROW * 9 + 3, prevButton);
        }

        // Add next page button if not on last page
        if (page < totalPages) {
            String cacheKey = "next-" + (page + 1);
            ItemStack nextButton = navigationButtonCache.computeIfAbsent(
                    cacheKey, k -> createNavigationButton("next", page + 1));
            updates.put(NAVIGATION_ROW * 9 + 5, nextButton);
        }

        // Add page indicator with key metrics for spawner
        String indicatorKey = getPageIndicatorKey(page, totalPages, spawner);
        int finalTotalPages = totalPages;
        ItemStack pageIndicator = pageIndicatorCache.computeIfAbsent(
                indicatorKey, k -> createPageIndicator(page, finalTotalPages, spawner)
        );
        updates.put(NAVIGATION_ROW * 9 + 4, pageIndicator);

        // Add static buttons directly from cache
        updates.put(NAVIGATION_ROW * 9 + 8, staticButtons.get("return"));
        updates.put(NAVIGATION_ROW * 9, staticButtons.get("takeAll"));

        // Add equipment toggle button if enabled
        if (configManager.getBoolean("allow-toggle-equipment-drops")) {
            updates.put(NAVIGATION_ROW * 9 + 1, equipmentToggleButtons.get(spawner.isAllowEquipmentItems()));
        }
    }

    private String getPageIndicatorKey(int page, int totalPages, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int usedSlots = virtualInv.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        return page + "-" + totalPages + "-" + usedSlots + "-" + maxSlots;
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationButton(String type, int targetPage) {
        String buttonName;
        String loreKey;

        if (type.equals("previous")) {
            buttonName = languageManager.getMessage("navigation-button.previous.name")
                    .replace("%target_page%", String.valueOf(targetPage));
            loreKey = "navigation-button.previous.lore";
        } else {
            buttonName = languageManager.getMessage("navigation-button.next.name")
                    .replace("%target_page%", String.valueOf(targetPage));
            loreKey = "navigation-button.next.lore";
        }

        List<String> buttonLore = Arrays.asList(
                languageManager.getMessage(loreKey)
                        .replace("%target_page%", String.valueOf(targetPage))
                        .split("\n")
        );

        return createButton(Material.ARROW, buttonName, buttonLore);
    }

    private ItemStack createEquipmentToggleButton(boolean currentState) {
        String displayName = languageManager.getMessage("equipment-toggle.name");
        String loreKey = currentState ? "equipment-toggle.lore.enabled" : "equipment-toggle.lore.disabled";
        List<String> lore = Arrays.asList(languageManager.getMessage(loreKey).split("\n"));

        return createButton(Material.HOPPER, displayName, lore);
    }

    private ItemStack createPageIndicator(int currentPage, int totalPages, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int usedSlots = virtualInv.getUsedSlots();
        int percentStorage = maxSlots > 0 ? (int) ((double) usedSlots / maxSlots * 100) : 0;

        String formattedMaxSlots = languageManager.formatNumberTenThousand(maxSlots);
        String formattedUsedSlots = languageManager.formatNumberTenThousand(usedSlots);

        Material material = plugin.hasShopIntegration() ? Material.GOLD_INGOT : Material.PAPER;
        String nameKey = plugin.hasShopIntegration() ? "shop-page-indicator.name" : "page-indicator.name";
        String loreKey = plugin.hasShopIntegration() ? "shop-page-indicator.lore" : "page-indicator.lore";

        String name = languageManager.getMessage(nameKey)
                .replace("%current_page%", String.valueOf(currentPage))
                .replace("%total_pages%", String.valueOf(totalPages));

        String loreText = languageManager.getMessage(loreKey)
                .replace("%max_slots%", formattedMaxSlots)
                .replace("%used_slots%", formattedUsedSlots)
                .replace("%percent_storage%", String.valueOf(percentStorage));

        List<String> lore = Arrays.asList(loreText.split("\n"));

        return createButton(material, name, lore);
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupCaches();
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // Run every 30 seconds
    }

    private void cleanupCaches() {
        // LRU-like cleanup for navigation buttons
        if (navigationButtonCache.size() > MAX_CACHE_SIZE) {
            int toRemove = navigationButtonCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(navigationButtonCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                navigationButtonCache.remove(keysToRemove.get(i));
            }
        }

        // LRU-like cleanup for page indicators
        if (pageIndicatorCache.size() > MAX_CACHE_SIZE) {
            int toRemove = pageIndicatorCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(pageIndicatorCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                pageIndicatorCache.remove(keysToRemove.get(i));
            }
        }
    }

    public void cleanup() {
        navigationButtonCache.clear();
        pageIndicatorCache.clear();

        // Re-initialize static buttons (just in case language has changed)
        initializeStaticButtons();
    }
}