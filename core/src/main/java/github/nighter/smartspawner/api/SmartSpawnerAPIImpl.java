package github.nighter.smartspawner.api;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.LanguageManager;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Implementation of the SmartSpawnerAPI interface
 */
public class SmartSpawnerAPIImpl implements SmartSpawnerAPI {

    private final SmartSpawner plugin;

    public SmartSpawnerAPIImpl(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType) {
        return createSpawnerItem(entityType, 1);
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null) {
            if (entityType != null && entityType != EntityType.UNKNOWN) {
                // Set display name
                LanguageManager languageManager = plugin.getLanguageManager();
                String entityTypeName = languageManager.getFormattedMobName(entityType);
                String displayName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
                meta.setDisplayName(displayName);

                // Store entity type in BlockStateMeta
                if (meta instanceof BlockStateMeta) {
                    BlockStateMeta blockMeta = (BlockStateMeta) meta;
                    BlockState blockState = blockMeta.getBlockState();

                    if (blockState instanceof CreatureSpawner) {
                        CreatureSpawner cs = (CreatureSpawner) blockState;
                        cs.setSpawnedType(entityType);
                        blockMeta.setBlockState(cs);
                    }
                }
            }
            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    @Override
    public EntityType getSpawnerEntityType(ItemStack item) {
        if (!isValidSpawner(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta) {
            BlockStateMeta blockMeta = (BlockStateMeta) meta;
            BlockState blockState = blockMeta.getBlockState();

            if (blockState instanceof CreatureSpawner) {
                CreatureSpawner cs = (CreatureSpawner) blockState;
                return cs.getSpawnedType();
            }
        }

        return null;
    }

    @Override
    public boolean isValidSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta instanceof BlockStateMeta;
    }
}