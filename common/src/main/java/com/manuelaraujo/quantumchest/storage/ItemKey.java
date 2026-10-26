package com.manuelaraujo.quantumchest.storage;

import net.minecraft.world.item.ItemStack;

/**
 * One item type inside the chest: the item plus its components
 * (enchantments, custom name, damage...). Two stacks with the same key
 * can be merged; anything else is a different entry.
 */
public final class ItemKey {
    private final ItemStack template;
    private final int hash;

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
