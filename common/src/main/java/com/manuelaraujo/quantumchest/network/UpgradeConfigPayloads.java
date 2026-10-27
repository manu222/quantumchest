package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.automation.CompactOption;
import com.manuelaraujo.quantumchest.storage.CompactRule;
import com.manuelaraujo.quantumchest.storage.FilterRules;
import com.manuelaraujo.quantumchest.storage.VoidRule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * The packets behind the settings screens of the filter and the upgrades.
 * The client sends what the player chose; the server checks permissions,
 * cleans it up and stores it.
 */
public final class UpgradeConfigPayloads {
    private UpgradeConfigPayloads() {
    }

    /** Client to server: the whole input filter. */
    public record SetFilter(int containerId, FilterRules filter) implements CustomPacketPayload {
        public static final Type<SetFilter> TYPE = new Type<>(QuantumChest.id("set_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetFilter> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetFilter::containerId,
                FilterRules.STREAM_CODEC, SetFilter::filter,
                SetFilter::new);

        @Override
        public Type<SetFilter> type() {
            return TYPE;
        }
    }

    /** Client to server: every destroyer rule. */
    public record SetVoidRules(int containerId, List<VoidRule> rules) implements CustomPacketPayload {
        public static final Type<SetVoidRules> TYPE = new Type<>(QuantumChest.id("set_destroy_rules"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetVoidRules> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetVoidRules::containerId,
                VoidRule.STREAM_CODEC.apply(ByteBufCodecs.list(VoidRule.MAX_RULES)), SetVoidRules::rules,
                SetVoidRules::new);

        @Override
        public Type<SetVoidRules> type() {
            return TYPE;
        }
    }

    /** Client to server: one row of the compactor. */
    public record SetCompactRule(int containerId, CompactRule rule) implements CustomPacketPayload {
        public static final Type<SetCompactRule> TYPE = new Type<>(QuantumChest.id("set_compact_rule"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetCompactRule> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetCompactRule::containerId,
                CompactRule.STREAM_CODEC, SetCompactRule::rule,
                SetCompactRule::new);

        @Override
        public Type<SetCompactRule> type() {
            return TYPE;
        }
    }

    /** Client to server: the magnet of the chest in front of the player. {@code range} 0 is the most the tier allows. */
    public record SetMagnet(int containerId, boolean on, int range) implements CustomPacketPayload {
        public static final Type<SetMagnet> TYPE = new Type<>(QuantumChest.id("set_magnet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetMagnet> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetMagnet::containerId,
                ByteBufCodecs.BOOL, SetMagnet::on,
                ByteBufCodecs.VAR_INT, SetMagnet::range,
                SetMagnet::new);

        @Override
        public Type<SetMagnet> type() {
            return TYPE;
        }
    }

    /** Client to server: the compactor screen opened and needs the list of what can be compacted. */
    public record RequestCompactOptions(int containerId) implements CustomPacketPayload {
        public static final Type<RequestCompactOptions> TYPE = new Type<>(QuantumChest.id("request_compact_options"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestCompactOptions> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RequestCompactOptions::containerId,
                RequestCompactOptions::new);

        @Override
        public Type<RequestCompactOptions> type() {
            return TYPE;
        }
    }

    /** Server to client: everything the compactor can do in this world. Recipes only live on the server. */
    public record CompactOptions(int containerId, List<CompactOption> options) implements CustomPacketPayload {
        public static final Type<CompactOptions> TYPE = new Type<>(QuantumChest.id("compact_options"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CompactOptions> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CompactOptions::containerId,
                CompactOption.STREAM_CODEC.apply(ByteBufCodecs.list(8192)), CompactOptions::options,
                CompactOptions::new);

        @Override
        public Type<CompactOptions> type() {
            return TYPE;
        }
    }
}
