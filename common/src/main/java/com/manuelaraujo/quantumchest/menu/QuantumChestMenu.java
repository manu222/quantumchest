package com.manuelaraujo.quantumchest.menu;

import com.manuelaraujo.quantumchest.automation.FaceMode;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import com.manuelaraujo.quantumchest.block.ChestNetwork;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.item.ChestUpgrade;
import com.manuelaraujo.quantumchest.network.ChestSettingsPayload;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload.ItemAmount;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.registry.ModMenus;
import com.manuelaraujo.quantumchest.storage.AccessMode;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The chest screen. Only the player inventory uses real slots: the item grid
 * is drawn by the screen from a list the server keeps in sync. Clicks on the
 * grid travel as {@link com.manuelaraujo.quantumchest.network.ClickPayload}
 * and are resolved here, on the server.
 *
 * The same menu serves a placed chest and a remote terminal: see {@link StorageHost}.
 */
public class QuantumChestMenu extends AbstractContainerMenu {
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 140;
    public static final int UPGRADE_X = 200;
    public static final int UPGRADE_Y = 18;
    public static final int UPGRADE_SLOTS = ChestTier.QUANTUM.upgradeSlots();
    private static final int PLAYER_SLOTS = 36;
    private static final int MAX_ENTRIES_PER_PACKET = 512;

    // Server side: what is being viewed. Null on the client.
    private final StorageHost host;
    private final Player player;

    // Server side: what this player's client has been told so far
    private final Map<ItemKey, Long> sent = new HashMap<>();
    private int sentVersion = -1;
    private int sentStateVersion = -1;
    private int sentFaceVersion = -1;

    // Client side: the chest contents and settings as last received
    private final Map<ItemKey, Long> clientItems = new LinkedHashMap<>();
    private SyncStatePayload.Access clientAccess = new SyncStatePayload.Access(0, "", false, false, false, List.of());
    private List<CustomTab> clientTabs = List.of();
    private SyncStatePayload.Faces clientFaces = SyncStatePayload.Faces.NONE;
    private int clientVersion;
    private int clientStateVersion;

    /** Client constructor, used by the MenuType when the server opens the screen. */
    public QuantumChestMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public QuantumChestMenu(int containerId, Inventory inventory, StorageHost host) {
        super(ModMenus.QUANTUM_CHEST.get(), containerId);
        this.host = host;
        this.player = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
        // Upgrade slots, in a column to the right of the panel. On the client a
        // plain container stands in; vanilla slot sync fills it from the server.
        Container upgrades = host != null ? host.storage().upgrades() : new SimpleContainer(UPGRADE_SLOTS);
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            addSlot(new UpgradeSlot(upgrades, i, UPGRADE_X, UPGRADE_Y + i * 18));
        }
    }

    /** Tier of the storage on screen, on either side. */
    public ChestTier tier() {
        QuantumStorage storage = host != null ? host.storage() : null;
        return storage != null ? storage.tier() : clientTier();
    }

    /** How many upgrade slots the current tier unlocks. */
    public int activeUpgradeSlots() {
        return tier().upgradeSlots();
    }

    /** True if this screen shows that storage. Server side. */
    public boolean shows(UUID storageId) {
        QuantumStorage storage = host != null ? host.storage() : null;
        return storage != null && storage.id().equals(storageId);
    }

    private class UpgradeSlot extends Slot {
        UpgradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() < activeUpgradeSlots();
        }

        // One of each upgrade per chest: a second one would do nothing
        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && stack.getItem() instanceof ChestUpgrade upgrade && tier().atLeast(upgrade.minTier())
                    && !alreadyInstalled(stack, this);
        }

        @Override
        public boolean mayPickup(Player player) {
            QuantumStorage storage = host != null ? host.storage() : null;
            if (storage == null || storage.canRemoveUpgrade(getItem())) return true;
            player.sendOverlayMessage(Component.translatable("message.quantumchest.tunnel.in_use"));
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        // The I/O upgrade opens and closes every face, so pipes have to look again
        @Override
        public void setChanged() {
            super.setChanged();
            QuantumStorage storage = host != null ? host.storage() : null;
            if (storage != null && player instanceof ServerPlayer serverPlayer) {
                ChestNetwork.invalidateFaces(serverPlayer.level().getServer(), storage);
            }
        }
    }

    private boolean alreadyInstalled(ItemStack stack, Slot except) {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            Slot slot = slots.get(PLAYER_SLOTS + i);
            if (slot != except && slot.getItem().is(stack.getItem())) return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host == null) return true;
        return host.storage() != null && host.stillValid(player);
    }

    /**
     * Shift click. From an upgrade slot: back to the inventory. From the
     * inventory: upgrades go to a free upgrade slot, everything else into the chest.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (host == null || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (index >= PLAYER_SLOTS) {
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            moveItemStackTo(stack, 0, PLAYER_SLOTS, true);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (stack.getItem() instanceof ChestUpgrade
                && moveItemStackTo(stack, PLAYER_SLOTS, PLAYER_SLOTS + activeUpgradeSlots(), false)) {
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        QuantumStorage storage = host.storage();
        long stored = storage.inventory().insert(stack, storage.tier().capacity());
        if (stored > 0) {
            stack.shrink((int) stored);
            storage.markChanged();
            slot.setChanged();
        }
        // Returning empty stops vanilla from calling us again in a loop
        return ItemStack.EMPTY;
    }

    // ---- server: clicks on the grid ----------------------------------------

    public void handleClick(ClickAction action, ItemStack clicked, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumStorage storage = host.storage();
        QuantumInventory inventory = storage.inventory();
        long capacity = storage.tier().capacity();
        ItemStack carried = getCarried();

        switch (action) {
            case TAKE_STACK -> {
                if (!carried.isEmpty() || clicked.isEmpty()) return;
                ItemKey key = ItemKey.of(clicked);
                setCarried(inventory.extract(key, key.maxStackSize()));
            }
            case TAKE_HALF -> {
                if (!carried.isEmpty() || clicked.isEmpty()) return;
                ItemKey key = ItemKey.of(clicked);
                long available = Math.min(inventory.count(key), key.maxStackSize());
                if (available <= 0) return;
                setCarried(inventory.extract(key, Math.max(1, (available + 1) / 2)));
            }
            case TAKE_TO_INVENTORY -> {
                if (clicked.isEmpty()) return;
                ItemKey key = ItemKey.of(clicked);
                ItemStack taken = inventory.extract(key, key.maxStackSize());
                if (taken.isEmpty()) return;
                moveItemStackTo(taken, 0, PLAYER_SLOTS, true);
                if (!taken.isEmpty()) {
                    // Inventory full: put the rest back, it came from here so it always fits
                    inventory.insert(taken, taken.getCount(), Long.MAX_VALUE);
                }
            }
            case INSERT_CARRIED -> {
                if (carried.isEmpty()) return;
                long stored = inventory.insert(carried, capacity);
                carried.shrink((int) stored);
                setCarried(carried);
            }
            case INSERT_ONE -> {
                if (carried.isEmpty()) return;
                long stored = inventory.insert(carried, 1, capacity);
                carried.shrink((int) stored);
                setCarried(carried);
            }
        }
        storage.markChanged();
        broadcastChanges();
    }

    // ---- server: keeping the client up to date -------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (host == null || !(player instanceof ServerPlayer serverPlayer)) return;
        QuantumStorage storage = host.storage();
        if (storage == null) return;
        syncState(storage, serverPlayer);
        syncItems(storage.inventory(), serverPlayer);
    }

    private void syncState(QuantumStorage storage, ServerPlayer serverPlayer) {
        QuantumChestBlockEntity chest = host.chest();
        int faceVersion = chest != null ? chest.faceVersion() : 0;
        if (storage.stateVersion() == sentStateVersion && faceVersion == sentFaceVersion) return;
        sentStateVersion = storage.stateVersion();
        sentFaceVersion = faceVersion;
        SyncStatePayload.Access access = new SyncStatePayload.Access(
                storage.tier().ordinal(), storage.ownerName(), storage.isPublic(),
                storage.canManage(serverPlayer), storage.canEditTabs(serverPlayer), storage.memberNames());
        SyncStatePayload.Faces faces = chest == null ? SyncStatePayload.Faces.NONE : new SyncStatePayload.Faces(
                storage.canManage(serverPlayer),
                chest.getBlockState().getValue(QuantumChestBlock.FACING).get3DDataValue(),
                chest.faces());
        Services.NETWORK.sendToPlayer(serverPlayer, new SyncStatePayload(containerId, access, storage.tabs(), faces));
    }

    private void syncItems(QuantumInventory inventory, ServerPlayer serverPlayer) {
        boolean first = sentVersion == -1;
        if (!first && inventory.version() == sentVersion) return;

        List<ItemAmount> changes = new ArrayList<>();
        Map<ItemKey, Long> now = inventory.view();
        now.forEach((key, count) -> {
            Long before = sent.get(key);
            if (before == null || before.longValue() != count) {
                changes.add(new ItemAmount(key.template(), count));
            }
        });
        for (ItemKey key : sent.keySet()) {
            if (!now.containsKey(key)) {
                changes.add(new ItemAmount(key.template(), 0));
            }
        }
        sent.clear();
        sent.putAll(now);
        sentVersion = inventory.version();

        // Big chests go out in several packets so none of them gets too large
        int from = 0;
        do {
            int to = Math.min(from + MAX_ENTRIES_PER_PACKET, changes.size());
            Services.NETWORK.sendToPlayer(serverPlayer, new SyncItemsPayload(
                    containerId, first && from == 0, List.copyOf(changes.subList(from, to))));
            from = to;
        } while (from < changes.size());
    }

    // ---- server: settings and tabs -----------------------------------------

    public void handleSettings(ChestSettingsPayload.Action action, String value, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumStorage storage = host.storage();
        if (!storage.canManage(player)) return;

        switch (action) {
            case MAKE_PUBLIC -> storage.setAccessMode(AccessMode.PUBLIC);
            case MAKE_PRIVATE -> storage.setAccessMode(AccessMode.PRIVATE);
            case ADD_MEMBER -> {
                ServerPlayer online = player.level().getServer().getPlayerList().getPlayerByName(value.strip());
                String key = switch (storage.addMember(value, online)) {
                    case ADDED -> online != null ? "message.quantumchest.member.added" : "message.quantumchest.member.added_offline";
                    case INVALID_NAME -> "message.quantumchest.member.invalid";
                    case IS_OWNER -> "message.quantumchest.member.is_owner";
                    case ALREADY_MEMBER -> "message.quantumchest.member.already";
                    case FULL -> "message.quantumchest.member.full";
                };
                player.sendSystemMessage(Component.translatable(key, value.strip(), QuantumStorage.MAX_MEMBERS));
            }
            case REMOVE_MEMBER -> storage.removeMember(value.strip());
        }
        ChestNetwork.syncAccess(player.level().getServer(), storage);
        broadcastChanges();
    }

    public void handleSetTabs(List<CustomTab> tabs, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumStorage storage = host.storage();
        if (!storage.canEditTabs(player)) return;
        storage.setTabs(tabs);
        broadcastChanges();
    }

    /** A face of the chest changes mode or export tab. Owner only, and the I/O upgrade has to be in. */
    public void handleFace(Direction side, FaceMode mode, String tab, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumChestBlockEntity chest = host.chest();
        QuantumStorage storage = host.storage();
        if (chest == null || !storage.canManage(player) || !storage.hasUpgrade(ModItems.IO_UPGRADE.get())) return;
        chest.setFace(side, mode, mode == FaceMode.EXPORT ? tab : "");
        broadcastChanges();
    }

    // ---- client ------------------------------------------------------------

    public void applySync(SyncItemsPayload payload) {
        if (payload.reset()) clientItems.clear();
        for (ItemAmount entry : payload.entries()) {
            ItemKey key = ItemKey.of(entry.stack());
            if (entry.count() <= 0) {
                clientItems.remove(key);
            } else {
                clientItems.put(key, entry.count());
            }
        }
        clientVersion++;
    }

    public void applyState(SyncStatePayload payload) {
        clientAccess = payload.access();
        clientTabs = List.copyOf(payload.tabs());
        clientFaces = payload.faces();
        clientStateVersion++;
        clientVersion++;
    }

    public Map<ItemKey, Long> clientItems() {
        return Collections.unmodifiableMap(clientItems);
    }

    public SyncStatePayload.Access clientAccess() {
        return clientAccess;
    }

    public ChestTier clientTier() {
        ChestTier[] tiers = ChestTier.values();
        return tiers[Math.max(0, Math.min(clientAccess.tier(), tiers.length - 1))];
    }

    public List<CustomTab> clientTabs() {
        return clientTabs;
    }

    public SyncStatePayload.Faces clientFaces() {
        return clientFaces;
    }

    /** True if an upgrade slot holds the I/O upgrade. Works on the client, where slots are synced. */
    public boolean hasIoUpgrade() {
        for (int i = 0; i < activeUpgradeSlots(); i++) {
            if (slots.get(PLAYER_SLOTS + i).getItem().is(ModItems.IO_UPGRADE.get())) return true;
        }
        return false;
    }

    /** Goes up every time new data arrives, so the screen knows when to rebuild its list. */
    public int clientVersion() {
        return clientVersion;
    }

    public int clientStateVersion() {
        return clientStateVersion;
    }

    public long clientTotal() {
        long total = 0;
        for (long count : clientItems.values()) total += count;
        return total;
    }
}
