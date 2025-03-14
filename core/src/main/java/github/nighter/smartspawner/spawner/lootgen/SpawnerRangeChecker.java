package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.config.ConfigManager;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerRangeChecker {
    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final SpawnerManager spawnerManager;
    private final SpawnerLootGenerator spawnerLootGenerator;
    private final Map<String, BukkitTask> spawnerTasks;
    private final Map<String, Set<UUID>> playersInRange;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerLootGenerator = plugin.getSpawnerLootGenerator();
        this.spawnerTasks = new ConcurrentHashMap<>();
        this.playersInRange = new ConcurrentHashMap<>();
        initializeRangeCheckTask();
    }

    private void initializeRangeCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () ->
                        spawnerManager.getAllSpawners().forEach(this::updateSpawnerStatus),
                CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void updateSpawnerStatus(SpawnerData spawner) {
        Location spawnerLoc = spawner.getSpawnerLocation();
        World world = spawnerLoc.getWorld();
        if (world == null) return;

        boolean playerFound = isPlayerInRange(spawner, spawnerLoc, world);
        boolean shouldStop = !playerFound;

        if (spawner.getSpawnerStop() != shouldStop) {
            spawner.setSpawnerStop(shouldStop);
            handleSpawnerStateChange(spawner, shouldStop);
        }
    }

    private boolean isPlayerInRange(SpawnerData spawner, Location spawnerLoc, World world) {
        int range = spawner.getSpawnerRange();
        double rangeSquared = range * range;
        int chunkRadius = (range >> 4) + 1;
        int baseX = spawnerLoc.getBlockX() >> 4;
        int baseZ = spawnerLoc.getBlockZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                if (!world.isChunkLoaded(baseX + dx, baseZ + dz)) continue;

                if (checkChunkForPlayers(world, spawnerLoc, range, rangeSquared)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkChunkForPlayers(World world, Location spawnerLoc, int range, double rangeSquared) {
        Collection<Entity> nearbyEntities = world.getNearbyEntities(spawnerLoc, range, range, range,
                entity -> entity instanceof Player);

        for (Entity entity : nearbyEntities) {
            if (entity.getLocation().distanceSquared(spawnerLoc) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
        if (!shouldStop) {
            activateSpawner(spawner);
        } else {
            deactivateSpawner(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        startSpawnerTask(spawner);
        spawner.refreshHologram();
        //configManager.debug("Spawner " + spawner.getSpawnerId() + " activated - Player in range");
    }

    private void deactivateSpawner(SpawnerData spawner) {
        stopSpawnerTask(spawner);
        spawner.removeHologram();
        //configManager.debug("Spawner " + spawner.getSpawnerId() + " deactivated - No players in range");
    }

    private void startSpawnerTask(SpawnerData spawner) {
        stopSpawnerTask(spawner);

        spawner.setLastSpawnTime(System.currentTimeMillis() + spawner.getSpawnDelay());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> {
                    if (!spawner.getSpawnerStop()) {
                        spawnerLootGenerator.spawnLootToSpawner(spawner);
                    }
                },
                0L, spawner.getSpawnDelay()
        );

        spawnerTasks.put(spawner.getSpawnerId(), task);
    }

    public void stopSpawnerTask(SpawnerData spawner) {
        BukkitTask task = spawnerTasks.remove(spawner.getSpawnerId());
        if (task != null) {
            task.cancel();
        }
    }

    public Set<UUID> getPlayersInRange(String spawnerId) {
        return playersInRange.getOrDefault(spawnerId, Collections.emptySet());
    }

    public void cleanup() {
        spawnerTasks.values().forEach(BukkitTask::cancel);
        spawnerTasks.clear();
        playersInRange.clear();
    }
}