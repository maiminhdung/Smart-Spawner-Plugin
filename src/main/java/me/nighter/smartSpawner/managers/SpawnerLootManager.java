package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.holders.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.utils.OptimizedVirtualInventory;
import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.*;

public class SpawnerLootManager {
    private static final int NAVIGATION_ROW = 5;
    private static final int SLOTS_PER_PAGE = 45; // 9x5 grid for items
    private static final int INVENTORY_SIZE = 54; // 9x6 including navigation
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    public SpawnerLootManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    public Inventory createLootInventory(SpawnerData spawner, String title, int page) {
        OptimizedVirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
        int totalItems = displayItems.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / SLOTS_PER_PAGE));
        page = Math.min(Math.max(1, page), totalPages);

        Inventory pageInv = Bukkit.createInventory(
                new PagedSpawnerLootHolder(spawner, page, totalPages),
                INVENTORY_SIZE,
                title + " - [" + page + "/" + totalPages + "]"
        );

        // Update inventory with current items
        updateInventoryContents(pageInv, spawner, page);

        return pageInv;
    }

    public void updateInventoryContents(Inventory inventory, SpawnerData spawner, int page) {
        OptimizedVirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
        int totalItems = displayItems.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / SLOTS_PER_PAGE));

        // Clear existing items (only in the item slots, not navigation)
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            inventory.setItem(i, null);
        }

        // Calculate the range of items to display on this page
        int startIndex = (page - 1) * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, totalItems);

        // Display items for the current page
        int slot = 0;
        for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
            if (slot >= startIndex && slot < endIndex) {
                inventory.setItem(slot - startIndex, entry.getValue());
            }
            slot++;
            if (slot >= endIndex) break;
        }

        // Update navigation items
        if (page > 1) {
            ItemStack prevButton = createNavigationButton(Material.ARROW, languageManager.getMessage("navigation-button.previous.name"), page - 1);
            inventory.setItem(NAVIGATION_ROW * 9 + 3, prevButton);
        } else {
            inventory.setItem(NAVIGATION_ROW * 9 + 3, null);
        }

        if (page < totalPages) {
            ItemStack nextButton = createNavigationButton(Material.ARROW, languageManager.getMessage("navigation-button.next.name"), page + 1);
            inventory.setItem(NAVIGATION_ROW * 9 + 5, nextButton);
        } else {
            inventory.setItem(NAVIGATION_ROW * 9 + 5, null);
        }

        // Page indicator
        ItemStack pageIndicator = createPageIndicator(page, totalPages, totalItems, virtualInv, spawner.getMaxSpawnerLootSlots());
        inventory.setItem(NAVIGATION_ROW * 9 + 4, pageIndicator);

        // Other buttons
        ItemStack returnButton = createReturnButton();
        inventory.setItem(NAVIGATION_ROW * 9 + 8, returnButton);

        ItemStack takeAllButton = new ItemStack(Material.CHEST);
        ItemMeta meta = takeAllButton.getItemMeta();
        meta.setDisplayName(languageManager.getMessage("take-all-button.name"));
        takeAllButton.setItemMeta(meta);
        inventory.setItem(NAVIGATION_ROW * 9 + 0, takeAllButton);

        if (configManager.isAllowToggleEquipmentItems()) {
            ItemStack durabilityToggle = createAllowEquipmentToggleButton(spawner.isAllowEquipmentItems());
            inventory.setItem(NAVIGATION_ROW * 9 + 1, durabilityToggle);
        }
    }

    private ItemStack createAllowEquipmentToggleButton(boolean currentState) {
        ItemStack button = new ItemStack(Material.HOPPER);
        ItemMeta meta = button.getItemMeta();

        // Set title
        meta.setDisplayName(languageManager.getMessage("equipment-toggle.name"));

        // Create lore with proper spacing
        List<String> lore = Arrays.asList(
                (currentState ? languageManager.getMessage("equipment-toggle.lore.enabled") : languageManager.getMessage("equipment-toggle.lore.disabled")).split("\n")
        );

        meta.setLore(lore);
        button.setItemMeta(meta);
        return button;
    }

    private ItemStack createItemStack(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationButton(Material material, String name, int targetPage) {
        String buttonName;
        if (name.equals(languageManager.getMessage("navigation-button.previous.name"))) {
            buttonName = languageManager.getMessage("navigation-button.previous.name")
                    .replace("%button_name%", name)
                    .replace("%target_page%", String.valueOf(targetPage));
        } else {
            buttonName = languageManager.getMessage("navigation-button.next.name")
                    .replace("%button_name%", name)
                    .replace("%target_page%", String.valueOf(targetPage));
        }

        List<String> buttonLore = Arrays.asList(
                languageManager.getMessage("navigation-button.previous.lore")
                        .replace("%target_page%", String.valueOf(targetPage))
        );

        return createItemStack(material, buttonName, buttonLore);
    }

    private ItemStack createPageIndicator(int currentPage, int totalPages, int totalItems, OptimizedVirtualInventory virtualInventory, int maxSlots) {
        Material material;
        String itemName;
        List<String> itemLore = new ArrayList<>();

        // Get total number of actual items (counting stacks)
        long totalActualItems = virtualInventory.getTotalItems();

        // Calculate maximum possible items (assuming max stack size of 64)
        long maxPossibleItems = (long) maxSlots * 64;

        // Calculate storage percentage based on actual items vs maximum possible
        int percentStorage = (int) ((double) totalActualItems / maxPossibleItems * 100);

        String formattedMaxPossibleItems = languageManager.formatNumber(maxPossibleItems);
        String formattedTotalActualItems = languageManager.formatNumber(totalActualItems);

        if (plugin.hasShopIntegration()) {
            material = Material.GOLD_INGOT;
            itemName = languageManager.getMessage("shop-page-indicator.name")
                    .replace("%current_page%", String.valueOf(currentPage))
                    .replace("%total_pages%", String.valueOf(totalPages));


            String loreMessage = languageManager.getMessage("shop-page-indicator.lore")
                    .replace("%max_slots%", formattedMaxPossibleItems)
                    .replace("%current_items%", formattedTotalActualItems)
                    .replace("%percent_storage%", String.valueOf(percentStorage));

            itemLore.addAll(Arrays.asList(loreMessage.split("\n")));
        } else {
            material = Material.PAPER;
            itemName = languageManager.getMessage("page-indicator.name")
                    .replace("%current_page%", String.valueOf(currentPage))
                    .replace("%total_pages%", String.valueOf(totalPages));

            String loreMessage = languageManager.getMessage("page-indicator.lore")
                    .replace("%max_slots%", formattedMaxPossibleItems)
                    .replace("%current_items%", formattedTotalActualItems);

            itemLore.addAll(Arrays.asList(loreMessage.split("\n")));
        }

        // Create ItemStack with the information
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(itemName);
        itemMeta.setLore(itemLore);
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    public ItemStack createReturnButton() {
        String buttonName = languageManager.getMessage("return-button.name");
        return createItemStack(Material.SPAWNER, buttonName, Collections.emptyList());
    }
}