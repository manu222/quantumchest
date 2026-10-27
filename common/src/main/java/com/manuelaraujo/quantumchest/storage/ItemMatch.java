package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * One entry of a filter or a destroy rule: an exact item, or with {@code tag}
 * set, every item in that tag (all ores with #c:ores, logs of any mod with
 * #minecraft:logs). Components are ignored, so a renamed copy still matches.
 */
public record ItemMatch(Identifier item, Optional<Identifier> tag) {
    public static final Codec<ItemMatch> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("item").forGetter(ItemMatch::item),
            Identifier.CODEC.optionalFieldOf("tag").forGetter(ItemMatch::tag)
    ).apply(i, ItemMatch::new));

    public static final StreamCodec<ByteBuf, ItemMatch> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, ItemMatch::item,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), ItemMatch::tag,
            ItemMatch::new);

    public static ItemMatch of(ItemStack stack) {
        return new ItemMatch(BuiltInRegistries.ITEM.getKey(stack.getItem()), Optional.empty());
    }

    public ItemMatch withTag(Optional<Identifier> newTag) {
        return new ItemMatch(item, newTag);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (tag.isPresent()) return stack.is(TagKey.create(Registries.ITEM, tag.get()));
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
    }

    /** The item the entry was made from, to draw it. */
    public ItemStack icon() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(item));
    }

    public boolean isValid() {
        return BuiltInRegistries.ITEM.containsKey(item);
    }
}
