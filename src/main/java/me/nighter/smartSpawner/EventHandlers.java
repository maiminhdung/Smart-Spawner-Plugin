package me.nighter.smartSpawner;

import me.nighter.smartSpawner.holders.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.holders.SpawnerHolder;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.holders.SpawnerStackerHolder;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.utils.SpawnerData;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;


import java.util.*;
import java.util.concurrent.TimeUnit;

public class EventHandlers implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;

    // Spawner Lock Mechanism (make only one player access GUI at a time)
    private final Map<Player, String> playerCurrentMenu = new HashMap<>();
    private final Set<Class<? extends InventoryHolder>> validHolderTypes = Set.of(
            PagedSpawnerLootHolder.class,
            SpawnerMenuHolder.class,
            SpawnerStackerHolder.class
    );

    public EventHandlers(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    // Prevent spawner from spawning mobs
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(SpawnerSpawnEvent event){
        if (event.getSpawner() == null) return;
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(event.getSpawner().getLocation());
        if (spawner != null && spawner.getSpawnerActive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!isValidHolder(holder)) {
            playerCurrentMenu.remove(player);
            return;
        }

        // Schedule check for inventory reopen
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            InventoryView view = player.getOpenInventory();
            // If player opened another valid inventory, don't unlock
            if (view != null && isValidHolder(view.getTopInventory().getHolder())) {
                return;
            }

            // Unlock spawner and clean up
            SpawnerData spawner = ((SpawnerHolder) holder).getSpawnerData();
            spawner.unlock(player.getUniqueId());
            playerCurrentMenu.remove(player);
        }, 1L, TimeUnit.SECONDS);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        spawnerManager.getAllSpawners().stream()
                .filter(spawner -> playerUUID.equals(spawner.getLockedBy()))
                .forEach(spawner -> spawner.unlock(playerUUID));
    }

    private boolean isValidHolder(InventoryHolder holder) {
        return holder != null && validHolderTypes.contains(holder.getClass());
    }

    public void cleanup() {
        // player is still in spawner GUI
        playerCurrentMenu.clear();
    }
}
