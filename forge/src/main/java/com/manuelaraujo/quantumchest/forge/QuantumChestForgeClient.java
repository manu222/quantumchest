package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.client.QuantumChestScreen;
import com.manuelaraujo.quantumchest.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client only setup. Never touched on a dedicated server. */
final class QuantumChestForgeClient {
    private QuantumChestForgeClient() {
    }

    static void init(BusGroup modBusGroup) {
        FMLClientSetupEvent.getBus(modBusGroup).addListener(event -> event.enqueueWork(() ->
                MenuScreens.register(ModMenus.QUANTUM_CHEST.get(), QuantumChestScreen::new)));
    }
}
