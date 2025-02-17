package me.nighter.smartSpawner.hooks.protections;

import java.util.UUID;

import me.nighter.smartSpawner.hooks.protections.api.Towny;
import me.nighter.smartSpawner.hooks.protections.api.Lands;
import me.nighter.smartSpawner.hooks.protections.api.WorldGuard;
import me.nighter.smartSpawner.hooks.protections.api.GriefPrevention;
import me.nighter.smartSpawner.hooks.protections.api.SuperiorSkyblock2;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.nighter.smartSpawner.SmartSpawner;

public class CheckOpenMenu {
    // Check if player can open a menu
    public static boolean CanPlayerOpenMenu(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention && !GriefPrevention.canPlayerOpenMenuOnClaim(playerUUID, location)) return false;
        if (SmartSpawner.hasWorldGuard && !WorldGuard.canPlayerInteractInRegion(player, location)) return false;
        if (SmartSpawner.hasLands && !Lands.CanPlayerInteractContainer(playerUUID, location)) return false;
        if (SmartSpawner.hasTowny && !Towny.ifPlayerHasResident(playerUUID, location)) return false;
        if (SmartSpawner.hasSuperiorSkyblock2 && SuperiorSkyblock2.canPlayerOpenMenu(playerUUID, location)) return false;

        return true;
    }

}
