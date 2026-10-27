package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final RegistrationProvider<Block> BLOCKS = RegistrationProvider.get(Registries.BLOCK, QuantumChest.MOD_ID);

    public static final RegistryEntry<Block, QuantumChestBlock> QUANTUM_CHEST = BLOCKS.registerWithKey("quantum_chest",
            key -> new QuantumChestBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_PURPLE)
                    // Normal mining time, but no explosion can break it (same resistance as bedrock).
                    // Withers and the dragon are stopped by the block tags in data/minecraft/tags/block.
                    .strength(3.5f, 3_600_000f)
                    .sound(SoundType.METAL)));

    private ModBlocks() {
    }

    static void init() {
    }
}
