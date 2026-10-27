package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.client.ClientHandlers;
import com.manuelaraujo.quantumchest.network.ChestSettingsPayload;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.network.QuantumNetwork;
import com.manuelaraujo.quantumchest.network.SetTabsPayload;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2")
                // Client handlers are only ever called on the client, so the
                // dedicated server never loads ClientHandlers
                .playToClient(SyncItemsPayload.TYPE, SyncItemsPayload.STREAM_CODEC,
                        (payload, context) -> ClientHandlers.onSyncItems(payload))
                .playToClient(SyncStatePayload.TYPE, SyncStatePayload.STREAM_CODEC,
                        (payload, context) -> ClientHandlers.onSyncState(payload))
                .playToServer(ClickPayload.TYPE, ClickPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onClick(payload, (ServerPlayer) context.player()))
                .playToServer(ChestSettingsPayload.TYPE, ChestSettingsPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSettings(payload, (ServerPlayer) context.player()))
                .playToServer(SetTabsPayload.TYPE, SetTabsPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onSetTabs(payload, (ServerPlayer) context.player()));
    }
}
