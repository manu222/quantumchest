package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: what is inside the open chest. The first packet after
 * opening has {@code reset} set and replaces everything; later ones only carry
 * the item types that changed. A count of 0 means the type is gone.
 */
public record SyncItemsPayload(int containerId, boolean reset, int tier, String owner,
                               List<ItemAmount> entries) implements CustomPacketPayload {

    public static final Type<SyncItemsPayload> TYPE = new Type<>(QuantumChest.id("sync_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncItemsPayload::containerId,
            ByteBufCodecs.BOOL, SyncItemsPayload::reset,
            ByteBufCodecs.VAR_INT, SyncItemsPayload::tier,
            ByteBufCodecs.STRING_UTF8, SyncItemsPayload::owner,
            ItemAmount.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncItemsPayload::entries,
            SyncItemsPayload::new);

    @Override
    public Type<SyncItemsPayload> type() {
        return TYPE;
    }

    public record ItemAmount(ItemStack stack, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemAmount> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, ItemAmount::stack,
                ByteBufCodecs.VAR_LONG, ItemAmount::count,
                ItemAmount::new);
    }
}
