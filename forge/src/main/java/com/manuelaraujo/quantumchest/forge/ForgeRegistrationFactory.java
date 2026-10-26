package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.registry.RegistrationProvider;
import com.manuelaraujo.quantumchest.registry.RegistryEntry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ForgeRegistrationFactory implements RegistrationProvider.Factory {
    // Filled while the common registry classes load, emptied into the mod bus afterwards
    private static final List<DeferredRegister<?>> PENDING = new ArrayList<>();

    @Override
    public <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> registry, String modId) {
        DeferredRegister<T> register = DeferredRegister.create(registry, modId);
        PENDING.add(register);
        return new Provider<>(register, registry, modId);
    }

    static void registerAll(BusGroup modBusGroup) {
        PENDING.forEach(register -> register.register(modBusGroup));
        PENDING.clear();
    }

    private record Provider<T>(DeferredRegister<T> deferred, ResourceKey<? extends Registry<T>> registryKey,
                               String modId) implements RegistrationProvider<T> {

        @Override
        public <I extends T> RegistryEntry<T, I> register(String name, Supplier<? extends I> factory) {
            RegistryObject<I> object = deferred.register(name, factory);
            return new RegistryEntry<>(keyFor(name), object.getId(), object);
        }
    }
}
