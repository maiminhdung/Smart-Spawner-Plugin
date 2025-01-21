package me.nighter.smartSpawner.serializers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemStackSerializer {
    private static final Gson gson = new Gson();

    public static String itemStackToJson(ItemStack item) {
        JsonObject json = new JsonObject();
        json.addProperty("type", item.getType().name());
        json.addProperty("amount", item.getAmount());
        json.addProperty("durability", item.getDurability());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                json.addProperty("displayName", meta.getDisplayName());
            }

            if (meta.hasLore()) {
                JsonArray loreArray = new JsonArray();
                for (String loreLine : meta.getLore()) {
                    loreArray.add(loreLine);
                }
                json.add("lore", loreArray);
            }

            if (!meta.getEnchants().isEmpty()) {
                JsonObject enchants = new JsonObject();
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    enchants.addProperty(entry.getKey().getName(), entry.getValue());
                }
                json.add("enchantments", enchants);
            }

            // Xử lý Tipped Arrow
            if (meta instanceof PotionMeta) {
                PotionMeta potionMeta = (PotionMeta) meta;
                JsonObject potionData = new JsonObject();

                // Lưu custom effects
                if (potionMeta.hasCustomEffects()) {
                    JsonArray customEffects = new JsonArray();
                    for (PotionEffect effect : potionMeta.getCustomEffects()) {
                        JsonObject effectObj = new JsonObject();
                        effectObj.addProperty("type", effect.getType().getName());
                        effectObj.addProperty("duration", effect.getDuration());
                        effectObj.addProperty("amplifier", effect.getAmplifier());
                        effectObj.addProperty("ambient", effect.isAmbient());
                        effectObj.addProperty("particles", effect.hasParticles());
                        effectObj.addProperty("icon", effect.hasIcon());
                        customEffects.add(effectObj);
                    }
                    potionData.add("customEffects", customEffects);
                }

                json.add("potionData", potionData);
            }
        }

        return gson.toJson(json);
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

            // Khôi phục Tipped Arrow data
            if (meta instanceof PotionMeta && json.has("potionData")) {
                PotionMeta potionMeta = (PotionMeta) meta;
                JsonObject potionData = json.getAsJsonObject("potionData");

                // Khôi phục custom effects
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