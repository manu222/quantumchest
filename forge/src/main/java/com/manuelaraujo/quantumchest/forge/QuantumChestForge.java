package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.QuantumChest;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(QuantumChest.MOD_ID)
public final class QuantumChestForge {

    public QuantumChestForge(FMLJavaModLoadingContext context) {
        QuantumChest.init();
        ForgeRegistrationFactory.registerAll(context.getModBusGroup());
    }
}
