package me.nighter.smartSpawner.holders;

import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerMenuHolder implements InventoryHolder, SpawnerHolder {
    private final SpawnerData spawnerData;

    public SpawnerMenuHolder(SpawnerData spawnerData) {
        this.spawnerData = spawnerData;
    }

    @Override
    public Inventory getInventory() {
        return null; // Required by interface
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }

}
