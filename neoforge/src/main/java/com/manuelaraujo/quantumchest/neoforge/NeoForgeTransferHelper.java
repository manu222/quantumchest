package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.platform.services.ITransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** The I/O upgrade on NeoForge: everything goes through the item capability, vanilla containers included. */
public final class NeoForgeTransferHelper implements ITransferHelper {

    @Override
    public int insert(ServerLevel level, BlockPos pos, Direction side, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (handler == null) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    @Override
    public int pull(ServerLevel level, BlockPos pos, Direction side, int max, ItemSink sink) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (handler == null) return 0;
        int moved = 0;
        for (int i = 0; i < handler.size() && moved < max; i++) {
            ItemResource resource = handler.getResource(i);
            if (resource.isEmpty()) continue;
            int wanted = Math.min(Math.min(handler.getAmountAsInt(i), max - moved), resource.getMaxStackSize());
            if (wanted <= 0) continue;
            int room = sink.accept(resource.toStack(wanted), true);
            if (room <= 0) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                int got = handler.extract(i, resource, room, transaction);
                if (got <= 0) continue;
                int taken = sink.accept(resource.toStack(got), false);
                // The simulation said it fits, so this should never happen; if it does, give the rest back
                if (taken < got) handler.insert(i, resource, got - taken, transaction);
                transaction.commit();
                moved += taken;
            }
        }
        return moved;
    }

    @Override
    public void invalidate(ServerLevel level, BlockPos pos) {
        level.invalidateCapabilities(pos);
    }
}
