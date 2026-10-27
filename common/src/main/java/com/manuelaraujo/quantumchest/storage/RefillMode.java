package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** Which way an auto-refill rule moves items between the chest and the player. */
public enum RefillMode implements StringRepresentable {
    /** Below the amount, the chest hands out more. Torches, food, arrows. */
    REFILL("refill", true, false),
    /** Above the amount, the rest goes into the chest. Dirt and stone while digging. */
    STORE("store", false, true),
    /** Both: always exactly the amount on you. */
    KEEP("keep", true, true);

    public static final Codec<RefillMode> CODEC = StringRepresentable.fromEnum(RefillMode::values);
    public static final StreamCodec<ByteBuf, RefillMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.max(0, Math.min(i, values().length - 1))], RefillMode::ordinal);

    private final String name;
    private final boolean gives;
    private final boolean takes;

    RefillMode(String name, boolean gives, boolean takes) {
        this.name = name;
        this.gives = gives;
        this.takes = takes;
    }

    /** Tops the player up from the chest. */
    public boolean gives() {
        return gives;
    }

    /** Puts what the player carries above the amount into the chest. */
    public boolean takes() {
        return takes;
    }

    public RefillMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public RefillMode previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public String translationKey() {
        return "gui.quantumchest.refill.mode." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
