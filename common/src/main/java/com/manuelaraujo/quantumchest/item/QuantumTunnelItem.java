package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.block.ChestTier;
import net.minecraft.world.item.Item;

/**
 * Upgrade that lets a network cross dimensions: entangled chests may sit in
 * different dimensions, and a terminal opens the chest from any of them.
 */
public class QuantumTunnelItem extends Item implements ChestUpgrade {

    public QuantumTunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public ChestTier minTier() {
        return ChestTier.ELITE;
    }
}
