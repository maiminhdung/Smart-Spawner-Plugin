package github.nighter.smartspawner.hooks.protections.api;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SuperiorSkyblock2 {

    public static boolean canPlayerStackBlock(@NotNull UUID playerUUID, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(playerUUID), IslandPrivilege.getByName("build"));
        }
        // Player is not in island
        return false;
    }

    public static boolean canPlayerOpenMenu(@NotNull UUID playerUUID, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(playerUUID), IslandPrivilege.getByName("chest_access"));
        }
        // Player is not in island
        return false;
    }
}
