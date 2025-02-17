package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.holders.SpawnerStackerHolder;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;


import java.util.*;

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

        // Chạy trên Main Thread để tránh lỗi truy cập Inventory Async
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            // Kiểm tra nếu player không còn mở GUI nào nữa
            if (player == null || !player.isOnline()) return;

            InventoryView view = player.getOpenInventory();
            if (view == null || !isValidHolder(view.getTopInventory().getHolder())) {
                playerCurrentMenu.remove(player);
            }
        }, 20L); // 1 giây = 20 ticks
    }

    private boolean isValidHolder(InventoryHolder holder) {
        return holder != null && validHolderTypes.contains(holder.getClass());
    }

    public void cleanup() {
        // player is still in spawner GUI
        playerCurrentMenu.clear();
    }
}
