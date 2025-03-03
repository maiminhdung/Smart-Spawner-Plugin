package github.nighter.smartspawner.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import github.nighter.smartspawner.SmartSpawner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class UpdateChecker implements Listener {
    private final SmartSpawner plugin;
    private final String projectId;
    private String latestVersion;
    private boolean hasUpdate = false;
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/%s/version";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/";
    private static final int TIMEOUT_SECONDS = 5;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private BukkitTask updateTask;

    // Console colors
    private static final String CONSOLE_RESET = "\u001B[0m";
    private static final String CONSOLE_RED = "\u001B[31m";
    private static final String CONSOLE_GREEN = "\u001B[32m";
    private static final String CONSOLE_YELLOW = "\u001B[33m";
    private static final String CONSOLE_BLUE = "\u001B[34m";
    private static final String CONSOLE_PURPLE = "\u001B[35m";

    public UpdateChecker(SmartSpawner plugin, String projectId) {
        this.plugin = plugin;
        this.projectId = projectId;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    public void initialize() {
        if (!configManager.getBoolean("update-checker-enabled")) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Initial check after server starts (delayed by 1 minute)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () ->
                checkForUpdate().thenAccept(this::handleUpdateResult), 20L * 60L);

        // Schedule periodic checks
        long intervalTicks = configManager.getInt("update-checker-interval") * 20L * 60L * 60L; // Convert hours to ticks
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> checkForUpdate().thenAccept(this::handleUpdateResult),
                intervalTicks,
                intervalTicks
        );
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    private void handleUpdateResult(boolean hasUpdate) {
        if (hasUpdate && configManager.getBoolean("update-checker-notify-console")) {
            printUpdateAlert();
        }
    }

    private static class Version implements Comparable<Version> {
        private final int[] parts;
        private static final int MAX_PARTS = 4; // Updated to support 4 parts

        public Version(String version) {
            // Remove any non-numeric prefix (e.g., "v1.0.0" -> "1.0.0")
            version = version.replaceAll("[^0-9.].*$", "")
                    .replaceAll("^[^0-9]*", "");

            String[] split = version.split("\\.");
            parts = new int[MAX_PARTS]; // Initialize array with 4 parts

            // Parse each part, defaulting to 0 if not present or not a valid number
            for (int i = 0; i < MAX_PARTS; i++) {
                if (i < split.length) {
                    try {
                        parts[i] = Integer.parseInt(split[i]);
                    } catch (NumberFormatException e) {
                        parts[i] = 0;
                    }
                } else {
                    parts[i] = 0; // Fill remaining parts with 0
                }
            }
        }


        @Override
        public int compareTo(Version other) {
            // Compare all 4 parts
            for (int i = 0; i < MAX_PARTS; i++) {
                if (parts[i] != other.parts[i]) {
                    return parts[i] - other.parts[i];
                }
            }
            return 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            // Always include the first three parts
            for (int i = 0; i < 3; i++) {
                if (i > 0) sb.append('.');
                sb.append(parts[i]);
            }

            // Only include the fourth part if it's non-zero
            if (parts[3] > 0) {
                sb.append('.').append(parts[3]);
            }

            return sb.toString();
        }

        /**
         * Gets the version parts as an array
         * @return array of version parts
         */
        public int[] getParts() {
            return parts.clone();
        }

        /**
         * Checks if this version has a fourth number
         * @return true if the version has a fourth number greater than 0
         */
        public boolean hasFourthNumber() {
            return parts[3] > 0;
        }
    }

    private CompletableFuture<Boolean> checkForUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            String currentVersion = plugin.getDescription().getVersion();
            try {
                URL url = new URL(String.format(MODRINTH_API_URL, projectId));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", plugin.getName() + "/" + currentVersion);
                connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    JsonArray versions = new Gson().fromJson(reader, JsonArray.class);
                    Version currentVer = new Version(currentVersion);
                    Version latestVer = null;

                    // Find the latest release version
                    for (JsonElement versionElement : versions) {
                        JsonObject version = versionElement.getAsJsonObject();
                        if (version.get("version_type").getAsString().equals("release")) {
                            String versionNumber = version.get("version_number").getAsString();
                            Version ver = new Version(versionNumber);

                            if (latestVer == null || ver.compareTo(latestVer) > 0) {
                                latestVer = ver;
                                latestVersion = versionNumber;
                            }
                        }
                    }

                    if (latestVer != null && latestVer.compareTo(currentVer) > 0) {
                        plugin.getLogger().info("Found new version: Current=" + currentVer + ", Latest=" + latestVer);
                        hasUpdate = true;
                        return true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
            return false;
        });
    }

    private void printUpdateAlert() {
        String currentVersion = plugin.getDescription().getVersion();
        //String downloadUrl = MODRINTH_PROJECT_URL + projectId;
        String downloadUrl = "https://modrinth.com/plugin/smart-spawner-plugin";

        String[] consoleMessage = {
                CONSOLE_PURPLE + "╔═══════════════════════ UPDATE AVAILABLE ═════════════════════╗" + CONSOLE_RESET,
                CONSOLE_PURPLE + "║" + CONSOLE_RESET + "                                                              " + CONSOLE_PURPLE + "║" + CONSOLE_RESET,
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Plugin: %s%-52s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_GREEN, plugin.getName()),
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Current Version: %s%-43s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_RED, currentVersion),
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Latest Version: %s%-44s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_GREEN, latestVersion),
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Download: %s%-50s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_BLUE, downloadUrl),
                CONSOLE_PURPLE + "║" + CONSOLE_RESET + "                                                              " + CONSOLE_PURPLE + "║" + CONSOLE_RESET,
                CONSOLE_PURPLE + "╚══════════════════════════════════════════════════════════════╝" + CONSOLE_RESET
        };

        for (String line : consoleMessage) {
            plugin.getLogger().info(line);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!configManager.getBoolean("update-checker-notify-on-join") || !hasUpdate) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("smartspawner.update")) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> sendUpdateMessage(player),
                    40L
            );
        }
    }

    private void sendUpdateMessage(Player player) {
        String currentVersion = plugin.getDescription().getVersion();
        String titleMsg = languageManager.getMessage("update.title");
        String versionMsg = languageManager.getMessage("update.current_version")
                .replace("%current_version%", currentVersion);
        String lastVersionMsg = languageManager.getMessage("update.last_version")
                .replace("%new_version%", getLatestVersion());
        String downloadMsg = languageManager.getMessage("update.download_button");
        String downloadHoverMsg = languageManager.getMessage("update.download_hover");

        Component title = Component.text(titleMsg)
                .color(TextColor.fromHexString("#3287A9"))
                .decorate(TextDecoration.BOLD);

        Component version = Component.text(versionMsg)
                .color(TextColor.fromHexString("#C6C6C6"));

        Component lastVersion = Component.text(lastVersionMsg)
                .color(TextColor.fromHexString("#00E689"));

        Component downloadButton = Component.text(downloadMsg)
                .color(TextColor.fromHexString("#ADF3FD"))
                .clickEvent(ClickEvent.openUrl(MODRINTH_PROJECT_URL + projectId))
                .hoverEvent(HoverEvent.showText(
                        Component.text(downloadHoverMsg)
                                .color(TextColor.fromHexString("#ADF3FD"))
                ));

        player.sendMessage(Component.empty());
        player.sendMessage(title);
        player.sendMessage(version);
        player.sendMessage(lastVersion);
        player.sendMessage(Component.empty());
        player.sendMessage(downloadButton);
        player.sendMessage(Component.empty());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
    }

    public boolean hasUpdate() {
        return hasUpdate;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
