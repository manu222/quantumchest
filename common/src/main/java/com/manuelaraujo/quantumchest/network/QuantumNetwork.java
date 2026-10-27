package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import net.minecraft.server.level.ServerPlayer;

/** Server side packet handling. Each loader registers the packets and calls into here. */
public final class QuantumNetwork {
    private QuantumNetwork() {
    }

    public static void onClick(ClickPayload payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleClick(payload.action(), payload.stack(), player);
    }

    public static void onSettings(ChestSettingsPayload payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleSettings(payload.action(), payload.value(), player);
    }

    public static void onSetTabs(SetTabsPayload payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleSetTabs(payload.tabs(), player);
    }

    public static void onSetFace(SetFacePayload payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleFace(payload.face().side(), payload.face().mode(), payload.face().tab(), player);
    }

    public static void onSetReject(SetRejectPayload payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleReject(payload.action(), player);
    }

    public static void onSetFilter(UpgradeConfigPayloads.SetFilter payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleFilter(payload.filter(), player);
    }

    public static void onSetVoidRules(UpgradeConfigPayloads.SetVoidRules payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleVoidRules(payload.rules(), player);
    }

    public static void onSetCompactRule(UpgradeConfigPayloads.SetCompactRule payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleCompactRule(payload.rule(), player);
    }

    public static void onSetMagnet(UpgradeConfigPayloads.SetMagnet payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.handleMagnet(payload.on(), payload.range(), player);
    }

    public static void onRequestCompactOptions(UpgradeConfigPayloads.RequestCompactOptions payload, ServerPlayer player) {
        QuantumChestMenu menu = openMenu(player, payload.containerId());
        if (menu != null) menu.sendCompactOptions(player);
    }

    private static QuantumChestMenu openMenu(ServerPlayer player, int containerId) {
        if (player == null || player.isSpectator()) return null;
        if (player.containerMenu instanceof QuantumChestMenu menu && menu.containerId == containerId) return menu;
        return null;
    }
}
