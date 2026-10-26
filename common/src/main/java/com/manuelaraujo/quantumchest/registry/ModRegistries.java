package com.manuelaraujo.quantumchest.registry;

/**
 * Touches every registry class so their static fields run before the
 * loader attaches the DeferredRegisters to the mod bus.
 */
public final class ModRegistries {
    private ModRegistries() {
    }

    public static void init() {
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModCreativeTabs.init();
    }
}
