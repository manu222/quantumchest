package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.client.ClientHandlers;
import com.manuelaraujo.quantumchest.network.ChestSettingsPayload;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.network.QuantumNetwork;
import com.manuelaraujo.quantumchest.network.SetFacePayload;
import com.manuelaraujo.quantumchest.network.SetTabsPayload;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import net.minecraftforge.network.Channel.VersionTest;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

final class ForgeNetwork {
    private static final int PROTOCOL_VERSION = 3;

    // addMain runs the handlers on the game thread, not the network thread.
    // The clientbound handlers only ever run on the client.
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
                    .addMain(SyncStatePayload.class, SyncStatePayload.STREAM_CODEC,
                            (payload, context) -> ClientHandlers.onSyncState(payload))
                .serverbound()
                    .addMain(ClickPayload.class, ClickPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onClick(payload, context.getSender()))
                    .addMain(ChestSettingsPayload.class, ChestSettingsPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSettings(payload, context.getSender()))
                    .addMain(SetTabsPayload.class, SetTabsPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetTabs(payload, context.getSender()))
                    .addMain(SetFacePayload.class, SetFacePayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetFace(payload, context.getSender()))
            .build();

    private ForgeNetwork() {
    }

    /** Loads the class so the channel exists before the game connects. */
    static void init() {
    }
}
