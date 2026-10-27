package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.automation.SmeltOption;
import com.manuelaraujo.quantumchest.storage.CompactRule;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.RefillRule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Packets of the auto-refill and smelting upgrades. Crafting uses real slots and needs none. */
public final class MachinePayloads {
    private MachinePayloads() {
    }

    /** Client to server: every auto-refill rule. */
    public record SetRefillRules(int containerId, List<RefillRule> rules) implements CustomPacketPayload {
        public static final Type<SetRefillRules> TYPE = new Type<>(QuantumChest.id("set_refill_rules"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetRefillRules> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetRefillRules::containerId,
                RefillRule.STREAM_CODEC.apply(ByteBufCodecs.list(RefillRule.MAX_RULES)), SetRefillRules::rules,
                SetRefillRules::new);

        @Override
        public Type<SetRefillRules> type() {
            return TYPE;
        }
    }

    /** Client to server: one row of the smelter. */
    public record SetSmeltRule(int containerId, CompactRule rule) implements CustomPacketPayload {
        public static final Type<SetSmeltRule> TYPE = new Type<>(QuantumChest.id("set_smelt_rule"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSmeltRule> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetSmeltRule::containerId,
                CompactRule.STREAM_CODEC, SetSmeltRule::rule,
                SetSmeltRule::new);

        @Override
        public Type<SetSmeltRule> type() {
            return TYPE;
        }
    }

    /** Client to server: the fuels the smelter may burn, in order. */
    public record SetFuels(int containerId, List<ItemMatch> fuels) implements CustomPacketPayload {
        public static final Type<SetFuels> TYPE = new Type<>(QuantumChest.id("set_fuels"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetFuels> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetFuels::containerId,
                ItemMatch.STREAM_CODEC.apply(ByteBufCodecs.list(QuantumStorage.MAX_FUELS)), SetFuels::fuels,
                SetFuels::new);

        @Override
        public Type<SetFuels> type() {
            return TYPE;
        }
    }

    /** Client to server: the smelter screen opened. */
    public record RequestSmeltOptions(int containerId) implements CustomPacketPayload {
        public static final Type<RequestSmeltOptions> TYPE = new Type<>(QuantumChest.id("request_smelt_options"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSmeltOptions> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RequestSmeltOptions::containerId,
                RequestSmeltOptions::new);

        @Override
        public Type<RequestSmeltOptions> type() {
            return TYPE;
        }
    }

    /** Server to client: everything the furnace recipes can melt. */
    public record SmeltOptions(int containerId, List<SmeltOption> options) implements CustomPacketPayload {
        public static final Type<SmeltOptions> TYPE = new Type<>(QuantumChest.id("smelt_options"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SmeltOptions> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SmeltOptions::containerId,
                SmeltOption.STREAM_CODEC.apply(ByteBufCodecs.list(8192)), SmeltOptions::options,
                SmeltOptions::new);

        @Override
        public Type<SmeltOptions> type() {
            return TYPE;
        }
    }
}
