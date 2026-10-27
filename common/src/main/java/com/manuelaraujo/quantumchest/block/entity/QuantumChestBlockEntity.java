package com.manuelaraujo.quantumchest.block.entity;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.item.QuantumChestItem;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.menu.StorageHost;
import com.manuelaraujo.quantumchest.registry.ModBlockEntities;
import com.manuelaraujo.quantumchest.registry.ModDataComponents;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The block only knows which storage it shows. The items themselves live in
 * {@link QuantumStorageData}, so they survive when the chest is broken.
 * Everything that touches the storage runs on the server only.
 */
public class QuantumChestBlockEntity extends BlockEntity implements MenuProvider {
    private static final String STORAGE_TAG = "StorageId";
    private static final String OWNER_TAG = "Owner";
    private static final String PUBLIC_TAG = "Public";
    private static final String MEMBERS_TAG = "Members";
    private static final String MEMBER_NAMES_TAG = "MemberNames";
    // Phase 0 kept the items inside the block. Read them once and move them over.
    private static final String LEGACY_ITEMS_TAG = "Items";

    private UUID storageId;
    // Copy of the storage permissions, sent to clients so they know whether they may break the chest
    private UUID ownerCache;
    private boolean publicCache;
    private Set<UUID> membersCache = Set.of();
    private Set<String> memberNamesCache = Set.of();
    private List<QuantumInventory.Entry> legacyItems = List.of();
    // Set after the first server tick, once the chest has caught up with its network
    private boolean checked;

    public QuantumChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_CHEST.get(), pos, state);
    }

    public ChestTier tier() {
        return getBlockState().getValue(QuantumChestBlock.TIER);
    }

    public UUID storageId() {
        return storageId;
    }

    /**
     * The storage this chest shows, created on first use. Server side only.
     * Also keeps the chest in step with its network: follows the id if the
     * storage was entangled into another one, registers its position and
     * takes the network tier.
     */
    public QuantumStorage storage() {
        QuantumStorageData data = QuantumStorageData.get(server());
        QuantumStorage storage = data.get(storageId);
        if (storage == null) {
            storage = data.create();
            storageId = storage.id();
            setChanged();
        } else if (!storage.id().equals(storageId)) {
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
        storage.addAnchor(anchor());
        matchTier(storage);
        syncAccess(storage);
        return storage;
    }

    public GlobalPos anchor() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** Points this chest at another storage, after entangling or splitting it off. */
    public void relink(QuantumStorage storage) {
        storageId = storage.id();
        setChanged();
        storage.addAnchor(anchor());
        matchTier(storage);
        syncAccess(storage);
    }

    /**
     * Chest and network end up at the higher of their two tiers. A kit raises
     * the network, and this is how the other chests follow it.
     */
    public void matchTier(QuantumStorage storage) {
        ChestTier own = tier();
        if (storage.tier().ordinal() > own.ordinal()) {
            if (level != null) {
                level.setBlock(worldPosition, getBlockState().setValue(QuantumChestBlock.TIER, storage.tier()), Block.UPDATE_ALL);
            }
        } else if (own.ordinal() > storage.tier().ordinal()) {
            storage.setTier(own);
        }
    }

    /**
     * The first tick after the chunk loads, catch up with anything that changed
     * while it was unloaded (a kit used on another chest of the network).
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, QuantumChestBlockEntity chest) {
        if (chest.checked) return;
        chest.checked = true;
        if (chest.storageId != null) chest.storage();
    }

    /** The chest is gone for good (broken, not unloaded): take it off its network. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.getServer() == null) return;
        QuantumStorage storage = existingStorage();
        if (storage != null) storage.removeAnchor(GlobalPos.of(level.dimension(), pos));
    }

    /** Makes the player the owner if the storage has none yet. */
    public void claim(Player player) {
        QuantumStorage storage = storage();
        if (!storage.hasOwner()) {
            storage.setOwner(player);
            syncAccess(storage);
        }
    }

    /** What the client can check without asking the server. Operators aren't known here. */
    public boolean clientCanUse(Player player) {
        return QuantumStorage.canUse(ownerCache, publicCache, membersCache, memberNamesCache,
                player.getUUID(), player.getName().getString());
    }

    /** Copies the storage permissions into the block and tells clients if they changed. */
    public void syncAccess(QuantumStorage storage) {
        Set<String> names = storage.memberNames().stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        if (Objects.equals(ownerCache, storage.owner()) && publicCache == storage.isPublic()
                && membersCache.equals(storage.members()) && memberNamesCache.equals(names)) return;
        ownerCache = storage.owner();
        publicCache = storage.isPublic();
        membersCache = storage.members();
        memberNamesCache = names;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The storage if this chest already has one, without creating it. Server side only. */
    public QuantumStorage existingStorage() {
        return QuantumStorageData.get(server()).get(storageId);
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
        long stored = storage.inventory().insert(stack, storage.tier().capacity());
        if (stored > 0) storage.markChanged();
        return stored;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(QuantumChestItem.nameKey(tier()));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new QuantumChestMenu(containerId, inventory, new StorageHost.Chest(this));
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
        if (publicCache) {
            output.putBoolean(PUBLIC_TAG, true);
        }
        if (!membersCache.isEmpty()) {
            var members = output.list(MEMBERS_TAG, UUIDUtil.CODEC);
            membersCache.forEach(members::add);
        }
        if (!memberNamesCache.isEmpty()) {
            var names = output.list(MEMBER_NAMES_TAG, Codec.STRING);
            memberNamesCache.forEach(names::add);
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
        publicCache = input.getBooleanOr(PUBLIC_TAG, false);
        membersCache = Set.copyOf(input.listOrEmpty(MEMBERS_TAG, UUIDUtil.CODEC).stream().toList());
        memberNamesCache = Set.copyOf(input.listOrEmpty(MEMBER_NAMES_TAG, Codec.STRING).stream().toList());
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
        output.discard(PUBLIC_TAG);
        output.discard(MEMBERS_TAG);
        output.discard(MEMBER_NAMES_TAG);
    }
}
