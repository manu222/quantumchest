package com.manuelaraujo.quantumchest;

import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuantumChest {
    public static final String MOD_ID = "quantumchest";
    public static final String MOD_NAME = "QuantumChest";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private QuantumChest() {
    }

    /**
     * Called once by each loader from its mod constructor, before the registries
     * are attached to the mod event bus.
     */
    public static void init() {
        ModRegistries.init();
        LOG.info("{} loading on {}", MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
