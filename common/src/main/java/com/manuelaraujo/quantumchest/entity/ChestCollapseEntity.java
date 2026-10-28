package com.manuelaraujo.quantumchest.entity;

import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.client.CollapseEffects;
import com.manuelaraujo.quantumchest.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * A broken chest falling into itself: it shrinks and spins into a small black
 * hole, the hole closes with a flash and leaves the chest item behind. The
 * drops travel inside this entity until then, so nothing is lost if the world
 * closes halfway through. It can't be hit, pushed or collided with.
 */
public class ChestCollapseEntity extends Entity {
    /** Ticks from the break until the chest item drops. */
    public static final int DURATION = 26;

    private static final EntityDataAccessor<BlockState> BLOCK =
            SynchedEntityData.defineId(ChestCollapseEntity.class, EntityDataSerializers.BLOCK_STATE);

    private List<ItemStack> drops = new ArrayList<>();
    private int age;

    public ChestCollapseEntity(EntityType<? extends ChestCollapseEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Starts the collapse where the chest was, carrying what it would have dropped. */
    public static void spawn(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
        ChestCollapseEntity entity = new ChestCollapseEntity(ModEntities.CHEST_COLLAPSE.get(), level);
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        BlockState shown = state.hasProperty(QuantumChestBlock.OPEN) ? state.setValue(QuantumChestBlock.OPEN, false) : state;
        entity.entityData.set(BLOCK, shown);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) entity.drops.add(drop.copy());
        }
        level.addFreshEntity(entity);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
    }

    public BlockState blockState() {
        return entityData.get(BLOCK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK, Blocks.AIR.defaultBlockState());
    }

    @Override
    public void tick() {
        if (level().isClientSide()) {
            CollapseEffects.tick(this);
            return;
        }
        if (++age < DURATION) return;
        if (level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 1.0f, 0.6f);
            release(server);
        }
        discard();
    }

    /** Lets the drops go as item entities. After this the collapse carries nothing. */
    private void release(ServerLevel server) {
        for (ItemStack drop : drops) {
            ItemEntity item = new ItemEntity(server, getX(), getY() + 0.5, getZ(), drop);
            item.setDeltaMovement(0, 0.18, 0);
            item.setDefaultPickUpDelay();
            server.addFreshEntity(item);
        }
        drops.clear();
    }

    /**
     * Killed before it finished (/kill, a mod that clears entities): the chest
     * still drops. Unloading with the chunk keeps the drops in the save instead.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !drops.isEmpty() && level() instanceof ServerLevel server) release(server);
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        drops = new ArrayList<>(input.read("Drops", ItemStack.CODEC.listOf()).orElse(List.of()));
        age = input.getIntOr("Age", 0);
        input.read("Block", BlockState.CODEC).ifPresent(state -> entityData.set(BLOCK, state));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Drops", ItemStack.CODEC.listOf(), drops);
        output.putInt("Age", age);
        output.store("Block", BlockState.CODEC, blockState());
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }
}
