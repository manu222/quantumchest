package com.manuelaraujo.quantumchest.automation;

import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One face of a chest as hoppers and pipes see it. The chest has no slots, so
 * this makes some up: an importing face is one empty slot that takes anything
 * the storage accepts automatically; an exporting face shows one slot per
 * stored item of its tab. Each loader wraps this in its own inventory API.
 * Server side only.
 */
public final class ChestPort {
    private final QuantumChestBlockEntity chest;
    private final Direction side;
    // Exporting faces: the item types on offer, rebuilt when the contents change
    private List<ItemKey> offered = List.of();
    private int offeredVersion = -1;
    private int offeredStateVersion = -1;
    private int offeredFaceVersion = -1;

    public ChestPort(QuantumChestBlockEntity chest, Direction side) {
        this.chest = chest;
        this.side = side;
    }

    private QuantumStorage storage() {
        if (chest.getLevel() == null || chest.getLevel().isClientSide() || chest.isRemoved()) return null;
        return chest.existingStorage();
    }

    /** OFF unless the network has the I/O upgrade. */
    public FaceMode mode() {
        QuantumStorage storage = storage();
        if (storage == null || !storage.hasUpgrade(ModItems.IO_UPGRADE.get())) return FaceMode.OFF;
        return chest.faceMode(side);
    }

    public static boolean isOpen(QuantumChestBlockEntity chest, Direction side) {
        return side != null && new ChestPort(chest, side).mode() != FaceMode.OFF;
    }

    public int size() {
        return switch (mode()) {
            case OFF -> 0;
            case IMPORT -> 1;
            case EXPORT -> offered().size();
        };
    }

    public ItemStack get(int slot) {
        ItemKey key = keyAt(slot);
        if (key == null) return ItemStack.EMPTY;
        long count = storage().inventory().count(key);
        return count <= 0 ? ItemStack.EMPTY : key.toStack((int) Math.min(count, key.maxStackSize()));
    }

    public long amount(int slot) {
        ItemKey key = keyAt(slot);
        return key == null ? 0 : storage().inventory().count(key);
    }

    /** The item type behind an export slot, or null. */
    public ItemKey keyAt(int slot) {
        if (mode() != FaceMode.EXPORT) return null;
        List<ItemKey> keys = offered();
        return slot >= 0 && slot < keys.size() ? keys.get(slot) : null;
    }

    public boolean canInsert(ItemStack stack) {
        if (mode() != FaceMode.IMPORT || stack.isEmpty()) return false;
        return storage().acceptAutomatic(stack, 1, true).total() > 0;
    }

    /** Importing faces only. Goes through the whitelist, destroy tabs and capacity. */
    public QuantumStorage.Accepted insert(ItemStack stack, long amount, boolean simulate) {
        if (mode() != FaceMode.IMPORT) return QuantumStorage.Accepted.NONE;
        return storage().acceptAutomatic(stack, amount, simulate);
    }

    /** Exporting faces only. At most one stack. */
    public ItemStack extract(int slot, int amount, boolean simulate) {
        ItemKey key = keyAt(slot);
        if (key == null || amount <= 0) return ItemStack.EMPTY;
        QuantumStorage storage = storage();
        int taken = (int) Math.min(Math.min(amount, key.maxStackSize()), storage.inventory().count(key));
        if (taken <= 0) return ItemStack.EMPTY;
        if (!simulate) {
            storage.inventory().remove(key, taken);
            storage.markChanged();
        }
        return key.toStack(taken);
    }

    /** Puts back items a transaction took out or let in. Only used to roll back. */
    public void undo(ItemStack template, long storedDelta) {
        QuantumStorage storage = storage();
        if (storage == null || storedDelta == 0) return;
        if (storedDelta > 0) {
            storage.inventory().remove(ItemKey.of(template), storedDelta);
        } else {
            storage.inventory().insert(template, -storedDelta, Long.MAX_VALUE);
        }
        storage.markChanged();
    }

    private List<ItemKey> offered() {
        QuantumStorage storage = storage();
        if (storage == null) return List.of();
        if (storage.inventory().version() == offeredVersion && storage.stateVersion() == offeredStateVersion
                && chest.faceVersion() == offeredFaceVersion) return offered;
        CustomTab tab = storage.tabNamed(chest.exportTab(side));
        List<ItemKey> keys = new ArrayList<>();
        if (tab != null) {
            for (ItemKey key : storage.inventory().view().keySet()) {
                if (tab.matches(key.template())) keys.add(key);
            }
        }
        offered = List.copyOf(keys);
        offeredVersion = storage.inventory().version();
        offeredStateVersion = storage.stateVersion();
        offeredFaceVersion = chest.faceVersion();
        return offered;
    }
}
