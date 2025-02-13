package me.nighter.smartSpawner.commands;

import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.SpawnerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class HologramCommand implements CommandExecutor {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    public HologramCommand(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smartspawner.hologram")) {
            sender.sendMessage(languageManager.getMessageWithPrefix("no-permission"));
            return true;
        }

        // Toggle hologram state
        boolean newValue = !configManager.isHologramEnabled();

        // Get main config and set new value
        FileConfiguration mainConfig = configManager.getMainConfig();
        mainConfig.set("hologram.enabled", newValue);

        // Save configs and reload
        configManager.saveConfigs();
        configManager.reloadConfigs();

        // Update all holograms
        spawnerManager.refreshAllHolograms();

        // Send message to player
        String messageKey = newValue ? "command.hologram.enabled" : "command.hologram.disabled";
        sender.sendMessage(languageManager.getMessageWithPrefix(messageKey));

        // Log debug message
        configManager.debug("Holograms " + (newValue ? "enabled" : "disabled") + " by " + sender.getName());

        return true;
    }
}