package com.manuelaraujo.quantumchest.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * The chest as an item. It only carries the storage id: if the item burns or
 * despawns, the contents are still in the world data and an operator can get
 * the chest back with /quantumchest give.
 */
public class QuantumChestItem extends BlockItem {

    public QuantumChestItem(Block block, Properties properties) {
        super(block, properties);
    }
}
