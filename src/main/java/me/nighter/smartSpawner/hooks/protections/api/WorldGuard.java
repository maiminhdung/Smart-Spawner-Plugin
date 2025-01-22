package me.nighter.smartSpawner.hooks.protections.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class WorldGuard {
    // Check if player can break in a location
    public static boolean canPlayerBreakBlockInRegion(@NotNull UUID pUUID, @NotNull org.bukkit.Location location) {
        Player player = Bukkit.getServer().getPlayer(pUUID);
        if (player != null && (player.isOp() || player.hasPermission("worldguard.region.bypass"))) {
            return true;
        }

        LocalPlayer localPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : WorldGuardPlugin.inst().wrapOfflinePlayer(Bukkit.getServer().getOfflinePlayer(pUUID));
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.BLOCK_BREAK};
        return query.testBuild(loc, localPlayer, condition);
    }

    // Check if player can place in a location
    public static boolean canPlayerPlaceBlockInRegion(@NotNull UUID pUUID, @NotNull org.bukkit.Location location) {
        Player player = Bukkit.getServer().getPlayer(pUUID);
        if (player != null) {
            if (player.isOp() || player.hasPermission("worldguard.region.bypass")) {
                return true;
            }
        } else {
            player = Bukkit.getServer().getOfflinePlayer(pUUID).getPlayer();
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapOfflinePlayer(player);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.BLOCK_PLACE};
        return query.testBuild(loc, localPlayer, condition);
    }

    // Check if player can build in a location
    public static boolean CanPlayerBuildInRegion(@NotNull Player p, org.bukkit.Location location) {
        if (p.isOp() || p.hasPermission("worldguard.region.bypass")) {
            return true;
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.BLOCK_BREAK};
        return query.testBuild(loc, localPlayer, condition);
    }

    // Check if player can interact in a location
    public static boolean CanPlayerInteractInRegion(@NotNull Player p, org.bukkit.Location location) {
        if (p.isOp() || p.hasPermission("worldguard.region.bypass")) {
            return true;
        }
        
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.INTERACT};
        return query.testBuild(loc, localPlayer, condition);
    }
}
