package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * How the settings of the phase 6 upgrades are saved: auto-refill rules, what
 * the smelter melts, which fuels it may burn, the heat it has left over and
 * how far the current batch has cooked.
 * One field in the storage instead of four keeps the codec under its limit.
 */
record MachineSettings(List<RefillRule> refill, List<CompactRule> smelt, List<ItemMatch> fuels, long fuelTicks, int cookProgress) {
    static final MachineSettings EMPTY = new MachineSettings(List.of(), List.of(), List.of(), 0, 0);

    static final Codec<MachineSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            RefillRule.CODEC.listOf().optionalFieldOf("refill", List.of()).forGetter(MachineSettings::refill),
            CompactRule.CODEC.listOf().optionalFieldOf("smelt", List.of()).forGetter(MachineSettings::smelt),
            ItemMatch.CODEC.listOf().optionalFieldOf("fuels", List.of()).forGetter(MachineSettings::fuels),
            Codec.LONG.optionalFieldOf("fuel_ticks", 0L).forGetter(MachineSettings::fuelTicks),
            Codec.INT.optionalFieldOf("cook_progress", 0).forGetter(MachineSettings::cookProgress)
    ).apply(i, MachineSettings::new));
}
