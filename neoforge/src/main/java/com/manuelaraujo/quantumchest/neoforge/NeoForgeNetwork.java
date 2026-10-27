package com.manuelaraujo.quantumchest.neoforge;

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
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("5")
                // Client handlers are only ever called on the client, so the
                // dedicated server never loads ClientHandlers
                .playToClient(SyncItemsPayload.TYPE, SyncItemsPayload.STREAM_CODEC,
                        (payload, context) -> ClientHandlers.onSyncItems(payload))
                .playToClient(SyncStatePayload.TYPE, SyncStatePayload.STREAM_CODEC,
                        (payload, context) -> ClientHandlers.onSyncState(payload))
                .playToClient(UpgradeConfigPayloads.CompactOptions.TYPE, UpgradeConfigPayloads.CompactOptions.STREAM_CODEC,
                        (payload, context) -> ClientHandlers.onCompactOptions(payload))
                .playToServer(ClickPayload.TYPE, ClickPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onClick(payload, (ServerPlayer) context.player()))
                .playToServer(ChestSettingsPayload.TYPE, ChestSettingsPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSettings(payload, (ServerPlayer) context.player()))
                .playToServer(SetTabsPayload.TYPE, SetTabsPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetTabs(payload, (ServerPlayer) context.player()))
                .playToServer(SetFacePayload.TYPE, SetFacePayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetFace(payload, (ServerPlayer) context.player()))
                .playToServer(SetRejectPayload.TYPE, SetRejectPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetReject(payload, (ServerPlayer) context.player()))
                .playToServer(UpgradeConfigPayloads.SetFilter.TYPE, UpgradeConfigPayloads.SetFilter.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetFilter(payload, (ServerPlayer) context.player()))
                .playToServer(UpgradeConfigPayloads.SetVoidRules.TYPE, UpgradeConfigPayloads.SetVoidRules.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetVoidRules(payload, (ServerPlayer) context.player()))
                .playToServer(UpgradeConfigPayloads.SetCompactRule.TYPE, UpgradeConfigPayloads.SetCompactRule.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetCompactRule(payload, (ServerPlayer) context.player()))
                .playToServer(UpgradeConfigPayloads.SetMagnet.TYPE, UpgradeConfigPayloads.SetMagnet.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetMagnet(payload, (ServerPlayer) context.player()))
                .playToServer(UpgradeConfigPayloads.RequestCompactOptions.TYPE, UpgradeConfigPayloads.RequestCompactOptions.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onRequestCompactOptions(payload, (ServerPlayer) context.player()));
    }
}
