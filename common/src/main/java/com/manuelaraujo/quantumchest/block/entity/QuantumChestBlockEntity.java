package com.manuelaraujo.quantumchest.block.entity;

import com.manuelaraujo.quantumchest.automation.ChestAutomation;
import com.manuelaraujo.quantumchest.automation.FaceMode;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import com.manuelaraujo.quantumchest.automation.RejectAction;
import com.manuelaraujo.quantumchest.automation.RejectBuffer;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.item.QuantumChestItem;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.menu.StorageHost;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModBlockEntities;
import com.manuelaraujo.quantumchest.registry.ModDataComponents;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
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

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String FACES_TAG = "Faces";
    private static final String REJECT_ACTION_TAG = "RejectAction";
    private static final String REJECTED_TAG = "Rejected";
    private static final String MAGNET_OFF_TAG = "MagnetOff";
    private static final String MAGNET_RANGE_TAG = "MagnetRange";
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
    // What each face does with the I/O upgrade. Faces belong to this chest, not to the network.
    private final FaceMode[] faceModes = new FaceMode[6];
    private final String[] exportTabs = new String[6];
    // Bumped when a face changes, so open screens resync
    private int faceVersion;
    // Turned away items waiting to leave through an eject face, and what to do when they can't
    private final RejectBuffer rejected = new RejectBuffer();
    private RejectAction rejectAction = RejectAction.REFUSE;
    // The magnet belongs to the network, but each chest can switch it off or shorten its reach
    private boolean magnetOn = true;
    private int magnetRange;

    public QuantumChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_CHEST.get(), pos, state);
        Arrays.fill(faceModes, FaceMode.INPUT);
        Arrays.fill(exportTabs, "");
    }

    // ---- faces (I/O upgrade) -----------------------------------------------

    // ---- magnet ------------------------------------------------------------

    public boolean magnetOn() {
        return magnetOn;
    }

    /** The reach chosen for this chest, or 0 for the most the tier allows. */
    public int magnetRange() {
        return magnetRange;
    }

    public void setMagnet(boolean on, int range) {
        int cleanRange = Math.max(0, Math.min(range, 16));
        if (magnetOn == on && magnetRange == cleanRange) return;
        magnetOn = on;
        magnetRange = cleanRange;
        faceVersion++;
        setChanged();
    }

    public boolean hasIo(QuantumStorage storage) {
        return storage.hasUpgrade(ModItems.IO_UPGRADE.get());
    }

    /** What a face does right now. Without the I/O upgrade every face is INPUT, like a vanilla chest. */
    public FaceMode effectiveMode(QuantumStorage storage, Direction side) {
        return hasIo(storage) ? faceMode(side) : FaceMode.INPUT;
    }

    public List<Direction> ejectFaces() {
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (faceMode(side) == FaceMode.EJECT) sides.add(side);
        }
        return sides;
    }

    public RejectBuffer rejected() {
        return rejected;
    }

    public RejectAction rejectAction() {
        return rejectAction;
    }

    public void setRejectAction(RejectAction action) {
        if (rejectAction == action) return;
        rejectAction = action;
        faceVersion++;
        setChanged();
    }

    /**
     * Whether turned away items may wait in the buffer to leave. Needs the I/O
     * upgrade, and either an eject face or the choice to drop or destroy them.
     * Otherwise they are refused and stay in the hopper, as with a full chest.
     */
    public boolean takesRejected(QuantumStorage storage) {
        return hasIo(storage) && (!ejectFaces().isEmpty() || rejectAction != RejectAction.REFUSE);
    }

    /** What happened to items that came in from a hopper, a pipe or an importing face. */
    public record Routed(long stored, long destroyed, long buffered) {
        public static final Routed NONE = new Routed(0, 0, 0);

        public long total() {
            return stored + destroyed + buffered;
        }
    }

    /**
     * The way in for hoppers, pipes and importing faces. The storage takes what
     * its whitelist, destroy tabs and capacity allow; what's left goes to the
     * reject buffer when this chest can send it on.
     */
    public Routed acceptFromPipe(QuantumStorage storage, ItemStack template, long amount, boolean simulate) {
        QuantumStorage.Accepted accepted = storage.acceptAutomatic(template, amount, simulate);
        long rest = amount - accepted.total();
        long buffered = 0;
        if (rest > 0 && takesRejected(storage)) {
            buffered = rejected.add(template, rest, simulate);
            if (buffered > 0 && !simulate) setChanged();
        }
        return new Routed(accepted.stored(), accepted.destroyed(), buffered);
    }

    public FaceMode faceMode(Direction side) {
        return faceModes[side.get3DDataValue()];
    }

    /** Name of the tab an exporting face sends, or "" for none. */
    public String exportTab(Direction side) {
        return exportTabs[side.get3DDataValue()];
    }

    public int faceVersion() {
        return faceVersion;
    }

    public void setFace(Direction side, FaceMode mode, String tab) {
        int i = side.get3DDataValue();
        String cleanTab = tab.length() > FaceSetting.MAX_TAB_NAME ? tab.substring(0, FaceSetting.MAX_TAB_NAME) : tab;
        if (faceModes[i] == mode && exportTabs[i].equals(cleanTab)) return;
        faceModes[i] = mode;
        exportTabs[i] = cleanTab;
        faceVersion++;
        setChanged();
        if (level instanceof ServerLevel serverLevel) Services.TRANSFER.invalidate(serverLevel, worldPosition);
    }

    /** All six faces, in Direction order. */
    public List<FaceSetting> faces() {
        List<FaceSetting> list = new ArrayList<>();
        for (Direction side : Direction.values()) {
            list.add(new FaceSetting(side, faceMode(side), exportTab(side)));
        }
        return list;
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
        if (!chest.checked) {
            chest.checked = true;
            if (chest.storageId != null) chest.storage();
            // Nobody can have it open right after loading (the game may have closed mid use)
            if (chest.viewers == 0 && state.getValue(QuantumChestBlock.OPEN)) {
                level.setBlock(pos, state.setValue(QuantumChestBlock.OPEN, false), Block.UPDATE_CLIENTS);
            }
        }
        if (chest.storageId == null || !(level instanceof ServerLevel serverLevel)) return;
        QuantumStorage storage = chest.existingStorage();
        if (storage != null && storage.worksIn(level.dimension())) {
            ChestAutomation.tick(serverLevel, chest, storage);
        }
    }

    // ---- open animation ----------------------------------------------------
    // Players with the screen open. Not saved: after a reload nobody has.
    private int viewers;

    /** A player opened the chest screen here. Server side. */
    public void startOpen(Player player) {
        if (level == null || level.isClientSide() || player.isSpectator()) return;
        if (viewers++ == 0) setOpen(true);
    }

    /** That player closed it again. */
    public void stopOpen(Player player) {
        if (level == null || level.isClientSide() || player.isSpectator() || viewers <= 0) return;
        if (--viewers == 0) setOpen(false);
    }

    private void setOpen(boolean open) {
        if (isRemoved() || level == null) return;
        BlockState state = level.getBlockState(worldPosition);
        if (!state.is(getBlockState().getBlock()) || state.getValue(QuantumChestBlock.OPEN) == open) return;
        level.setBlock(worldPosition, state.setValue(QuantumChestBlock.OPEN, open), Block.UPDATE_CLIENTS);
        level.playSound(null, worldPosition, open ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE,
                SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.1f + 0.9f);
    }

    /** The chest is gone for good (broken, not unloaded): take it off its network and empty the reject buffer. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.getServer() == null) return;
        for (ItemStack stack : rejected.takeAll()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
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
        List<FaceSetting> used = faces().stream().filter(f -> f.mode() != FaceMode.INPUT || !f.tab().isEmpty()).toList();
        if (!used.isEmpty()) {
            var list = output.list(FACES_TAG, FaceSetting.CODEC);
            used.forEach(list::add);
        }
        if (rejectAction != RejectAction.REFUSE) {
            output.store(REJECT_ACTION_TAG, RejectAction.CODEC, rejectAction);
        }
        if (!rejected.isEmpty()) {
            var list = output.list(REJECTED_TAG, ItemStack.CODEC);
            rejected.stacks().forEach(list::add);
        }
        if (!magnetOn) output.putBoolean(MAGNET_OFF_TAG, true);
        if (magnetRange > 0) output.putInt(MAGNET_RANGE_TAG, magnetRange);
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
        Arrays.fill(faceModes, FaceMode.INPUT);
        Arrays.fill(exportTabs, "");
        input.listOrEmpty(FACES_TAG, FaceSetting.CODEC).stream().forEach(face -> {
            faceModes[face.side().get3DDataValue()] = face.mode();
            exportTabs[face.side().get3DDataValue()] = face.tab();
        });
        rejectAction = input.read(REJECT_ACTION_TAG, RejectAction.CODEC).orElse(RejectAction.REFUSE);
        rejected.load(input.listOrEmpty(REJECTED_TAG, ItemStack.CODEC).stream().toList());
        magnetOn = !input.getBooleanOr(MAGNET_OFF_TAG, false);
        magnetRange = input.getIntOr(MAGNET_RANGE_TAG, 0);
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

    // Deprecated in vanilla but still used for Ctrl + pick block in creative. Without it
    // the copied item would carry the storage id and open the same storage again
    @SuppressWarnings("deprecation")
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
