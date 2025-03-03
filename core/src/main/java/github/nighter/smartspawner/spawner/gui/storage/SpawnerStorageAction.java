package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemClickHandler;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveHelper;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveResult;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;
    private static final Set<Integer> CONTROL_SLOTS = Set.of(45, 46, 48, 49, 50, 53);

    // Add cooldown system properties
    private final Map<UUID, Long> sellCooldowns = new ConcurrentHashMap<>();

    private final Map<ClickType, ItemClickHandler> clickHandlers;
    private final Map<UUID, Inventory> openStorageInventories = new HashMap<>();

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.clickHandlers = initializeClickHandlers();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiManager();
    }

    private Map<ClickType, ItemClickHandler> initializeClickHandlers() {
        Map<ClickType, ItemClickHandler> handlers = new EnumMap<>(ClickType.class);
        handlers.put(ClickType.RIGHT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, true));
        handlers.put(ClickType.LEFT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, false));
        return Collections.unmodifiableMap(handlers);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player) ||
                !(event.getInventory().getHolder() instanceof StoragePageHolder)) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        StoragePageHolder holder = (StoragePageHolder) event.getInventory().getHolder();
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        if (CONTROL_SLOTS.contains(slot)) {
            handleControlSlotClick(player, slot, holder, holder.getSpawnerData(), event.getInventory());
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        ItemClickHandler handler = clickHandlers.get(event.getClick());
        if (handler != null) {
            handler.handle(player, event.getInventory(), slot, clickedItem, holder.getSpawnerData());
        }
    }

    private void takeSingleItem(Player player, Inventory sourceInv, int slot, ItemStack item,
                                SpawnerData spawner, boolean singleItem) {
        PlayerInventory playerInv = player.getInventory();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        ItemMoveResult result = ItemMoveHelper.moveItems(
                item,
                singleItem ? 1 : item.getAmount(),
                playerInv,
                virtualInv
        );
        int oldTotalPages = calculateTotalPages(spawner);
        if (result.getAmountMoved() > 0) {
            updateInventorySlot(sourceInv, slot, item, result.getAmountMoved());
            virtualInv.removeItems(result.getMovedItems());
            player.updateInventory();

            // Update hologram and recalculate pages after item removal
            spawner.updateHologramData();

            // Synchronize total pages after item removal
            StoragePageHolder holder = (StoragePageHolder) sourceInv.getHolder();
            if (holder != null) {
                int newTotalPages = calculateTotalPages(spawner);
                if (newTotalPages != holder.getTotalPages()) {
                    holder.setTotalPages(newTotalPages);
                }
                holder.updateOldUsedSlots();
                spawnerGuiViewManager.updateStorageGuiViewers(spawner,oldTotalPages,newTotalPages);

                // Check if spawner is at capacity and update if necessary
                if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.isAtCapacity()) {
                    spawner.setAtCapacity(false);
                }
            }
        } else {
            languageManager.sendMessage(player, "messages.inventory-full");
        }
    }

    private static void updateInventorySlot(Inventory sourceInv, int slot, ItemStack item, int amountMoved) {
        if (amountMoved >= item.getAmount()) {
            sourceInv.setItem(slot, null);
            return;
        }

        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - amountMoved);
        sourceInv.setItem(slot, remaining);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StoragePageHolder) {
            event.setCancelled(true);
        }
    }

    private void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                        SpawnerData spawner, Inventory inventory) {
        switch (slot) {
            case 48:
                if (holder.getCurrentPage() > 1) {
                    updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory, false);
                }
                break;
            case 50:
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory, false);
                }
                break;
            case 49:
                handleSellAllItems(player, spawner, holder);
                break;
            case 53:
                openMainMenu(player, spawner);
                break;
            case 45:
                handleTakeAllItems(player, inventory);
                break;
            case 46:
                handleToggleEquipment(player, spawner, inventory);
                break;
        }
    }

    private void updatePageContent(Player player, SpawnerData spawner, int newPage, Inventory inventory, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();

        // Always recalculate total pages to ensure synchronization
        int totalPages = calculateTotalPages(spawner);

        // Update holder with latest values
        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        // Update inventory display
        lootManager.updateDisplay(inventory, spawner, newPage, totalPages);

        // Update inventory title to reflect current page and total pages
        updateInventoryTitle(player, inventory, spawner, newPage, totalPages);

        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        String baseTitle = languageManager.getGuiTitle("gui-title.loot-menu");
        String newTitle = baseTitle + " - [" + page + "/" + totalPages + "]";

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            // Fallback: if title update fails, recreate and reopen the inventory
            configManager.debug("Fallback: Opening loot page due to title update failure");
            openLootPage(player, spawner, page, false);
        }
    }

    private long getSellCooldownMs() {
        return configManager.getInt("sell-cooldown") * 1000L;
    }

    private boolean isOnCooldown(Player player) {
        long cooldownMs = getSellCooldownMs();
        // If cooldown is disabled (set to 0), always return false
        if (cooldownMs <= 0) {
            return false;
        }

        long lastSellTime = sellCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        boolean onCooldown = (currentTime - lastSellTime) < cooldownMs;

        // Debug information if needed
        if (onCooldown) {
            configManager.debug("Player " + player.getName() + " tried to sell items while on cooldown");
        }

        return onCooldown;
    }

    private void updateCooldown(Player player) {
        sellCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // Method to periodically clean up old cooldowns to prevent memory leaks
    private void clearOldCooldowns() {
        long cooldownMs = getSellCooldownMs();
        if (cooldownMs <= 0) {
            // If cooldown is disabled, clear all entries
            sellCooldowns.clear();
            return;
        }

        long currentTime = System.currentTimeMillis();
        sellCooldowns.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > cooldownMs * 10);
    }

    private void handleSellAllItems(Player player, SpawnerData spawner, StoragePageHolder holder) {
        if (!plugin.hasShopIntegration()) return;

        // Play click sound
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Permission check
        if (!player.hasPermission("smartspawner.sellall")) {
            player.sendMessage(languageManager.getMessage("no-permission"));
            return;
        }

        // Anti-spam cooldown check
        if (isOnCooldown(player)) {
            int cooldownSeconds = configManager.getInt("sell-cooldown");
            languageManager.sendMessage(player, "messages.sell-cooldown",
                    "%seconds%", String.valueOf(cooldownSeconds));
            return;
        }

        // Update cooldown timestamp before processing
        updateCooldown(player);

        // Clean up old cooldowns periodically (e.g., every 10th call)
        if (Math.random() < 0.1) {
            clearOldCooldowns();
        }

        // Process the sale through shop integration
        boolean success = plugin.getShopIntegration().sellAllItems(player, spawner);

        // Reset at capacity if successful
        if (success && spawner.isAtCapacity()) {
            spawner.setAtCapacity(false);
        }
    }

    private void openMainMenu(Player player, SpawnerData spawner) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
    }

    private void handleToggleEquipment(Player player, SpawnerData spawner, Inventory inventory) {
        if (configManager.getBoolean("allow-toggle-equipment-drops")) {
            spawner.setAllowEquipmentItems(!spawner.isAllowEquipmentItems());

            StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();
            updatePageContent(player, spawner, holder.getCurrentPage(), inventory, true);
        }
    }

    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        String title = languageManager.getGuiTitle("gui-title.loot-menu");

        // Recalculate total pages to ensure consistency
        int totalPages = calculateTotalPages(spawner);

        // Ensure page is valid
        page = Math.max(1, Math.min(page, totalPages));

        // Check if player already has an open inventory cache
        UUID playerId = player.getUniqueId();
        Inventory existingInventory = openStorageInventories.get(playerId);

        // If the player already has an open inventory, update the content
        if (existingInventory != null && !refresh && existingInventory.getHolder() instanceof StoragePageHolder) {
            StoragePageHolder holder = (StoragePageHolder) existingInventory.getHolder();

            // Update holder with latest values
            holder.setTotalPages(totalPages);
            holder.setCurrentPage(page);
            holder.updateOldUsedSlots();

            updatePageContent(player, spawner, page, existingInventory, false);
            return;
        }

        // If there is no existing inventory or a refresh is needed, create a new inventory
        Inventory pageInventory = lootManager.createInventory(spawner, title, page, totalPages);

        // Store the inventory in the cache
        openStorageInventories.put(playerId, pageInventory);

        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);

        player.openInventory(pageInventory);
    }

    public void handleTakeAllItems(Player player, Inventory sourceInventory) {
        StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder();
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        Map<Integer, ItemStack> sourceItems = new HashMap<>();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = sourceInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                sourceItems.put(i, item.clone());
            }
        }

        if (sourceItems.isEmpty()) {
            languageManager.sendMessage(player, "messages.no-items-to-take");
            return;
        }

        TransferResult result = transferItems(player, sourceInventory, sourceItems, virtualInv);
        sendTransferMessage(player, result);
        player.updateInventory();
        int oldTotalPages = calculateTotalPages(spawner);

        // After items are taken, recalculate pages and update the UI
        if (result.anyItemMoved) {
            // Recalculate total pages
            int newTotalPages = calculateTotalPages(spawner);

            // Update holder
            holder.setTotalPages(newTotalPages);

            // Ensure current page is valid
            int currentPage = Math.min(holder.getCurrentPage(), newTotalPages);
            holder.setCurrentPage(currentPage);

            // Update the display and title
            spawnerGuiViewManager.updateStorageGuiViewers(spawner,oldTotalPages,newTotalPages);
            holder.updateOldUsedSlots();

            // Check if spawner is at capacity and update if necessary
            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.isAtCapacity()) {
                spawner.setAtCapacity(false);
            }
        }
    }

    private TransferResult transferItems(Player player, Inventory sourceInventory,
                                         Map<Integer, ItemStack> sourceItems, VirtualInventory virtualInv) {
        boolean anyItemMoved = false;
        boolean inventoryFull = false;
        PlayerInventory playerInv = player.getInventory();
        int totalAmountMoved = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<Integer, ItemStack> entry : sourceItems.entrySet()) {
            int sourceSlot = entry.getKey();
            ItemStack itemToMove = entry.getValue();

            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack targetItem = playerInv.getItem(i);

                if (targetItem == null || targetItem.getType() == Material.AIR) {
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    playerInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();
                    anyItemMoved = true;
                }
                else if (targetItem.isSimilar(itemToMove)) {
                    int spaceInStack = targetItem.getMaxStackSize() - targetItem.getAmount();
                    if (spaceInStack > 0) {
                        int addAmount = Math.min(spaceInStack, amountToMove);
                        targetItem.setAmount(targetItem.getAmount() + addAmount);
                        amountMoved += addAmount;
                        amountToMove -= addAmount;
                        anyItemMoved = true;
                    }
                }
            }

            if (amountMoved > 0) {
                totalAmountMoved += amountMoved;

                ItemStack movedItem = itemToMove.clone();
                movedItem.setAmount(amountMoved);
                itemsToRemove.add(movedItem);

                if (amountMoved == itemToMove.getAmount()) {
                    sourceInventory.setItem(sourceSlot, null);
                } else {
                    ItemStack remaining = itemToMove.clone();
                    remaining.setAmount(itemToMove.getAmount() - amountMoved);
                    sourceInventory.setItem(sourceSlot, remaining);
                    inventoryFull = true;
                }
            }

            if (inventoryFull) {
                break;
            }
        }

        if (!itemsToRemove.isEmpty()) {
            virtualInv.removeItems(itemsToRemove);
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder();
            holder.getSpawnerData().updateHologramData();
            holder.updateOldUsedSlots();
        }

        return new TransferResult(anyItemMoved, inventoryFull, totalAmountMoved);
    }

    private void sendTransferMessage(Player player, TransferResult result) {
        if (!result.anyItemMoved) {
            languageManager.sendMessage(player, "messages.inventory-full");
        } else if (result.inventoryFull) {
            languageManager.sendMessage(player, "messages.take-some-items",
                    "%amount%", String.valueOf(result.totalMoved));
        } else {
            languageManager.sendMessage(player, "messages.take-all-items",
                    "%amount%", String.valueOf(result.totalMoved));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StoragePageHolder)) {
            return;
        }

        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            openStorageInventories.remove(player.getUniqueId());
        }
    }

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}
}