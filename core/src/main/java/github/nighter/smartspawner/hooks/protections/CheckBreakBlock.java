package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.protections.api.Towny;
import github.nighter.smartspawner.hooks.protections.api.Lands;
import github.nighter.smartspawner.hooks.protections.api.WorldGuard;
import github.nighter.smartspawner.hooks.protections.api.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CheckBreakBlock {
    // Check if player can break a block
    public static boolean CanPlayerBreakBlock(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention && !GriefPrevention.canPlayerBreakClaimBlock(playerUUID, location)) return false;
        if (SmartSpawner.hasWorldGuard && !WorldGuard.canPlayerBreakBlockInRegion(playerUUID, location)) return false;
        if (SmartSpawner.hasLands && !Lands.canPlayerBreakClaimBlock(playerUUID, location)) return false;
        if (SmartSpawner.hasTowny && !Towny.ifPlayerHasResident(playerUUID, location)) return false;

        return true;
    }

}
