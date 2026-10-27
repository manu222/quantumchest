package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item storage without slots: a map from item type to amount.
 * This class knows nothing about blocks or worlds, so it can be shared
 * later by linked chests and the remote terminal.
 */
public final class QuantumInventory {
    private final Map<ItemKey, Long> items = new LinkedHashMap<>();
    private long total;
    // Bumped on every change, so open screens only resync when something moved
    private int version;

    /**
     * Stores as much of the stack as fits under the capacity.
     * Does not touch the stack, returns how many items were taken.
     */
    public long insert(ItemStack stack, long capacity) {
        return insert(stack, stack.getCount(), capacity);
    }

    /** Same as above, for amounts bigger than a stack. Only the item type of {@code template} matters. */
    public long insert(ItemStack template, long amount, long capacity) {
        if (template.isEmpty() || amount <= 0) return 0;
        long accepted = Math.min(amount, capacity - total);
        if (accepted <= 0) return 0;
        items.merge(ItemKey.of(template), accepted, QuantumInventory::addCapped);
        total = addCapped(total, accepted);
        version++;
        return accepted;
    }

    /** Takes up to {@code max} items of the given type, capped at one stack. */
    public ItemStack extract(ItemKey key, long max) {
        Long stored = items.get(key);
        if (stored == null || max <= 0) return ItemStack.EMPTY;
        int amount = (int) Math.min(Math.min(stored, max), key.maxStackSize());
        if (amount == stored) {
            items.remove(key);
        } else {
            items.put(key, stored - amount);
        }
        total -= amount;
        version++;
        return key.toStack(amount);
    }

    /** Oldest stored type, handy until the real interface exists. */
    public ItemKey firstKey() {
        return items.isEmpty() ? null : items.keySet().iterator().next();
    }

    public long count(ItemKey key) {
        return items.getOrDefault(key, 0L);
    }

    public int version() {
        return version;
    }

    public long totalItems() {
        return total;
    }

    public int typeCount() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Map<ItemKey, Long> view() {
        return Collections.unmodifiableMap(items);
    }

    public void clear() {
        items.clear();
        total = 0;
        version++;
    }

    public List<Entry> entryList() {
        return items.entrySet().stream()
                .map(e -> new Entry(e.getKey().template(), e.getValue()))
                .toList();
    }

    public void load(Iterable<Entry> saved) {
        clear();
        for (Entry entry : saved) {
            if (entry.stack().isEmpty() || entry.count() <= 0) continue;
            items.merge(ItemKey.of(entry.stack()), entry.count(), QuantumInventory::addCapped);
            total = addCapped(total, entry.count());
        }
    }

    // A hand edited save could push the sum past Long.MAX_VALUE
    private static long addCapped(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    /** What gets written to disk for each item type. */
    public record Entry(ItemStack stack, long count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("item").forGetter(Entry::stack),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)
        ).apply(i, Entry::new));
    }
}
