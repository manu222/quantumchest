package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.client.ClientHandlers;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.network.QuantumNetwork;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                // The client handler is only ever called on the client, so the
                // dedicated server never loads ClientHandlers
                .playToClient(SyncItemsPayload.TYPE, SyncItemsPayload.STREAM_CODEC,
                        (payload, context) -> ClientHandlers.onSyncItems(payload))
                .playToServer(ClickPayload.TYPE, ClickPayload.STREAM_CODEC,
                        (payload, context) -> QuantumNetwork.onClick(payload, (ServerPlayer) context.player()));
    }
}
