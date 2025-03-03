package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Utility class for detecting natural dungeon spawners in the Minecraft world.
 * This class provides methods to analyze block surroundings and determine if
 * a spawner was naturally generated as part of world structures.
 */
public class NaturalSpawnerDetector {

    /**
     * Checks if a spawner is likely to be naturally generated based on surrounding blocks
     * and efficient contextual analysis.
     *
     * @param spawnerBlock The spawner block to check
     * @param spawnerData The SpawnerData object, null if not registered in the plugin
     * @return true if it appears to be a natural spawner
     */
    public static boolean isNaturalDungeonSpawner(Block spawnerBlock, SpawnerData spawnerData) {
        // First, check if spawnerData is null, which suggests it might be naturally generated
        if (spawnerData != null) {
            return false;
        }

        // Check the block below first (most efficient check)
        Block blockBelow = spawnerBlock.getRelative(BlockFace.DOWN);
        Material belowMaterial = blockBelow.getType();

        // Check for classic dungeon structures (zombie, skeleton, spider)
        if (isDungeonFloorMaterial(belowMaterial)) {
            return true;
        }

        // Check for mineshaft structures (cave spider spawners)
        if (isMineshaftMaterial(belowMaterial)) {
            return true;
        }

        // Check for nether fortress (blaze spawners)
        if (isNetherFortressMaterial(belowMaterial)) {
            return true;
        }

        // Check for stronghold (silverfish spawners)
        if (isStrongholdMaterial(belowMaterial)) {
            return true;
        }

        // For floating spawners, check if there's no block below
        if (belowMaterial == Material.AIR || belowMaterial == Material.CAVE_AIR || belowMaterial == Material.VOID_AIR) {
            return checkSurroundingBlocks(spawnerBlock);
        }

        return false;
    }

    /**
     * Checks if the material is typically found in dungeon floors.
     *
     * @param material The material to check
     * @return true if it's a typical dungeon floor material
     */
    private static boolean isDungeonFloorMaterial(Material material) {
        return material == Material.COBBLESTONE ||
                material == Material.MOSSY_COBBLESTONE;
    }

    /**
     * Checks if the material is typically found in mineshafts.
     *
     * @param material The material to check
     * @return true if it's a typical mineshaft material
     */
    private static boolean isMineshaftMaterial(Material material) {
        return material == Material.COBWEB ||
                material == Material.OAK_PLANKS ||
                material == Material.OAK_FENCE ||
                material == Material.RAIL ||
                material == Material.POWERED_RAIL ||
                material == Material.DETECTOR_RAIL ||
                material == Material.DEEPSLATE;
    }

    /**
     * Checks if the material is typically found in nether fortresses (for blaze spawners).
     *
     * @param material The material to check
     * @return true if it's a typical nether fortress material
     */
    private static boolean isNetherFortressMaterial(Material material) {
        return material == Material.NETHER_BRICKS ||
                material == Material.NETHER_BRICK_FENCE ||
                material == Material.NETHER_BRICK_STAIRS ||
                material == Material.NETHERRACK ||
                material == Material.SOUL_SAND;
    }

    /**
     * Checks if the material is typically found in strongholds (for silverfish spawners).
     *
     * @param material The material to check
     * @return true if it's a typical stronghold material
     */
    private static boolean isStrongholdMaterial(Material material) {
        return material == Material.STONE_BRICKS ||
                material == Material.CRACKED_STONE_BRICKS ||
                material == Material.MOSSY_STONE_BRICKS ||
                material == Material.STONE_BRICK_STAIRS ||
                material == Material.END_PORTAL_FRAME;
    }

    /**
     * Checks the surrounding blocks of a spawner to determine if it's in a natural structure.
     *
     * @param spawnerBlock The spawner block to check surroundings for
     * @return true if the surrounding blocks suggest a natural structure
     */
    private static boolean checkSurroundingBlocks(Block spawnerBlock) {
        int naturalBlockCount = 0;
        int cobwebCount = 0;
        int netherBrickCount = 0;
        int stoneBrickCount = 0;

        // Only check immediate surroundings (6 adjacent blocks) for better performance
        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
        }) {
            Block adjacentBlock = spawnerBlock.getRelative(face);
            Material material = adjacentBlock.getType();

            // Count natural blocks and structure-specific blocks
            if (material.toString().contains("STONE") ||
                    material.toString().contains("DEEPSLATE") ||
                    material == Material.DIRT ||
                    material == Material.GRAVEL) {
                naturalBlockCount++;
            } else if (material == Material.COBWEB) {
                cobwebCount++;
            } else if (material == Material.NETHER_BRICKS) {
                netherBrickCount++;
            } else if (material.toString().contains("STONE_BRICK")) {
                stoneBrickCount++;
            }
        }

        // Different checks for different structure types
        if (cobwebCount >= 1) {
            // Likely a mineshaft
            return true;
        } else if (netherBrickCount >= 2) {
            // Likely a nether fortress
            return true;
        } else if (stoneBrickCount >= 2) {
            // Likely a stronghold
            return true;
        } else if (naturalBlockCount >= 3) {
            // Likely some natural cave spawner
            return true;
        }

        return false;
    }
}