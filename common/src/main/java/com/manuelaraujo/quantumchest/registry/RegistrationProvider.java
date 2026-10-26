package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registers objects from common code. Forge and NeoForge each provide a
 * {@link Factory} that wraps their own DeferredRegister.
 */
public interface RegistrationProvider<T> {

    static <T> RegistrationProvider<T> get(ResourceKey<? extends Registry<T>> registry, String modId) {
        return Factory.INSTANCE.create(registry, modId);
    }

    ResourceKey<? extends Registry<T>> registryKey();

    String modId();

    <I extends T> RegistryEntry<T, I> register(String name, Supplier<? extends I> factory);

    /**
     * Same as {@link #register}, but hands the entry's own key to the factory.
     * Blocks and items need it since 1.21.2 ({@code Properties#setId}).
     */
    default <I extends T> RegistryEntry<T, I> registerWithKey(String name, Function<ResourceKey<T>, ? extends I> factory) {
        ResourceKey<T> key = keyFor(name);
        return register(name, () -> factory.apply(key));
    }

    default ResourceKey<T> keyFor(String name) {
        return ResourceKey.create(registryKey(), Identifier.fromNamespaceAndPath(modId(), name));
    }

    interface Factory {
        Factory INSTANCE = Services.load(Factory.class);

        <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> registry, String modId);
    }
}
