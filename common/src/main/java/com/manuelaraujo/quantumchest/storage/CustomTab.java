package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * A tab the players made themselves, like "Wood". It lists item types (by id,
 * so renamed or enchanted copies still count) and item tags, so a tab with
 * #minecraft:logs also picks up logs from mods added later.
 *
 * A tab can also have a {@link TabRole}: whitelist, destroy or compact. For
 * the last two, {@code limit} is how many to keep before the rule kicks in.
 */
public record CustomTab(String name, ItemStack icon, List<Identifier> items, List<Identifier> tags,
                        TabRole role, long limit) {
    public static final int MAX_TABS = 16;
    public static final int MAX_NAME = 24;
    private static final int MAX_ITEMS = 256;
    private static final int MAX_TAGS = 16;

    public static final Codec<CustomTab> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(CustomTab::name),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("icon", ItemStack.EMPTY).forGetter(CustomTab::icon),
            Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(CustomTab::items),
            Identifier.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(CustomTab::tags),
            TabRole.CODEC.optionalFieldOf("role", TabRole.NORMAL).forGetter(CustomTab::role),
            Codec.LONG.optionalFieldOf("limit", 0L).forGetter(CustomTab::limit)
    ).apply(i, CustomTab::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CustomTab> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CustomTab::name,
            ItemStack.OPTIONAL_STREAM_CODEC, CustomTab::icon,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), CustomTab::items,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), CustomTab::tags,
            TabRole.STREAM_CODEC, CustomTab::role,
            ByteBufCodecs.VAR_LONG, CustomTab::limit,
            CustomTab::new);

    public boolean matches(ItemStack stack) {
        if (items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return true;
        for (Identifier tag : tags) {
            if (stack.is(TagKey.create(Registries.ITEM, tag))) return true;
        }
        return false;
    }

    /** What the icon shows: the chosen one, or the first item of the tab. */
    public ItemStack displayIcon() {
        if (!icon.isEmpty()) return icon;
        if (!items.isEmpty()) {
            Item item = BuiltInRegistries.ITEM.getValue(items.getFirst());
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Clean up whatever a client sent: trim the name, drop unknown items,
     * duplicates and anything over the limits.
     */
    public CustomTab sanitized() {
        String cleanName = name.strip();
        if (cleanName.length() > MAX_NAME) cleanName = cleanName.substring(0, MAX_NAME);
        LinkedHashSet<Identifier> cleanItems = new LinkedHashSet<>();
        for (Identifier id : items) {
            if (cleanItems.size() >= MAX_ITEMS) break;
            if (BuiltInRegistries.ITEM.containsKey(id)) cleanItems.add(id);
        }
        LinkedHashSet<Identifier> cleanTags = new LinkedHashSet<>(tags.subList(0, Math.min(tags.size(), MAX_TAGS)));
        return new CustomTab(cleanName, icon.copyWithCount(1), List.copyOf(cleanItems), List.copyOf(cleanTags),
                role, role.usesLimit() ? Math.max(0, limit) : 0);
    }
}
