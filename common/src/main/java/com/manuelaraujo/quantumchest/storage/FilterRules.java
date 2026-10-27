package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The input filter of a chest. It only applies to items that arrive on their
 * own (magnet, hoppers, pipes, the I/O upgrade); a player can always put
 * anything in by hand. Empty means everything gets in.
 */
public record FilterRules(boolean blacklist, List<ItemMatch> entries) {
    public static final int MAX_ENTRIES = 18;
    public static final FilterRules NONE = new FilterRules(false, List.of());

    public static final Codec<FilterRules> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(FilterRules::blacklist),
            ItemMatch.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(FilterRules::entries)
    ).apply(i, FilterRules::new));

    public static final StreamCodec<ByteBuf, FilterRules> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FilterRules::blacklist,
            ItemMatch.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), FilterRules::entries,
            FilterRules::new);

    public boolean admits(ItemStack stack) {
        if (entries.isEmpty()) return true;
        boolean listed = entries.stream().anyMatch(entry -> entry.matches(stack));
        return blacklist != listed;
    }

    /** Whatever a client sent, cut down to something valid. */
    public FilterRules sanitized() {
        List<ItemMatch> clean = new ArrayList<>();
        for (ItemMatch entry : entries) {
            if (clean.size() >= MAX_ENTRIES) break;
            if (entry.isValid() && !clean.contains(entry)) clean.add(entry);
        }
        return new FilterRules(blacklist, List.copyOf(clean));
    }
}
