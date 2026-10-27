package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.client.ClientHandlers;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.network.QuantumNetwork;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import net.minecraftforge.network.Channel.VersionTest;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

final class ForgeNetwork {
    private static final int PROTOCOL_VERSION = 1;

    // addMain runs the handlers on the game thread, not the network thread.
    // The clientbound handler only ever runs on the client.
    static final SimpleChannel CHANNEL = ChannelBuilder
            .named(QuantumChest.id("main"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .clientAcceptedVersions(VersionTest.exact(PROTOCOL_VERSION))
            .serverAcceptedVersions(VersionTest.exact(PROTOCOL_VERSION))
            .simpleChannel()
            .play()
                .clientbound()
                    .addMain(SyncItemsPayload.class, SyncItemsPayload.STREAM_CODEC,
                            (payload, context) -> ClientHandlers.onSyncItems(payload))
                .serverbound()
                    .addMain(ClickPayload.class, ClickPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onClick(payload, context.getSender()))
            .build();

    private ForgeNetwork() {
    }

    /** Loads the class so the channel exists before the game connects. */
    static void init() {
    }
}
