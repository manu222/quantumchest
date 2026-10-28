package com.manuelaraujo.quantumchest.storage;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.QuantumConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every QuantumChest storage in the world, saved under data/quantumchest/ in the
 * world folder. Lives on the overworld so all dimensions share it.
 *
 * When two chests are entangled, one storage is poured into the other and its
 * id becomes an alias of the survivor. Anything still holding the old id (a
 * chest in an unloaded chunk, a chest item in a box, a terminal) keeps working.
 */
public final class QuantumStorageData extends SavedData {
    private static final int MAX_ALIAS_HOPS = 32;

    private static final Codec<QuantumStorageData> CODEC = RecordCodecBuilder.<QuantumStorageData>mapCodec(i -> i.group(
            QuantumStorage.CODEC.listOf().fieldOf("storages").forGetter(d -> List.copyOf(d.storages.values())),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC).optionalFieldOf("aliases", Map.of())
                    .forGetter(d -> Map.copyOf(d.aliases))
    ).apply(i, QuantumStorageData::new)).codec();

    private static final SavedDataType<QuantumStorageData> TYPE = new SavedDataType<>(
            QuantumChest.id("storages"), QuantumStorageData::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, QuantumStorage> storages = new HashMap<>();
    private final Map<UUID, UUID> aliases = new HashMap<>();

    private QuantumStorageData() {
    }

    private QuantumStorageData(List<QuantumStorage> saved, Map<UUID, UUID> savedAliases) {
        int forgotten = 0;
        for (QuantumStorage storage : saved) {
            // Chests that were crafted, placed and picked up again leave an empty storage behind.
            // Nothing in it, nothing set up and no chest placed: drop it so the file doesn't grow forever
            if (storage.isUnused()) {
                forgotten++;
                continue;
            }
            track(storage);
        }
        aliases.putAll(savedAliases);
        if (forgotten > 0) {
            QuantumChest.LOG.info("Forgot {} empty, unused chest storages", forgotten);
            setDirty();
        }
    }

    // Looked up by every chest every tick, so the last one is kept at hand
    private static volatile MinecraftServer cachedServer;
    private static volatile QuantumStorageData cached;

    public static QuantumStorageData get(MinecraftServer server) {
        QuantumStorageData data = cached;
        if (data != null && cachedServer == server) return data;
        // Everything that needs the settings goes through here first, so this is where they load
        QuantumConfig.ensureLoaded(server);
        data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        cached = data;
        cachedServer = server;
        return data;
    }

    /** The storage with that id, following aliases. Null if there is none. */
    public QuantumStorage get(UUID id) {
        return id == null ? null : storages.get(resolve(id));
    }

    /** The id a storage ended up with after being entangled, or the same id. */
    public UUID resolve(UUID id) {
        UUID current = id;
        for (int i = 0; i < MAX_ALIAS_HOPS && aliases.containsKey(current); i++) {
            current = aliases.get(current);
        }
        return current;
    }

    public QuantumStorage create() {
        QuantumStorage storage = new QuantumStorage(UUID.randomUUID());
        track(storage);
        setDirty();
        return storage;
    }

    /**
     * Pours {@code from} into {@code into} and leaves the old id pointing at
     * {@code into}. Check {@link QuantumStorage#checkAbsorb} before calling.
     * Returns the upgrades that were left over because {@code into} already had one.
     */
    public List<ItemStack> merge(QuantumStorage into, QuantumStorage from) {
        if (into == from) return List.of();
        List<ItemStack> spare = into.absorb(from);
        storages.remove(from.id());
        aliases.replaceAll((alias, target) -> target.equals(from.id()) ? into.id() : target);
        aliases.put(from.id(), into.id());
        setDirty();
        return spare;
    }

    /**
     * Takes one chest out of its network. The chest gets a new, empty storage
     * with the same owner, access, members, tabs and tier; the items stay with
     * the rest of the network.
     */
    public QuantumStorage split(QuantumStorage network, GlobalPos chest) {
        QuantumStorage fresh = create();
        fresh.copySettingsFrom(network);
        network.removeAnchor(chest);
        fresh.addAnchor(chest);
        setDirty();
        return fresh;
    }

    public int size() {
        return storages.size();
    }

    public Collection<QuantumStorage> all() {
        return Collections.unmodifiableCollection(storages.values());
    }

    private void track(QuantumStorage storage) {
        storage.setOnChange(this::setDirty);
        storage.setResolver(this::resolve);
        storages.put(storage.id(), storage);
    }
}
