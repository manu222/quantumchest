package com.manuelaraujo.quantumchest.automation;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * What one face of a chest does. Without the I/O upgrade every face is
 * INPUT, like a vanilla chest: hoppers and pipes can put things in. The
 * upgrade unlocks the rest.
 */
public enum FaceMode implements StringRepresentable {
    /** Hoppers and pipes may push in. The default. */
    INPUT("input"),
    /** Like INPUT, and the chest also pulls from the block on that side. */
    IMPORT("import"),
    /** Sends the items of one tab to the block on that side; hoppers and pipes may pull them. */
    EXPORT("export"),
    /** Whatever the chest turns away (not in the whitelist, or no room) leaves through here. */
    EJECT("eject"),
    /** Nothing goes in or out. */
    OFF("off");

    public static final Codec<FaceMode> CODEC = StringRepresentable.fromEnum(FaceMode::values);
    public static final StreamCodec<ByteBuf, FaceMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.max(0, Math.min(i, values().length - 1))], FaceMode::ordinal);

    private final String name;

    FaceMode(String name) {
        this.name = name;
    }

    /** Faces that hoppers and pipes may put items into. */
    public boolean acceptsInput() {
        return this == INPUT || this == IMPORT;
    }

    public FaceMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public FaceMode previous() {
        return values()[Math.floorMod(ordinal() - 1, values().length)];
    }

    public String translationKey() {
        return "gui.quantumchest.face." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
