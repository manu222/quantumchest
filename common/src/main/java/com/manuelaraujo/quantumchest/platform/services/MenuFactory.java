package com.manuelaraujo.quantumchest.platform.services;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Same shape as vanilla's MenuType.MenuSupplier, which common code can't see. */
@FunctionalInterface
public interface MenuFactory<T extends AbstractContainerMenu> {
    T create(int containerId, Inventory inventory);
}
