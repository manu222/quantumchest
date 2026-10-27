package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * A destroyer rule: keep up to {@code keep} of the matching items, destroy
 * whatever else arrives on its own. Keep 0 destroys every arrival. Stored
 * items are never touched.
 */
public record VoidRule(ItemMatch match, long keep) {
    public static final int MAX_RULES = 12;

    public static final Codec<VoidRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemMatch.CODEC.fieldOf("match").forGetter(VoidRule::match),
            Codec.LONG.optionalFieldOf("keep", 0L).forGetter(VoidRule::keep)
    ).apply(i, VoidRule::new));

    public static final StreamCodec<ByteBuf, VoidRule> STREAM_CODEC = StreamCodec.composite(
            ItemMatch.STREAM_CODEC, VoidRule::match,
            ByteBufCodecs.VAR_LONG, VoidRule::keep,
            VoidRule::new);

    public static List<VoidRule> sanitize(List<VoidRule> rules) {
        List<VoidRule> clean = new ArrayList<>();
        for (VoidRule rule : rules) {
            if (clean.size() >= MAX_RULES) break;
            if (rule.match().isValid()) clean.add(new VoidRule(rule.match(), Math.max(0, rule.keep())));
        }
        return List.copyOf(clean);
    }
}
