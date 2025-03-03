package github.nighter.smartspawner.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

import com.google.gson.Gson;

public class SpawnerDataConverter {
    private final SmartSpawner plugin;
    private final FileConfiguration oldConfig;
    private final FileConfiguration newConfig;
    private static final Gson gson = new Gson();

    public SpawnerDataConverter(SmartSpawner plugin, FileConfiguration oldConfig, FileConfiguration newConfig) {
        this.plugin = plugin;
        this.oldConfig = oldConfig;
        this.newConfig = newConfig;
    }

    public void convertData() {
        ConfigurationSection spawnersSection = oldConfig.getConfigurationSection("spawners");
        if (spawnersSection == null) return;

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                convertSpawner(spawnerId);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to convert spawner " + spawnerId);
                e.printStackTrace();
            }
        }
    }

    private void convertSpawner(String spawnerId) {
        String oldPath = "spawners." + spawnerId;

        try {
            // Format location
            String worldName = oldConfig.getString(oldPath + ".world");
            int x = oldConfig.getInt(oldPath + ".x");
            int y = oldConfig.getInt(oldPath + ".y");
            int z = oldConfig.getInt(oldPath + ".z");

            // Format settings string
            String settings = String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%b",
                    oldConfig.getInt(oldPath + ".spawnerExp"),
                    oldConfig.getBoolean(oldPath + ".spawnerActive"),
                    oldConfig.getInt(oldPath + ".spawnerRange"),
                    oldConfig.getBoolean(oldPath + ".spawnerStop"),
                    oldConfig.getInt(oldPath + ".spawnDelay"),
                    oldConfig.getInt(oldPath + ".maxSpawnerLootSlots"),
                    oldConfig.getInt(oldPath + ".maxStoredExp"),
                    oldConfig.getInt(oldPath + ".minMobs"),
                    oldConfig.getInt(oldPath + ".maxMobs"),
                    oldConfig.getInt(oldPath + ".stackSize"),
                    oldConfig.getLong(oldPath + ".lastSpawnTime"),
                    oldConfig.getBoolean(oldPath + ".allowEquipmentItems")
            );

            // Format inventory
            List<String> newInventoryFormat = new ArrayList<>();
            ConfigurationSection invSection = oldConfig.getConfigurationSection(oldPath + ".virtualInventory");
            if (invSection != null) {
                List<String> serializedItems = invSection.getStringList("items");
                Map<String, Map<Integer, Integer>> durabilityItems = new HashMap<>(); // Material -> (Durability -> Count)
                Map<String, Integer> regularItems = new HashMap<>(); // For items without durability

                for (String serialized : serializedItems) {
                    try {
                        String[] parts = serialized.split(":", 2);
                        if (parts.length == 2) {
                            ItemStack item = itemStackFromJson(parts[1]);
                            if (item != null) {
                                if (item.getType() == Material.TIPPED_ARROW) {
                                    ItemMeta meta = item.getItemMeta();
                                    if (meta instanceof PotionMeta && ((PotionMeta) meta).hasCustomEffects()) {
                                        PotionEffect effect = ((PotionMeta) meta).getCustomEffects().get(0);
                                        String itemKey = String.format("TIPPED_ARROW#%s;%d;%d",
                                                effect.getType().getName(),
                                                effect.getDuration(),
                                                effect.getAmplifier());
                                        regularItems.merge(itemKey, item.getAmount(), Integer::sum);
                                    } else {
                                        regularItems.merge("ARROW", item.getAmount(), Integer::sum);
                                    }
                                } else if (isDestructibleItem(item.getType())) {
                                    // Handle items with durability
                                    String itemType = item.getType().name();
                                    durabilityItems.computeIfAbsent(itemType, k -> new TreeMap<>())
                                            .merge((int) item.getDurability(), item.getAmount(), Integer::sum);
                                } else {
                                    // Handle regular items
                                    regularItems.merge(item.getType().name(), item.getAmount(), Integer::sum);
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to convert item in spawner " + spawnerId + ": " + e.getMessage());
                    }
                }

                // Add regular items to the output
                for (Map.Entry<String, Integer> entry : regularItems.entrySet()) {
                    newInventoryFormat.add(entry.getKey() + ":" + entry.getValue());
                }

                // Add items with durability to the output using the correct format
                for (Map.Entry<String, Map<Integer, Integer>> itemEntry : durabilityItems.entrySet()) {
                    StringBuilder itemString = new StringBuilder(itemEntry.getKey());
                    if (!itemEntry.getValue().isEmpty()) {
                        itemString.append(";");
                        boolean first = true;
                        for (Map.Entry<Integer, Integer> durabilityEntry : itemEntry.getValue().entrySet()) {
                            if (!first) {
                                itemString.append(",");
                            }
                            itemString.append(durabilityEntry.getKey())
                                    .append(":")
                                    .append(durabilityEntry.getValue());
                            first = false;
                        }
                    }
                    newInventoryFormat.add(itemString.toString());
                }
            }

            // Build the complete spawner section
            String spawnerPath = "spawners." + spawnerId;
            newConfig.set(spawnerPath + ".location", String.format("%s,%d,%d,%d", worldName, x, y, z));
            newConfig.set(spawnerPath + ".entityType", oldConfig.getString(oldPath + ".entityType"));
            newConfig.set(spawnerPath + ".settings", settings);
            newConfig.set(spawnerPath + ".inventory", newInventoryFormat);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to convert spawner " + spawnerId + ": " + e.getMessage());
            throw e;
        }
    }


    private boolean isDestructibleItem(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SPADE")
                || name.endsWith("_HOE")
                || name.equals("BOW")
                || name.equals("FISHING_ROD")
                || name.equals("FLINT_AND_STEEL")
                || name.equals("SHEARS")
                || name.equals("SHIELD")
                || name.equals("ELYTRA")
                || name.equals("TRIDENT")
                || name.equals("CROSSBOW")
                || name.startsWith("LEATHER_")
                || name.startsWith("CHAINMAIL_")
                || name.startsWith("IRON_")
                || name.startsWith("GOLDEN_")
                || name.startsWith("DIAMOND_")
                || name.startsWith("NETHERITE_");
    }

    public static ItemStack itemStackFromJson(String data) {
        JsonObject json = gson.fromJson(data, JsonObject.class);
        ItemStack item = new ItemStack(
                Material.valueOf(json.get("type").getAsString()),
                json.get("amount").getAsInt(),
                (short) json.get("durability").getAsInt()
        );

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (json.has("displayName")) {
                meta.setDisplayName(json.get("displayName").getAsString());
            }

            if (json.has("lore")) {
                List<String> lore = new ArrayList<>();
                JsonArray loreArray = json.getAsJsonArray("lore");
                for (JsonElement element : loreArray) {
                    lore.add(element.getAsString());
                }
                meta.setLore(lore);
            }

            if (json.has("enchantments")) {
                JsonObject enchants = json.getAsJsonObject("enchantments");
                for (Map.Entry<String, JsonElement> entry : enchants.entrySet()) {
                    Enchantment enchantment = Enchantment.getByName(entry.getKey());
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, entry.getValue().getAsInt(), true);
                    }
                }
            }

            if (meta instanceof PotionMeta && json.has("potionData")) {
                PotionMeta potionMeta = (PotionMeta) meta;
                JsonObject potionData = json.getAsJsonObject("potionData");

                if (potionData.has("customEffects")) {
                    JsonArray customEffects = potionData.getAsJsonArray("customEffects");
                    for (JsonElement element : customEffects) {
                        JsonObject effectObj = element.getAsJsonObject();
                        PotionEffectType type = PotionEffectType.getByName(
                                effectObj.get("type").getAsString()
                        );
                        if (type != null) {
                            PotionEffect effect = new PotionEffect(
                                    type,
                                    effectObj.get("duration").getAsInt(),
                                    effectObj.get("amplifier").getAsInt(),
                                    effectObj.get("ambient").getAsBoolean(),
                                    effectObj.get("particles").getAsBoolean(),
                                    effectObj.get("icon").getAsBoolean()
                            );
                            potionMeta.addCustomEffect(effect, true);
                        }
                    }
                }
            }

            item.setItemMeta(meta);
        }

        return item;
    }
}