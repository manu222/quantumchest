package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import java.util.UUID;

public final class ModDataComponents {
    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, QuantumChest.MOD_ID);

    /** Which storage a chest item points to. Set when a used chest is broken. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<UUID>> STORAGE_ID =
            COMPONENTS.register("storage_id", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }

    static void init() {
    }
}
