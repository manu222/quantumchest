package com.manuelaraujo.quantumchest.platform.services;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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

    /**
     * How many ticks this item burns in a furnace, 0 if it isn't fuel. Both
     * loaders let mods add fuels their own way, so vanilla's table isn't enough.
     */
    int burnTime(ItemStack stack, Level level);

    /** What is left after using this item up in a recipe or as fuel: an empty bucket for a lava bucket. */
    ItemStack craftingRemainder(ItemStack stack);
}
