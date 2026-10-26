package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.QuantumChest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(QuantumChest.MOD_ID)
public final class QuantumChestNeoForge {

    public QuantumChestNeoForge(IEventBus modBus) {
        QuantumChest.init();
        NeoForgeRegistrationFactory.registerAll(modBus);
    }
}
