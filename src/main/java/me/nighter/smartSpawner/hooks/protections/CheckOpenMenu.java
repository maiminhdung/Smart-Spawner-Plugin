package me.nighter.smartSpawner.hooks.protections;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.protections.api.Towny;
import me.nighter.smartSpawner.hooks.protections.api.WorldGuard;
import me.nighter.smartSpawner.hooks.protections.api.GriefPrevention;
import me.nighter.smartSpawner.hooks.protections.api.Lands;

public class CheckOpenMenu {
    public static boolean CanPlayerOpenMenu(@NotNull final UUID playerUUID, @NotNull Block block) {
        return CanPlayerOpenMenu(playerUUID, block.getLocation());
    }
    // Check if player can open a menu
    public static boolean CanPlayerOpenMenu(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention)
            if (!GriefPrevention.CanplayerOpenMenuOnClaim(playerUUID, location)) return false;

        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuard.CanPlayerInteractInRegion(player, location)) return false;

        if (SmartSpawner.hasLands)
            if (!Lands.CanPlayerInteractContainer(playerUUID, location)) return false;
        
        if (SmartSpawner.hasTowny)
            if (!Towny.CheckPlayerHasResident(playerUUID, location)) return false;

        return true;
    }

}
