package com.manuelaraujo.quantumchest.platform.services;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public interface IPlatformHelper {

    /** "Forge" or "NeoForge". */
    String getPlatformName();

    boolean isModLoaded(String modId);

    /**
     * Builds a creative tab. It has to live in the loaders: vanilla only offers
     * builder(row, column) and keeps CreativeModeTab.Output protected, and both
     * loaders open those up with their own patches.
     */
    CreativeModeTab creativeTab(Component title, Supplier<ItemStack> icon, Supplier<List<ItemStack>> contents);

    /**
     * Vanilla keeps the MenuType constructor private; both loaders make it
     * public, so the loaders build it.
     */
    <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory);
}
