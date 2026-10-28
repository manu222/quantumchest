package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.storage.ItemKey;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Comparator;
import java.util.Map;

/** How the grid is ordered. */
public enum SortMode {
    AMOUNT("#"),
    NAME("Aa"),
    MOD("@");

    private final String icon;

    SortMode(String icon) {
        this.icon = icon;
    }

    public String icon() {
        return icon;
    }

    public String translationKey() {
        return "gui.quantumchest.sort." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Amount sorts biggest first by default, names A to Z. {@code reversed}
     * flips whichever that is. Names come from {@code names}, which keeps them:
     * building a display name for every comparison makes big chests stutter.
     */
    public Comparator<Map.Entry<ItemKey, Long>> comparator(boolean reversed, ItemSearch names) {
        Comparator<Map.Entry<ItemKey, Long>> natural = switch (this) {
            case AMOUNT -> Comparator.<Map.Entry<ItemKey, Long>>comparingLong(Map.Entry::getValue).reversed();
            case NAME -> Comparator.comparing(e -> names.name(e.getKey()));
            case MOD -> Comparator.<Map.Entry<ItemKey, Long>, String>comparing(e -> names.namespace(e.getKey()))
                    .thenComparing(e -> names.name(e.getKey()));
        };
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
