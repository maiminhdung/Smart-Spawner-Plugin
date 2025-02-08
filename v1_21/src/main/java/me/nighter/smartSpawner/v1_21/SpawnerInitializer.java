package me.nighter.smartSpawner.v1_21;

import me.nighter.smartSpawner.nms.SpawnerWrapper;
import java.util.Arrays;

public class SpawnerInitializer {
    public static void init() {
        SpawnerWrapper.SUPPORTED_MOBS = Arrays.asList(
                "BLAZE", "BOGGED", "BREEZE", "CAVE_SPIDER", "CHICKEN", "COW", "CREEPER",
                "DROWNED", "ENDERMAN", "EVOKER", "GHAST", "GLOW_SQUID", "GUARDIAN",
                "HOGLIN", "HUSK", "IRON_GOLEM", "MAGMA_CUBE", "MOOSHROOM", "PIG",
                "PIGLIN", "PIGLIN_BRUTE", "PILLAGER", "PUFFERFISH", "RABBIT", "RAVAGER",
                "SALMON", "SHEEP", "SHULKER", "SKELETON", "SLIME", "SPIDER", "SQUID",
                "STRAY", "STRIDER", "TROPICAL_FISH", "VINDICATOR", "WITCH",
                "WITHER_SKELETON", "ZOGLIN", "ZOMBIE", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN"
        );
    }
}