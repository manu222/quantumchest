package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.MachinePayloads;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import com.manuelaraujo.quantumchest.network.UpgradeConfigPayloads;
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

    public static void onCompactOptions(UpgradeConfigPayloads.CompactOptions payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof QuantumChestMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applyCompactOptions(payload.options());
        }
    }

    private static QuantumChestMenu menu(int containerId) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof QuantumChestMenu menu && menu.containerId == containerId) return menu;
        return null;
    }

    public static void onSmeltOptions(MachinePayloads.SmeltOptions payload) {
        QuantumChestMenu menu = menu(payload.containerId());
        if (menu != null) menu.applySmeltOptions(payload.options());
    }

    public static void onSyncState(SyncStatePayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof QuantumChestMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applyState(payload);
        }
    }
}
