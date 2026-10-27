package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

@Mod(QuantumChest.MOD_ID)
public final class QuantumChestNeoForge {

    public QuantumChestNeoForge(IEventBus modBus) {
        QuantumChest.init();
        NeoForgeRegistrationFactory.registerAll(modBus);
        NeoForge.EVENT_BUS.addListener(QuantumChestNeoForge::onBreak);
    }

    private static void onBreak(BreakBlockEvent event) {
        if (!QuantumChestBlock.canBreak(event.getLevel(), event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
            // We only check on the server, so tell the client to put the block back
            event.setNotifyClient(true);
        }
    }
}
