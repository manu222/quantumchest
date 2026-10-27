package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.automation.ChestPort;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** A chest face as Forge's IItemHandler, for hoppers and pipes of other mods. */
final class ForgeChestHandler implements IItemHandler {
    private final ChestPort port;

    ForgeChestHandler(QuantumChestBlockEntity chest, Direction side) {
        this.port = new ChestPort(chest, side);
    }

    @Override
    public int getSlots() {
        return port.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return port.get(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot != 0) return stack;
        int taken = (int) port.insert(stack, stack.getCount(), simulate).total();
        return taken <= 0 ? stack : stack.copyWithCount(stack.getCount() - taken);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return port.extract(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return port.mode().acceptsInput() ? Integer.MAX_VALUE : 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0 && port.canInsert(stack);
    }
}
