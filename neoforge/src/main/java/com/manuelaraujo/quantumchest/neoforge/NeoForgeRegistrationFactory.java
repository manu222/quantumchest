package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.registry.RegistrationProvider;
import com.manuelaraujo.quantumchest.registry.RegistryEntry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class NeoForgeRegistrationFactory implements RegistrationProvider.Factory {
    // Filled while the common registry classes load, emptied into the mod bus afterwards
    private static final List<DeferredRegister<?>> PENDING = new ArrayList<>();

    @Override
    public <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> registry, String modId) {
        DeferredRegister<T> register = DeferredRegister.create(registry, modId);
        PENDING.add(register);
        return new Provider<>(register, registry, modId);
    }

    static void registerAll(IEventBus modBus) {
        PENDING.forEach(register -> register.register(modBus));
        PENDING.clear();
    }

    private record Provider<T>(DeferredRegister<T> deferred, ResourceKey<? extends Registry<T>> registryKey,
                               String modId) implements RegistrationProvider<T> {

        @Override
        public <I extends T> RegistryEntry<T, I> register(String name, Supplier<? extends I> factory) {
            DeferredHolder<T, I> holder = deferred.register(name, factory);
            return new RegistryEntry<>(keyFor(name), holder.getId(), holder);
        }
    }
}
