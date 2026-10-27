package com.manuelaraujo.quantumchest.menu;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload.ItemAmount;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModMenus;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
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

/**
 * The chest screen. Only the player inventory uses real slots: the item grid
 * is drawn by the screen from a list the server keeps in sync. Clicks on the
 * grid travel as {@link com.manuelaraujo.quantumchest.network.ClickPayload}
 * and are resolved here, on the server.
 */
public class QuantumChestMenu extends AbstractContainerMenu {
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 140;
    private static final int PLAYER_SLOTS = 36;
    private static final int MAX_ENTRIES_PER_PACKET = 512;

    // Server side: the chest being viewed. Null on the client.
    private final QuantumChestBlockEntity chest;
    private final Player player;

    // Server side: what this player's client has been told so far
    private final Map<ItemKey, Long> sent = new HashMap<>();
    private int sentVersion = -1;
    private ChestTier sentTier;
    private String sentOwner;

    // Client side: the chest contents as last received
    private final Map<ItemKey, Long> clientItems = new LinkedHashMap<>();
    private ChestTier clientTier = ChestTier.BASIC;
    private String clientOwner = "";
    private int clientVersion;

    /** Client constructor, used by the MenuType when the server opens the screen. */
    public QuantumChestMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public QuantumChestMenu(int containerId, Inventory inventory, QuantumChestBlockEntity chest) {
        super(ModMenus.QUANTUM_CHEST.get(), containerId);
        this.chest = chest;
        this.player = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (chest == null) return true;
        return Container.stillValidBlockEntity(chest, player) && chest.storage().canUse(player);
    }

    /** Shift click in the player inventory: store the whole stack. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (chest == null || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        long stored = chest.insert(stack);
        if (stored > 0) {
            stack.shrink((int) stored);
            slot.setChanged();
        }
        // Returning empty stops vanilla from calling us again in a loop
        return ItemStack.EMPTY;
    }

    // ---- server: clicks on the grid ----------------------------------------

    public void handleClick(ClickAction action, ItemStack clicked, ServerPlayer player) {
        if (chest == null || !stillValid(player)) return;
        QuantumStorage storage = chest.storage();
        QuantumInventory inventory = storage.inventory();
        long capacity = chest.tier().capacity();
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
        if (chest == null || !(player instanceof ServerPlayer serverPlayer)) return;

        QuantumStorage storage = chest.storage();
        QuantumInventory inventory = storage.inventory();
        ChestTier tier = chest.tier();
        String owner = storage.ownerName();
        boolean first = sentVersion == -1;
        if (!first && inventory.version() == sentVersion && tier == sentTier && owner.equals(sentOwner)) return;

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
        sentTier = tier;
        sentOwner = owner;

        // Big chests go out in several packets so none of them gets too large
        int from = 0;
        do {
            int to = Math.min(from + MAX_ENTRIES_PER_PACKET, changes.size());
            Services.NETWORK.sendToPlayer(serverPlayer, new SyncItemsPayload(
                    containerId, first && from == 0, tier.ordinal(), owner, List.copyOf(changes.subList(from, to))));
            from = to;
        } while (from < changes.size());
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
        ChestTier[] tiers = ChestTier.values();
        clientTier = tiers[Math.max(0, Math.min(payload.tier(), tiers.length - 1))];
        clientOwner = payload.owner();
        clientVersion++;
    }

    public Map<ItemKey, Long> clientItems() {
        return Collections.unmodifiableMap(clientItems);
    }

    public ChestTier clientTier() {
        return clientTier;
    }

    public String clientOwner() {
        return clientOwner;
    }

    /** Goes up every time new data arrives, so the screen knows when to rebuild its list. */
    public int clientVersion() {
        return clientVersion;
    }

    public long clientTotal() {
        long total = 0;
        for (long count : clientItems.values()) total += count;
        return total;
    }
}
