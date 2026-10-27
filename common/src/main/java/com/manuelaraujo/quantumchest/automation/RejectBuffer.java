package com.manuelaraujo.quantumchest.automation;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Where turned away items wait to leave the chest, like the nine slots of a
 * dropper. Taking them in is always reversible (nothing is dropped or
 * destroyed until the next tick), which NeoForge needs to undo simulated
 * transfers. Saved with the chest, and dropped on the ground if it breaks.
 */
public final class RejectBuffer {
    public static final int SLOTS = 9;

    private final List<ItemStack> stacks = new ArrayList<>();

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public int size() {
        return stacks.size();
    }

    public ItemStack get(int slot) {
        return slot >= 0 && slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
    }

    public List<ItemStack> stacks() {
        return List.copyOf(stacks);
    }

    /** Adds up to {@code amount} of the item, merging into stacks that have room. Returns how many fit. */
    public long add(ItemStack template, long amount, boolean simulate) {
        if (template.isEmpty() || amount <= 0) return 0;
        int max = template.getMaxStackSize();
        long left = amount;
        for (ItemStack stack : stacks) {
            if (left <= 0) break;
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
            int room = max - stack.getCount();
            if (room <= 0) continue;
            int moved = (int) Math.min(room, left);
            if (!simulate) stack.grow(moved);
            left -= moved;
        }
        int freeSlots = SLOTS - stacks.size();
        while (left > 0 && freeSlots > 0) {
            int moved = (int) Math.min(max, left);
            if (!simulate) stacks.add(template.copyWithCount(moved));
            left -= moved;
            freeSlots--;
        }
        return amount - left;
    }

    /** Takes up to {@code amount} from one slot. */
    public ItemStack take(int slot, int amount, boolean simulate) {
        ItemStack stack = get(slot);
        if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int taken = Math.min(amount, stack.getCount());
        ItemStack result = stack.copyWithCount(taken);
        if (!simulate) {
            stack.shrink(taken);
            if (stack.isEmpty()) stacks.remove(slot);
        }
        return result;
    }

    /** Takes up to {@code amount} of an item from anywhere in the buffer. Used to undo. */
    public long remove(ItemStack template, long amount) {
        long left = amount;
        for (int i = stacks.size() - 1; i >= 0 && left > 0; i--) {
            ItemStack stack = stacks.get(i);
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
            int taken = (int) Math.min(left, stack.getCount());
            stack.shrink(taken);
            if (stack.isEmpty()) stacks.remove(i);
            left -= taken;
        }
        return amount - left;
    }

    /** Puts a stack back after a failed attempt to send it out, ignoring the slot limit so nothing is lost. */
    public void putBack(ItemStack stack) {
        if (!stack.isEmpty()) stacks.add(stack);
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> all = List.copyOf(stacks);
        stacks.clear();
        return all;
    }

    public void load(List<ItemStack> saved) {
        stacks.clear();
        for (ItemStack stack : saved) {
            if (!stack.isEmpty()) stacks.add(stack.copy());
        }
    }
}
