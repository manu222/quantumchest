package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.automation.RejectAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the owner picks what the open chest does with turned away items that can't leave. */
public record SetRejectPayload(int containerId, RejectAction action) implements CustomPacketPayload {

    public static final Type<SetRejectPayload> TYPE = new Type<>(QuantumChest.id("set_reject"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRejectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetRejectPayload::containerId,
            RejectAction.STREAM_CODEC, SetRejectPayload::action,
            SetRejectPayload::new);

    @Override
    public Type<SetRejectPayload> type() {
        return TYPE;
    }
}
