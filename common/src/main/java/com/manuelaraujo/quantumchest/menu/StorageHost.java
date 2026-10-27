package com.manuelaraujo.quantumchest.menu;

import com.manuelaraujo.quantumchest.block.ChestNetwork;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.item.SuperpositionTerminalItem;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * What a chest screen is looking at, on the server: either a placed chest or
 * a storage reached through a terminal. The menu only needs the storage and
 * a way to tell when the player should lose access.
 */
public interface StorageHost {

    /** The storage on screen. May be null if it was merged away meanwhile. */
    QuantumStorage storage();

    boolean stillValid(Player player);

    /** Opened by right clicking a chest. */
    record Chest(QuantumChestBlockEntity chest) implements StorageHost {
        // Called every tick, so it only looks the storage up instead of syncing the chest each time
        @Override
        public QuantumStorage storage() {
            QuantumStorage storage = chest.existingStorage();
            return storage != null ? storage : chest.storage();
        }

        @Override
        public boolean stillValid(Player player) {
            if (!Container.stillValidBlockEntity(chest, player)) return false;
            QuantumStorage storage = storage();
            return storage.canUse(player) && storage.worksIn(chest.getLevel().dimension());
        }
    }

    /** Opened from a superposition terminal. Closes when the player walks out of range or drops the terminal. */
    record Remote(ServerPlayer owner, UUID storageId) implements StorageHost {
        @Override
        public QuantumStorage storage() {
            return QuantumStorageData.get(owner.level().getServer()).get(storageId);
        }

        @Override
        public boolean stillValid(Player player) {
            QuantumStorage storage = storage();
            return storage != null
                    && storage.canUse(player)
                    && ChestNetwork.reach(storage, player) == ChestNetwork.Reach.OK
                    && SuperpositionTerminalItem.carries(player, storage);
        }
    }
}
