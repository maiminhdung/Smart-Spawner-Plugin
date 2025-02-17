package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

public class SpawnerExplosionListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final ConfigManager configManager;

    public SpawnerExplosionListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        List<Block> blocksToRemove = new ArrayList<>();

        for (Block block : event.blockList()) {
            if (block.getType() == Material.SPAWNER) {
                SpawnerData spawnerData = this.spawnerManager.getSpawnerByLocation(block.getLocation());

                if (spawnerData != null) {
                    if (!configManager.isAllowGrief()) {
                        // Add the spawner block to the list of blocks to remove
                        blocksToRemove.add(block);
                    } else {
                        String spawnerId = spawnerData.getSpawnerId();
                        spawnerManager.removeSpawner(spawnerId);
                        plugin.getRangeChecker().stopSpawnerTask(spawnerData);
                        plugin.getConfigManager().debug("Spawner " + spawnerId + " has been exploded.");
                    }
                } else {
                    // If no spawner data is found, we still want the spawner block to be destroyed
                    // So don't add it to the blocksToRemove list
                }
            }
        }

        // Remove the spawner blocks that should not be destroyed from the explosion list
        event.blockList().removeAll(blocksToRemove);
    }
}
