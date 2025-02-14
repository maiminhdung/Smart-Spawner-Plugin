package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.nms.ParticleWrapper;
import me.nighter.smartSpawner.utils.*;
import me.nighter.smartSpawner.managers.*;
import me.nighter.smartSpawner.nms.ParticleWrapper;
import me.nighter.smartSpawner.hooks.protections.CheckOpenMenu;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.commands.SpawnerListCommand;
import me.nighter.smartSpawner.holders.SpawnerStackerHolder;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerListener implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private final SpawnerStackHandler stackHandler;
    private final SpawnerListCommand listCommand;
    private static final Set<Material> SPAWNER_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD, Material.SPAWNER, Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD
    );
    // Click cooldown for optimization
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 250;

    public SpawnerListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.stackHandler = plugin.getSpawnerStackHandler();
        this.listCommand = new SpawnerListCommand(plugin);

        startCleanupTask();
    }

    // Helper method to create new spawner
    private SpawnerData createNewSpawner(Block block, Player player) {
        String newSpawnerId = UUID.randomUUID().toString().substring(0, 8);
        CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
        EntityType entityType = creatureSpawner.getSpawnedType();

        // Handle null entityType
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            entityType = configManager.getDefaultEntityType();
        }

        creatureSpawner.setSpawnedType(entityType);
        creatureSpawner.update();
        if (configManager.isSpawnerCreateParticlesEnabled()) {
            Location loc = block.getLocation();
            loc.getWorld().spawnParticle(
                    ParticleWrapper.SPELL_WITCH,
                    loc.clone().add(0.5, 0.5, 0.5),
                    50, 0.5, 0.5, 0.5, 0
            );
        }

        // Create new spawner with default config values
        SpawnerData spawner = new SpawnerData(newSpawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);

        // Add to manager and save
        spawnerManager.addSpawner(newSpawnerId, spawner);
        spawnerManager.saveSpawnerData();
        languageManager.sendMessage(player, "messages.activated");
        configManager.debug("Created new spawner with ID: " + newSpawnerId + " at " + block.getLocation());
        return spawner;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerClick(PlayerInteractEvent event) {
        // Early exit if not right-click
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        // Cache block to avoid multiple calls
        Block block = event.getClickedBlock();
        if (block.getType() != Material.SPAWNER) return;

        // Cache player and check cooldown
        Player player = event.getPlayer();
        if (!checkCooldown(player)) {
            event.setCancelled(true);
            return;
        }

        // Cache item to avoid multiple getter calls
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        Material itemType = itemInHand.getType();

        // Allow block placement when sneaking with placeable block
        if (player.isSneaking() && itemType.isBlock() && itemType != Material.SPAWNER) {
            event.setCancelled(false);
            return;
        }

        // Handle Bedrock player tool usage
        if (isBedrockPlayer(player)) {
            String itemName = itemType.name();
            if (itemName.endsWith("_PICKAXE") || itemName.endsWith("_SHOVEL") ||
                    itemName.endsWith("_HOE") || itemName.endsWith("_AXE")) {
                event.setCancelled(false);
                return;
            }
        }

        // Prevent further event processing
        event.setCancelled(true);

        // Get spawner data with O(1) lookup
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(block.getLocation());

        // Handle spawner initialization
        if (spawner == null) {
            spawner = createNewSpawner(block, player);
        } else if (spawner.getEntityType() == null) {
            initializeExistingSpawner(spawner, block);
        }

        // Check if player can open menu on private land claims(GriefPrevention, Lands, etc.)
        if (!CheckOpenMenu.CanPlayerOpenMenu(player.getUniqueId(), block.getLocation())) {
            languageManager.sendMessage(player, "messages.spawner-protected");
            return;
        }

        // Handle spawn egg usage
        if (isSpawnEgg(itemType)) {
            handleSpawnEggUse(player, (CreatureSpawner) block.getState(), spawner, itemInHand);
            return;
        }

        // Handle spawner stacking
        if (itemType == Material.SPAWNER) {
            handleSpawnerStacking(player, block, spawner, itemInHand);
            return;
        }

        // Open menu
        openSpawnerMenu(player, spawner, false);
    }

    private boolean checkCooldown(Player player) {
        long currentTime = System.currentTimeMillis();
        Long lastInteraction = playerCooldowns.get(player.getUniqueId());

        if (lastInteraction != null) {
            if (currentTime - lastInteraction < COOLDOWN_MS) {
                return false;
            }
        }

        playerCooldowns.put(player.getUniqueId(), currentTime);
        return true;
    }

    private void startCleanupTask() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,
                task -> cleanupCooldowns(),
                6000L,  // 5 minutes initial delay
                6000L   // 5 minutes between runs
        );
    }

    // Clean up cooldowns periodically (call this every 5-10 minutes)
    public void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        playerCooldowns.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > COOLDOWN_MS * 10);
    }

    private void initializeExistingSpawner(SpawnerData spawner, Block block) {
        CreatureSpawner cs = (CreatureSpawner) block.getState();
        EntityType entityType = cs.getSpawnedType();
        spawner.setEntityType(entityType == null || entityType == EntityType.UNKNOWN ?
                configManager.getDefaultEntityType() : entityType);
    }

    private void handleSpawnerStacking(Player player, Block block, SpawnerData spawner, ItemStack itemInHand) {
        boolean success = stackHandler.handleSpawnerStack(player, spawner, itemInHand, player.isSneaking());
        if (success) {
            if (player.isSneaking()) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                if (configManager.isSpawnerStackParticlesEnabled()) {
                    block.getWorld().spawnParticle(ParticleWrapper.VILLAGER_HAPPY,
                            block.getLocation().add(0.5, 0.5, 0.5),
                            10, 0.3, 0.3, 0.3, 0);
                }
            }
        }
    }

    protected void openSpawnerMenu(Player player, SpawnerData spawner, boolean refresh) {

        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String title;
        if (spawner.getStackSize() >1){
            title = languageManager.getGuiTitle("gui-title.stacked-menu", "%amount%", String.valueOf(spawner.getStackSize()), "%entity%", entityName);
        } else {
            title = languageManager.getGuiTitle("gui-title.menu", "%entity%", entityName);
        }

        // Create new inventory with custom holder
        Inventory menu = Bukkit.createInventory(new SpawnerMenuHolder(spawner), 27, title);

        // Create chest item
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chestItem.getItemMeta();
        chestMeta.setDisplayName(languageManager.getMessage("spawner-loot-item.name"));

        List<String> chestLore = new ArrayList<>();
        OptimizedVirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = (int) ((double) currentItems / maxSlots * 100);
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest")
                .replace("%max_slots%", String.valueOf(maxSlots))
                .replace("%current_items%", String.valueOf(currentItems))
                .replace("%percent_storage%", String.valueOf(percentStorage));

        chestLore.addAll(Arrays.asList(loreMessageChest.split("\n")));
        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);

        // Create spawner info item
        ItemStack spawnerItem = SpawnerHeadManager.getCustomHead(spawner.getEntityType(), player);
        ItemMeta spawnerMeta = spawnerItem.getItemMeta();
        spawnerMeta.setDisplayName(languageManager.getMessage("spawner-info-item.name"));
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%entity%", entityName);
        replacements.put("%stack_size%", String.valueOf(spawner.getStackSize()));
        replacements.put("%range%", String.valueOf(spawner.getSpawnerRange()));
        replacements.put("%delay%", String.valueOf(spawner.getSpawnDelay() / 20)); // Convert ticks to seconds
        replacements.put("%min_mobs%", String.valueOf(spawner.getMinMobs()));
        replacements.put("%max_mobs%", String.valueOf(spawner.getMaxMobs()));
        String lorePath = "spawner-info-item.lore.spawner-info";
        String loreMessage = languageManager.getMessage(lorePath, replacements);
        List<String> lore = Arrays.asList(loreMessage.split("\n"));
        spawnerMeta.setLore(lore);
        spawnerItem.setItemMeta(spawnerMeta);

        // Create exp bottle item
        ItemStack expItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta expMeta = expItem.getItemMeta();
        Map<String, String> nameReplacements = new HashMap<>();
        String formattedExp = languageManager.formatNumber(spawner.getSpawnerExp());
        String formattedMaxExp = languageManager.formatNumber(spawner.getMaxStoredExp());
        int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

        nameReplacements.put("%current_exp%", String.valueOf(spawner.getSpawnerExp()));
        expMeta.setDisplayName(languageManager.getMessage("exp-info-item.name", nameReplacements));
        Map<String, String> loreReplacements = new HashMap<>();
        loreReplacements.put("%current_exp%", formattedExp);
        loreReplacements.put("%max_exp%", formattedMaxExp);
        loreReplacements.put("%percent_exp%", String.valueOf(percentExp));
        loreReplacements.put("%u_max_exp%", String.valueOf(spawner.getMaxStoredExp()));
        String lorePathExp = "exp-info-item.lore.exp-bottle";
        String loreMessageExp = languageManager.getMessage(lorePathExp, loreReplacements);
        List<String> loreEx = Arrays.asList(loreMessageExp.split("\n"));
        expMeta.setLore(loreEx);
        expItem.setItemMeta(expMeta);

        // Set items in menu
        menu.setItem(11, chestItem);
        menu.setItem(13, spawnerItem);
        menu.setItem(15, expItem);

        // Open menu and play sound
        player.openInventory(menu);
        if (!refresh) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerMenuHolder)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        SpawnerMenuHolder holder = (SpawnerMenuHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();

        // Check if clicked in top inventory (menu)
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof SpawnerMenuHolder)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        switch (clickedItem.getType()) {
            case CHEST:
                handleChestClick(player, spawner);
                break;

            case PLAYER_HEAD, SPAWNER, ZOMBIE_HEAD, SKELETON_SKULL, WITHER_SKELETON_SKULL, CREEPER_HEAD, PIGLIN_HEAD:
                handleSpawnerInfoClick(player, spawner);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                break;

            case EXPERIENCE_BOTTLE:
                handleExpBottleClick(player, spawner);
                break;
        }
    }

    // Helper method to create buttons
    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        button.setItemMeta(meta);
        return button;
    }

    private void handleSpawnerInfoClick(Player player, SpawnerData spawner) {
        // Create new inventory with 27 slots
        String title = languageManager.getMessage("gui-title.stacker-menu");
        Inventory gui = Bukkit.createInventory(new SpawnerStackerHolder(spawner), 27, title);

        // Create decrease buttons
        ItemStack decreaseBy64 = createButton(Material.RED_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.decrease-64"),
                Arrays.asList(languageManager.getMessage("button.lore.remove")
                        .replace("%amount%", "64")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        decreaseBy64.setAmount(64);

        ItemStack decreaseBy16 = createButton(Material.RED_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.decrease-16"),
                Arrays.asList(languageManager.getMessage("button.lore.remove")
                        .replace("%amount%", "16")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        decreaseBy16.setAmount(16);

        ItemStack decreaseBy1 = createButton(Material.RED_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.decrease-1"),
                Arrays.asList(languageManager.getMessage("button.lore.remove")
                        .replace("%amount%", "1")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        decreaseBy1.setAmount(1);

        // Create increase buttons
        ItemStack increaseBy64 = createButton(Material.LIME_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.increase-64"),
                Arrays.asList(languageManager.getMessage("button.lore.add")
                        .replace("%amount%", "64")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        increaseBy64.setAmount(64);

        ItemStack increaseBy16 = createButton(Material.LIME_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.increase-16"),
                Arrays.asList(languageManager.getMessage("button.lore.add")
                        .replace("%amount%", "16")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        increaseBy16.setAmount(16);

        ItemStack increaseBy1 = createButton(Material.LIME_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.increase-1"),
                Arrays.asList(languageManager.getMessage("button.lore.add")
                        .replace("%amount%", "1")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        increaseBy1.setAmount(1);

        ItemStack spawnerItem = createButton(Material.SPAWNER,
                languageManager.getMessage("button.name.spawner","%entity%", languageManager.getFormattedMobName(spawner.getEntityType())),
                Arrays.asList(languageManager.getMessage("button.lore.spawner")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .replace("%max_stack_size%", String.valueOf(configManager.getMaxStackSize()))
                        .replace("%entity%", languageManager.getFormattedMobName(spawner.getEntityType()))
                        .split("\n")));

        // Set items in inventory
        gui.setItem(9, decreaseBy64);
        gui.setItem(10, decreaseBy16);
        gui.setItem(11, decreaseBy1);
        gui.setItem(13, spawnerItem);
        gui.setItem(15, increaseBy1);
        gui.setItem(16, increaseBy16);
        gui.setItem(17, increaseBy64);

        player.openInventory(gui);
    }

    // Add new event handler for the stack control GUI
    @EventHandler
    public void onStackControlClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        SpawnerStackerHolder holder = (SpawnerStackerHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Return to main menu if spawner is clicked
        if (clicked.getType() == Material.SPAWNER) {
            openSpawnerMenu(player, spawner, true);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK
                    , 1.0f, 1.0f);
            return;
        }

        int maxStackSize = configManager.getMaxStackSize();

        // Get the display name of clicked item to determine the change amount
        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName == null) return;

        // Determine the change amount based on the clicked item's name
        int change = getChangeAmount(displayName);
        if (change == 0) return;

        handleStackChange(player, spawner, change, maxStackSize);
        handleSpawnerInfoClick(player, spawner);
    }

    private void handleStackChange(Player player, SpawnerData spawner, int change, int maxStackSize) {
        int currentSize = spawner.getStackSize();
        int targetSize = currentSize + change;

        if (change > 0) {
            // Handle stack increase
            int spaceLeft = maxStackSize - currentSize;
            if (spaceLeft <= 0) {
                languageManager.sendMessage(player, "messages.stack-full");
                return;
            }

            int actualChange = Math.min(change, spaceLeft);
            int validSpawners = countValidSpawnersInInventory(player, spawner.getEntityType());

            if (validSpawners == 0 && hasDifferentSpawnerType(player, spawner.getEntityType())) {
                languageManager.sendMessage(player, "messages.different-type");
                return;
            }

            if (validSpawners < actualChange) {
                languageManager.sendMessage(player, "messages.not-enough-spawners",
                        "%amountChange%", String.valueOf(actualChange),
                        "%amountAvailable%", String.valueOf(validSpawners));
                return;
            }

            removeValidSpawnersFromInventory(player, spawner.getEntityType(), actualChange);
            spawner.setStackSize(currentSize + actualChange, player);

            if (actualChange < change) {
                languageManager.sendMessage(player, "messages.stack-full-overflow",
                        "%amount%", String.valueOf(actualChange));
            }
        } else {
            // Handle stack decrease
            int removeAmount = Math.abs(change);
            if (targetSize < 1) {
                int actualChange = -(currentSize - 1);
                removeAmount = Math.abs(actualChange);
                languageManager.sendMessage(player, "messages.cannot-go-below-one", "%amount%", String.valueOf(removeAmount));
                return;
            }

            spawner.setStackSize(targetSize, player);
            giveSpawnersWithMergeAndDrop(player, removeAmount, spawner.getEntityType());
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Refresh spawner menu for all players who have it open
        refreshSpawnerMenuForAllPlayers(spawner);
    }

    private EntityType getSpawnerEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return null;
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType spawnerEntity = spawner.getSpawnedType();

        // Support for stacking spawners with Spawner from EconomyShopGUI
        if (spawnerEntity == null) {
            String displayName = meta.getDisplayName();
            if (displayName.matches("§9§l[A-Za-z]+(?: [A-Za-z]+)? §rSpawner")) {
                String entityName = displayName
                        .replaceAll("§9§l", "")
                        .replaceAll(" §rSpawner", "")
                        .replace(" ", "_")
                        .toUpperCase();
                try {
                    spawnerEntity = EntityType.valueOf(entityName);
                } catch (IllegalArgumentException e) {
                    configManager.debug("Could not find entity type: " + entityName);
                }
            }
        }

        return spawnerEntity;
    }

    private boolean hasDifferentSpawnerType(Player player, EntityType requiredType) {
        for (ItemStack item : player.getInventory().getContents()) {
            EntityType spawnerEntity = getSpawnerEntityType(item);
            if (spawnerEntity != null && spawnerEntity != requiredType) {
                return true;
            }
        }
        return false;
    }

    private int countValidSpawnersInInventory(Player player, EntityType requiredType) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            EntityType spawnerEntity = getSpawnerEntityType(item);
            if (spawnerEntity == requiredType) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeValidSpawnersFromInventory(Player player, EntityType requiredType, int amountToRemove) {
        int remainingToRemove = amountToRemove;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remainingToRemove > 0; i++) {
            ItemStack item = contents[i];
            EntityType spawnerEntity = getSpawnerEntityType(item);

            if (spawnerEntity == requiredType) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remainingToRemove) {
                    player.getInventory().setItem(i, null);
                    remainingToRemove -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remainingToRemove);
                    remainingToRemove = 0;
                }
            }
        }
        player.updateInventory();
    }

    private ItemStack createSpawnerItem(EntityType entityType) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null) {
            if (entityType != null && entityType != EntityType.UNKNOWN) {
                // Set display name
                String entityTypeName = languageManager.getFormattedMobName(entityType);
                String displayName = languageManager.getMessage("spawner-name","%entity%",entityTypeName);
                meta.setDisplayName(displayName);

                // Store entity type in item NBT
                BlockStateMeta blockMeta = (BlockStateMeta) meta;
                CreatureSpawner cs = (CreatureSpawner) blockMeta.getBlockState();
                cs.setSpawnedType(entityType);
                blockMeta.setBlockState(cs);

                // Add lore
//                List<String> lore = new ArrayList<>();
//                lore.add(ChatColor.GRAY + "Entity: " + StringUtils.capitalize(entityName));
//                meta.setLore(lore);

            }
            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    private synchronized void giveSpawnersWithMergeAndDrop(Player player, int amount, EntityType entityType) {
        ItemStack[] contents = player.getInventory().getContents();
        int remainingAmount = amount;

        // Phase 1: Merge với các stack chưa đầy trong inventory
        for (int i = 0; i < contents.length && remainingAmount > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.SPAWNER) {
                // Kiểm tra entity type của spawner trong inventory
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof BlockStateMeta) {
                    BlockStateMeta blockMeta = (BlockStateMeta) meta;
                    CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();

                    // Chỉ merge với spawner cùng loại
                    if (spawner.getSpawnedType() == entityType) {
                        int currentAmount = item.getAmount();
                        if (currentAmount < 64) {
                            int canAdd = Math.min(64 - currentAmount, remainingAmount);
                            item.setAmount(currentAmount + canAdd);
                            remainingAmount -= canAdd;
                        }
                    }
                }
            }
        }

        // Phase 2: Tạo stack mới cho số còn lại và drop nếu không đủ chỗ
        if (remainingAmount > 0) {
            Location dropLoc = player.getLocation();
            while (remainingAmount > 0) {
                int stackSize = Math.min(64, remainingAmount);
                // Tạo spawner với đúng entity type
                ItemStack spawnerItem = createSpawnerItem(entityType);
                spawnerItem.setAmount(stackSize);

                // Thử thêm vào inventory trước
                Map<Integer, ItemStack> failed = player.getInventory().addItem(spawnerItem);

                // Nếu không thể thêm vào inventory, drop ra
                if (!failed.isEmpty()) {
                    failed.values().forEach(item ->
                            player.getWorld().dropItemNaturally(dropLoc, item)
                    );
                    languageManager.sendMessage(player, "messages.inventory-full-drop");
                }

                remainingAmount -= stackSize;
            }
        }

        // Cập nhật inventory
        player.updateInventory();
    }

    private int getChangeAmount(String displayName) {
        if (displayName.contains("-64")) return -64;
        else if (displayName.contains("-16")) return -16;
        else if (displayName.contains("-1 ")) return -1;
        else if (displayName.contains("+1 ")) return 1;
        else if (displayName.contains("+16")) return 16;
        else if (displayName.contains("+64")) return 64;
        return 0;
    }

    /**
     * Handle chest click to open spawner loot inventory
     */
    private void handleChestClick(Player player, SpawnerData spawner) {
        String title = languageManager.getGuiTitle("gui-title.loot-menu");
        SpawnerLootManager lootManager = new SpawnerLootManager(plugin);
        Inventory pageInventory = lootManager.createLootInventory(spawner, title, 1);

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        player.closeInventory();
        player.openInventory(pageInventory);
    }

    /**
     * Handle experience bottle click to give experience
     */
    private void handleExpBottleClick(Player player, SpawnerData spawner) {
        int exp = spawner.getSpawnerExp();

        if (exp <= 0) {
            languageManager.sendMessage(player, "messages.no-exp");
            return;
        }

        int remainingExp = exp;

        if (configManager.isAllowExpMending()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack mainHand = inventory.getItemInMainHand();
            ItemStack offHand = inventory.getItemInOffHand();
            ItemStack armor = inventory.getChestplate();

            List<ItemStack> itemsToCheck = Arrays.asList(mainHand, offHand, armor);

            for (ItemStack item : itemsToCheck) {
                if (item != null && !item.getType().equals(Material.AIR)) {
                    if (item.getEnchantments().containsKey(Enchantment.MENDING)) {
                        if (item.getDurability() > 0) {
                            int durabilityToRepair = Math.min(item.getDurability(), remainingExp * 2);
                            int expNeeded = durabilityToRepair / 2;

                            if (expNeeded > 0 && remainingExp >= expNeeded) {
                                item.setDurability((short) (item.getDurability() - durabilityToRepair));
                                remainingExp -= expNeeded;

                                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
                                player.spawnParticle(ParticleWrapper.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 5);
                            }
                        }
                    }
                }
            }
        }

        if (remainingExp > 0) {
            player.giveExp(remainingExp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        spawner.setSpawnerExp(0);

        // Refresh spawner menu for all players who have it open
        refreshSpawnerMenuForAllPlayers(spawner);

        if (remainingExp < exp) {
            languageManager.sendMessage(player, "messages.exp-collected-with-mending",
                    "%exp-mending%", languageManager.formatNumberTenThousand(exp-remainingExp), "%exp%", languageManager.formatNumberTenThousand(remainingExp));
        } else {
            languageManager.sendMessage(player, "messages.exp-collected", "%exp%", languageManager.formatNumberTenThousand(exp));
        }
    }

    private EntityType getEntityTypeFromSpawnEgg(Material material) {
        String entityName = material.name().replace("_SPAWN_EGG", "");
        try {
            return EntityType.valueOf(entityName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }

    private boolean isBedrockPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api.isFloodgatePlayer(uuid);
        } catch (NoClassDefFoundError | NullPointerException e) {
            return false;
        }
    }

    public void handleSpawnEggUse(Player player, CreatureSpawner spawner, SpawnerData spawnerData, ItemStack spawnEgg) {
        if (!player.hasPermission("smartspawner.changetype")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }

        EntityType newType = getEntityTypeFromSpawnEgg(spawnEgg.getType());

        if (newType != null && spawnerData != null) {
            spawnerData.setEntityType(newType);
            spawner.setSpawnedType(newType);
            spawner.update();

            languageManager.sendMessage(player, "messages.changed",
                    "%type%", languageManager.getFormattedMobName(newType));
        } else {
            languageManager.sendMessage(player, "messages.invalid-egg");
        }

        if (player.getGameMode() == GameMode.SURVIVAL) {
            spawnEgg.setAmount(spawnEgg.getAmount() - 1);
        }
    }

    @EventHandler
    public void onWorldSelectionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerListCommand.WorldSelectionHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        switch (event.getSlot()) {
            case 11 -> listCommand.openSpawnerListGUI(player, "world", 1);
            case 13 -> listCommand.openSpawnerListGUI(player, "world_nether", 1);
            case 15 -> listCommand.openSpawnerListGUI(player, "world_the_end", 1);
        }
    }

    @EventHandler
    public void onSpawnerListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerListCommand.SpawnerListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        // Navigation handling
        if (event.getSlot() == 45 && holder.getCurrentPage() > 1) {
            listCommand.openSpawnerListGUI(player, holder.getWorldName(), holder.getCurrentPage() - 1);
        } else if (event.getSlot() == 53 && holder.getCurrentPage() < holder.getTotalPages()) {
            listCommand.openSpawnerListGUI(player, holder.getWorldName(), holder.getCurrentPage() + 1);
        }
        // Back button
        else if (event.getSlot() == 49) {
            listCommand.openWorldSelectionGUI(player);
        }
        // Spawner click handling
        else if (SPAWNER_MATERIALS.contains(event.getCurrentItem().getType())) {
            handleSpawnerClick(event);
        }
    }

    private void handleSpawnerClick(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();
        if (displayName == null) return;

        // Extract spawner ID from display name, now handling alphanumeric IDs
        String patternString = languageManager.getMessage("spawner-list.spawner-item.id_pattern");
        configManager.debug("Pattern string: " + patternString);
        Pattern pattern = Pattern.compile(patternString);
        configManager.debug("Pattern: " + pattern);
        Matcher matcher = pattern.matcher(ChatColor.stripColor(displayName));
        configManager.debug("Matcher: " + ChatColor.stripColor(displayName));

        if (matcher.find()) {
            String spawnerId = matcher.group(1);
            configManager.debug("Clicked spawner ID: " + spawnerId);
            SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);

            if (spawner != null) {
                Player player = (Player) event.getWhoClicked();
                Location loc = spawner.getSpawnerLocation();
                player.teleport(loc);
                languageManager.sendMessage(player, "messages.teleported",
                        "%spawnerId%", spawnerId);
            } else {
                Player player = (Player) event.getWhoClicked();
                languageManager.sendMessage(player, "messages.not-found");
            }
        } else {
            Player player = (Player) event.getWhoClicked();
            languageManager.sendMessage(player, "messages.not-found");
        }
    }
    private void refreshSpawnerMenuForAllPlayers(SpawnerData spawner) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getOpenInventory().getTopInventory().getHolder() instanceof SpawnerMenuHolder) {
                SpawnerMenuHolder holder = (SpawnerMenuHolder) onlinePlayer.getOpenInventory().getTopInventory().getHolder();
                if (holder.getSpawnerData().equals(spawner)) {
                    openSpawnerMenu(onlinePlayer, spawner, true);
                }
            }
        }
    }
}