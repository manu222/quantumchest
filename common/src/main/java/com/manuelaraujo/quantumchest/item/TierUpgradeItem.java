package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.block.ChestTier;
import net.minecraft.world.item.Item;

/**
 * Raises a placed chest by one tier. Used with right click on the chest; the
 * block handles it (see QuantumChestBlock#useItemOn) so the contents, owner,
 * tabs and upgrades all stay where they are.
 */
public class TierUpgradeItem extends Item {
    private final ChestTier target;

    public TierUpgradeItem(ChestTier target, Properties properties) {
        super(properties);
        this.target = target;
    }

    /** The tier the chest ends up at. */
    public ChestTier target() {
        return target;
    }

    /** The tier the chest must have for this kit to work. */
    public ChestTier required() {
        ChestTier[] tiers = ChestTier.values();
        return tiers[Math.max(0, target.ordinal() - 1)];
    }
}
