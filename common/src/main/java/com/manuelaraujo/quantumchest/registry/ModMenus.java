package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static final RegistrationProvider<MenuType<?>> MENUS = RegistrationProvider.get(Registries.MENU, QuantumChest.MOD_ID);

    public static final RegistryEntry<MenuType<?>, MenuType<QuantumChestMenu>> QUANTUM_CHEST = MENUS.register("quantum_chest",
            () -> Services.PLATFORM.menuType(QuantumChestMenu::new));

    private ModMenus() {
    }

    static void init() {
    }
}
