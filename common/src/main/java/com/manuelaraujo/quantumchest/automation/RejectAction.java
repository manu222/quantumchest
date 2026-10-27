package com.manuelaraujo.quantumchest.automation;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * What a chest does with items it turns away when they can't leave through
 * an eject face (there is none, or the block in front is full).
 */
public enum RejectAction implements StringRepresentable {
    /** Don't take them: the hopper or pipe keeps them, as with a full chest. The default. */
    REFUSE("refuse"),
    /** Take them and drop them on the ground in front of the eject face (or the front of the chest). */
    DROP("drop"),
    /** Take them and destroy them. */
    DESTROY("destroy");

    public static final Codec<RejectAction> CODEC = StringRepresentable.fromEnum(RejectAction::values);
    public static final StreamCodec<ByteBuf, RejectAction> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.max(0, Math.min(i, values().length - 1))], RejectAction::ordinal);

    private final String name;

    RejectAction(String name) {
        this.name = name;
    }

    public RejectAction next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "gui.quantumchest.reject." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
