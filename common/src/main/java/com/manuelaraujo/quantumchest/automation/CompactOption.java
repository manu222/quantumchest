package com.manuelaraujo.quantumchest.automation;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Something the compactor can do: {@code ratio} of {@code input} become one {@code result}. */
public record CompactOption(Identifier input, Identifier result, int ratio) {
    public static final StreamCodec<ByteBuf, CompactOption> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CompactOption::input,
            Identifier.STREAM_CODEC, CompactOption::result,
            ByteBufCodecs.VAR_INT, CompactOption::ratio,
            CompactOption::new);
}
