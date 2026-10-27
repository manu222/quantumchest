package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.platform.Services;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;

import java.util.ArrayList;
import java.util.List;

public final class ModCreativeTabs {
    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, QuantumChest.MOD_ID);

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> Services.PLATFORM.creativeTab(
                    Component.translatable("itemGroup.quantumchest"),
                    () -> ModItems.QUANTUM_CHEST.get().getDefaultInstance(),
                    ModCreativeTabs::contents));

    /** A chest of every tier (handy for testing), the upgrade kits and the tools. */
    private static List<ItemStack> contents() {
        List<ItemStack> stacks = new ArrayList<>();
        for (ChestTier tier : ChestTier.values()) {
            ItemStack chest = new ItemStack(ModItems.QUANTUM_CHEST.get());
            if (tier != ChestTier.BASIC) {
                chest.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(QuantumChestBlock.TIER, tier));
            }
            stacks.add(chest);
        }
        ModItems.KITS.forEach(kit -> stacks.add(new ItemStack(kit.get())));
        stacks.add(new ItemStack(ModItems.QUANTUM_ENTANGLER.get()));
        stacks.add(new ItemStack(ModItems.SUPERPOSITION_TERMINAL.get()));
        stacks.add(new ItemStack(ModItems.QUANTUM_TUNNEL.get()));
        ModItems.UPGRADES.forEach(upgrade -> stacks.add(new ItemStack(upgrade.get())));
        return stacks;
    }

    private ModCreativeTabs() {
    }

    static void init() {
    }
}
