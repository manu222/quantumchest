package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.command.QuantumCommands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.function.Predicate;

@Mod(QuantumChest.MOD_ID)
public final class QuantumChestForge {

    public QuantumChestForge(FMLJavaModLoadingContext context) {
        QuantumChest.init();
        ForgeRegistrationFactory.registerAll(context.getModBusGroup());
        ForgeNetwork.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            QuantumChestForgeClient.init(context.getModBusGroup());
        }

        // Returning true cancels the break. The explicit type picks the
        // Predicate overload instead of the Consumer one.
        Predicate<BlockEvent.BreakEvent> protectChests =
                event -> !QuantumChestBlock.canBreak(event.getLevel(), event.getPos(), event.getPlayer());
        BlockEvent.BreakEvent.BUS.addListener(protectChests);

        RegisterCommandsEvent.BUS.addListener(event -> QuantumCommands.register(event.getDispatcher()));

        // Hoppers and pipes reach the chest through this, face by face
        AttachCapabilitiesEvent.BlockEntities.BUS.addListener(event -> {
            if (event.getObject() instanceof QuantumChestBlockEntity chest) {
                ForgeChestCapabilities capabilities = new ForgeChestCapabilities(chest);
                event.addCapability(QuantumChest.id("item_handler"), capabilities);
                event.addListener(capabilities::invalidate);
            }
        });
    }
}
