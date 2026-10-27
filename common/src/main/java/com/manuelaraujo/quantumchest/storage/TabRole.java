package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * What a custom tab does besides grouping items. The rules only apply to
 * items that arrive on their own (magnet, hoppers, pipes, the I/O upgrade);
 * whatever a player puts in by hand always goes in. Compacting is the
 * exception: it works on the stored items, wherever they came from.
 */
public enum TabRole implements StringRepresentable {
    /** Just a tab for browsing. */
    NORMAL("normal", false),
    /** With at least one of these, only matching items get in automatically. Built into every chest. */
    WHITELIST("whitelist", false),
    /** Matching items are destroyed on arrival once the chest holds the limit. Needs the destroyer upgrade. */
    VOID("void", true),
    /** Matching items are turned into their block form, leaving the limit loose. Needs the compactor upgrade. */
    COMPACT("compact", true);

    public static final Codec<TabRole> CODEC = StringRepresentable.fromEnum(TabRole::values);
    public static final StreamCodec<ByteBuf, TabRole> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.max(0, Math.min(i, values().length - 1))], TabRole::ordinal);

    private final String name;
    private final boolean usesLimit;

    TabRole(String name, boolean usesLimit) {
        this.name = name;
        this.usesLimit = usesLimit;
    }

    /** Destroy and compact take an amount to keep. */
    public boolean usesLimit() {
        return usesLimit;
    }

    public TabRole next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "gui.quantumchest.role." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
