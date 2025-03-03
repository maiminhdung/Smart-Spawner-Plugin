package github.nighter.smartspawner.spawner.interactions;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.protections.CheckOpenMenu;
import github.nighter.smartspawner.nms.ParticleWrapper;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.interactions.stack.SpawnerStackHandler;
import github.nighter.smartspawner.spawner.interactions.type.SpawnEggHandler;
import github.nighter.smartspawner.spawner.utils.NaturalSpawnerDetector;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player interactions with spawner blocks
 * Handles right-click events on spawners for various actions:
 * - Opening spawner menu
 * - Changing spawner type with spawn eggs
 * - Stacking spawners
 */
public class SpawnerClickManager implements Listener {
    private static final long COOLDOWN_MS = 250;
    private static final long CLEANUP_INTERVAL_TICKS = 6000L; // 5 minutes

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private final SpawnEggHandler spawnEggHandler;
    private final SpawnerStackHandler spawnerStackHandler;
    private final SpawnerMenuUI spawnerMenuUI;

    // Use ConcurrentHashMap for thread safety without explicit synchronization
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public SpawnerClickManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnEggHandler = plugin.getSpawnEggHandler();
        this.spawnerStackHandler = plugin.getSpawnerStackHandler();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        initCleanupTask();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerClick(PlayerInteractEvent event) {
        // Quick validation checks
        if (!isValidSpawnerInteraction(event)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        SpawnerData spawner = spawnerManager.getSpawnerByLocation(block.getLocation());
        if (!configManager.getBoolean("natural-spawner-interaction")) {
            if (NaturalSpawnerDetector.isNaturalDungeonSpawner(block, spawner)) {
                return;
            }
        }

        // Apply interaction cooldown
        if (!isInteractionAllowed(player)) {
            event.setCancelled(true);
            return;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Material itemType = heldItem.getType();

        // Allow normal block placement when sneaking with placeable block
        if (shouldAllowNormalBlockPlacement(player, itemType)) {
            return;
        }

        // Special handling for Bedrock players using tools
        if (isBedrockPlayerUsingTool(player, itemType)) {
            return;
        }

        // Prevent default interaction
        event.setCancelled(true);

        if (spawner != null && !spawner.getSpawnerActive()) {
            handleInactiveSpawnerInteraction(player, block, spawner, heldItem, itemType);
        }

        // Process spawner interaction
        handleSpawnerInteraction(player, block, heldItem, itemType);
    }

    /**
     * Validates if the event is a right-click on a spawner block
     */
    private boolean isValidSpawnerInteraction(PlayerInteractEvent event) {
        return event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock() != null &&
                event.getClickedBlock().getType() == Material.SPAWNER;
    }

    /**
     * Checks if the player's interaction should be allowed based on cooldown
     */
    private boolean isInteractionAllowed(Player player) {
        long currentTime = System.currentTimeMillis();
        Long lastInteraction = playerCooldowns.get(player.getUniqueId());

        if (lastInteraction != null && currentTime - lastInteraction < COOLDOWN_MS) {
            return false;
        }

        playerCooldowns.put(player.getUniqueId(), currentTime);
        return true;
    }

    /**
     * Determines if normal block placement should be allowed
     */
    private boolean shouldAllowNormalBlockPlacement(Player player, Material itemType) {
        return player.isSneaking() && itemType.isBlock() && itemType != Material.SPAWNER;
    }

    /**
     * Checks if the player is a Bedrock player using a tool
     */
    private boolean isBedrockPlayerUsingTool(Player player, Material itemType) {
        if (!isBedrockPlayer(player)) {
            return false;
        }

        String itemName = itemType.name();
        boolean isTool = itemName.endsWith("_PICKAXE") ||
                itemName.endsWith("_SHOVEL") ||
                itemName.endsWith("_HOE") ||
                itemName.endsWith("_AXE");

        return isTool;
    }

    /**
     * Main handler for spawner interactions
     */
    private void handleSpawnerInteraction(Player player, Block block, ItemStack heldItem, Material itemType) {

        // Get or create spawner data
        SpawnerData spawner = getOrCreateSpawnerData(block, player);

        // Check permission on claimed land
        if (!CheckOpenMenu.CanPlayerOpenMenu(player.getUniqueId(), block.getLocation())) {
            languageManager.sendMessage(player, "messages.spawner-protected");
            return;
        }

        // Handle spawn egg usage
        if (isSpawnEgg(itemType)) {
            spawnEggHandler.handleSpawnEggUse(player, (CreatureSpawner) block.getState(), spawner, heldItem);
            return;
        }

        // Handle spawner stacking
        if (itemType == Material.SPAWNER) {
            spawnerStackHandler.handleSpawnerStacking(player, block, spawner, heldItem);
            return;
        }

        // Open spawner menu if not using special items
        openSpawnerMenu(player, spawner);
    }

    private void handleInactiveSpawnerInteraction(Player player, Block block, SpawnerData spawner, ItemStack heldItem, Material itemType) {

        // Check permission on claimed land
        if (!CheckOpenMenu.CanPlayerOpenMenu(player.getUniqueId(), block.getLocation())) {
            languageManager.sendMessage(player, "messages.spawner-protected");
            return;
        }

        // Handle spawn egg usage
        if (isSpawnEgg(itemType)) {
            spawnEggHandler.handleSpawnEggUse(player, (CreatureSpawner) block.getState(), spawner, heldItem);
            return;
        }

        // Activate the spawner
        spawner.setSpawnerActive(true);
        spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());
        languageManager.sendMessage(player, "messages.activated");

        // Handle spawner stacking
        if (itemType == Material.SPAWNER) {
            spawnerStackHandler.handleSpawnerStacking(player, block, spawner, heldItem);
            return;
        }

        // Open spawner menu if not using special items
        openSpawnerMenu(player, spawner);
    }

    /**
     * Gets existing spawner data or creates a new one
     */
    private SpawnerData getOrCreateSpawnerData(Block block, Player player) {
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(block.getLocation());

        if (spawner == null) {
            // Create new spawner if it doesn't exist
            spawner = createNewSpawner(block, player);
        } else if (spawner.getEntityType() == null) {
            // Initialize existing spawner with null entity type
            initializeExistingSpawner(spawner, block);
        }

        return spawner;
    }

    /**
     * Opens the spawner menu if possible
     */
    private void openSpawnerMenu(Player player, SpawnerData spawner) {
        // Open the menu as normal
        spawnerMenuUI.openSpawnerMenu(player, spawner, false);
    }

    /**
     * Checks if the material is a spawn egg
     */
    private boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }

    /**
     * Creates a new spawner with default settings
     */
    private SpawnerData createNewSpawner(Block block, Player player) {
        // Generate short unique ID
        String spawnerId = UUID.randomUUID().toString().substring(0, 8);

        // Update the block state
        CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
        EntityType entityType = getValidEntityType(creatureSpawner.getSpawnedType());

        // Apply changes to the block
        creatureSpawner.setSpawnedType(entityType);
        creatureSpawner.update();

        // Show particles if enabled
        if (configManager.getBoolean("particles-spawner-activate")) {
            spawnActivationParticles(block.getLocation());
        }

        // Create and save spawner data
        SpawnerData spawner = new SpawnerData(spawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);

        spawnerManager.addSpawner(spawnerId, spawner);
        spawnerManager.queueSpawnerForSaving(spawnerId);

        // Notify player
        languageManager.sendMessage(player, "messages.activated");
        configManager.debug("Created new spawner with ID: " + spawnerId + " at " + block.getLocation());

        return spawner;
    }

    /**
     * Gets a valid entity type, defaulting if necessary
     */
    private EntityType getValidEntityType(EntityType type) {
        return (type == null || type == EntityType.UNKNOWN) ?
                configManager.getDefaultEntityType() : type;
    }

    /**
     * Spawns particles at the given location to indicate spawner activation
     */
    private void spawnActivationParticles(Location location) {
        location.getWorld().spawnParticle(
                ParticleWrapper.SPELL_WITCH,
                location.clone().add(0.5, 0.5, 0.5),
                50, 0.5, 0.5, 0.5, 0
        );
    }

    /**
     * Initializes an existing spawner with proper entity type
     */
    private void initializeExistingSpawner(SpawnerData spawner, Block block) {
        CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
        spawner.setEntityType(getValidEntityType(creatureSpawner.getSpawnedType()));
    }

    /**
     * Detects if a player is connecting from Bedrock Edition
     */
    private boolean isBedrockPlayer(Player player) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api != null && api.isFloodgatePlayer(player.getUniqueId());
        } catch (NoClassDefFoundError | NullPointerException e) {
            return false;
        }
    }

    /**
     * Initializes the periodic cooldown cleanup task
     */
    private void initCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::cleanupCooldowns,
                CLEANUP_INTERVAL_TICKS,
                CLEANUP_INTERVAL_TICKS
        );
    }

    /**
     * Removes expired cooldown entries
     */
    public void cleanupCooldowns() {
        long expirationThreshold = System.currentTimeMillis() - (COOLDOWN_MS * 10);
        playerCooldowns.entrySet().removeIf(entry -> entry.getValue() < expirationThreshold);
    }

    public void cleanup() {
        playerCooldowns.clear();
    }
}