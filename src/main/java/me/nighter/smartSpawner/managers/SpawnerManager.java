package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.serializers.ItemStackSerializer;
import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.nms.ParticleWrapper;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.holders.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.utils.OptimizedVirtualInventory;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.nms.ParticleWrapper;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager {
    private final SmartSpawner plugin;
    private Map<String, SpawnerData> spawners = new HashMap<>();
    private Map<LocationKey, SpawnerData> locationIndex = new HashMap<>();
    private long previousExpValue = 0;
    private File spawnerDataFile;
    private FileConfiguration spawnerData;
    private final SpawnerLootGenerator spawnerLootGenerator;
    private final SpawnerLootManager lootManager;
    private final Map<UUID, SpawnerData> openSpawnerGuis = new ConcurrentHashMap<>();
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    public SpawnerManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerLootGenerator = plugin.getLootGenerator();
        this.lootManager = plugin.getLootManager();
        setupSpawnerDataFile();
        startSaveTask();
    }

    private static class LocationKey {
        private final String world;
        private final int x, y, z;

        public LocationKey(Location location) {
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LocationKey)) return false;
            LocationKey that = (LocationKey) o;
            return x == that.x &&
                    y == that.y &&
                    z == that.z &&
                    world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, y, z);
        }
    }

    // Add methods to maintain the location index
    public void addSpawner(String id, SpawnerData spawner) {
        spawners.put(id, spawner);
        locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);
    }

    public void removeSpawner(String id) {
        SpawnerData spawner = spawners.get(id);
        if (spawner != null) {
            locationIndex.remove(new LocationKey(spawner.getSpawnerLocation()));
            spawners.remove(id);

        }
        deleteSpawnerFromFile(id);
    }

    public void deleteSpawnerFromFile(String spawnerId) {
        try {
            // Xóa toàn bộ section của spawner đó
            String path = "spawners." + spawnerId;
            spawnerData.set(path, null);

            // Lưu file
            spawnerData.save(spawnerDataFile);

            // Xóa khỏi cache maps
            SpawnerData removedSpawner = spawners.remove(spawnerId);
            if (removedSpawner != null) {
                locationIndex.remove(new LocationKey(removedSpawner.getSpawnerLocation()));
            }

            configManager.debug("Successfully deleted spawner " + spawnerId + " from data file");

        } catch (IOException e) {
            plugin.getLogger().severe("Could not delete spawner " + spawnerId + " from spawners_data.yml!");
            e.printStackTrace();
        }
    }

    // Add efficient location lookup method
    public SpawnerData getSpawnerByLocation(Location location) {
        return locationIndex.get(new LocationKey(location));
    }

    public SpawnerData getSpawnerById(String id) {
        return spawners.get(id);
    }

    public void setupSpawnerDataFile() {
        // Tạo file spawners_data.yml nếu chưa tồn tại
        spawnerDataFile = new File(plugin.getDataFolder(), "spawners_data.yml");
        if (!spawnerDataFile.exists()) {
            plugin.saveResource("spawners_data.yml", false);
        }
        spawnerData = YamlConfiguration.loadConfiguration(spawnerDataFile);
    }

    public void saveSpawnerData() {
        try {
            // Clear existing data
            spawnerData.getKeys(false).forEach(key -> spawnerData.set(key, null));

            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                SpawnerData spawner = entry.getValue();
                Location loc = spawner.getSpawnerLocation();
                String path = "spawners." + spawnerId;

                // Save basic spawner properties
                spawnerData.set(path + ".location", String.format("%s,%d,%d,%d",
                        loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                spawnerData.set(path + ".entityType", spawner.getEntityType().name());
                spawnerData.set(path + ".settings", String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%b",
                        spawner.getSpawnerExp(),
                        spawner.getSpawnerActive(),
                        spawner.getSpawnerRange(),
                        spawner.getSpawnerStop(),
                        spawner.getSpawnDelay(),
                        spawner.getMaxSpawnerLootSlots(),
                        spawner.getMaxStoredExp(),
                        spawner.getMinMobs(),
                        spawner.getMaxMobs(),
                        spawner.getStackSize(),
                        spawner.getLastSpawnTime(),
                        spawner.isAllowEquipmentItems()));

                // Save OptimizedVirtualInventory
                OptimizedVirtualInventory virtualInv = spawner.getVirtualInventory();
                if (virtualInv != null) {
                    Map<OptimizedVirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();
                    List<String> serializedItems = ItemStackSerializer.serializeInventory(items);
                    spawnerData.set(path + ".inventory", serializedItems);
                }
            }

            spawnerData.save(spawnerDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawners_data.yml!");
            e.printStackTrace();
        }
    }

    public void loadSpawnerData() {
        spawners.clear();
        locationIndex.clear();

        ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
        if (spawnersSection == null) return;

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                String path = "spawners." + spawnerId;

                // Load location
                String[] locParts = spawnerData.getString(path + ".location").split(",");
                World world = Bukkit.getWorld(locParts[0]);
                if (world == null) continue;

                Location location = new Location(world,
                        Integer.parseInt(locParts[1]),
                        Integer.parseInt(locParts[2]),
                        Integer.parseInt(locParts[3]));

                // Load entity type and create spawner
                EntityType entityType = EntityType.valueOf(spawnerData.getString(path + ".entityType"));
                SpawnerData spawner = new SpawnerData(spawnerId, location, entityType, plugin);

                // Load settings
                String[] settings = spawnerData.getString(path + ".settings").split(",");
                spawner.setSpawnerExp(Integer.parseInt(settings[0]));
                spawner.setSpawnerActive(Boolean.parseBoolean(settings[1]));
                spawner.setSpawnerRange(Integer.parseInt(settings[2]));
                spawner.setSpawnerStop(Boolean.parseBoolean(settings[3]));
                spawner.setSpawnDelay(Integer.parseInt(settings[4]));
                spawner.setMaxSpawnerLootSlots(Integer.parseInt(settings[5]));
                spawner.setMaxStoredExp(Integer.parseInt(settings[6]));
                spawner.setMinMobs(Integer.parseInt(settings[7]));
                spawner.setMaxMobs(Integer.parseInt(settings[8]));
                spawner.setStackSize(Integer.parseInt(settings[9]));
                spawner.setLastSpawnTime(Long.parseLong(settings[10]));
                spawner.setAllowEquipmentItems(Boolean.parseBoolean(settings[11]));

                // Load inventory
                OptimizedVirtualInventory virtualInv = new OptimizedVirtualInventory(spawner.getMaxSpawnerLootSlots());
                List<String> inventoryData = spawnerData.getStringList(path + ".inventory");

                if (inventoryData != null) {
                    Map<ItemStack, Integer> items = ItemStackSerializer.deserializeInventory(inventoryData);
                    for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
                        ItemStack item = entry.getKey();
                        int amount = entry.getValue();

                        // Split into manageable stack sizes
                        while (amount > 0) {
                            int batchSize = Math.min(amount, item.getMaxStackSize());
                            ItemStack batch = item.clone();
                            batch.setAmount(batchSize);
                            virtualInv.addItems(Collections.singletonList(batch));
                            amount -= batchSize;
                        }
                    }
                }

                spawner.setVirtualInventory(virtualInv);
                spawners.put(spawnerId, spawner);
                locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);

            } catch (Exception e) {
                plugin.getLogger().severe("Error loading spawner " + spawnerId);
                e.printStackTrace();
            }
        }
    }

    public List<SpawnerData> getAllSpawners() {
        return new ArrayList<>(spawners.values());
    }

    private void startSaveTask() {
        configManager.debug("Starting spawner data save task");
        int intervalSeconds = configManager.getSaveInterval();
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
            this.saveSpawnerData();
        }, 1, intervalSeconds, TimeUnit.SECONDS); // 5 phút
    }

    public void spawnLoot(SpawnerData spawner) {
        if (System.currentTimeMillis() - spawner.getLastSpawnTime() >= spawner.getSpawnDelay()) {
            // Run heavy calculations async
            Bukkit.getAsyncScheduler().runTaskAsynchronously(plugin task -> {
                LootResult loot = spawnerLootGenerator.generateLoot(
                        spawner.getEntityType(),
                        spawner.getMinMobs(),
                        spawner.getMaxMobs(),
                        spawner
                );

                // Switch back to main thread for Bukkit API calls
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location loc = spawner.getSpawnerLocation();
                    World world = loc.getWorld();
                    world.spawnParticle(ParticleWrapper.VILLAGER_HAPPY,
                            loc.clone().add(0.5, 0.5, 0.5),
                            10, 0.3, 0.3, 0.3, 0);

                    // Calculate pages before adding new loot
                    int oldTotalPages = calculateTotalPages(spawner);

                    spawnerLootGenerator.addLootToSpawner(spawner, loot);
                    spawner.setLastSpawnTime(System.currentTimeMillis());

                    // Calculate pages after adding new loot
                    int newTotalPages = calculateTotalPages(spawner);

                    updateLootInventoryViewers(spawner, oldTotalPages, newTotalPages);
                    updateSpawnerGuiViewers(spawner);
                });
            });
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        OptimizedVirtualInventory virtualInv = spawner.getVirtualInventory();
        int totalItems = virtualInv.getDisplayInventory().size();
        return Math.max(1, (int) Math.ceil((double) totalItems / 45));
    }

    private record UpdateAction(Player player, SpawnerData spawner, int page, boolean requiresNewInventory) {
    }

    private void updateLootInventoryViewers(SpawnerData spawner, int oldTotalPages, int newTotalPages) {
        // Check if total pages changed
        boolean pagesChanged = oldTotalPages != newTotalPages;

        // Batch all viewers that need updates
        List<UpdateAction> updateQueue = new ArrayList<>();

        for (HumanEntity viewer : getViewersForSpawner(spawner)) {
            if (viewer instanceof Player) {
                Player player = (Player) viewer;
                Inventory currentInv = player.getOpenInventory().getTopInventory();
                if (currentInv.getHolder() instanceof PagedSpawnerLootHolder) {
                    PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) currentInv.getHolder();
                    int currentPage = holder.getCurrentPage();

                    boolean needsNewInventory = false;
                    int targetPage = currentPage;

                    // Determine if we need a new inventory
                    if (currentPage > newTotalPages) {
                        targetPage = newTotalPages;
                        needsNewInventory = true;
                    } else if (pagesChanged) {
                        // If total pages changed but current page is still valid,
                        // we need new inventory just to update the title
                        needsNewInventory = true;
                    }

                    updateQueue.add(new UpdateAction(player, spawner, targetPage, needsNewInventory));
                }
            }
        }

        // Process all updates in one server tick
        if (!updateQueue.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UpdateAction action : updateQueue) {
                    if (action.requiresNewInventory) {
                        // Need new inventory for title update
                        Inventory newInv = lootManager.createLootInventory(
                                action.spawner,
                                languageManager.getGuiTitle("gui-title.loot-menu"),
                                action.page
                        );
                        action.player.closeInventory();
                        action.player.openInventory(newInv);
                    } else {
                        // Just update contents of current inventory
                        Inventory currentInv = action.player.getOpenInventory().getTopInventory();
                        lootManager.updateInventoryContents(currentInv, action.spawner, action.page);
                        action.player.updateInventory();
                    }
                }
            });
        }
    }

    private void updateSpawnerGuiViewers(SpawnerData spawner) {
        for (Map.Entry<UUID, SpawnerData> entry : openSpawnerGuis.entrySet()) {
            if (entry.getValue().getSpawnerId().equals(spawner.getSpawnerId())) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null && viewer.isOnline()) {
                    updateSpawnerGui(viewer, spawner, true);
                }
            }
        }
    }

    public void updateSpawnerGui(Player player, SpawnerData spawner, boolean forceUpdate) {
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

    public void trackOpenGui(UUID playerId, SpawnerData spawner) {
        openSpawnerGuis.put(playerId, spawner);
    }

    public void untrackOpenGui(UUID playerId) {
        openSpawnerGuis.remove(playerId);
    }

    private void updateChestItem(Inventory inventory, SpawnerData spawner) {
        ItemStack chestItem = inventory.getItem(11);
        if (chestItem == null || !chestItem.hasItemMeta()) return;

        ItemMeta chestMeta = chestItem.getItemMeta();
        chestMeta.setDisplayName(languageManager.getMessage("spawner-loot-item.name"));

        List<String> chestLore = new ArrayList<>();
        OptimizedVirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = (int) ((double) currentItems / maxSlots * 100);
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest")
                .replace("%max_slots%", String.valueOf(maxSlots))
                .replace("%current_items%", String.valueOf(currentItems))
                .replace("%percent_storage%", String.valueOf(percentStorage));

        chestLore.addAll(Arrays.asList(loreMessageChest.split("\n")));
        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);
        inventory.setItem(11, chestItem);
    }

    private void updateExpItem(Inventory inventory, SpawnerData spawner) {
        ItemStack expItem = inventory.getItem(15);
        if (expItem == null || !expItem.hasItemMeta()) return;

        long currentExp = spawner.getSpawnerExp();
        if (currentExp != previousExpValue) {

            ItemMeta expMeta = expItem.getItemMeta();
            Map<String, String> nameReplacements = new HashMap<>();
            String formattedExp = languageManager.formatNumber(currentExp);
            String formattedMaxExp = languageManager.formatNumber(spawner.getMaxStoredExp());
            int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

            nameReplacements.put("%current_exp%", String.valueOf(spawner.getSpawnerExp()));
            expMeta.setDisplayName(languageManager.getMessage("exp-info-item.name", nameReplacements));
            Map<String, String> loreReplacements = new HashMap<>();
            loreReplacements.put("%current_exp%", formattedExp);
            loreReplacements.put("%max_exp%", formattedMaxExp);
            loreReplacements.put("%percent_exp%", String.valueOf(percentExp));
            loreReplacements.put("%u_max_exp%", String.valueOf(spawner.getMaxStoredExp()));
            String lorePathExp = "exp-info-item.lore.exp-bottle";
            String loreMessageExp = languageManager.getMessage(lorePathExp, loreReplacements);
            List<String> loreEx = Arrays.asList(loreMessageExp.split("\n"));
            expMeta.setLore(loreEx);
            expItem.setItemMeta(expMeta);
            inventory.setItem(15, expItem);
            previousExpValue = currentExp;
        }
    }

    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner) {
        ItemStack spawnerItem = inventory.getItem(13);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) return;

        ItemMeta meta = spawnerItem.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        String timeDisplay = getTimeDisplay(timeUntilNextSpawn);

        // Lấy template cho next spawn line
        String nextSpawnTemplate = languageManager.getMessage("spawner-info-item.lore-change");

        // Strip màu để so sánh
        String strippedTemplate = ChatColor.stripColor(nextSpawnTemplate);

        // Optimize lore update
        boolean updated = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            // Strip màu của line hiện tại để so sánh
            String strippedLine = ChatColor.stripColor(line);

            // So sánh nội dung đã strip màu
            if (strippedLine.startsWith(ChatColor.stripColor(strippedTemplate))) {
                String newLine = nextSpawnTemplate + timeDisplay;
                if (!line.equals(newLine)) {
                    lore.set(i, newLine);
                    updated = true;
                }
                break;
            }
        }

        // Chỉ update ItemMeta nếu có thay đổi
        if (updated || !lore.stream()
                .map(ChatColor::stripColor)
                .anyMatch(line -> line.startsWith(ChatColor.stripColor(strippedTemplate)))) {
            if (!updated) {
                lore.add(nextSpawnTemplate + timeDisplay);
            }
            meta.setLore(lore);
            spawnerItem.setItemMeta(meta);
        }
    }

    private String getTimeDisplay(long timeUntilNextSpawn) {
        if (timeUntilNextSpawn == -1) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn == 0) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn < 0) {
            return languageManager.getMessage("spawner-info-item.lore-error");
        }
        return formatTime(timeUntilNextSpawn);
    }

    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop()) {
            return -1;
        }
        final long currentTime = System.currentTimeMillis();
        final long lastSpawnTime = spawner.getLastSpawnTime();
        final long spawnDelay = spawner.getSpawnDelay() * 50L;
        return lastSpawnTime + spawnDelay - currentTime;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }


    public Map<UUID, SpawnerData> getOpenSpawnerGuis() {
        return openSpawnerGuis;
    }
}