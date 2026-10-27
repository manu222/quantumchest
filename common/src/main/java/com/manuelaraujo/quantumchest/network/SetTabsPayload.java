package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Client to server: the new list of custom tabs after creating, editing or deleting one. */
public record SetTabsPayload(int containerId, List<CustomTab> tabs) implements CustomPacketPayload {

    public static final Type<SetTabsPayload> TYPE = new Type<>(QuantumChest.id("set_tabs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTabsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetTabsPayload::containerId,
            CustomTab.STREAM_CODEC.apply(ByteBufCodecs.list(CustomTab.MAX_TABS)), SetTabsPayload::tabs,
            SetTabsPayload::new);

    @Override
    public Type<SetTabsPayload> type() {
        return TYPE;
    }
}
