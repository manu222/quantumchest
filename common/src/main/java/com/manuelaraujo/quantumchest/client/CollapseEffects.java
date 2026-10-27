package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.entity.ChestCollapseEntity;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The particles of a collapsing chest, client side: a disc of the tier's
 * colour spinning into a dark core, and a flash when it closes. The shrinking
 * block itself is drawn by {@link ChestCollapseRenderer}.
 */
public final class CollapseEffects {
    private static final int DARK = 0x07040E;

    private CollapseEffects() {
    }

    public static void tick(ChestCollapseEntity entity) {
        Level level = entity.level();
        RandomSource random = level.getRandom();
        int t = entity.tickCount;
        int color = color(entity.blockState());
        Vec3 center = entity.position().add(0, 0.5, 0);
        int end = ChestCollapseEntity.DURATION;

        if (t >= 3 && t < end - 2) {
            float p = (t - 3) / (float) (end - 5);
            // Accretion disc: a flat ring that narrows and spins faster as the hole eats it
            double radius = 1.1 - p * 0.75;
            int points = 8;
            for (int i = 0; i < points; i++) {
                double a = t * (0.35 + p * 0.6) + i * (Math.PI * 2 / points);
                double x = center.x + Math.cos(a) * radius;
                double z = center.z + Math.sin(a) * radius;
                double y = center.y + Math.sin(a * 2) * 0.05;
                level.addParticle(new DustParticleOptions(color, 0.9f), x, y, z, 0, 0, 0);
            }
            // Bits of the chest pulled in from all around
            for (int i = 0; i < 2; i++) {
                Vec3 start = center.add(randomOffset(random, 1.6 + random.nextDouble()));
                level.addParticle(new TrailParticleOption(center, color, 8 + random.nextInt(6)), start.x, start.y, start.z, 0, 0, 0);
            }
            // The dark core grows while the chest shrinks
            float core = 0.6f + p * 1.8f;
            level.addParticle(new DustParticleOptions(DARK, core), center.x, center.y, center.z, 0, 0, 0);
        }
        if (t == end - 2) {
            level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, color), center.x, center.y, center.z, 0, 0, 0);
            for (int i = 0; i < 14; i++) {
                Vec3 dir = randomOffset(random, 0.18);
                level.addParticle(ParticleTypes.END_ROD, center.x, center.y, center.z, dir.x, dir.y, dir.z);
            }
        }
    }

    static int color(BlockState state) {
        return state.hasProperty(QuantumChestBlock.TIER) ? state.getValue(QuantumChestBlock.TIER).glowColor()
                : ChestTier.BASIC.glowColor();
    }

    /** A random point on a sphere of that radius, a bit flattened so it reads as a disc. */
    static Vec3 randomOffset(RandomSource random, double radius) {
        double a = random.nextDouble() * Math.PI * 2;
        double y = (random.nextDouble() - 0.5) * 1.2;
        double r = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(a) * r * radius, y * radius * 0.6, Math.sin(a) * r * radius);
    }
}
