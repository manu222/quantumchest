package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.platform.services.ITransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.VanillaInventoryCodeHooks;

/** The I/O upgrade on Forge, through IItemHandler. Vanilla containers expose one too. */
public final class ForgeTransferHelper implements ITransferHelper {

    @Override
    public int insert(ServerLevel level, BlockPos pos, Direction side, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        IItemHandler handler = VanillaInventoryCodeHooks.getItemHandler(level, pos, side).orElse(null);
        if (handler == null) return 0;
        ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
        return stack.getCount() - rest.getCount();
    }

    @Override
    public int pull(ServerLevel level, BlockPos pos, Direction side, int max, ItemSink sink) {
        IItemHandler handler = VanillaInventoryCodeHooks.getItemHandler(level, pos, side).orElse(null);
        if (handler == null) return 0;
        int moved = 0;
        for (int i = 0; i < handler.getSlots() && moved < max; i++) {
            ItemStack offered = handler.extractItem(i, max - moved, true);
            if (offered.isEmpty()) continue;
            int room = sink.accept(offered, true);
            if (room <= 0) continue;
            ItemStack got = handler.extractItem(i, room, false);
            if (got.isEmpty()) continue;
            int taken = sink.accept(got, false);
            // The simulation said it fits, so this should never happen; if it does, give the rest back
            if (taken < got.getCount()) handler.insertItem(i, got.copyWithCount(got.getCount() - taken), false);
            moved += taken;
        }
        return moved;
    }

    // Our handlers read the face settings live, so cached ones already see the change
    @Override
    public void invalidate(ServerLevel level, BlockPos pos) {
    }
}
