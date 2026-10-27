package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.automation.ChestPort;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Attached to every chest. Offers an item handler on the faces the I/O
 * upgrade has open; closed faces offer nothing, so pipes don't connect.
 */
final class ForgeChestCapabilities implements ICapabilityProvider {
    private final QuantumChestBlockEntity chest;
    @SuppressWarnings("unchecked")
    private final LazyOptional<ForgeChestHandler>[] handlers = new LazyOptional[6];

    ForgeChestCapabilities(QuantumChestBlockEntity chest) {
        this.chest = chest;
        for (Direction side : Direction.values()) {
            handlers[side.get3DDataValue()] = LazyOptional.of(() -> new ForgeChestHandler(chest, side));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (capability != ForgeCapabilities.ITEM_HANDLER || !ChestPort.isOpen(chest, side)) return LazyOptional.empty();
        return handlers[side.get3DDataValue()].cast();
    }

    void invalidate() {
        for (LazyOptional<ForgeChestHandler> handler : handlers) handler.invalidate();
    }
}
