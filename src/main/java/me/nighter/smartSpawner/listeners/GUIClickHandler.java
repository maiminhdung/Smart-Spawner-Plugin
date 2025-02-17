package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.spawner.properties.VirtualInventory;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import me.nighter.smartSpawner.managers.SpawnerLootManager;
import me.nighter.smartSpawner.holders.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class GUIClickHandler implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    public GUIClickHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player) || !(event.getInventory().getHolder() instanceof PagedSpawnerLootHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();
        int slot = event.getRawSlot();
        int inventorySize = event.getInventory().getSize();

        if (slot < 0 || slot >= inventorySize) {
            event.setCancelled(true);
            return;
        }

        if (isControlSlot(slot)) {
            event.setCancelled(true);
            handleSlotClick(player, slot, holder, spawner, event.getInventory());
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        switch (event.getClick()) {
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                takeAllSimilarItems(player, event.getInventory(), clickedItem, spawner);
                break;
            case RIGHT:
                takeSingleItem(player, event.getInventory(), slot, clickedItem, spawner, true);
                break;
            case LEFT:
                takeSingleItem(player, event.getInventory(), slot, clickedItem, spawner, false);
                break;
            default:
                break;
        }
    }

    private boolean isControlSlot(int slot) {
        return slot == 45 || slot == 46 || slot == 48 || slot == 49 || slot == 50 || slot == 53;
    }

    private void takeSingleItem(Player player, Inventory sourceInv, int slot, ItemStack item, SpawnerData spawner, boolean singleItem) {
        int amountToMove = singleItem ? 1 : item.getAmount();
        int amountMoved = 0;
        PlayerInventory playerInv = player.getInventory();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // Try to add to player inventory
        for (int i = 0; i < 36 && amountToMove > 0; i++) {
            ItemStack current = playerInv.getItem(i);

            if (current == null || current.getType() == Material.AIR) {
                ItemStack newStack = item.clone();
                newStack.setAmount(Math.min(amountToMove, item.getMaxStackSize()));
                playerInv.setItem(i, newStack);
                amountMoved += newStack.getAmount();
                amountToMove -= newStack.getAmount();
                break;
            } else if (current.isSimilar(item)) {
                int spaceInStack = current.getMaxStackSize() - current.getAmount();
                if (spaceInStack > 0) {
                    int addAmount = Math.min(spaceInStack, amountToMove);
                    current.setAmount(current.getAmount() + addAmount);
                    amountMoved += addAmount;
                    amountToMove -= addAmount;
                    if (amountToMove <= 0) break;
                }
            }
        }

        if (amountMoved > 0) {
            // Update source inventory display
            if (amountMoved == item.getAmount()) {
                sourceInv.setItem(slot, null);
            } else {
                ItemStack remaining = item.clone();
                remaining.setAmount(item.getAmount() - amountMoved);
                sourceInv.setItem(slot, remaining);
            }

            // Xóa vật phẩm khỏi Virtual Inventory
            ItemStack itemToRemove = item.clone();
            itemToRemove.setAmount(amountMoved);
            virtualInv.removeItems(Collections.singletonList(itemToRemove));

            // Đảm bảo dữ liệu được cập nhật ngay lập tức
            spawner.setVirtualInventory(virtualInv);

            // Cập nhật GUI cho tất cả người chơi
            refreshSpawnerMenuForAllPlayers(spawner);
        } else {
            languageManager.sendMessage(player, "messages.inventory-full");
        }
    }

    private void takeAllSimilarItems(Player player, Inventory sourceInv, ItemStack targetItem, SpawnerData spawner) {
        Map<Integer, ItemStack> similarItems = new HashMap<>();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // Find all similar items
        for (int i = 0; i < 45; i++) {
            ItemStack invItem = sourceInv.getItem(i);
            if (invItem != null && invItem.isSimilar(targetItem)) {
                similarItems.put(i, invItem.clone());
            }
        }

        if (similarItems.isEmpty()) return;

        PlayerInventory playerInv = player.getInventory();
        int totalMoved = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<Integer, ItemStack> entry : similarItems.entrySet()) {
            ItemStack itemToMove = entry.getValue();
            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack current = playerInv.getItem(i);

                if (current == null || current.getType() == Material.AIR) {
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    playerInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();
                } else if (current.isSimilar(itemToMove)) {
                    int spaceInStack = current.getMaxStackSize() - current.getAmount();
                    if (spaceInStack > 0) {
                        int addAmount = Math.min(spaceInStack, amountToMove);
                        current.setAmount(current.getAmount() + addAmount);
                        amountMoved += addAmount;
                        amountToMove -= addAmount;
                    }
                }
            }

            if (amountMoved > 0) {
                totalMoved += amountMoved;
                // Update display inventory
                if (amountMoved == itemToMove.getAmount()) {
                    sourceInv.setItem(entry.getKey(), null);
                } else {
                    ItemStack remaining = itemToMove.clone();
                    remaining.setAmount(itemToMove.getAmount() - amountMoved);
                    sourceInv.setItem(entry.getKey(), remaining);
                }

                // Track items to remove from virtual inventory
                ItemStack movedItem = itemToMove.clone();
                movedItem.setAmount(amountMoved);
                itemsToRemove.add(movedItem);
            }

            if (amountToMove > 0) {
                languageManager.sendMessage(player, "messages.inventory-full");
                break;
            }
        }

        if (!itemsToRemove.isEmpty()) {
            virtualInv.removeItems(itemsToRemove);
            player.updateInventory();
        }

        refreshSpawnerMenuForAllPlayers(spawner);
    }

    //---------------------------------------------------
    // Spawner Loot Menu Click Handler
    //---------------------------------------------------
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PagedSpawnerLootHolder) {
            event.setCancelled(true);
        }
    }

    // Handles player interaction with specific slots in the inventory
    private void handleSlotClick(Player player, int slot, PagedSpawnerLootHolder holder,
                                 SpawnerData spawner, Inventory inventory) {
        switch (slot) {
            case 48: // Navigate to the previous page
                if (holder.getCurrentPage() > 1) {
                    openLootPage(player, spawner, holder.getCurrentPage() - 1, false);
                }
                break;

            case 50: // Navigate to the next page
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    openLootPage(player, spawner, holder.getCurrentPage() + 1, false);
                }
                break;

            case 49: // Sell all items (requires economy integration)
                if (plugin.hasShopIntegration()) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

                    if (!player.hasPermission("smartspawner.sellall")) {
                        player.sendMessage(languageManager.getMessage("no-permission"));
                    } else if (plugin.getShopIntegration().sellAllItems(player, spawner)) {
                        openLootPage(player, spawner, holder.getCurrentPage(), true);
                        refreshSpawnerMenuForAllPlayers(spawner);
                    }
                }
                break;


            case 53: // Open the main spawner menu
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getSpawnerListener().openSpawnerMenu(player, spawner, true);
                break;

            case 45: // Take all items from the inventory
                handleTakeAllItems(player, inventory);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                break;

            case 46: // Toggle equipment items on or off
                if (configManager.isAllowToggleEquipmentItems()) {
                    spawner.setAllowEquipmentItems(!spawner.isAllowEquipmentItems());
                    openLootPage(player, spawner, holder.getCurrentPage(), true);
                }
                break;

            default: // No action for other slots
                break;
        }
    }

    // Opens the loot inventory for the specified page
    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerLootManager lootManager = plugin.getLootManager();
        String title = languageManager.getGuiTitle("gui-title.loot-menu");
        Inventory pageInventory = lootManager.createLootInventory(spawner, title, page);

        // Play appropriate sound based on the action
        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);

        // Open the inventory for the player
        player.openInventory(pageInventory);
    }

    public void handleTakeAllItems(Player player, Inventory sourceInventory) {
        PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) sourceInventory.getHolder();
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // Collect all non-null and non-air items
        Map<Integer, ItemStack> sourceItems = new HashMap<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = sourceInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                sourceItems.put(i, item.clone());
            }
        }

        if (sourceItems.isEmpty()) {
            languageManager.sendMessage(player, "messages.no-items-to-take");
            return;
        }

        // Try to transfer items
        boolean anyItemMoved = false;
        boolean inventoryFull = false;
        PlayerInventory playerInv = player.getInventory();
        int totalAmountMoved = 0;

        // Track items that were successfully moved
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Process each source slot
        for (Map.Entry<Integer, ItemStack> entry : sourceItems.entrySet()) {
            int sourceSlot = entry.getKey();
            ItemStack itemToMove = entry.getValue();

            // Try to partially add items
            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            // Check each inventory slot for partial stacking
            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack targetItem = playerInv.getItem(i);

                if (targetItem == null || targetItem.getType() == Material.AIR) {
                    // Empty slot - can take full stack or remaining amount
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    playerInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();
                    anyItemMoved = true;
                }
                else if (targetItem.isSimilar(itemToMove)) {
                    // Similar item - can stack
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

            // Update tracking of items to remove
            if (amountMoved > 0) {
                totalAmountMoved += amountMoved;

                // Create an item stack for the amount that was actually moved
                ItemStack movedItem = itemToMove.clone();
                movedItem.setAmount(amountMoved);
                itemsToRemove.add(movedItem);

                // Update source inventory
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

        // Remove successfully moved items from virtual inventory
        if (!itemsToRemove.isEmpty()) {
            virtualInv.removeItems(itemsToRemove);
            spawner.updateHologramData();
        }

        // Send appropriate message
        if (!anyItemMoved) {
            languageManager.sendMessage(player, "messages.inventory-full");
        } else if (inventoryFull) {
            languageManager.sendMessage(player, "messages.take-some-items", "%amount%", String.valueOf(totalAmountMoved));
        } else {
            languageManager.sendMessage(player, "messages.take-all-items", "%amount%", String.valueOf(totalAmountMoved));
        }

        refreshSpawnerMenuForAllPlayers(spawner);
    }

    // Refreshes the spawner menu for all players
    private void refreshSpawnerMenuForAllPlayers(SpawnerData spawner) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PagedSpawnerLootHolder) {
                PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) player.getOpenInventory().getTopInventory().getHolder();
                if (holder.getSpawnerData().equals(spawner)) {
                    openLootPage(player, spawner, holder.getCurrentPage(), true);
                }
            }
        }
    }
}
