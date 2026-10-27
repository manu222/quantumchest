package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.block.ChestTier;
import net.minecraft.world.item.Item;

/**
 * An upgrade that only needs to be in a slot to work. What each one does
 * lives in ChestAutomation, Compactor, Smelter, Crafter and Refiller.
 */
public class UpgradeItem extends Item implements ChestUpgrade {
    private final ChestTier minTier;

    public UpgradeItem(ChestTier minTier, Properties properties) {
        super(properties);
        this.minTier = minTier;
    }

    @Override
    public ChestTier minTier() {
        return minTier;
    }
}
