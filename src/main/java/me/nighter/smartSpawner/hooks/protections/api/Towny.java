package me.nighter.smartSpawner.hooks.protections.api;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

import java.util.UUID;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public class Towny {
    // Check if player has a resident in the location
    public static boolean ifPlayerHasResident(@NotNull UUID pUUID, @NotNull Location location){
        Resident resident = TownyAPI.getInstance().getResident(pUUID);
        if (resident == null) return false;

        try {
            Town town = resident.getTown();
        } catch (NotRegisteredException e) {
            // Log the exception and return false if the resident does not belong to any town
            // e.printStackTrace();
            return false;
        }
        return true;
    }
}
