package github.nighter.smartspawner.v1_20;

import github.nighter.smartspawner.nms.SpawnerWrapper;
import java.util.Arrays;

public class SpawnerInitializer {
    public static void init() {
        SpawnerWrapper.SUPPORTED_MOBS = Arrays.asList(
                "ALLAY", "AXOLOTL", "BAT", "BEE", "BLAZE", "CAMEL", "CAT", "CAVE_SPIDER",
                "CHICKEN", "COD", "COW", "CREEPER", "DOLPHIN", "DONKEY", "DROWNED",
                "ELDER_GUARDIAN", "ENDERMAN", "ENDERMITE", "EVOKER", "FOX", "FROG", "GHAST",
                "GLOW_SQUID", "GOAT", "GUARDIAN", "HOGLIN", "HORSE", "HUSK", "IRON_GOLEM",
                "LLAMA", "MAGMA_CUBE", "MULE", "MUSHROOM_COW", "OCELOT", "PANDA", "PARROT",
                "PHANTOM", "PIG", "PIGLIN", "PIGLIN_BRUTE", "PILLAGER", "POLAR_BEAR", "PUFFERFISH",
                "RABBIT", "RAVAGER", "SALMON", "SHEEP", "SHULKER", "SILVERFISH", "SKELETON",
                "SKELETON_HORSE", "SLIME", "SNIFFER", "SNOWMAN", "SPIDER", "SQUID", "STRAY",
                "STRIDER", "TADPOLE", "TRADER_LLAMA", "TROPICAL_FISH", "TURTLE", "VEX",
                "VILLAGER", "VINDICATOR", "WANDERING_TRADER", "WARDEN", "WITCH", "WITHER_SKELETON",
                "WOLF", "ZOGLIN", "ZOMBIE", "ZOMBIE_HORSE", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN"
        );
    }
}