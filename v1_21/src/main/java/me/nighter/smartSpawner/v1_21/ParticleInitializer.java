package me.nighter.smartSpawner.v1_21;

import me.nighter.smartSpawner.nms.ParticleWrapper;
import org.bukkit.Particle;

public class ParticleInitializer {
    public static void init() {
        ParticleWrapper.VILLAGER_HAPPY = Particle.HAPPY_VILLAGER;
        ParticleWrapper.SPELL_WITCH = Particle.WITCH;
    }
}