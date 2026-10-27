package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import com.manuelaraujo.quantumchest.automation.RejectAction;
import com.manuelaraujo.quantumchest.storage.CompactRule;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.FilterRules;
import com.manuelaraujo.quantumchest.storage.VoidRule;
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
public record SyncStatePayload(int containerId, Access access, List<CustomTab> tabs, Faces faces, Config config)
        implements CustomPacketPayload {

    public static final Type<SyncStatePayload> TYPE = new Type<>(QuantumChest.id("sync_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncStatePayload::containerId,
            Access.STREAM_CODEC, SyncStatePayload::access,
            CustomTab.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStatePayload::tabs,
            Faces.STREAM_CODEC, SyncStatePayload::faces,
            Config.STREAM_CODEC, SyncStatePayload::config,
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
     * {@code reject} is what happens to turned away items that can't leave.
     * The magnet fields are this chest's switch and reach (0 = the most the tier allows).
     */
    public record Faces(boolean editable, int facing, List<FaceSetting> sides, RejectAction reject,
                        boolean magnetOn, int magnetRange) {
        public static final Faces NONE = new Faces(false, 2, List.of(), RejectAction.REFUSE, true, 0);

        public static final StreamCodec<RegistryFriendlyByteBuf, Faces> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Faces::editable,
                ByteBufCodecs.VAR_INT, Faces::facing,
                FaceSetting.STREAM_CODEC.apply(ByteBufCodecs.list(6)), Faces::sides,
                RejectAction.STREAM_CODEC, Faces::reject,
                ByteBufCodecs.BOOL, Faces::magnetOn,
                ByteBufCodecs.VAR_INT, Faces::magnetRange,
                Faces::new);
    }

    /** The filter and the settings of the destroyer and the compactor, shared by the whole network. */
    public record Config(FilterRules filter, List<VoidRule> destroy, List<CompactRule> compact) {
        public static final Config NONE = new Config(FilterRules.NONE, List.of(), List.of());

        public static final StreamCodec<RegistryFriendlyByteBuf, Config> STREAM_CODEC = StreamCodec.composite(
                FilterRules.STREAM_CODEC, Config::filter,
                VoidRule.STREAM_CODEC.apply(ByteBufCodecs.list(VoidRule.MAX_RULES)), Config::destroy,
                CompactRule.STREAM_CODEC.apply(ByteBufCodecs.list(8192)), Config::compact,
                Config::new);
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
