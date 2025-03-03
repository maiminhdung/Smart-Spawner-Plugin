package github.nighter.smartspawner.hooks.protections.api;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class GriefPrevention {

    public static boolean canPlayerBreakClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        Player player = Bukkit.getPlayer(pUUID);

        if (player == null) return false;
        return claim.allowBreak(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    public static boolean canPlayerStackClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        Player player = Bukkit.getPlayer(pUUID);
        
        if (player == null) return false;
        return claim.allowBuild(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    public static boolean canPlayerOpenMenuOnClaim(@NotNull UUID pUUID, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        Player player = Bukkit.getPlayer(pUUID);
        
        if (player == null) return false;
        return claim.allowContainers(player) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }
}