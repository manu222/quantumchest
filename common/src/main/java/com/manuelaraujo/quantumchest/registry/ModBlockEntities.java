package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlockEntities {
    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, QuantumChest.MOD_ID);

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<QuantumChestBlockEntity>> QUANTUM_CHEST =
            BLOCK_ENTITIES.register("quantum_chest",
                    () -> new BlockEntityType<>(QuantumChestBlockEntity::new, Set.of(ModBlocks.QUANTUM_CHEST.get())));

    private ModBlockEntities() {
    }

    static void init() {
    }
}
