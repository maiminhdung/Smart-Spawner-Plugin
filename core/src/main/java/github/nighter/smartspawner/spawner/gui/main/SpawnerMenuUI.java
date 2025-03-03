package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.utils.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Manages the user interface for spawner menu interactions.
 */
public class SpawnerMenuUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int CHEST_SLOT = 11;
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int EXP_SLOT = 15;
    private static final int TICKS_PER_SECOND = 20;

    private final LanguageManager languageManager;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    //private final SpawnerMenuFormUI spawnerMenuFormUI;

    /**
     * Constructs the SpawnerMenuUI.
     *
     * @param plugin The main plugin instance
     */
    public SpawnerMenuUI(SmartSpawner plugin) {
        this.languageManager = plugin.getLanguageManager();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiManager();
        //this.spawnerMenuFormUI = new SpawnerMenuFormUI(plugin);
    }

    /**
     * Opens the spawner menu for a player.
     *
     * @param player The player to open the menu for
     * @param spawner The spawner data to display
     * @param refresh Whether this is a menu refresh (suppresses sound effects)
     */
    public void openSpawnerMenu(Player player, SpawnerData spawner, boolean refresh) {
//        if (SpawnerMenuFormUI.isBedrockPlayer(player)) {
//            spawnerMenuFormUI.openSpawnerForm(player, spawner);
//            return;
//        }

        Inventory menu = createMenu(player, spawner);

        // Populate menu items
        menu.setItem(CHEST_SLOT, createLootStorageItem(spawner));
        menu.setItem(SPAWNER_INFO_SLOT, createSpawnerInfoItem(player, spawner));
        menu.setItem(EXP_SLOT, createExpItem(spawner));

        // Open inventory and play sound if not refreshing
        player.openInventory(menu);
        if (!refresh) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        }
        if (spawner.isAtCapacity()){
            spawnerGuiViewManager.updateSpawnerGuiInfo(player, spawner, false);
        }
    }

    /**
     * Creates the spawner menu inventory with appropriate title.
     *
     * @param player The player to create the menu for
     * @param spawner The spawner data
     * @return The created inventory
     */
    private Inventory createMenu(Player player, SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String title;

        if (spawner.getStackSize() > 1) {
            title = languageManager.getGuiTitle("gui-title.stacked-menu",
                    "%amount%", String.valueOf(spawner.getStackSize()),
                    "%entity%", entityName);
        } else {
            title = languageManager.getGuiTitle("gui-title.menu",
                    "%entity%", entityName);
        }

        return Bukkit.createInventory(new SpawnerMenuHolder(spawner), INVENTORY_SIZE, title);
    }

    /**
     * Creates the loot storage chest item with usage statistics.
     *
     * @param spawner The spawner data
     * @return The configured chest item
     */
    private ItemStack createLootStorageItem(SpawnerData spawner) {
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chestItem.getItemMeta();
        if (chestMeta == null) return chestItem;

        chestMeta.setDisplayName(languageManager.getMessage("spawner-loot-item.name"));

        // Calculate storage usage stats
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = calculatePercentage(currentItems, maxSlots);

        // Create lore with proper vararg method call
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest",
                "%max_slots%", String.valueOf(maxSlots),
                "%current_items%", String.valueOf(currentItems),
                "%percent_storage%", String.valueOf(percentStorage));

        List<String> chestLore = Arrays.asList(loreMessageChest.split("\n"));
        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);
        return chestItem;
    }

    /**
     * Creates the spawner info item with entity and configuration details.
     *
     * @param player The player viewing the menu
     * @param spawner The spawner data
     * @return The configured spawner info item
     */
    private ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner) {
        ItemStack spawnerItem = SpawnerMobHeadTexture.getCustomHead(spawner.getEntityType(), player);
        ItemMeta spawnerMeta = spawnerItem.getItemMeta();
        if (spawnerMeta == null) return spawnerItem;

        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        spawnerMeta.setDisplayName(languageManager.getMessage("spawner-info-item.name"));

        // Build vararg parameters for getMessage method
        String loreMessage = languageManager.getMessage("spawner-info-item.lore.spawner-info",
                "%entity%", entityName,
                "%stack_size%", String.valueOf(spawner.getStackSize()),
                "%range%", String.valueOf(spawner.getSpawnerRange()),
                "%delay%", String.valueOf(spawner.getSpawnDelay() / TICKS_PER_SECOND),
                "%min_mobs%", String.valueOf(spawner.getMinMobs()),
                "%max_mobs%", String.valueOf(spawner.getMaxMobs()));

        List<String> lore = Arrays.asList(loreMessage.split("\n"));
        spawnerMeta.setLore(lore);
        spawnerItem.setItemMeta(spawnerMeta);
        return spawnerItem;
    }

    /**
     * Creates the experience bottle item with experience stats.
     *
     * @param spawner The spawner data
     * @return The configured experience item
     */
    private ItemStack createExpItem(SpawnerData spawner) {
        ItemStack expItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta expMeta = expItem.getItemMeta();
        if (expMeta == null) return expItem;

        // Format and calculate experience values
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        String formattedExp = languageManager.formatNumber(currentExp);
        String formattedMaxExp = languageManager.formatNumber(maxExp);
        int percentExp = calculatePercentage(currentExp, maxExp);

        // Set item name using vararg method call
        expMeta.setDisplayName(languageManager.getMessage("exp-info-item.name",
                "%current_exp%", String.valueOf(currentExp)));

        // Set item lore using vararg method call
        String loreMessageExp = languageManager.getMessage("exp-info-item.lore.exp-bottle",
                "%current_exp%", formattedExp,
                "%max_exp%", formattedMaxExp,
                "%percent_exp%", String.valueOf(percentExp),
                "%u_max_exp%", String.valueOf(maxExp));

        List<String> loreEx = Arrays.asList(loreMessageExp.split("\n"));
        expMeta.setLore(loreEx);
        expItem.setItemMeta(expMeta);
        return expItem;
    }

    /**
     * Calculates a percentage safely (avoids division by zero).
     *
     * @param current Current value
     * @param maximum Maximum value
     * @return Percentage (0-100)
     */
    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }
}