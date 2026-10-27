package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Whether the compactor turns this item into its block form, and how many to leave loose. */
public record CompactRule(Identifier item, boolean enabled, long keep) {
    public static final Codec<CompactRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("item").forGetter(CompactRule::item),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CompactRule::enabled),
            Codec.LONG.optionalFieldOf("keep", 0L).forGetter(CompactRule::keep)
    ).apply(i, CompactRule::new));

    public static final StreamCodec<ByteBuf, CompactRule> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CompactRule::item,
            ByteBufCodecs.BOOL, CompactRule::enabled,
            ByteBufCodecs.VAR_LONG, CompactRule::keep,
            CompactRule::new);
}
