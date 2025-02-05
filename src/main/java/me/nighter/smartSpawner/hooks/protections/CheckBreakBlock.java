package me.nighter.smartSpawner.hooks.protections;

import me.nighter.smartSpawner.SmartSpawner;

import me.nighter.smartSpawner.hooks.protections.api.GriefPrevention;
import me.nighter.smartSpawner.hooks.protections.api.Lands;
import me.nighter.smartSpawner.hooks.protections.api.Towny;
import me.nighter.smartSpawner.hooks.protections.api.WorldGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CheckBreakBlock {
    public static boolean CanPlayerBreakBlock(@NotNull final UUID playerUUID, @NotNull Block block) {
        return CanPlayerBreakBlock(playerUUID, block.getLocation());
    }
    // Check if player can break a block
    public static boolean CanPlayerBreakBlock(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention)
            if (!GriefPrevention.CanplayerBreakClaimBlock(playerUUID, location)) return false;

        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuard.canPlayerBreakBlockInRegion(playerUUID, location)) return false;

        if (SmartSpawner.hasLands)
            if (!Lands.canPlayerBreakClaimBlock(playerUUID, location)) return false;
        
        if (SmartSpawner.hasTowny)
            if (!Towny.CheckPlayerHasResident(playerUUID, location)) return false;

        return true;
    }

}
