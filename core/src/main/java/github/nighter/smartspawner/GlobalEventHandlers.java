package github.nighter.smartspawner;

import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;

public class GlobalEventHandlers implements Listener {
    private final SpawnerManager spawnerManager;

    public GlobalEventHandlers(SmartSpawner plugin) { this.spawnerManager = plugin.getSpawnerManager(); }

    // Prevent spawner from spawning mobs
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(SpawnerSpawnEvent event){
        if (event.getSpawner() == null) return;
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(event.getSpawner().getLocation());
        if (spawner != null && spawner.getSpawnerActive()) {
            event.setCancelled(true);
        }
    }
}
