package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.command.QuantumCommands;
import com.manuelaraujo.quantumchest.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

@Mod(QuantumChest.MOD_ID)
public final class QuantumChestNeoForge {

    public QuantumChestNeoForge(IEventBus modBus) {
        QuantumChest.init();
        NeoForgeRegistrationFactory.registerAll(modBus);
        modBus.addListener(NeoForgeNetwork::register);
        modBus.addListener(QuantumChestNeoForge::onRegisterCapabilities);
        NeoForge.EVENT_BUS.addListener(QuantumChestNeoForge::onBreak);
        NeoForge.EVENT_BUS.addListener(QuantumChestNeoForge::onRegisterCommands);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            QuantumChestNeoForgeClient.init(modBus);
        }
    }

    /** Hoppers and pipes reach the chest through this, face by face. */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.QUANTUM_CHEST.get(), NeoChestHandler::of);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        QuantumCommands.register(event.getDispatcher());
    }

    private static void onBreak(BreakBlockEvent event) {
        if (!QuantumChestBlock.canBreak(event.getLevel(), event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
            // We only check on the server, so tell the client to put the block back
            event.setNotifyClient(true);
        }
    }
}
