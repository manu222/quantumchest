package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client side touches of a placed chest, in the tier's colour:
 * <ul>
 *   <li>while someone has it open, specks drift into the front;</li>
 *   <li>while you mine it, more and more of them fall into its centre and a
 *       dark core starts to form, so the collapse begins before it breaks.</li>
 * </ul>
 */
public final class ChestEffects {
    private static final int DARK = 0x07040E;

    private ChestEffects() {
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, QuantumChestBlockEntity chest) {
        RandomSource random = level.getRandom();
        int color = CollapseEffects.color(state);
        Vec3 center = Vec3.atCenterOf(pos);

        if (state.getValue(QuantumChestBlock.OPEN) && level.getGameTime() % 2 == 0) {
            Direction facing = state.getValue(QuantumChestBlock.FACING);
            Vec3 out = new Vec3(facing.getStepX(), 0, facing.getStepZ());
            Vec3 front = center.add(out.scale(0.52));
            double distance = 0.8 + random.nextDouble() * 1.2;
            Vec3 side = new Vec3(-out.z, 0, out.x);
            Vec3 start = front.add(out.scale(distance))
                    .add(side.scale((random.nextDouble() - 0.5) * 1.6))
                    .add(0, (random.nextDouble() - 0.5) * 1.2, 0);
            level.addParticle(new TrailParticleOption(front, color, 18 + random.nextInt(14)), start.x, start.y, start.z, 0, 0, 0);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null && minecraft.gameMode.isDestroying()
                && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos)) {
            int stage = Math.max(0, minecraft.gameMode.getDestroyStage());
            // Starts as a trickle and builds up until the block breaks
            int count = 1 + stage / 3;
            for (int i = 0; i < count; i++) {
                Vec3 start = center.add(CollapseEffects.randomOffset(random, 1.2 + random.nextDouble()));
                level.addParticle(new TrailParticleOption(center, color, 10 + random.nextInt(8)), start.x, start.y, start.z, 0, 0, 0);
            }
            if (stage >= 4 && random.nextInt(3) == 0) {
                Vec3 jitter = CollapseEffects.randomOffset(random, 0.25);
                level.addParticle(new DustParticleOptions(DARK, 0.6f + stage * 0.12f),
                        center.x + jitter.x, center.y + jitter.y, center.z + jitter.z, 0, 0, 0);
            }
        }
    }
}
