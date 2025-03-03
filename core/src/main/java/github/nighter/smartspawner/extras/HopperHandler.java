package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class HopperHandler implements Listener {
    private final SmartSpawner plugin;
    private final Map<Location, BukkitTask> activeHoppers = new ConcurrentHashMap<>();
    private final SpawnerManager spawnerManager;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final Map<String, ReentrantLock> spawnerLocks = new ConcurrentHashMap<>();

    public HopperHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiManager();
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::restartAllHoppers, 40L);
    }

    public void restartAllHoppers() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state.getType() == Material.HOPPER) {
                        Block hopperBlock = state.getBlock();
                        Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);

                        if (aboveBlock.getType() == Material.SPAWNER) {
                            startHopperTask(hopperBlock.getLocation(), aboveBlock.getLocation());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() == Material.HOPPER) {
                Block hopperBlock = state.getBlock();
                Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);
                if (aboveBlock.getType() == Material.SPAWNER) {
                    startHopperTask(hopperBlock.getLocation(), aboveBlock.getLocation());
                }
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() == Material.HOPPER) {
                stopHopperTask(state.getLocation());
            }
        }
    }

    public void cleanup() {
        activeHoppers.values().forEach(BukkitTask::cancel);
        activeHoppers.clear();
        spawnerLocks.clear();
    }

    @EventHandler
    public void onHopperPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) return;

        Block above = event.getBlockPlaced().getRelative(BlockFace.UP);
        if (above.getType() == Material.SPAWNER) {
            startHopperTask(event.getBlockPlaced().getLocation(), above.getLocation());
        }
    }

    @EventHandler
    public void onHopperBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            stopHopperTask(event.getBlock().getLocation());
        }
    }

    private ReentrantLock getOrCreateLock(SpawnerData spawner) {
        return spawnerLocks.computeIfAbsent(spawner.getSpawnerId(), k -> new ReentrantLock());
    }

    public void startHopperTask(Location hopperLoc, Location spawnerLoc) {
        if (!configManager.getBoolean("hopper-enabled")) return;
        if (activeHoppers.containsKey(hopperLoc)) return;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValidSetup(hopperLoc, spawnerLoc)) {
                    stopHopperTask(hopperLoc);
                    return;
                }
                transferItems(hopperLoc, spawnerLoc);
            }
        }.runTaskTimer(plugin, 0L, configManager.getInt("hopper-check-interval") * 20L); // Convert seconds to ticks

        activeHoppers.put(hopperLoc, task);
    }

    private boolean isValidSetup(Location hopperLoc, Location spawnerLoc) {
        Block hopper = hopperLoc.getBlock();
        Block spawner = spawnerLoc.getBlock();

        return hopper.getType() == Material.HOPPER &&
                spawner.getType() == Material.SPAWNER &&
                hopper.getRelative(BlockFace.UP).equals(spawner);
    }

    public void stopHopperTask(Location hopperLoc) {
        BukkitTask task = activeHoppers.remove(hopperLoc);
        if (task != null) {
            task.cancel();
        }
    }

    private void transferItems(Location hopperLoc, Location spawnerLoc) {
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(spawnerLoc);
        if (spawner == null) return;

        ReentrantLock lock = getOrCreateLock(spawner);
        if (!lock.tryLock()) return; // Skip this tick if we can't get the lock

        try {
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            Hopper hopper = (Hopper) hopperLoc.getBlock().getState();

            int itemsPerTransfer = configManager.getInt("hopper-items-per-transfer");
            int transferred = 0;
            boolean inventoryChanged = false;

            Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
            List<ItemStack> itemsToRemove = new ArrayList<>();

            for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
                if (transferred >= itemsPerTransfer) break;

                ItemStack item = entry.getValue();
                if (item == null || item.getType() == Material.AIR) continue;

                ItemStack[] hopperContents = hopper.getInventory().getContents();
                for (int i = 0; i < hopperContents.length; i++) {
                    if (transferred >= itemsPerTransfer) break;

                    ItemStack hopperItem = hopperContents[i];
                    if (hopperItem == null || hopperItem.getType() == Material.AIR) {
                        hopper.getInventory().setItem(i, item.clone());
                        itemsToRemove.add(item);
                        transferred++;
                        inventoryChanged = true;
                        break;
                    } else if (hopperItem.isSimilar(item) &&
                            hopperItem.getAmount() < hopperItem.getMaxStackSize()) {
                        int space = hopperItem.getMaxStackSize() - hopperItem.getAmount();
                        int toTransfer = Math.min(space, item.getAmount());

                        hopperItem.setAmount(hopperItem.getAmount() + toTransfer);

                        ItemStack toRemove = item.clone();
                        toRemove.setAmount(toTransfer);
                        itemsToRemove.add(toRemove);

                        transferred++;
                        inventoryChanged = true;
                        break;
                    }
                }
            }

            if (!itemsToRemove.isEmpty()) {
                virtualInv.removeItems(itemsToRemove);
            }

            if (inventoryChanged) {
                updateOpenGuis(spawner);
            }
        } finally {
            lock.unlock();
        }
    }

    private void updateOpenGuis(SpawnerData spawner) {
        // Keep track of the old used slots for page calculation
        int oldUsedSlots = spawner.getVirtualInventory().getUsedSlots();

        // Batch update - run every 2 ticks
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Calculate total pages before and after the inventory change
            int oldTotalPages = (int) Math.ceil((double) oldUsedSlots / 45); // Using same ITEMS_PER_PAGE constant as in SpawnerGuiManager
            int newTotalPages = (int) Math.ceil((double) spawner.getVirtualInventory().getUsedSlots() / 45);

            // Update all storage GUI viewers
            spawnerGuiViewManager.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);

            // Update all spawner menu GUI viewers
            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
        }, 2L);
    }
}