package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.item.StoredChest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import java.util.UUID;

public final class ModDataComponents {
    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, QuantumChest.MOD_ID);

    /** Which storage a chest item or a terminal points to. Set when a used chest is broken. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<UUID>> STORAGE_ID =
            COMPONENTS.register("storage_id", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    /** The chest an entangler is holding until it is used on a second one. The item model reads it. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<StoredChest>> STORED_CHEST =
            COMPONENTS.register("stored_chest", () -> DataComponentType.<StoredChest>builder()
                    .persistent(StoredChest.CODEC)
                    .networkSynchronized(StoredChest.STREAM_CODEC)
                    .build());

    /** Tier of the chest a terminal is linked to. The item model reads it to colour the screen. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<ChestTier>> LINKED_TIER =
            COMPONENTS.register("linked_tier", () -> DataComponentType.<ChestTier>builder()
                    .persistent(ChestTier.CODEC)
                    .networkSynchronized(ChestTier.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }

    static void init() {
    }
}
