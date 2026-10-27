package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import net.minecraft.server.level.ServerPlayer;

/** Server side packet handling. Each loader registers the packets and calls into here. */
public final class QuantumNetwork {
    private QuantumNetwork() {
    }

    public static void onClick(ClickPayload payload, ServerPlayer player) {
        if (player == null || player.isSpectator()) return;
        if (player.containerMenu instanceof QuantumChestMenu menu && menu.containerId == payload.containerId()) {
            menu.handleClick(payload.action(), payload.stack(), player);
        }
    }
}
