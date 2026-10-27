package com.manuelaraujo.quantumchest.automation;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * What the upgrades do every few ticks, run from each chest's ticker. The
 * magnet and the I/O faces work around each chest of a network; compacting
 * works on the shared storage, once per second for the whole network.
 */
public final class ChestAutomation {
    private ChestAutomation() {
    }

    public static void tick(ServerLevel level, QuantumChestBlockEntity chest, QuantumStorage storage) {
        long time = level.getGameTime();
        // Spread chests over the ticks so a hundred of them don't all work on the same one
        long phase = Math.floorMod(chest.getBlockPos().asLong(), 20L);

        if ((time + phase) % 10 == 0 && storage.hasUpgrade(ModItems.MAGNET_UPGRADE.get())) {
            magnet(level, chest, storage);
        }
        if (storage.hasUpgrade(ModItems.COMPACTOR_UPGRADE.get()) && storage.compactDue(time)) {
            Compactor.run(level, storage);
        }
        if (storage.hasUpgrade(ModItems.IO_UPGRADE.get())) {
            Speed speed = Speed.of(storage.tier());
            if ((time + phase) % speed.interval() == 0) transfer(level, chest, storage, speed.items());
        }
    }

    // ---- magnet ------------------------------------------------------------

    public static int magnetRange(ChestTier tier) {
        return switch (tier) {
            case BASIC, REINFORCED -> 3;
            case ADVANCED -> 5;
            case ELITE, QUANTUM -> 8;
        };
    }

    private static void magnet(ServerLevel level, QuantumChestBlockEntity chest, QuantumStorage storage) {
        AABB area = new AABB(chest.getBlockPos()).inflate(magnetRange(storage.tier()));
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && !e.hasPickUpDelay())) {
            ItemStack stack = entity.getItem();
            long taken = storage.acceptAutomatic(stack, stack.getCount(), false).total();
            if (taken <= 0) continue;
            ItemStack rest = stack.copyWithCount(stack.getCount() - (int) taken);
            if (rest.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(rest);
            }
        }
    }

    // ---- I/O upgrade -------------------------------------------------------

    /** How often each face works and how many items it moves each time. */
    public record Speed(int interval, int items) {
        public static Speed of(ChestTier tier) {
            return switch (tier) {
                case BASIC, REINFORCED -> new Speed(10, 8);
                case ADVANCED -> new Speed(10, 32);
                case ELITE -> new Speed(5, 64);
                case QUANTUM -> new Speed(2, 128);
            };
        }

        public int perSecond() {
            return items * 20 / interval;
        }
    }

    private static void transfer(ServerLevel level, QuantumChestBlockEntity chest, QuantumStorage storage, int budget) {
        for (Direction side : Direction.values()) {
            FaceMode mode = chest.faceMode(side);
            if (mode == FaceMode.OFF) continue;
            BlockPos target = chest.getBlockPos().relative(side);
            // A chest of the same network next to this one would just pass items back and forth
            if (sameNetwork(level, target, storage)) continue;
            if (mode == FaceMode.IMPORT) {
                Services.TRANSFER.pull(level, target, side.getOpposite(), budget,
                        (stack, simulate) -> (int) storage.acceptAutomatic(stack, stack.getCount(), simulate).total());
            } else {
                export(level, chest, storage, side, target, budget);
            }
        }
    }

    private static void export(ServerLevel level, QuantumChestBlockEntity chest, QuantumStorage storage,
                               Direction side, BlockPos target, int budget) {
        CustomTab tab = storage.tabNamed(chest.exportTab(side));
        if (tab == null) return;
        List<ItemKey> keys = new ArrayList<>();
        for (ItemKey key : storage.inventory().view().keySet()) {
            if (tab.matches(key.template())) keys.add(key);
        }
        int left = budget;
        boolean changed = false;
        for (ItemKey key : keys) {
            if (left <= 0) break;
            int amount = (int) Math.min(Math.min(storage.inventory().count(key), key.maxStackSize()), left);
            if (amount <= 0) continue;
            // Hand the items over first and only remove what the other side took
            int accepted = Services.TRANSFER.insert(level, target, side.getOpposite(), key.toStack(amount));
            if (accepted <= 0) continue;
            storage.inventory().remove(key, accepted);
            left -= accepted;
            changed = true;
        }
        if (changed) storage.markChanged();
    }

    private static boolean sameNetwork(ServerLevel level, BlockPos pos, QuantumStorage storage) {
        if (!(level.getBlockEntity(pos) instanceof QuantumChestBlockEntity other) || other.storageId() == null) return false;
        return storage.id().equals(QuantumStorageData.get(level.getServer()).resolve(other.storageId()));
    }
}
