package github.nighter.smartspawner.utils;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for efficiently updating ItemMeta using Bukkit API.
 * Optimized to minimize the impact of item updates.
 */
public class ItemUpdater {
    private static final Logger LOGGER = Logger.getLogger("SmartSpawner");

    /**
     * Updates the lore of an ItemStack efficiently by checking if changes are needed.
     *
     * @param item The ItemStack to update
     * @param newLore The new lore list
     * @return true if update was successful, false otherwise
     */
    public static boolean updateLore(ItemStack item, List<String> newLore) {
        if (item == null || newLore == null) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            // Apply color codes to new lore
            List<String> coloredLore = colorLoreList(newLore);

            // Check if lore is different to avoid unnecessary updates
            if (meta.hasLore() && areLoreListsEqual(meta.getLore(), coloredLore)) {
                return true; // No changes needed
            }

            // Update lore
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update lore", e);
            return false;
        }
    }

    /**
     * Updates the display name of an ItemStack if changes are needed.
     *
     * @param item The ItemStack to update
     * @param displayName The new display name
     * @return true if update was successful, false otherwise
     */
    public static boolean updateDisplayName(ItemStack item, String displayName) {
        if (item == null || displayName == null) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            // Apply color codes
            String coloredName = ChatColor.translateAlternateColorCodes('&', displayName);

            // Check if name is different to avoid unnecessary updates
            if (meta.hasDisplayName() && meta.getDisplayName().equals(coloredName)) {
                return true; // No changes needed
            }

            // Update name
            meta.setDisplayName(coloredName);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update display name", e);
            return false;
        }
    }

    /**
     * Update both display name and lore in a single operation if changes are needed.
     *
     * @param item The ItemStack to update
     * @param displayName The new display name (or null to leave unchanged)
     * @param lore The new lore (or null to leave unchanged)
     * @return true if update was successful, false otherwise
     */
    public static boolean updateItemMeta(ItemStack item, String displayName, List<String> lore) {
        if (item == null) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            boolean needsUpdate = false;

            // Check and update display name if needed
            if (displayName != null) {
                String coloredName = ChatColor.translateAlternateColorCodes('&', displayName);
                if (!meta.hasDisplayName() || !meta.getDisplayName().equals(coloredName)) {
                    meta.setDisplayName(coloredName);
                    needsUpdate = true;
                }
            }

            // Check and update lore if needed
            if (lore != null) {
                List<String> coloredLore = colorLoreList(lore);
                if (!meta.hasLore() || !areLoreListsEqual(meta.getLore(), coloredLore)) {
                    meta.setLore(coloredLore);
                    needsUpdate = true;
                }
            }

            // Only update if changes were made
            if (needsUpdate) {
                item.setItemMeta(meta);
            }

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update item meta", e);
            return false;
        }
    }

    /**
     * Updates a specific line in the lore without replacing the entire lore list.
     *
     * @param item The ItemStack to update
     * @param lineIndex The index of the line to update (0-based)
     * @param newLine The new content for the line
     * @return true if update was successful, false otherwise
     */
    public static boolean updateLoreLine(ItemStack item, int lineIndex, String newLine) {
        if (item == null || newLine == null || lineIndex < 0) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            // Ensure list is large enough
            while (lore.size() <= lineIndex) {
                lore.add("");
            }

            // Apply color codes
            String coloredLine = ChatColor.translateAlternateColorCodes('&', newLine);

            // Check if line is different to avoid unnecessary updates
            if (lore.get(lineIndex).equals(coloredLine)) {
                return true; // No changes needed
            }

            // Update the line
            lore.set(lineIndex, coloredLine);
            meta.setLore(lore);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update lore line", e);
            return false;
        }
    }

    /**
     * Applies color codes to a list of lore strings.
     */
    private static List<String> colorLoreList(List<String> lore) {
        List<String> coloredLore = new ArrayList<>(lore.size());
        for (String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return coloredLore;
    }

    /**
     * Compares two lore lists for equality.
     */
    private static boolean areLoreListsEqual(List<String> lore1, List<String> lore2) {
        if (lore1.size() != lore2.size()) {
            return false;
        }

        for (int i = 0; i < lore1.size(); i++) {
            if (!lore1.get(i).equals(lore2.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Utility method to find a specific line in lore based on a search string.
     *
     * @param lore The lore list to search in
     * @param search The text to search for (can be partial)
     * @return The index of the matching line, or -1 if not found
     */
    public static int findLoreLine(List<String> lore, String search) {
        if (lore == null || search == null) {
            return -1;
        }

        String strippedSearch = ChatColor.stripColor(search);

        for (int i = 0; i < lore.size(); i++) {
            String strippedLine = ChatColor.stripColor(lore.get(i));
            if (strippedLine.contains(strippedSearch)) {
                return i;
            }
        }

        return -1;
    }
}