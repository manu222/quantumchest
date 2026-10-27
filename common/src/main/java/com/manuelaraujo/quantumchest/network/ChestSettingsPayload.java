package com.manuelaraujo.quantumchest.network;

import com.manuelaraujo.quantumchest.QuantumChest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the owner changes who may use the chest.
 * {@code value} is the player name for ADD_MEMBER and REMOVE_MEMBER, unused otherwise.
 */
public record ChestSettingsPayload(int containerId, Action action, String value) implements CustomPacketPayload {

    public static final Type<ChestSettingsPayload> TYPE = new Type<>(QuantumChest.id("chest_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChestSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChestSettingsPayload::containerId,
            ByteBufCodecs.VAR_INT.map(Action::byId, Action::ordinal), ChestSettingsPayload::action,
            ByteBufCodecs.stringUtf8(64), ChestSettingsPayload::value,
            ChestSettingsPayload::new);

    @Override
    public Type<ChestSettingsPayload> type() {
        return TYPE;
    }

    public enum Action {
        MAKE_PUBLIC, MAKE_PRIVATE, ADD_MEMBER, REMOVE_MEMBER;

        private static final Action[] VALUES = values();

        public static Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : MAKE_PRIVATE;
        }
    }
}
