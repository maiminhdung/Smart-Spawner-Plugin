package me.nighter.smartSpawner.v1_20;

import me.nighter.smartSpawner.nms.ParticleWrapper;
import org.bukkit.Particle;

public class ParticleInitializer {
    public static void init() {
        ParticleWrapper.VILLAGER_HAPPY = Particle.VILLAGER_HAPPY;
        ParticleWrapper.SPELL_WITCH = Particle.SPELL_WITCH;
    }
}
