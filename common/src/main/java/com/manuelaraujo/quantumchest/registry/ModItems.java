package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final RegistrationProvider<Item> ITEMS = RegistrationProvider.get(Registries.ITEM, QuantumChest.MOD_ID);

    public static final RegistryEntry<Item, BlockItem> QUANTUM_CHEST = ITEMS.registerWithKey("quantum_chest",
            key -> new BlockItem(ModBlocks.QUANTUM_CHEST.get(), new Item.Properties()
                    .setId(key)
                    .useBlockDescriptionPrefix()));

    private ModItems() {
    }

    static void init() {
    }
}
