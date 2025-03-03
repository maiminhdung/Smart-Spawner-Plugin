package github.nighter.smartspawner.api;

/**
 * Interface that the main SmartSpawner plugin class will implement.
 * This allows access to the API from the plugin instance.
 */
public interface SmartSpawnerPlugin {

    /**
     * Gets the SmartSpawnerAPI instance.
     *
     * @return The API instance
     */
    SmartSpawnerAPI getAPI();
}