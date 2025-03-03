package github.nighter.smartspawner.nms;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles version-specific initialization for the SmartSpawner plugin.
 * This class manages loading appropriate implementation classes based on the server version.
 *
 */
public class VersionInitializer {
    private final Logger logger;
    private final String serverVersion;
    private final String basePackage = "github.nighter.smartspawner";

    // Component definitions - each entry contains component type and initializer class name
    private final String[][] components = {
            {"Particles", "ParticleInitializer"},
            {"Textures", "TextureInitializer"},
            {"Spawners", "SpawnerInitializer"}
    };

    /**
     * Creates a new VersionInitializer for the specified plugin.
     *
     * @param plugin The plugin instance
     */
    public VersionInitializer(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.serverVersion = Bukkit.getServer().getBukkitVersion();
    }

    /**
     * Initializes all version-specific components.
     *
     * @throws IllegalStateException if the server version is not supported
     */
    public void initialize() {
        String versionPath = determineVersionPath();
        if (versionPath == null) {
            throw new IllegalStateException("Unsupported server version: " + serverVersion);
        }

        initializeComponentsForVersion(versionPath);
    }

    /**
     * Determines the appropriate version package path based on the server version.
     *
     * @return The version package path, or null if not supported
     */
    private String determineVersionPath() {
        // Define supported versions and their package names
        Map<String, String> supportedVersions = new HashMap<>();
        supportedVersions.put("1.20", "v1_20");
        supportedVersions.put("1.21", "v1_21");

        // Find matching version path
        for (Map.Entry<String, String> entry : supportedVersions.entrySet()) {
            if (serverVersion.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Initializes all components for the detected version.
     *
     * @param versionPath The version package path
     */
    private void initializeComponentsForVersion(String versionPath) {
        for (String[] component : components) {
            initializeComponent(component[0], component[1], versionPath);
        }
    }

    /**
     * Initializes a specific component for the detected version.
     *
     * @param componentName The name of the component (for logging)
     * @param initializerClass The class name of the initializer
     * @param versionPath The version package path
     */
    private void initializeComponent(String componentName, String initializerClass, String versionPath) {
        try {
            String className = String.format("%s.%s.%s", basePackage, versionPath, initializerClass);
            Class<?> clazz = Class.forName(className);
            Method initMethod = clazz.getMethod("init");
            initMethod.invoke(null);

            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("Successfully initialized %s for version %s",
                        componentName, serverVersion));
            }
        } catch (Exception e) {
            logger.severe(String.format("Failed to initialize %s for version %s: %s",
                    componentName, serverVersion, e.getMessage()));
            if (logger.isLoggable(Level.FINE)) {
                e.printStackTrace();
            }
            throw new RuntimeException("Failed to initialize " + componentName, e);
        }
    }
}