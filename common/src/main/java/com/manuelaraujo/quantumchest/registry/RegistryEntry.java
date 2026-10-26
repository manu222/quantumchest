package com.manuelaraujo.quantumchest.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/**
 * Loader independent handle to something registered by the mod.
 * The value is only available once the loader has fired its registry events.
 */
public final class RegistryEntry<T, I extends T> implements Supplier<I> {
    private final ResourceKey<T> key;
    private final Identifier id;
    private final Supplier<I> value;

    public RegistryEntry(ResourceKey<T> key, Identifier id, Supplier<I> value) {
        this.key = key;
        this.id = id;
        this.value = value;
    }

    @Override
    public I get() {
        return value.get();
    }

    public ResourceKey<T> key() {
        return key;
    }

    public Identifier id() {
        return id;
    }
}
