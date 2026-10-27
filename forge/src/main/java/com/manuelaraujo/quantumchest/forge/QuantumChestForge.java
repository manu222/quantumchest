package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.function.Predicate;

@Mod(QuantumChest.MOD_ID)
public final class QuantumChestForge {

    public QuantumChestForge(FMLJavaModLoadingContext context) {
        QuantumChest.init();
        ForgeRegistrationFactory.registerAll(context.getModBusGroup());

        // Returning true cancels the break. The explicit type picks the
        // Predicate overload instead of the Consumer one.
        Predicate<BlockEvent.BreakEvent> protectChests =
                event -> !QuantumChestBlock.canBreak(event.getLevel(), event.getPos(), event.getPlayer());
        BlockEvent.BreakEvent.BUS.addListener(protectChests);
    }
}
