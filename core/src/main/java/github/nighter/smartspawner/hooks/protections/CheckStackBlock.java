package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.protections.api.*;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckStackBlock {
    // Check if player can place a block
    public static boolean CanPlayerPlaceBlock(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention && !GriefPrevention.canPlayerStackClaimBlock(playerUUID, location)) return false;
        if (SmartSpawner.hasWorldGuard && !WorldGuard.canPlayerStackBlockInRegion(playerUUID, location)) return false;
        if (SmartSpawner.hasLands && !Lands.canPlayerStackClaimBlock(playerUUID, location)) return false;
        if (SmartSpawner.hasTowny && !Towny.ifPlayerHasResident(playerUUID, location)) return false;
        if (SmartSpawner.hasSuperiorSkyblock2 && SuperiorSkyblock2.canPlayerStackBlock(playerUUID, location)) return false;

        return true;
    }
}
