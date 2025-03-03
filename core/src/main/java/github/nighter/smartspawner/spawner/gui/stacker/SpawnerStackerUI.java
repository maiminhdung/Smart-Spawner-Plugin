package github.nighter.smartspawner.spawner.gui.stacker;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.SpawnerStackerHolder;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Manages the user interface for spawner stacking functionality.
 * Provides methods to create and display spawner stacking GUI to players.
 */
public class SpawnerStackerUI {
    // GUI layout constants
    private static final int GUI_SIZE = 27;
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;

    // Stack modification amounts
    private static final int[] STACK_AMOUNTS = {64, 10, 1};
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    /**
     * Constructs a new SpawnerStackerUI with the given plugin instance.
     *
     * @param plugin The SmartSpawner plugin instance
     */
    public SpawnerStackerUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    /**
     * Opens the spawner stacker GUI for a player.
     * Displays information about the spawner and allows stack size modification.
     *
     * @param player The player to show the GUI to
     * @param spawner The spawner data to display and modify
     */
    public void openStackerGui(Player player, SpawnerData spawner) {
        if (player == null || spawner == null) {
            return;
        }

        String title = languageManager.getMessage("gui-title.stacker-menu");
        Inventory gui = Bukkit.createInventory(new SpawnerStackerHolder(spawner), GUI_SIZE, title);

        // Add decrease buttons
        for (int i = 0; i < STACK_AMOUNTS.length; i++) {
            gui.setItem(DECREASE_SLOTS[i], createDecreaseButton(spawner, STACK_AMOUNTS[i]));
        }

        // Add increase buttons
        for (int i = 0; i < STACK_AMOUNTS.length; i++) {
            gui.setItem(INCREASE_SLOTS[i], createIncreaseButton(spawner, STACK_AMOUNTS[i]));
        }

        // Add spawner info button
        gui.setItem(SPAWNER_INFO_SLOT, createSpawnerInfoButton(spawner));
        player.openInventory(gui);
    }

    /**
     * Creates a button for decreasing stack size.
     *
     * @param spawner The spawner data
     * @param amount The amount to decrease by
     * @return The configured ItemStack for the decrease button
     */
    private ItemStack createDecreaseButton(SpawnerData spawner, int amount) {
        String name = languageManager.getMessage("button.name.decrease-" + amount);
        String[] lore = languageManager.getMessage("button.lore.remove")
                .replace("%amount%", String.valueOf(amount))
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .split("\n");

        ItemStack button = createButton(Material.RED_STAINED_GLASS_PANE, name, Arrays.asList(lore));
        button.setAmount(amount);
        return button;
    }

    /**
     * Creates a button for increasing stack size.
     *
     * @param spawner The spawner data
     * @param amount The amount to increase by
     * @return The configured ItemStack for the increase button
     */
    private ItemStack createIncreaseButton(SpawnerData spawner, int amount) {
        String name = languageManager.getMessage("button.name.increase-" + amount);
        String[] lore = languageManager.getMessage("button.lore.add")
                .replace("%amount%", String.valueOf(amount))
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .split("\n");

        ItemStack button = createButton(Material.LIME_STAINED_GLASS_PANE, name, Arrays.asList(lore));
        button.setAmount(amount);
        return button;
    }

    /**
     * Creates the central spawner info button showing current stack information.
     *
     * @param spawner The spawner data to display
     * @return The configured ItemStack for the spawner info button
     */
    private ItemStack createSpawnerInfoButton(SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String name = languageManager.getMessage("button.name.spawner", "%entity%", entityName);

        String[] lore = languageManager.getMessage("button.lore.spawner")
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .replace("%max_stack_size%", String.valueOf(configManager.getInt("max-stack-size")))
                .replace("%entity%", entityName)
                .split("\n");

        return createButton(Material.SPAWNER, name, Arrays.asList(lore));
    }

    /**
     * Utility method to create a button with specified properties.
     *
     * @param material The material for the button
     * @param name The display name for the button
     * @param lore The lore text for the button
     * @return The configured ItemStack
     */
    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            button.setItemMeta(meta);
        }

        return button;
    }
}