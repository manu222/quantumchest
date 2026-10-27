package com.manuelaraujo.quantumchest.block;

import com.manuelaraujo.quantumchest.QuantumConfig;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The placed chests of one storage. A storage remembers where its chests are,
 * so changes (a new tier, new permissions) can reach all of them, and the
 * terminal knows how far away the closest one is. Server side only.
 */
public final class ChestNetwork {
    private ChestNetwork() {
    }

    /** Runs for every chest of the storage whose chunk is loaded right now. */
    public static void forEachLoadedChest(MinecraftServer server, QuantumStorage storage,
                                          Consumer<QuantumChestBlockEntity> action) {
        QuantumStorageData data = QuantumStorageData.get(server);
        for (GlobalPos pos : storage.anchors()) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null || !level.isLoaded(pos.pos())) continue;
            if (level.getBlockEntity(pos.pos()) instanceof QuantumChestBlockEntity chest
                    && chest.storageId() != null && storage.id().equals(data.resolve(chest.storageId()))) {
                action.accept(chest);
            }
        }
    }

    /**
     * Forgets positions where a loaded chunk no longer has a chest of this
     * storage, for chests removed in ways that skip the usual hooks (world
     * editors, other mods). Positions in unloaded chunks are kept.
     */
    public static void prune(MinecraftServer server, QuantumStorage storage) {
        QuantumStorageData data = QuantumStorageData.get(server);
        for (GlobalPos pos : storage.anchors()) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null) {
                storage.removeAnchor(pos);
                continue;
            }
            if (!level.isLoaded(pos.pos())) continue;
            boolean present = level.getBlockEntity(pos.pos()) instanceof QuantumChestBlockEntity chest
                    && chest.storageId() != null && storage.id().equals(data.resolve(chest.storageId()));
            if (!present) storage.removeAnchor(pos);
        }
    }

    /** Sends the new owner, access mode and members to every loaded chest, for the break check on clients. */
    public static void syncAccess(MinecraftServer server, QuantumStorage storage) {
        forEachLoadedChest(server, storage, chest -> chest.syncAccess(storage));
    }

    /** Brings every loaded chest up to the tier of the network. The rest catch up when their chunk loads. */
    public static void matchTier(MinecraftServer server, QuantumStorage storage) {
        forEachLoadedChest(server, storage, chest -> chest.matchTier(storage));
    }

    /**
     * Hoppers and pipes may cache what each chest offers. Call this when an
     * upgrade goes in or out, since the I/O upgrade opens or closes every face.
     */
    public static void invalidateFaces(MinecraftServer server, QuantumStorage storage) {
        forEachLoadedChest(server, storage, chest -> {
            if (chest.getLevel() instanceof ServerLevel level) Services.TRANSFER.invalidate(level, chest.getBlockPos());
        });
    }

    /** Closes the chest screen of everyone looking at this storage. */
    public static void closeScreens(MinecraftServer server, UUID storageId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof QuantumChestMenu menu && menu.shows(storageId)) {
                player.closeContainer();
            }
        }
    }

    public enum Reach { OK, TIER_TOO_LOW, NOT_PLACED, TOO_FAR, OTHER_DIMENSION }

    /**
     * Whether a terminal held by {@code player} reaches this storage: 64 blocks
     * from the closest chest at Advanced, the whole dimension at Elite and
     * above, and other dimensions too with a tunnel installed.
     */
    public static Reach reach(QuantumStorage storage, Player player) {
        if (!storage.tier().atLeast(ChestTier.ADVANCED)) return Reach.TIER_TOO_LOW;
        boolean placed = false;
        boolean sameDimension = false;
        double closest = Double.MAX_VALUE;
        for (GlobalPos pos : storage.anchors()) {
            if (!storage.worksIn(pos.dimension())) continue;
            placed = true;
            if (!pos.dimension().equals(player.level().dimension())) continue;
            sameDimension = true;
            closest = Math.min(closest, player.position().distanceToSqr(Vec3.atCenterOf(pos.pos())));
        }
        if (!placed) return Reach.NOT_PLACED;
        if (!sameDimension) return storage.hasTunnel() ? Reach.OK : Reach.OTHER_DIMENSION;
        if (storage.tier().atLeast(ChestTier.ELITE)) return Reach.OK;
        // Advanced chests: as far as the config says. Elite and up reach the whole dimension.
        double range = QuantumConfig.terminalRange();
        return closest <= range * range ? Reach.OK : Reach.TOO_FAR;
    }
}
