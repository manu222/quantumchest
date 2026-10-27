package com.manuelaraujo.quantumchest.storage;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every QuantumChest storage in the world, saved under data/quantumchest/ in the
 * world folder. Lives on the overworld so all dimensions share it.
 */
public final class QuantumStorageData extends SavedData {
    private static final Codec<QuantumStorageData> CODEC = RecordCodecBuilder.<QuantumStorageData>mapCodec(i -> i.group(
            QuantumStorage.CODEC.listOf().fieldOf("storages").forGetter(d -> List.copyOf(d.storages.values()))
    ).apply(i, QuantumStorageData::new)).codec();

    private static final SavedDataType<QuantumStorageData> TYPE = new SavedDataType<>(
            QuantumChest.id("storages"), QuantumStorageData::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, QuantumStorage> storages = new HashMap<>();

    private QuantumStorageData() {
    }

    private QuantumStorageData(List<QuantumStorage> saved) {
        for (QuantumStorage storage : saved) {
            track(storage);
        }
    }

    public static QuantumStorageData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Null if no storage has that id. */
    public QuantumStorage get(UUID id) {
        return storages.get(id);
    }

    public QuantumStorage create() {
        QuantumStorage storage = new QuantumStorage(UUID.randomUUID());
        track(storage);
        setDirty();
        return storage;
    }

    public int size() {
        return storages.size();
    }

    public java.util.Collection<QuantumStorage> all() {
        return java.util.Collections.unmodifiableCollection(storages.values());
    }

    private void track(QuantumStorage storage) {
        storage.setOnChange(this::setDirty);
        storages.put(storage.id(), storage);
    }
}
