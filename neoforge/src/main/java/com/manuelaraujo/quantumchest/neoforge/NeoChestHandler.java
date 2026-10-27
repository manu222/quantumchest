package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.automation.ChestPort;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest face for NeoForge's transfer API (hoppers, pipes, other mods).
 * Changes go straight into the storage and are written down, so an aborted
 * transaction can put everything back as it was.
 */
final class NeoChestHandler implements ResourceHandler<ItemResource> {
    private final ChestPort port;
    private final Journal journal = new Journal();

    private NeoChestHandler(QuantumChestBlockEntity chest, Direction side) {
        this.port = new ChestPort(chest, side);
    }

    /** Null for a closed face, which tells pipes there is nothing to connect to. */
    static NeoChestHandler of(QuantumChestBlockEntity chest, Direction side) {
        return ChestPort.isOpen(chest, side) ? new NeoChestHandler(chest, side) : null;
    }

    @Override
    public int size() {
        return port.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.of(port.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return port.amount(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return port.mode().acceptsInput() ? Integer.MAX_VALUE : port.amount(index);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty() && port.canInsert(resource.toStack(1));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (index != 0 || amount == 0) return 0;
        ItemStack template = resource.toStack(1);
        if (port.insert(template, amount, true).total() <= 0) return 0;
        journal.updateSnapshots(transaction);
        QuantumChestBlockEntity.Routed done = port.insert(template, amount, false);
        journal.record(template, done.stored(), done.buffered());
        return (int) done.total();
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemStack inSlot = port.get(index);
        if (inSlot.isEmpty() || amount == 0 || !resource.matches(inSlot)) return 0;
        if (port.extract(index, amount, true).isEmpty()) return 0;
        boolean fromBuffer = port.ejects();
        journal.updateSnapshots(transaction);
        ItemStack taken = port.extract(index, amount, false);
        if (fromBuffer) {
            journal.record(taken.copyWithCount(1), 0, -taken.getCount());
        } else {
            journal.record(taken.copyWithCount(1), -taken.getCount(), 0);
        }
        return taken.getCount();
    }

    /** What one call changed: items added to (positive) or taken from (negative) the storage and the reject buffer. */
    private record Change(ItemStack template, long stored, long buffered) {
    }

    /** The snapshot is how many changes had happened; going back undoes the ones after it. */
    private final class Journal extends SnapshotJournal<Integer> {
        private final List<Change> changes = new ArrayList<>();

        void record(ItemStack template, long stored, long buffered) {
            if (stored != 0 || buffered != 0) changes.add(new Change(template, stored, buffered));
        }

        @Override
        protected Integer createSnapshot() {
            return changes.size();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            for (int i = changes.size() - 1; i >= snapshot; i--) {
                Change change = changes.remove(i);
                port.undo(change.template(), change.stored(), change.buffered());
            }
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            changes.clear();
        }
    }
}
