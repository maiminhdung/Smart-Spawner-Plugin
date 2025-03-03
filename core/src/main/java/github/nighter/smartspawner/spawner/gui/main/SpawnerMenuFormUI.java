package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.utils.LanguageManager;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.awt.*;

/**
 * Manages form-based UI for Bedrock players using Floodgate
 */
public class SpawnerMenuFormUI {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    /**
     * Constructs the SpawnerMenuFormUI.
     *
     * @param plugin The main plugin instance
     */
    public SpawnerMenuFormUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    /**
     * Opens the spawner form for a Bedrock player
     *
     * @param player The player to open the form for
     * @param spawner The spawner data to display
     */
    public void openSpawnerForm(Player player, SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String title = spawner.getStackSize() > 1
                ? languageManager.getGuiTitle("gui-title.stacked-menu",
                "%amount%", String.valueOf(spawner.getStackSize()),
                "%entity%", entityName)
                : languageManager.getGuiTitle("gui-title.menu", "%entity%", entityName);

        // Directly set button text with Bedrock-compatible colors
        String lootButtonText = "Open Storage";
        String infoButtonText = "Open Stack Menu";
        String expButtonText = "Collect Experience";

        // Create a simple form with buttons for each action
        SimpleForm form = SimpleForm.builder()
                .title(title)
                .content(createInfoContent(player, spawner))
                // Add buttons with hardcoded text
                .button(lootButtonText, FormImage.Type.URL, "https://img.icons8.com/?size=100&id=e78DnJp8bhmX&format=png&color=000000")
                .button(infoButtonText, FormImage.Type.URL, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/c/cf/Spawner_with_fire.png/revision/latest?cb=20190925003048")
                .button(expButtonText, FormImage.Type.URL, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/1/10/Bottle_o%27_Enchanting.gif/revision/latest?cb=20200428012753")
                // Add closed or invalid response handler
                .closedOrInvalidResultHandler(() -> {
                    // Do nothing when form is closed without selecting
                })
                // Add valid response handler
                .validResultHandler(response -> {
                    // Get the index of the clicked button
                    int buttonId = response.clickedButtonId();

                    // Schedule the action to run on the main server thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Handle the button click based on the button ID
                        switch (buttonId) {
                            case 0: // Loot Storage
                                handleLootStorage(player, spawner);
                                break;
                            case 1: // Spawner Info
                                handleSpawnerInfo(player, spawner);
                                break;
                            case 2: // Experience
                                handleExpCollection(player, spawner);
                                break;
                        }
                    });
                })
                .build();

        // Send the form to the player
        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }

    private String createInfoContent(Player player, SpawnerData spawner) {
        StringBuilder content = new StringBuilder();

        // Spawner Info Section
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        content.append("§l§b»»» Spawner Information «««§r\n\n");
        content.append("§8▪ §b Entity: §f").append(entityName).append("\n");
        content.append("§8▪ §b Stack Size: §f").append(spawner.getStackSize()).append("\n");
        content.append("§8▪ §b Range: §f").append(spawner.getSpawnerRange()).append(" §7blocks\n");
        content.append("§8▪ §b Delay: §f").append(spawner.getSpawnDelay() / 20).append(" §7seconds\n");
        content.append("§8▪ §b Mob Rate: §f").append(spawner.getMinMobs()).append(" §7- §f").append(spawner.getMaxMobs()).append("\n\n");

        // Storage Info Section
        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        content.append("§l§6»»» Storage «««§r\n\n");
        content.append("§8▪ §6 Slots: §f").append(currentItems).append("§7/§f").append(maxSlots).append("\n");
        content.append("§8▪ §6 Status: §f").append(getStorageStatus(currentItems, maxSlots)).append("\n\n");

        // XP Info Section
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        String formattedExp = languageManager.formatNumber(currentExp);
        String formattedMaxExp = languageManager.formatNumber(maxExp);
        content.append("§l§a»»» Experience «««§r\n\n");
        content.append("§8▪ §a Current: §e").append(formattedExp).append("§7/§e").append(formattedMaxExp).append(" §7XP\n");
        content.append("§8▪ §a Status: §e").append(getExpStatus(currentExp, maxExp)).append("\n\n");

        return content.toString();
    }

    private String getStorageStatus(int current, int max) {
        double ratio = (double) current / max;
        if (ratio >= 0.9) return "§cNearly Full";
        if (ratio >= 0.7) return "§6Filling Up";
        if (ratio >= 0.4) return "§eHalf Full";
        if (ratio > 0) return "§aPlenty of Space";
        return "§aEmpty";
    }

    private String getExpStatus(long current, long max) {
        double ratio = (double) current / max;
        if (ratio >= 0.9) return "§cAlmost Full";
        if (ratio >= 0.7) return "§6Large Amount";
        if (ratio >= 0.4) return "§eMedium Amount";
        if (ratio > 0) return "§aSmall Amount";
        return "§aEmpty";
    }

    /**
     * Handles the loot storage button click
     *
     * @param player The player clicking the button
     * @param spawner The spawner data
     */
    private void handleLootStorage(Player player, SpawnerData spawner) {
        // Use the existing chest handler from your SpawnerMenuAction class
        plugin.getSpawnerMenuAction().handleChestClick(player, spawner);
    }

    /**
     * Handles the spawner info button click
     *
     * @param player The player clicking the button
     * @param spawner The spawner data
     */
    private void handleSpawnerInfo(Player player, SpawnerData spawner) {
        if (!player.hasPermission("smartspawner.stack")) {
            languageManager.sendMessage(player, "messages.no-permission");
            return;
        }

        plugin.getSpawnerStackerUI().openStackerGui(player, spawner);
    }

    /**
     * Handles the experience collection button click
     *
     * @param player The player clicking the button
     * @param spawner The spawner data
     */
    private void handleExpCollection(Player player, SpawnerData spawner) {
        // This will execute the same logic as your handleExpBottleClick method
        plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner);
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

    /**
     * Checks if a player is a Bedrock player via Floodgate
     *
     * @param player The player to check
     * @return true if player is from Bedrock, false otherwise
     */
    public static boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}