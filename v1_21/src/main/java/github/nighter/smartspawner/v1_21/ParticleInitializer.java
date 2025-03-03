package github.nighter.smartspawner.v1_21;

import github.nighter.smartspawner.nms.ParticleWrapper;
import org.bukkit.Particle;

public class ParticleInitializer {
    public static void init() {
        ParticleWrapper.VILLAGER_HAPPY = Particle.HAPPY_VILLAGER;
        ParticleWrapper.SPELL_WITCH = Particle.WITCH;
    }
}