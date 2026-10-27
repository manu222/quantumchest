package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * PUBLIC: anyone can open, use and break the chest, like a vanilla chest.
 * PRIVATE: only the owner and the players on the member list.
 * Changing permissions is always the owner's job (and operators').
 */
public enum AccessMode implements StringRepresentable {
    PRIVATE("private"),
    PUBLIC("public");

    public static final Codec<AccessMode> CODEC = StringRepresentable.fromEnum(AccessMode::values);

    private final String name;

    AccessMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
