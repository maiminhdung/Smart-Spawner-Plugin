package me.nighter.smartSpawner.hooks.claiming;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class GriefPreventionAPI {

    public static boolean CanplayerBreakClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        Player player = Bukkit.getPlayer(pUUID);

        if (player == null) return false;
        return claim.allowBreak(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    public static boolean CanplayerPlaceClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        Player player = Bukkit.getPlayer(pUUID);
        
        if (player == null) return false;
        return claim.allowBuild(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    public static boolean CanplayerOpenMenuOnClaim(@NotNull UUID pUUID, @NotNull Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        Player player = Bukkit.getPlayer(pUUID);
        
        if (player == null) return false;
        return claim.allowContainers(player) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }
}