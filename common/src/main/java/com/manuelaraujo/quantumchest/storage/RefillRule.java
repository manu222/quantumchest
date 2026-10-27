package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * An auto-refill rule: with a linked terminal on you, the chest keeps
 * {@code amount} of the matching items in your inventory. The mode says
 * which way: hand out what's missing, take what's above, or both.
 */
public record RefillRule(ItemMatch match, long amount, RefillMode mode) {
    public static final int MAX_RULES = 12;
    /** A full inventory of one thing. Nobody needs more on them. */
    public static final long MAX_AMOUNT = 64 * 36;

    public static final Codec<RefillRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemMatch.CODEC.fieldOf("match").forGetter(RefillRule::match),
            Codec.LONG.optionalFieldOf("amount", 64L).forGetter(RefillRule::amount),
            RefillMode.CODEC.optionalFieldOf("mode", RefillMode.REFILL).forGetter(RefillRule::mode)
    ).apply(i, RefillRule::new));

    public static final StreamCodec<ByteBuf, RefillRule> STREAM_CODEC = StreamCodec.composite(
            ItemMatch.STREAM_CODEC, RefillRule::match,
            ByteBufCodecs.VAR_LONG, RefillRule::amount,
            RefillMode.STREAM_CODEC, RefillRule::mode,
            RefillRule::new);

    public RefillRule withAmount(long newAmount) {
        return new RefillRule(match, newAmount, mode);
    }

    public RefillRule withMode(RefillMode newMode) {
        return new RefillRule(match, amount, newMode);
    }

    public RefillRule withMatch(ItemMatch newMatch) {
        return new RefillRule(newMatch, amount, mode);
    }

    public static List<RefillRule> sanitize(List<RefillRule> rules) {
        List<RefillRule> clean = new ArrayList<>();
        for (RefillRule rule : rules) {
            if (clean.size() >= MAX_RULES) break;
            if (!rule.match().isValid() || clean.stream().anyMatch(r -> r.match().equals(rule.match()))) continue;
            // 0 makes sense when storing: everything of that kind goes to the chest
            clean.add(new RefillRule(rule.match(), Math.max(0, Math.min(MAX_AMOUNT, rule.amount())),
                    rule.mode() == null ? RefillMode.REFILL : rule.mode()));
        }
        return List.copyOf(clean);
    }
}
