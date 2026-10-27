package com.manuelaraujo.quantumchest.block;

import com.manuelaraujo.quantumchest.QuantumConfig;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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

    public static final Codec<ChestTier> CODEC = StringRepresentable.fromEnum(ChestTier::values);
    public static final StreamCodec<ByteBuf, ChestTier> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.max(0, Math.min(i, values().length - 1))], ChestTier::ordinal);

    private final String name;
    private final long capacity;
    private final int upgradeSlots;

    ChestTier(String name, long capacity, int upgradeSlots) {
        this.name = name;
        this.capacity = capacity;
        this.upgradeSlots = upgradeSlots;
    }

    /** From the server config. On a client connected to a server, use the value the chest screen received. */
    public long capacity() {
        return QuantumConfig.capacity(this);
    }

    /** The capacity this tier has unless the config changes it. */
    public long defaultCapacity() {
        return capacity;
    }

    public boolean isUnlimited() {
        return capacity() == Long.MAX_VALUE;
    }

    public int upgradeSlots() {
        return upgradeSlots;
    }

    /** Next tier up, or null if this is already the last one. */
    public ChestTier next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    public boolean atLeast(ChestTier other) {
        return ordinal() >= other.ordinal();
    }

    public static ChestTier max(ChestTier a, ChestTier b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public String translationKey() {
        return "tier.quantumchest." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
