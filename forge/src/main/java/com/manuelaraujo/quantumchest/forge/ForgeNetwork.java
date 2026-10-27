package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.client.ClientHandlers;
import com.manuelaraujo.quantumchest.network.ChestSettingsPayload;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.network.QuantumNetwork;
import com.manuelaraujo.quantumchest.network.SetFacePayload;
import com.manuelaraujo.quantumchest.network.SetRejectPayload;
import com.manuelaraujo.quantumchest.network.SetTabsPayload;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import com.manuelaraujo.quantumchest.network.UpgradeConfigPayloads;
import net.minecraftforge.network.Channel.VersionTest;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

final class ForgeNetwork {
    private static final int PROTOCOL_VERSION = 5;

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
                    .addMain(UpgradeConfigPayloads.CompactOptions.class, UpgradeConfigPayloads.CompactOptions.STREAM_CODEC,
                            (payload, context) -> ClientHandlers.onCompactOptions(payload))
                .serverbound()
                    .addMain(ClickPayload.class, ClickPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onClick(payload, context.getSender()))
                    .addMain(ChestSettingsPayload.class, ChestSettingsPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSettings(payload, context.getSender()))
                    .addMain(SetTabsPayload.class, SetTabsPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetTabs(payload, context.getSender()))
                    .addMain(SetFacePayload.class, SetFacePayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetFace(payload, context.getSender()))
                    .addMain(SetRejectPayload.class, SetRejectPayload.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetReject(payload, context.getSender()))
                    .addMain(UpgradeConfigPayloads.SetFilter.class, UpgradeConfigPayloads.SetFilter.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetFilter(payload, context.getSender()))
                    .addMain(UpgradeConfigPayloads.SetVoidRules.class, UpgradeConfigPayloads.SetVoidRules.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetVoidRules(payload, context.getSender()))
                    .addMain(UpgradeConfigPayloads.SetCompactRule.class, UpgradeConfigPayloads.SetCompactRule.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetCompactRule(payload, context.getSender()))
                    .addMain(UpgradeConfigPayloads.SetMagnet.class, UpgradeConfigPayloads.SetMagnet.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onSetMagnet(payload, context.getSender()))
                    .addMain(UpgradeConfigPayloads.RequestCompactOptions.class, UpgradeConfigPayloads.RequestCompactOptions.STREAM_CODEC,
                            (payload, context) -> QuantumNetwork.onRequestCompactOptions(payload, context.getSender()))
            .build();

    private ForgeNetwork() {
    }

    /** Loads the class so the channel exists before the game connects. */
    static void init() {
    }
}
