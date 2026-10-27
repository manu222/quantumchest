package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Who besides the owner (and server operators) may use a chest. */
public enum AccessMode implements StringRepresentable {
    PRIVATE("private"),
    LIST("list"),
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
