package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.entity.ChestCollapseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the chest of a {@link ChestCollapseEntity}: it trembles for a moment,
 * then shrinks while it spins faster and faster, until it's gone.
 */
public class ChestCollapseRenderer extends EntityRenderer<ChestCollapseEntity, FallingBlockRenderState> {
    private static final float SHAKE = 3;
    private static final float SHRINK = ChestCollapseEntity.DURATION - 4 - SHAKE;

    public ChestCollapseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
    }

    @Override
    public FallingBlockRenderState createRenderState() {
        return new FallingBlockRenderState();
    }

    @Override
    public void extractRenderState(ChestCollapseEntity entity, FallingBlockRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        BlockPos pos = entity.blockPosition();
        state.movingBlockRenderState.randomSeedPos = pos;
        state.movingBlockRenderState.blockPos = pos;
        state.movingBlockRenderState.blockState = entity.blockState();
        if (entity.level() instanceof ClientLevel level) {
            state.movingBlockRenderState.biome = level.getBiome(pos);
            state.movingBlockRenderState.cardinalLighting = level.cardinalLighting();
            state.movingBlockRenderState.lightEngine = level.getLightEngine();
        }
    }

    @Override
    public void submit(FallingBlockRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState block = state.movingBlockRenderState.blockState;
        if (block.getRenderShape() != RenderShape.MODEL) return;
        float t = state.ageInTicks;
        float p = Math.max(0, Math.min(1, (t - SHAKE) / SHRINK));
        // Ease in: slow at first, then it gets swallowed
        float scale = 1 - p * p * (3 - 2 * p);
        if (scale <= 0.02f) return;
        poseStack.pushPose();
        if (t < SHAKE) {
            poseStack.translate(Math.sin(t * 9.1) * 0.03, Math.cos(t * 7.3) * 0.02, Math.sin(t * 11.7) * 0.03);
        }
        poseStack.translate(0, 0.5, 0);
        // The spin speeds up as it shrinks: the angle grows with the square of the time
        float spin = p * p * 14f;
        poseStack.mulPose(Axis.YP.rotation(spin));
        poseStack.mulPose(Axis.XP.rotation(p * 0.6f));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5, -0.5, -0.5);
        collector.submitMovingBlock(poseStack, state.movingBlockRenderState, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
