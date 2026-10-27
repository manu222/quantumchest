package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the owner sets what one face of the open chest does. */
public record SetFacePayload(int containerId, FaceSetting face) implements CustomPacketPayload {

    public static final Type<SetFacePayload> TYPE = new Type<>(QuantumChest.id("set_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFacePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFacePayload::containerId,
            FaceSetting.STREAM_CODEC, SetFacePayload::face,
            SetFacePayload::new);

    @Override
    public Type<SetFacePayload> type() {
        return TYPE;
    }
}
