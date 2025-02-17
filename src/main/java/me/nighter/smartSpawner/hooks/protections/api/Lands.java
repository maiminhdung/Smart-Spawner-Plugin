package me.nighter.smartSpawner.hooks.protections.api;

import java.util.UUID;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.Flags;
import me.angeschossen.lands.api.land.LandWorld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Lands {

    private static LandsIntegration landsIntegration;

    public Lands(Plugin smartSpawner) {
        landsIntegration = LandsIntegration.of(smartSpawner);
    }

    public static boolean canPlayerBreakClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {
        if (landsIntegration == null) {
            return true;
        }

        Player player = Bukkit.getPlayer(pUUID);
        if (player == null) {
            return true;
        }

        LandWorld world = landsIntegration.getWorld(location.getWorld());
        if (world != null) { // Lands is enabled in this world
            return world.hasFlag(player, location, Material.SPAWNER, Flags.BLOCK_BREAK, true);
        }
        return true;
    }

    public static boolean canPlayerStackClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {
        if (landsIntegration == null) {
            return true;
        }

        Player player = Bukkit.getPlayer(pUUID);
        if (player == null) {
            return true;
        }

        LandWorld world = landsIntegration.getWorld(location.getWorld());
        if (world != null) { // Lands is enabled in this world
            return world.hasFlag(player, location, Material.SPAWNER, Flags.BLOCK_PLACE, true);
        }
        return true;
    }

    public static boolean CanPlayerInteractContainer(@NotNull UUID pUUID, @NotNull Location location) {
        if (landsIntegration == null) {
            return true;
        }

        Player player = Bukkit.getPlayer(pUUID);
        if (player == null) {
            return true;
        }

        LandWorld world = landsIntegration.getWorld(location.getWorld());
        if (world != null) { // Lands is enabled in this world
            return world.hasFlag(player, location, Material.SPAWNER, Flags.INTERACT_CONTAINER, true);
        }
        return true;
    }
}
