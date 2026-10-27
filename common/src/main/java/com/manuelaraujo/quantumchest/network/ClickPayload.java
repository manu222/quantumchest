package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.menu.ClickAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: a click on the item grid. {@code stack} is the item type
 * that was clicked (empty when storing what is on the cursor). The server
 * checks that the item really is in the chest before giving anything out.
 */
public record ClickPayload(int containerId, ClickAction action, ItemStack stack) implements CustomPacketPayload {

    public static final Type<ClickPayload> TYPE = new Type<>(QuantumChest.id("click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClickPayload::containerId,
            ByteBufCodecs.VAR_INT.map(ClickAction::byId, ClickAction::ordinal), ClickPayload::action,
            ItemStack.OPTIONAL_STREAM_CODEC, ClickPayload::stack,
            ClickPayload::new);

    @Override
    public Type<ClickPayload> type() {
        return TYPE;
    }
}
