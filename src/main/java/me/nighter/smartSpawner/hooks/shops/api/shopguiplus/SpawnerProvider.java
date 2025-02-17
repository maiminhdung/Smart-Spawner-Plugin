package me.nighter.smartSpawner.hooks.shops.api.shopguiplus;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import net.brcdev.shopgui.spawner.external.provider.ExternalSpawnerProvider;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class SpawnerProvider implements ExternalSpawnerProvider {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;

    public SpawnerProvider(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public String getName() {
        return "SmartSpawner";
    }

    @Override
    public ItemStack getSpawnerItem(EntityType entityType) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null) {
            if (entityType != null && entityType != EntityType.UNKNOWN) {
                // Set display name
                String entityTypeName = languageManager.getFormattedMobName(entityType);
                String displayName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
                meta.setDisplayName(displayName);

                // Store entity type in item NBT
                BlockStateMeta blockMeta = (BlockStateMeta) meta;
                CreatureSpawner cs = (CreatureSpawner) blockMeta.getBlockState();
                cs.setSpawnedType(entityType);
                blockMeta.setBlockState(cs);
            }
            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    @Override
    public EntityType getSpawnerEntityType(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.SPAWNER) {
            return getDefaultEntityType();
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return getDefaultEntityType();
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();

        EntityType entityType = spawner.getSpawnedType();
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return getDefaultEntityType();
        }

        return entityType;
    }

    private EntityType getDefaultEntityType() {
        return configManager.getDefaultEntityType();
    }
}
