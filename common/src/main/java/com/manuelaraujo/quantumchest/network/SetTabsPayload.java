package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

/**
 * Client to server: one custom tab was created, edited or deleted. Only that
 * tab travels, so a chest full of big tabs never goes over the packet limit.
 * {@code index} past the last tab adds a new one; an empty {@code tab} deletes.
 */
public record SetTabsPayload(int containerId, int index, Optional<CustomTab> tab) implements CustomPacketPayload {

    public static final Type<SetTabsPayload> TYPE = new Type<>(QuantumChest.id("set_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTabsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetTabsPayload::containerId,
            ByteBufCodecs.VAR_INT, SetTabsPayload::index,
            ByteBufCodecs.optional(CustomTab.STREAM_CODEC), SetTabsPayload::tab,
            SetTabsPayload::new);

    @Override
    public Type<SetTabsPayload> type() {
        return TYPE;
    }
}
