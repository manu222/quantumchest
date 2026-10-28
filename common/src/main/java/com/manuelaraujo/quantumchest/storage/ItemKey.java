package com.manuelaraujo.quantumchest.storage;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One item type inside the chest: the item plus its components
 * (enchantments, custom name, damage...). Two stacks with the same key
 * can be merged; anything else is a different entry.
 */
public final class ItemKey {
    private final ItemStack template;
    private final int hash;
    // Bytes the template takes in a packet, worked out the first time it's asked for
    private int encodedSize = -1;

    private ItemKey(ItemStack template) {
        this.template = template;
        this.hash = 31 * template.getItem().hashCode() + template.getComponentsPatch().hashCode();
    }

    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.copyWithCount(1));
    }

    /** A fresh stack of this item. The caller is free to modify it. */
    public ItemStack toStack(int count) {
        return template.copyWithCount(count);
    }

    /**
     * How many bytes this item takes in a packet. Most are a handful, but a
     * shulker box full of books can be huge, and the chest sync has to split
     * its packets before they reach the size limit.
     */
    public int encodedSize(RegistryAccess registries) {
        if (encodedSize < 0) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                ItemStack.STREAM_CODEC.encode(buffer, template);
                encodedSize = buffer.writerIndex();
            } catch (RuntimeException e) {
                // Can't be measured: count it as big, the real encode will report the problem
                encodedSize = 64 * 1024;
            } finally {
                buffer.release();
            }
        }
        return encodedSize;
    }

    public int maxStackSize() {
        return template.getMaxStackSize();
    }

    /** Read only view, do not modify the returned stack. */
    public ItemStack template() {
        return template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey other)) return false;
        return hash == other.hash && ItemStack.isSameItemSameComponents(template, other.template);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
