package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/** A player invited to a private chest. The name is kept so the screen can show it. */
public record Member(UUID id, String name) {
    public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Member::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Member::name)
    ).apply(i, Member::new));
}
