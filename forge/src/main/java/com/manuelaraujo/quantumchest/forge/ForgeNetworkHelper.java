package com.manuelaraujo.quantumchest.forge;

import com.manuelaraujo.quantumchest.platform.services.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class ForgeNetworkHelper implements INetworkHelper {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgeNetwork.CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ForgeNetwork.CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }
}
