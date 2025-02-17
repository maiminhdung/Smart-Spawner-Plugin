package me.nighter.smartSpawner.spawner.properties;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class SpawnerHologram {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private TextDisplay textDisplay;
    private final Location spawnerLocation;
    private int stackSize;
    private EntityType entityType;
    private int currentExp;
    private int maxExp;
    private int currentItems;
    private int maxSlots;
    private static final String HOLOGRAM_IDENTIFIER = "SmartSpawner-Holo";
    private String uniqueIdentifier;

    private static final Vector3f SCALE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f TRANSLATION = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final AxisAngle4f ROTATION = new AxisAngle4f(0, 0, 0, 0);

    public SpawnerHologram(Location location) {
        this.plugin = SmartSpawner.getInstance();
        this.spawnerLocation = location;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
        this.uniqueIdentifier = generateUniqueIdentifier(location);
    }

    private String generateUniqueIdentifier(Location location) {
        return HOLOGRAM_IDENTIFIER + "-" +
                location.getWorld().getName() + "-" +
                location.getBlockX() + "-" +
                location.getBlockY() + "-" +
                location.getBlockZ();
    }

    public void createHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        double offsetX = configManager.getHologramOffsetX();
        double offsetY = configManager.getHologramHeight();
        double offsetZ = configManager.getHologramOffsetZ();

        Location holoLoc = spawnerLocation.clone().add(offsetX, offsetY, offsetZ);

        try {
            textDisplay = spawnerLocation.getWorld().spawn(holoLoc, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setViewRange(16.0f);
                display.setShadowed(configManager.isHologramShadowed());
                display.setDefaultBackground(false);
                display.setTransformation(new Transformation(TRANSLATION, ROTATION, SCALE, ROTATION));
                display.setSeeThrough(configManager.isHologramSeeThrough());
                // Add custom name for identification
                display.setCustomName(uniqueIdentifier);
                display.setCustomNameVisible(false);
            });

            updateText();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateText() {
        if (textDisplay == null || !textDisplay.isValid()) return;
        if (entityType == null) return;

        String entityTypeName = languageManager.getFormattedMobName(entityType);

        // Get text from language file with replacements
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%entity%", entityTypeName);
        replacements.put("%stack_size%", String.valueOf(stackSize));
        replacements.put("%current_exp%", languageManager.formatNumberTenThousand(currentExp));
        replacements.put("%max_exp%", languageManager.formatNumberTenThousand(maxExp));
        replacements.put("%used_slots%",languageManager.formatNumberTenThousand(currentItems));
        replacements.put("%max_slots%", languageManager.formatNumberTenThousand(maxSlots));

        String hologramText = languageManager.getMessage("spawner-hologram.format", replacements);
        textDisplay.setText(hologramText);
    }

    public void updateData(int stackSize, EntityType entityType, int currentExp, int maxExp, int currentItems, int maxSlots) {
        this.stackSize = stackSize;
        this.entityType = entityType;
        this.currentExp = currentExp;
        this.maxExp = maxExp;
        this.currentItems = currentItems;
        this.maxSlots = maxSlots;
        updateText();
    }

    public void remove() {
        if (textDisplay != null && textDisplay.isValid()) {
            textDisplay.remove();
            textDisplay = null;
        }
    }

    public void cleanupExistingHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        double offsetX = configManager.getHologramOffsetX();
        double offsetY = configManager.getHologramHeight();
        double offsetZ = configManager.getHologramOffsetZ();

        // Calculate efficient search area
        double searchRadius = Math.max(Math.max(Math.abs(offsetX), Math.abs(offsetY)), Math.abs(offsetZ)) + 1.0;

        // Use efficient entity lookup
        spawnerLocation.getWorld().getNearbyEntities(spawnerLocation, searchRadius, searchRadius, searchRadius)
                .stream()
                .filter(entity -> entity instanceof TextDisplay)
                .filter(entity -> {
                    String customName = entity.getCustomName();
                    return customName != null &&
                            customName.startsWith(HOLOGRAM_IDENTIFIER) &&
                            (customName.equals(uniqueIdentifier) || isOldHologramForLocation(customName, spawnerLocation));
                })
                .forEach(Entity::remove);
    }

    private boolean isOldHologramForLocation(String hologramName, Location location) {
        try {
            String[] parts = hologramName.split("-");
            if (parts.length != 6) return false;

            return parts[2].equals(location.getWorld().getName()) &&
                    Integer.parseInt(parts[3]) == location.getBlockX() &&
                    Integer.parseInt(parts[4]) == location.getBlockY() &&
                    Integer.parseInt(parts[5]) == location.getBlockZ();
        } catch (Exception e) {
            return false;
        }
    }
}