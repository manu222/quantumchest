package com.manuelaraujo.quantumchest.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Moving items in and out of the blocks next to a chest. Every loader has its
 * own inventory API (Forge's IItemHandler, NeoForge's ResourceHandler), and
 * both cover vanilla containers and machines from other mods.
 */
public interface ITransferHelper {

    /** Puts as much of {@code stack} as fits into the block at {@code pos}, seen from {@code side}. Returns how many went in. */
    int insert(ServerLevel level, BlockPos pos, Direction side, ItemStack stack);

    /** Takes up to {@code max} items from the block at {@code pos} and hands them to {@code sink}. Returns how many moved. */
    int pull(ServerLevel level, BlockPos pos, Direction side, int max, ItemSink sink);

    /** Tells hoppers and pipes that cached what the chest offers on each face that it changed. */
    void invalidate(ServerLevel level, BlockPos pos);

    /** Where pulled items go. Returns how many of the stack it takes; with {@code simulate} nothing changes. */
    interface ItemSink {
        int accept(ItemStack stack, boolean simulate);
    }
}
