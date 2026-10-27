package com.manuelaraujo.quantumchest.block.entity;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.registry.ModBlockEntities;
import com.manuelaraujo.quantumchest.registry.ModDataComponents;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The block only knows which storage it shows. The items themselves live in
 * {@link QuantumStorageData}, so they survive when the chest is broken.
 * Everything that touches the storage runs on the server only.
 */
public class QuantumChestBlockEntity extends BlockEntity {
    private static final String STORAGE_TAG = "StorageId";
    private static final String OWNER_TAG = "Owner";
    // Phase 0 kept the items inside the block. Read them once and move them over.
    private static final String LEGACY_ITEMS_TAG = "Items";

    private UUID storageId;
    // Copy of the storage owner, sent to clients so they know whether they may break the chest
    private UUID ownerCache;
    private List<QuantumInventory.Entry> legacyItems = List.of();

    public QuantumChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_CHEST.get(), pos, state);
    }

    public ChestTier tier() {
        return getBlockState().getValue(QuantumChestBlock.TIER);
    }

    public UUID storageId() {
        return storageId;
    }

    /** The storage this chest shows, created on first use. Server side only. */
    public QuantumStorage storage() {
        QuantumStorageData data = QuantumStorageData.get(server());
        QuantumStorage storage = storageId == null ? null : data.get(storageId);
        if (storage == null) {
            storage = data.create();
            storageId = storage.id();
            setChanged();
        }
        if (!legacyItems.isEmpty()) {
            for (QuantumInventory.Entry entry : legacyItems) {
                storage.inventory().insert(entry.stack(), entry.count(), Long.MAX_VALUE);
            }
            legacyItems = List.of();
            storage.markChanged();
            setChanged();
        }
        syncOwner(storage);
        return storage;
    }

    /** Makes the player the owner if the storage has none yet. */
    public void claim(Player player) {
        QuantumStorage storage = storage();
        if (!storage.hasOwner()) {
            storage.setOwner(player);
            syncOwner(storage);
        }
    }

    /** Owner as the client sees it. Null means nobody owns it yet. */
    public UUID cachedOwner() {
        return ownerCache;
    }

    private void syncOwner(QuantumStorage storage) {
        if (Objects.equals(ownerCache, storage.owner())) return;
        ownerCache = storage.owner();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The storage if this chest already has one, without creating it. Server side only. */
    public QuantumStorage existingStorage() {
        return storageId == null ? null : QuantumStorageData.get(server()).get(storageId);
    }

    private MinecraftServer server() {
        if (level == null || level.getServer() == null) {
            throw new IllegalStateException("QuantumChest storage used outside of a server level");
        }
        return level.getServer();
    }

    /** Returns how many items of the stack were stored. The stack itself is not changed. */
    public long insert(ItemStack stack) {
        QuantumStorage storage = storage();
        long stored = storage.inventory().insert(stack, tier().capacity());
        if (stored > 0) storage.markChanged();
        return stored;
    }

    public ItemStack extractFirst() {
        QuantumStorage storage = storage();
        ItemKey key = storage.inventory().firstKey();
        if (key == null) return ItemStack.EMPTY;
        ItemStack taken = storage.inventory().extract(key, key.maxStackSize());
        storage.markChanged();
        return taken;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (storageId != null) {
            output.store(STORAGE_TAG, UUIDUtil.CODEC, storageId);
        }
        if (ownerCache != null) {
            output.store(OWNER_TAG, UUIDUtil.CODEC, ownerCache);
        }
        if (!legacyItems.isEmpty()) {
            var list = output.list(LEGACY_ITEMS_TAG, QuantumInventory.Entry.CODEC);
            legacyItems.forEach(list::add);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storageId = input.read(STORAGE_TAG, UUIDUtil.CODEC).orElse(null);
        ownerCache = input.read(OWNER_TAG, UUIDUtil.CODEC).orElse(null);
        legacyItems = input.listOrEmpty(LEGACY_ITEMS_TAG, QuantumInventory.Entry.CODEC).stream().toList();
    }

    // Sent to clients when the chunk loads and after sendBlockUpdated

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    // The three methods below move the storage id between the block and its item:
    // breaking copies it to the drop (see the loot table), placing reads it back.

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        UUID fromItem = components.get(ModDataComponents.STORAGE_ID.get());
        if (fromItem != null) {
            storageId = fromItem;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (storageId != null) {
            components.set(ModDataComponents.STORAGE_ID.get(), storageId);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard(STORAGE_TAG);
        output.discard(OWNER_TAG);
    }
}
