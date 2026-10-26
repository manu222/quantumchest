package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ModCreativeTabs {
    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, QuantumChest.MOD_ID);

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> Services.PLATFORM.creativeTab(
                    Component.translatable("itemGroup.quantumchest"),
                    () -> ModItems.QUANTUM_CHEST.get().getDefaultInstance(),
                    () -> List.of(new ItemStack(ModItems.QUANTUM_CHEST.get()))));

    private ModCreativeTabs() {
    }

    static void init() {
    }
}
