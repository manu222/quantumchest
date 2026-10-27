package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import net.minecraft.client.Minecraft;

/** Client side packet handling. Only ever loaded on the client. */
public final class ClientHandlers {
    private ClientHandlers() {
    }

    public static void onSyncItems(SyncItemsPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof QuantumChestMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applySync(payload);
        }
    }
}
