package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** The chest an entangler is holding: its storage, its tier and where it was, for the tooltip. */
public record StoredChest(UUID storage, ChestTier tier, GlobalPos pos) {
    public static final Codec<StoredChest> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("storage").forGetter(StoredChest::storage),
            ChestTier.CODEC.fieldOf("tier").forGetter(StoredChest::tier),
            GlobalPos.CODEC.fieldOf("pos").forGetter(StoredChest::pos)
    ).apply(i, StoredChest::new));

    public static final StreamCodec<ByteBuf, StoredChest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, StoredChest::storage,
            ChestTier.STREAM_CODEC, StoredChest::tier,
            GlobalPos.STREAM_CODEC, StoredChest::pos,
            StoredChest::new);
}
