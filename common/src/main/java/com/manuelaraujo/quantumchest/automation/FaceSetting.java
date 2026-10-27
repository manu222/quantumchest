package com.manuelaraujo.quantumchest.automation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One face of a chest: its mode and, when exporting, the name of the tab it sends. */
public record FaceSetting(Direction side, FaceMode mode, String tab) {
    public static final int MAX_TAB_NAME = 64;

    public static final Codec<FaceSetting> CODEC = RecordCodecBuilder.create(i -> i.group(
            Direction.CODEC.fieldOf("side").forGetter(FaceSetting::side),
            FaceMode.CODEC.fieldOf("mode").forGetter(FaceSetting::mode),
            Codec.STRING.optionalFieldOf("tab", "").forGetter(FaceSetting::tab)
    ).apply(i, FaceSetting::new));

    public static final StreamCodec<ByteBuf, FaceSetting> STREAM_CODEC = StreamCodec.composite(
            Direction.STREAM_CODEC, FaceSetting::side,
            FaceMode.STREAM_CODEC, FaceSetting::mode,
            ByteBufCodecs.stringUtf8(MAX_TAB_NAME), FaceSetting::tab,
            FaceSetting::new);
}
