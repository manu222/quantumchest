package com.manuelaraujo.quantumchest.block.entity;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.registry.ModBlockEntities;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * For now the items live inside the block entity. In phase 1 they move to a
 * world level SavedData and this class will only keep the storage UUID.
 */
public class QuantumChestBlockEntity extends BlockEntity {
    private static final String ITEMS_TAG = "Items";

    private final QuantumInventory inventory = new QuantumInventory();

    public QuantumChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_CHEST.get(), pos, state);
    }

    public ChestTier tier() {
        return getBlockState().getValue(QuantumChestBlock.TIER);
    }

    public QuantumInventory inventory() {
        return inventory;
    }

    /** Returns how many items of the stack were stored. The stack itself is not changed. */
    public long insert(ItemStack stack) {
        long stored = inventory.insert(stack, tier().capacity());
        if (stored > 0) setChanged();
        return stored;
    }

    public ItemStack extractFirst() {
        ItemKey key = inventory.firstKey();
        if (key == null) return ItemStack.EMPTY;
        ItemStack taken = inventory.extract(key, key.maxStackSize());
        setChanged();
        return taken;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.list(ITEMS_TAG, QuantumInventory.Entry.CODEC);
        for (QuantumInventory.Entry entry : inventory.entries()) {
            list.add(entry);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.load(input.listOrEmpty(ITEMS_TAG, QuantumInventory.Entry.CODEC).stream().toList());
    }
}
