package com.manuelaraujo.quantumchest.block;

import net.minecraft.util.StringRepresentable;

/**
 * Chest levels. Capacity counts items, not item types: any number of
 * different items fits as long as the total stays under the limit.
 */
public enum ChestTier implements StringRepresentable {
    BASIC("basic", 10_000L, 1),
    REINFORCED("reinforced", 100_000L, 2),
    ADVANCED("advanced", 1_000_000L, 3),
    ELITE("elite", 100_000_000L, 5),
    QUANTUM("quantum", Long.MAX_VALUE, 7);

    private final String name;
    private final long capacity;
    private final int upgradeSlots;

    ChestTier(String name, long capacity, int upgradeSlots) {
        this.name = name;
        this.capacity = capacity;
        this.upgradeSlots = upgradeSlots;
    }

    public long capacity() {
        return capacity;
    }

    public boolean isUnlimited() {
        return capacity == Long.MAX_VALUE;
    }

    public int upgradeSlots() {
        return upgradeSlots;
    }

    /** Next tier up, or null if this is already the last one. */
    public ChestTier next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    public String translationKey() {
        return "tier.quantumchest." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
