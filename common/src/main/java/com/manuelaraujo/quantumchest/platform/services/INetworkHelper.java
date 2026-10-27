package com.manuelaraujo.quantumchest.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Sending our own packets. Registration happens in each loader. */
public interface INetworkHelper {

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client side only. */
    void sendToServer(CustomPacketPayload payload);
}
