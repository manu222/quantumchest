package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/**
 * The chest as an item. It only carries the storage id. It doesn't burn, and if
 * it despawns or falls into the void the contents are still in the world data:
 * an operator can get the chest back with /quantumchest give.
 */
public class QuantumChestItem extends BlockItem {

    public QuantumChestItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** "Elite Quantum Chest" and so on, read from the tier stored on the item. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(nameKey(tierOf(stack)));
    }

    public static String nameKey(ChestTier tier) {
        return "block.quantumchest.quantum_chest." + tier.getSerializedName();
    }

    public static ChestTier tierOf(ItemStack stack) {
        BlockItemStateProperties state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        ChestTier tier = state.get(QuantumChestBlock.TIER);
        return tier != null ? tier : ChestTier.BASIC;
    }
}
