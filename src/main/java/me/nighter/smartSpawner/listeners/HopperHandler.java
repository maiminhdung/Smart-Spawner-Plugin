package me.nighter.smartSpawner.listeners;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.managers.SpawnerLootManager;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.holders.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.utils.OptimizedVirtualInventory;
import me.nighter.smartSpawner.utils.SpawnerData;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public class HopperHandler implements Listener {
    private final SmartSpawner plugin;
    private final Map<Location, ScheduledTask> activeHoppers = new HashMap<>();
    private final Map<Location, Boolean> hopperPaused = new HashMap<>();
    private SpawnerManager spawnerManager;
    private SpawnerLootManager lootManager;
    private LanguageManager languageManager;
    private ConfigManager config;

    public HopperHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.lootManager = plugin.getLootManager();
        this.languageManager = plugin.getLanguageManager();
        this.config = plugin.getConfigManager();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            this.restartAllHoppers();
        }, 40L);
    }

    public void restartAllHoppers() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();

                // Thực thi trên RegionScheduler để đảm bảo quyền truy cập dữ liệu an toàn
                Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                    for (BlockState state : chunk.getTileEntities()) {
                        if (state.getType() == Material.HOPPER) {
                            Block hopperBlock = state.getBlock();
                            Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);

                            if (aboveBlock.getType() == Material.SPAWNER) {
                                startHopperTask(hopperBlock.getLocation(), aboveBlock.getLocation());
                            }
                        }
                    }
                });
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Khởi động lại hopper trong chunk mới load
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
        // Dừng hopper trong chunk bị unload
        Chunk chunk = event.getChunk();
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() == Material.HOPPER) {
                stopHopperTask(state.getLocation());
            }
        }
    }

    public void cleanup() {
        activeHoppers.values().forEach(ScheduledTask::cancel);
        activeHoppers.clear();
        hopperPaused.clear();
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof PagedSpawnerLootHolder) {
            PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) event.getInventory().getHolder();
            SpawnerData spawner = holder.getSpawnerData();
            Location spawnerLoc = spawner.getSpawnerLocation();
            Location hopperLoc = spawnerLoc.getBlock().getRelative(BlockFace.DOWN).getLocation();

            if (activeHoppers.containsKey(hopperLoc)) {
                hopperPaused.put(hopperLoc, true);
                if (event.getPlayer() instanceof Player) {
                    Player player = (Player) event.getPlayer();
                    languageManager.sendMessage(player, "messages.hopper-paused");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PagedSpawnerLootHolder) {
            PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) event.getInventory().getHolder();
            SpawnerData spawner = holder.getSpawnerData();
            Location spawnerLoc = spawner.getSpawnerLocation();
            Location hopperLoc = spawnerLoc.getBlock().getRelative(BlockFace.DOWN).getLocation();

            if (activeHoppers.containsKey(hopperLoc)) {
                hopperPaused.put(hopperLoc, false);
                if (event.getPlayer() instanceof Player) {
                    Player player = (Player) event.getPlayer();
                    languageManager.sendMessage(player, "messages.hopper-resumed");
                }
            }
        }
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
            hopperPaused.remove(event.getBlock().getLocation());
        }
    }

    public void startHopperTask(Location hopperLoc, Location spawnerLoc) {
        if (!config.isHopperEnabled()) return;

        // Nếu đã có task đang chạy thì không cần tạo mới
        if (activeHoppers.containsKey(hopperLoc)) return;
        Consumer<ScheduledTask> taskConsumer = task -> {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (!isValidSetup(hopperLoc, spawnerLoc)) {
                        stopHopperTask(hopperLoc);
                        return;
                    }

                    // Kiểm tra trạng thái pause
                    if (hopperPaused.getOrDefault(hopperLoc, false)) {
                        return;
                    }

                    transferItems(hopperLoc, spawnerLoc);
                }
            };
            runnable.run();
        };
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, taskConsumer, 1L, config.getHopperCheckInterval());
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
        ScheduledTask task = activeHoppers.remove(hopperLoc);
        if (task != null) {
            task.cancel();
        }
    }

    private void transferItems(Location hopperLoc, Location spawnerLoc) {
        // Get SpawnerData from location
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(spawnerLoc);
        if (spawner == null) return;

        OptimizedVirtualInventory virtualInv = spawner.getVirtualInventory();
        Hopper hopper = (Hopper) hopperLoc.getBlock().getState();

        int itemsPerTransfer = config.getHopperItemsPerTransfer();
        int transferred = 0;
        boolean inventoryChanged = false;

        // Get current display inventory to work with
        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Process items for transfer
        for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
            if (transferred >= itemsPerTransfer) break;

            ItemStack item = entry.getValue();
            if (item == null || item.getType() == Material.AIR) continue;

            // Find empty slot in hopper
            ItemStack[] hopperContents = hopper.getInventory().getContents();
            for (int i = 0; i < hopperContents.length; i++) {
                if (transferred >= itemsPerTransfer) break;

                ItemStack hopperItem = hopperContents[i];
                if (hopperItem == null || hopperItem.getType() == Material.AIR) {
                    // Transfer whole stack
                    hopper.getInventory().setItem(i, item.clone());
                    itemsToRemove.add(item);
                    transferred++;
                    inventoryChanged = true;
                    break;
                } else if (hopperItem.isSimilar(item) &&
                        hopperItem.getAmount() < hopperItem.getMaxStackSize()) {
                    // Stack with existing items
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

        // Remove transferred items from virtual inventory
        if (!itemsToRemove.isEmpty()) {
            virtualInv.removeItems(itemsToRemove);
        }

        if (inventoryChanged) {
            updateOpenGuis(spawner);
        }
    }

    // Optional: Method to manually start/stop hopper tasks (useful for reloads)
    public void checkAllHoppers() {
        plugin.getServer().getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Hopper) {
                        Block above = state.getBlock().getRelative(BlockFace.UP);
                        if (above.getType() == Material.SPAWNER) {
                            startHopperTask(state.getLocation(), above.getLocation());
                        }
                    }
                }
            }
        });
    }

    private void updateOpenGuis(SpawnerData spawner) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            // Cập nhật cho người chơi đang xem inventory
            for (HumanEntity viewer : getViewersForSpawner(spawner)) {
                if (viewer instanceof Player) {
                    Player player = (Player) viewer;
                    Inventory currentInv = player.getOpenInventory().getTopInventory();
                    if (currentInv.getHolder() instanceof PagedSpawnerLootHolder) {
                        PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) currentInv.getHolder();
                        int currentPage = holder.getCurrentPage();
                        // Tạo inventory mới với data mới nhất
                        Inventory newInv = lootManager.createLootInventory(spawner,
                                languageManager.getGuiTitle("gui-title.loot-menu"), currentPage);
                        // Copy items từ inventory mới sang inventory cũ
                        for (int i = 0; i < newInv.getSize(); i++) {
                            currentInv.setItem(i, newInv.getItem(i));
                        }
                        player.updateInventory();
                    }
                }
            }

            // Cập nhật cho người chơi đang xem GUI spawner
            Map<UUID, SpawnerData> openGuis = spawnerManager.getOpenSpawnerGuis();
            for (Map.Entry<UUID, SpawnerData> entry : openGuis.entrySet()) {
                if (entry.getValue().getSpawnerId().equals(spawner.getSpawnerId())) {
                    Player viewer = Bukkit.getPlayer(entry.getKey());
                    if (viewer != null && viewer.isOnline()) {
                        spawnerManager.updateSpawnerGui(viewer, spawner, true);
                    }
                }
            }
        });
    }

    private List<HumanEntity> getViewersForSpawner(SpawnerData spawner) {
        List<HumanEntity> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory openInv = player.getOpenInventory().getTopInventory();
            if (openInv.getHolder() instanceof PagedSpawnerLootHolder) {
                PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) openInv.getHolder();
                if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
                    viewers.add(player);
                }
            }
        }
        return viewers;
    }
}