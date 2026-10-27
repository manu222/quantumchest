package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.storage.ItemKey;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Comparator;
import java.util.Map;

/** How the grid is ordered. */
public enum SortMode {
    AMOUNT("#", Comparator.comparingLong(Map.Entry::getValue)),
    NAME("Aa", Comparator.comparing(e -> name(e.getKey()))),
    MOD("@", Comparator.<Map.Entry<ItemKey, Long>, String>comparing(e -> namespace(e.getKey()))
            .thenComparing(e -> name(e.getKey())));

    private final String icon;
    private final Comparator<Map.Entry<ItemKey, Long>> comparator;

    SortMode(String icon, Comparator<Map.Entry<ItemKey, Long>> comparator) {
        this.icon = icon;
        this.comparator = comparator;
    }

    public String icon() {
        return icon;
    }

    public String translationKey() {
        return "gui.quantumchest.sort." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Amount sorts biggest first by default, names A to Z. {@code reversed}
     * flips whichever that is.
     */
    public Comparator<Map.Entry<ItemKey, Long>> comparator(boolean reversed) {
        Comparator<Map.Entry<ItemKey, Long>> natural = this == AMOUNT ? comparator.reversed() : comparator;
        return reversed ? natural.reversed() : natural;
    }

    public SortMode next() {
        SortMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    static String name(ItemKey key) {
        return key.template().getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
    }

    static String namespace(ItemKey key) {
        return BuiltInRegistries.ITEM.getKey(key.template().getItem()).getNamespace();
    }
}
