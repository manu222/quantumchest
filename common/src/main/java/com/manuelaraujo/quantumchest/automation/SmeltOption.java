package com.manuelaraujo.quantumchest.automation;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Something the smelter can melt: {@code input} becomes {@code count} of {@code result}, using {@code cookTime} ticks of fuel. */
public record SmeltOption(Identifier input, Identifier result, int count, int cookTime) {
    public static final StreamCodec<ByteBuf, SmeltOption> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SmeltOption::input,
            Identifier.STREAM_CODEC, SmeltOption::result,
            ByteBufCodecs.VAR_INT, SmeltOption::count,
            ByteBufCodecs.VAR_INT, SmeltOption::cookTime,
            SmeltOption::new);
}
