package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.block.ChestTier;

/**
 * Items that go into the upgrade slots of a chest. The slots are shared by
 * every chest of a network, since they belong to the storage.
 */
public interface ChestUpgrade {

    /** Lowest chest tier that accepts this upgrade. */
    default ChestTier minTier() {
        return ChestTier.BASIC;
    }
}
