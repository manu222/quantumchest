package com.manuelaraujo.quantumchest.automation;

import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One face of a chest as hoppers and pipes see it. The chest has no slots, so
 * this makes some up:
 * <ul>
 *   <li>input and import faces: one empty slot that takes what the chest accepts
 *       (and what it turns away, if it can send that on);</li>
 *   <li>export faces: one slot per stored item of the face's tab;</li>
 *   <li>eject faces: the slots of the reject buffer.</li>
 * </ul>
 * Each loader wraps this in its own inventory API. Server side only.
 */
public final class ChestPort {
    private final QuantumChestBlockEntity chest;
    private final Direction side;
    // Export faces: the item types on offer, rebuilt when the contents or the settings change
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

    public FaceMode mode() {
        QuantumStorage storage = storage();
        return storage == null ? FaceMode.OFF : chest.effectiveMode(storage, side);
    }

    public static boolean isOpen(QuantumChestBlockEntity chest, Direction side) {
        return side != null && new ChestPort(chest, side).mode() != FaceMode.OFF;
    }

    public int size() {
        return switch (mode()) {
            case OFF -> 0;
            case INPUT, IMPORT -> 1;
            case EXPORT -> offered().size();
            case EJECT -> chest.rejected().size();
        };
    }

    public ItemStack get(int slot) {
        FaceMode mode = mode();
        if (mode == FaceMode.EJECT) return chest.rejected().get(slot).copy();
        ItemKey key = keyAt(slot);
        if (key == null) return ItemStack.EMPTY;
        long count = storage().inventory().count(key);
        return count <= 0 ? ItemStack.EMPTY : key.toStack((int) Math.min(count, key.maxStackSize()));
    }

    public long amount(int slot) {
        if (mode() == FaceMode.EJECT) return chest.rejected().get(slot).getCount();
        ItemKey key = keyAt(slot);
        return key == null ? 0 : storage().inventory().count(key);
    }

    /** The item type behind an export slot, or null. */
    private ItemKey keyAt(int slot) {
        if (mode() != FaceMode.EXPORT) return null;
        List<ItemKey> keys = offered();
        return slot >= 0 && slot < keys.size() ? keys.get(slot) : null;
    }

    public boolean canInsert(ItemStack stack) {
        if (!mode().acceptsInput() || stack.isEmpty()) return false;
        return chest.acceptFromPipe(storage(), stack, 1, true).total() > 0;
    }

    /** Input and import faces only. */
    public QuantumChestBlockEntity.Routed insert(ItemStack stack, long amount, boolean simulate) {
        if (!mode().acceptsInput()) return QuantumChestBlockEntity.Routed.NONE;
        return chest.acceptFromPipe(storage(), stack, amount, simulate);
    }

    /** True if this face hands out items from the reject buffer instead of the storage. */
    public boolean ejects() {
        return mode() == FaceMode.EJECT;
    }

    /** Export and eject faces only. At most one stack. */
    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        if (mode() == FaceMode.EJECT) {
            ItemStack taken = chest.rejected().take(slot, amount, simulate);
            if (!simulate && !taken.isEmpty()) chest.setChanged();
            return taken;
        }
        ItemKey key = keyAt(slot);
        if (key == null) return ItemStack.EMPTY;
        QuantumStorage storage = storage();
        int taken = (int) Math.min(Math.min(amount, key.maxStackSize()), storage.inventory().count(key));
        if (taken <= 0) return ItemStack.EMPTY;
        if (!simulate) {
            storage.inventory().remove(key, taken);
            storage.markChanged();
        }
        return key.toStack(taken);
    }

    /**
     * Puts things back as they were before a transaction. Positive amounts were
     * added (to the storage or the buffer) and get removed; negative ones were
     * taken and get returned.
     */
    public void undo(ItemStack template, long storedDelta, long bufferDelta) {
        QuantumStorage storage = storage();
        if (storage != null && storedDelta != 0) {
            if (storedDelta > 0) {
                storage.inventory().remove(ItemKey.of(template), storedDelta);
            } else {
                storage.inventory().insert(template, -storedDelta, Long.MAX_VALUE);
            }
            storage.markChanged();
        }
        if (bufferDelta > 0) {
            chest.rejected().remove(template, bufferDelta);
        } else if (bufferDelta < 0) {
            chest.rejected().putBack(template.copyWithCount((int) -bufferDelta));
        }
        if (bufferDelta != 0) chest.setChanged();
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
