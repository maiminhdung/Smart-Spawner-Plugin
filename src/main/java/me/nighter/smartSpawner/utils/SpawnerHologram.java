package me.nighter.smartSpawner.utils;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;

import org.bukkit.Location;
import org.bukkit.entity.Display;
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

    private static final Vector3f SCALE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f TRANSLATION = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final AxisAngle4f ROTATION = new AxisAngle4f(0, 0, 0, 0);

    public SpawnerHologram(Location location) {
        this.plugin = SmartSpawner.getInstance();
        this.spawnerLocation = location;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
    }

    public void createHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;
        Location holoLoc = spawnerLocation.clone().add(0.5, 1.6, 0.5);

        try {
            textDisplay = spawnerLocation.getWorld().spawn(holoLoc, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setViewRange(16.0f);
                display.setShadowed(false);
                display.setDefaultBackground(false);
                display.setTransformation(new Transformation(TRANSLATION, ROTATION, SCALE, ROTATION));
                display.setSeeThrough(configManager.isHologramSeeThrough());
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
        replacements.put("%current_items%",languageManager.formatNumberTenThousand(currentItems));
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
}