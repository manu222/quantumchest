package com.manuelaraujo.quantumchest.automation;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * What one face of a chest does with the I/O upgrade installed. The same
 * setting covers both directions: the chest pulling or pushing on its own,
 * and hoppers or pipes connected to that face.
 */
public enum FaceMode implements StringRepresentable {
    OFF("off"),
    /** Takes items from the block on that side; hoppers and pipes may push in. */
    IMPORT("import"),
    /** Sends the items of one tab to the block on that side; hoppers and pipes may pull them. */
    EXPORT("export");

    public static final Codec<FaceMode> CODEC = StringRepresentable.fromEnum(FaceMode::values);
    public static final StreamCodec<ByteBuf, FaceMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.max(0, Math.min(i, values().length - 1))], FaceMode::ordinal);

    private final String name;

    FaceMode(String name) {
        this.name = name;
    }

    public FaceMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "gui.quantumchest.face." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
