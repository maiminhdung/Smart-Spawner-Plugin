package github.nighter.smartspawner.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Utility class to get the SmartSpawnerAPI instance.
 */
public class SmartSpawnerProvider {

    private static final String PLUGIN_NAME = "SmartSpawner";

    /**
     * Gets the SmartSpawnerAPI instance.
     *
     * @return The API instance, or null if SmartSpawner is not loaded
     */
    public static SmartSpawnerAPI getAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);

        if (plugin == null) {
            return null;
        }

        if (!(plugin instanceof SmartSpawnerPlugin)) {
            return null;
        }

        return ((SmartSpawnerPlugin) plugin).getAPI();
    }
}