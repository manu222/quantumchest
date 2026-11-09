package com.manuelaraujo.quantumchest.automation;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** How many ticks one {@code item} burns in a plain furnace. Sent to the smelter screen for its fuel gauge. */
public record FuelTime(Identifier item, int ticks) {
    public static final StreamCodec<ByteBuf, FuelTime> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, FuelTime::item,
            ByteBufCodecs.VAR_INT, FuelTime::ticks,
            FuelTime::new);
}
