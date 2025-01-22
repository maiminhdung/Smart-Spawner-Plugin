package me.nighter.smartSpawner.hooks.protections;

import me.nighter.smartSpawner.SmartSpawner;

import java.util.UUID;

import me.nighter.smartSpawner.hooks.protections.api.GriefPrevention;
import me.nighter.smartSpawner.hooks.protections.api.Lands;
import me.nighter.smartSpawner.hooks.protections.api.Towny;
import me.nighter.smartSpawner.hooks.protections.api.WorldGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckPlaceBlock {
    public static boolean CanPlayerPlaceBlock(@NotNull final UUID playerUUID, @NotNull Block block) {
        return CanPlayerPlaceBlock(playerUUID, block.getLocation());
    }
    // Check if player can place a block
    public static boolean CanPlayerPlaceBlock(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention)
            if (!GriefPrevention.CanplayerPlaceClaimBlock(playerUUID, location)) return false;
        
        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuard.canPlayerPlaceBlockInRegion(playerUUID, location)) return false;

        if (SmartSpawner.hasLands)
            if (!Lands.canPlayerBreakClaimBlock(playerUUID, location)) return false;
        
        if (SmartSpawner.hasTowny)
            if (!Towny.IfPlayerHasResident(playerUUID, location)) return false;

        return true;
    }
}
