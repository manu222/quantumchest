package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server to client: everything about the open chest except its items. Sent on
 * open and whenever the tier, owner, access, members or tabs change. The two
 * "can" flags are worked out on the server for this particular player.
 */
public record SyncStatePayload(int containerId, Access access, List<CustomTab> tabs, Faces faces) implements CustomPacketPayload {

    public static final Type<SyncStatePayload> TYPE = new Type<>(QuantumChest.id("sync_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncStatePayload::containerId,
            Access.STREAM_CODEC, SyncStatePayload::access,
            CustomTab.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStatePayload::tabs,
            Faces.STREAM_CODEC, SyncStatePayload::faces,
            SyncStatePayload::new);

    @Override
    public Type<SyncStatePayload> type() {
        return TYPE;
    }

    /**
     * The faces of the chest on screen. {@code editable} is false when the
     * screen came from a terminal (there is no chest in front of you) or the
     * player may not manage it. {@code facing} is where the front looks, as a
     * 3D data value, so the screen can say front, back, left and right.
     */
    public record Faces(boolean editable, int facing, List<FaceSetting> sides) {
        public static final Faces NONE = new Faces(false, 2, List.of());

        public static final StreamCodec<RegistryFriendlyByteBuf, Faces> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Faces::editable,
                ByteBufCodecs.VAR_INT, Faces::facing,
                FaceSetting.STREAM_CODEC.apply(ByteBufCodecs.list(6)), Faces::sides,
                Faces::new);
    }

    public record Access(int tier, String owner, boolean isPublic, boolean canManage, boolean canEditTabs,
                         List<String> members) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Access> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Access::tier,
                ByteBufCodecs.STRING_UTF8, Access::owner,
                ByteBufCodecs.BOOL, Access::isPublic,
                ByteBufCodecs.BOOL, Access::canManage,
                ByteBufCodecs.BOOL, Access::canEditTabs,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Access::members,
                Access::new);
    }
}
