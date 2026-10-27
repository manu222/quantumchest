package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.client.QuantumChestScreen;
import com.manuelaraujo.quantumchest.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client only setup. Never touched on a dedicated server. */
final class QuantumChestNeoForgeClient {
    private QuantumChestNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(QuantumChestNeoForgeClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.QUANTUM_CHEST.get(), QuantumChestScreen::new);
    }
}
