package github.nighter.smartspawner.spawner.gui.synchronization;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.SpawnerMenuHolder;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.ItemUpdater;
import github.nighter.smartspawner.utils.LanguageManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Unified controller responsible for managing all spawner GUI interactions and updates.
 * Handles the tracking, updating, and synchronization of open spawner GUI interfaces.
 */
public class SpawnerGuiViewManager implements Listener {
    private static final long UPDATE_INTERVAL_TICKS = 10L;
    private static final long INITIAL_DELAY_TICKS = 10L;
    private static final int ITEMS_PER_PAGE = 45; // Standard chest inventory size minus navigation items

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerStorageUI spawnerStorageUI;

    // Data structures to track viewers
    private final Map<UUID, SpawnerData> playerToSpawnerMap; // Player UUID -> SpawnerData
    private final Map<String, Set<UUID>> spawnerToPlayersMap; // SpawnerID -> Set of Player UUIDs
    private final Set<Class<? extends InventoryHolder>> validHolderTypes;

    private BukkitTask updateTask;
    private volatile boolean isTaskRunning;
    private long previousExpValue = 0;

    public SpawnerGuiViewManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerStorageUI = new SpawnerStorageUI(plugin);
        this.playerToSpawnerMap = new ConcurrentHashMap<>();
        this.spawnerToPlayersMap = new ConcurrentHashMap<>();
        this.isTaskRunning = false;
        this.validHolderTypes = Set.of(
                SpawnerMenuHolder.class,
                StoragePageHolder.class
        );
    }

    // ===============================================================
    //                      Task Management
    // ===============================================================

    private synchronized void startUpdateTask() {
        if (isTaskRunning) {
            return;
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateGuiForSpawnerInfo,
                INITIAL_DELAY_TICKS, UPDATE_INTERVAL_TICKS);
        isTaskRunning = true;

//        if (configManager.isDebugEnabled()) {
//            plugin.getLogger().info("Started GUI update task");
//        }
    }

    public synchronized void stopUpdateTask() {
        if (!isTaskRunning) {
            return;
        }

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        isTaskRunning = false;

//        if (configManager.isDebugEnabled()) {
//            plugin.getLogger().info("Stopped GUI update task");
//        }
    }

    private boolean isValidGuiSession(Player player) {
        return player != null && player.isOnline();
    }

    private boolean isValidHolder(InventoryHolder holder) {
        return holder != null && validHolderTypes.contains(holder.getClass());
    }

    // ===============================================================
    //                      Viewer Tracking
    // ===============================================================

    public void trackViewer(UUID playerId, SpawnerData spawner) {
        playerToSpawnerMap.put(playerId, spawner);
        spawnerToPlayersMap.computeIfAbsent(spawner.getSpawnerId(), k -> ConcurrentHashMap.newKeySet())
                .add(playerId);

        if (!isTaskRunning) {
            startUpdateTask();
        }
    }

    public void untrackViewer(UUID playerId) {
        SpawnerData spawner = playerToSpawnerMap.remove(playerId);
        if (spawner != null) {
            Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
            if (viewers != null) {
                viewers.remove(playerId);
                if (viewers.isEmpty()) {
                    spawnerToPlayersMap.remove(spawner.getSpawnerId());
                }
            }
        }

        // Check if we need to stop the update task
        if (playerToSpawnerMap.isEmpty()) {
            stopUpdateTask();
        }
    }

    public Set<Player> getViewers(String spawnerId) {
        Set<UUID> viewerIds = spawnerToPlayersMap.get(spawnerId);
        if (viewerIds == null || viewerIds.isEmpty()) {
            return Collections.emptySet();
        }

        return viewerIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toSet());
    }

    public boolean hasViewers(SpawnerData spawner) {
        Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
        return viewers != null && !viewers.isEmpty();
    }

    public void clearAllTrackedGuis() {
        playerToSpawnerMap.clear();
        spawnerToPlayersMap.clear();
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Cleared all tracked spawner GUIs");
        }
    }

    // ===============================================================
    //                      Event Handlers
    // ===============================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!isValidHolder(holder)) return;

        UUID playerId = player.getUniqueId();
        SpawnerData spawnerData = null;

        if (holder instanceof SpawnerMenuHolder spawnerHolder) {
            spawnerData = spawnerHolder.getSpawnerData();
        } else if (holder instanceof StoragePageHolder storageHolder) {
            spawnerData = storageHolder.getSpawnerData();
        }

        if (spawnerData != null) {
            trackViewer(playerId, spawnerData);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Use runTask to check if player has another valid inventory open after closing this one
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            Inventory openInv = player.getOpenInventory().getTopInventory();
            if (openInv == null || !isValidHolder(openInv.getHolder())) {
                untrackViewer(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        untrackViewer(event.getPlayer().getUniqueId());
    }

    // ===============================================================
    //                      Update Logic
    // ===============================================================

    private void updateGuiForSpawnerInfo() {
        if (playerToSpawnerMap.isEmpty()) {
            stopUpdateTask();
            return;
        }

        // Use iterator to avoid ConcurrentModificationException
        Iterator<Map.Entry<UUID, SpawnerData>> iterator = playerToSpawnerMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnerData> entry = iterator.next();
            UUID playerId = entry.getKey();
            SpawnerData spawner = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);

            if (!isValidGuiSession(player)) {
                iterator.remove();
                untrackViewer(playerId);
                continue;
            }

            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (openInventory.getHolder() instanceof SpawnerMenuHolder) {
                if (!spawner.isAtCapacity()) {
                    updateSpawnerGuiInfo(player, spawner, false);
                }
            } else if (!(openInventory.getHolder() instanceof StoragePageHolder)) {
                // If inventory is neither SpawnerMenuHolder nor StoragePageHolder, untrack
                iterator.remove();
                untrackViewer(playerId);
            }
        }
    }

    // ===============================================================
    //                      Spawner Main GUI Update
    // ===============================================================

    public void updateSpawnerMenuViewers(SpawnerData spawner) {
        Set<Player> viewers = getViewers(spawner.getSpawnerId());
        if (viewers.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player viewer : viewers) {
                if (!viewer.isOnline()) continue;

                Inventory openInv = viewer.getOpenInventory().getTopInventory();
                if (openInv == null) continue;

                InventoryHolder holder = openInv.getHolder();
                if (holder instanceof SpawnerMenuHolder) {
                    updateSpawnerMenuGui(viewer, spawner, true);
                } else if (holder instanceof StoragePageHolder) {
                    StoragePageHolder storageHolder = (StoragePageHolder) holder;
                    int oldTotalPages = calculateTotalPages(storageHolder.getOldUsedSlots());
                    int newTotalPages = calculateTotalPages(spawner.getVirtualInventory().getUsedSlots());
                    updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
                }
            }
        });
    }

    private int calculateTotalPages(int totalItems) {
        return (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
    }

    public void updateSpawnerMenuGui(Player player, SpawnerData spawner, boolean forceUpdate) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof SpawnerMenuHolder) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) openInv.getHolder();
            if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId()) || forceUpdate) {
                updateSpawnerInfoItem(openInv, spawner);
                updateExpItem(openInv, spawner);
                updateChestItem(openInv, spawner);
                player.updateInventory();
            }
        }
    }

    public void updateSpawnerGuiInfo(Player player, SpawnerData spawner, boolean forceUpdate) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof SpawnerMenuHolder) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) openInv.getHolder();
            if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId()) || forceUpdate) {
                updateSpawnerInfoItem(openInv, spawner);
                player.updateInventory();
            }
        }
    }

    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner) {
        ItemStack spawnerItem = inventory.getItem(13);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) return;

        ItemMeta meta = spawnerItem.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        String timeDisplay = getTimeDisplay(timeUntilNextSpawn);

        String nextSpawnTemplate = languageManager.getMessage("spawner-info-item.lore-change");
        String strippedTemplate = ChatColor.stripColor(nextSpawnTemplate);

        int lineIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            String strippedLine = ChatColor.stripColor(lore.get(i));
            if (strippedLine.startsWith(ChatColor.stripColor(strippedTemplate))) {
                lineIndex = i;
                break;
            }
        }
        // Check if spawner is at capacity and display message
        if (spawner.isAtCapacity()){
            timeDisplay = languageManager.getMessage("spawner-info-item.lore-full");
        }
        String newLine = nextSpawnTemplate + timeDisplay;
        if (lineIndex >= 0) {
            if (!lore.get(lineIndex).equals(newLine)) {
                ItemUpdater.updateLoreLine(spawnerItem, lineIndex, newLine);
            }
        } else {
            lore.add(newLine);
            ItemUpdater.updateLore(spawnerItem, lore);
        }
    }

    private String getTimeDisplay(long timeUntilNextSpawn) {
        if (timeUntilNextSpawn == -1) {
            return languageManager.getMessage("spawner-info-item.lore-inactive");
        } else if (timeUntilNextSpawn <= 0) {
            return formatTime(timeUntilNextSpawn);
        }
        return formatTime(timeUntilNextSpawn);
    }

    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop()) {
            return -1;
        }

        // Cache spawn delay calculation
        if (spawner.getCachedSpawnDelay() == 0) {
            spawner.setCachedSpawnDelay(spawner.getSpawnDelay() * 50L);
        }

        long currentTime = System.currentTimeMillis();
        long lastSpawnTime = spawner.getLastSpawnTime();
        long cachedDelay = spawner.getCachedSpawnDelay();
        long timeUntilNextSpawn = lastSpawnTime + cachedDelay - currentTime;
        if (timeUntilNextSpawn < 0) {
            spawner.getLock().lock();
            try {
                if (System.currentTimeMillis() - spawner.getLastSpawnTime() > cachedDelay) {
                    spawner.setLastSpawnTime(currentTime - cachedDelay);
                    plugin.getRangeChecker().activateSpawner(spawner);
                    return 0;
                }
            } finally {
                spawner.getLock().unlock();
            }
        }

        return timeUntilNextSpawn;
    }


    private String formatTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "00:00";
        }
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateChestItem(Inventory inventory, SpawnerData spawner) {
        ItemStack chestItem = inventory.getItem(11);
        if (chestItem == null || !chestItem.hasItemMeta()) return;

        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = (int) ((double) currentItems / maxSlots * 100);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("%max_slots%", String.valueOf(maxSlots));
        replacements.put("%current_items%", String.valueOf(currentItems));
        replacements.put("%percent_storage%", String.valueOf(percentStorage));

        String name = languageManager.getMessage("spawner-loot-item.name");
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest", replacements);
        List<String> chestLore = Arrays.asList(loreMessageChest.split("\n"));

        ItemUpdater.updateItemMeta(chestItem, name, chestLore);
    }

    private void updateExpItem(Inventory inventory, SpawnerData spawner) {
        ItemStack expItem = inventory.getItem(15);
        if (expItem == null || !expItem.hasItemMeta()) return;

        long currentExp = spawner.getSpawnerExp();
        if (currentExp != previousExpValue) {
            Map<String, String> nameReplacements = new HashMap<>();
            String formattedExp = languageManager.formatNumber(currentExp);
            String formattedMaxExp = languageManager.formatNumber(spawner.getMaxStoredExp());
            int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

            nameReplacements.put("%current_exp%", String.valueOf(spawner.getSpawnerExp()));
            String name = languageManager.getMessage("exp-info-item.name", nameReplacements);

            Map<String, String> loreReplacements = new HashMap<>();
            loreReplacements.put("%current_exp%", formattedExp);
            loreReplacements.put("%max_exp%", formattedMaxExp);
            loreReplacements.put("%percent_exp%", String.valueOf(percentExp));
            loreReplacements.put("%u_max_exp%", String.valueOf(spawner.getMaxStoredExp()));

            String lorePathExp = "exp-info-item.lore.exp-bottle";
            String loreMessageExp = languageManager.getMessage(lorePathExp, loreReplacements);
            List<String> loreExp = Arrays.asList(loreMessageExp.split("\n"));

            ItemUpdater.updateItemMeta(expItem, name, loreExp);
            previousExpValue = currentExp;
        }
    }

    // ===============================================================
    //                      Storage GUI Update
    // ===============================================================

    private record UpdateAction(Player player, SpawnerData spawner, int page, int totalPages, boolean requiresNewInventory) {}

    public void updateStorageGuiViewers(SpawnerData spawner, int oldTotalPages, int newTotalPages) {
        // Check if total pages changed
        boolean pagesChanged = oldTotalPages != newTotalPages;

        // Batch all viewers that need updates
        List<UpdateAction> updateQueue = new ArrayList<>();

        Set<Player> viewers = getViewers(spawner.getSpawnerId());
        for (Player player : viewers) {
            if (!player.isOnline()) continue;

            Inventory currentInv = player.getOpenInventory().getTopInventory();
            if (currentInv.getHolder() instanceof StoragePageHolder) {
                StoragePageHolder holder = (StoragePageHolder) currentInv.getHolder();
                int currentPage = holder.getCurrentPage();

                boolean needsNewInventory = false;
                int targetPage = currentPage;

                // Determine if we need a new inventory
                if (currentPage > newTotalPages) {
                    // if current page is out of bounds, set to last page
                    targetPage = newTotalPages;
                    holder.setCurrentPage(targetPage);
                    needsNewInventory = true;
                } else if (pagesChanged) {
                    // If total pages changed but current page is still valid, update current page
                    needsNewInventory = true;
                }

                updateQueue.add(new UpdateAction(player, spawner, targetPage, newTotalPages, needsNewInventory));
            }
        }

        // Process all updates in one server tick
        if (!updateQueue.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UpdateAction action : updateQueue) {
                    if (action.requiresNewInventory) {
                        try {
                            // Update inventory title and contents
                            String newTitle = languageManager.getGuiTitle("gui-title.loot-menu") + " - [" + action.page + "/" + action.totalPages + "]";
                            action.player.getOpenInventory().setTitle(newTitle);
                            Inventory currentInv = action.player.getOpenInventory().getTopInventory();
                            spawnerStorageUI.updateDisplay(currentInv, action.spawner, action.page, action.totalPages);
                        } catch (Exception e) {
                            // Fall back to creating a new inventory
                            Inventory newInv = spawnerStorageUI.createInventory(
                                    action.spawner,
                                    languageManager.getGuiTitle("gui-title.loot-menu"),
                                    action.page,
                                    action.totalPages
                            );
                            action.player.closeInventory();
                            action.player.openInventory(newInv);
                        }
                    } else {
                        // Just update contents of current inventory
                        Inventory currentInv = action.player.getOpenInventory().getTopInventory();
                        spawnerStorageUI.updateDisplay(currentInv, action.spawner, action.page, action.totalPages);
                        action.player.updateInventory();
                    }
                }
            });
        }
    }

    // ===============================================================
    //                      Utility Methods
    // ===============================================================

    public void closeAllViewersInventory(SpawnerData spawner) {
        String spawnerId = spawner.getSpawnerId();
        Set<Player> viewers = getViewers(spawnerId);
        if (!viewers.isEmpty()) {
            for (Player viewer : viewers) {
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                }
            }
        }

        // Check for stacker viewers if that functionality exists
        if (plugin.getSpawnerStackerHandler() != null) {
            plugin.getSpawnerStackerHandler().closeAllViewersInventory(spawnerId);
        }
    }

    public void cleanup() {
        stopUpdateTask();
        clearAllTrackedGuis();
    }
}