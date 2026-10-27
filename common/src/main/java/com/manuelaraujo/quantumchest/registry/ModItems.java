package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.item.QuantumChestItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final RegistrationProvider<Item> ITEMS = RegistrationProvider.get(Registries.ITEM, QuantumChest.MOD_ID);

    public static final RegistryEntry<Item, QuantumChestItem> QUANTUM_CHEST = ITEMS.registerWithKey("quantum_chest",
            key -> new QuantumChestItem(ModBlocks.QUANTUM_CHEST.get(), new Item.Properties()
                    .setId(key)
                    .useBlockDescriptionPrefix()
                    .stacksTo(1)));

    private ModItems() {
    }

    static void init() {
    }
}
