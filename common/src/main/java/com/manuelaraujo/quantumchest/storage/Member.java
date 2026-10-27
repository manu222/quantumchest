package com.manuelaraujo.quantumchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A player invited to a private chest. Players can be invited by name while
 * offline: {@code id} stays null until they first use the chest, and from
 * then on the UUID is what counts, so a later rename doesn't lock them out.
 */
public record Member(UUID id, String name) {
    public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("id").forGetter(m -> Optional.ofNullable(m.id())),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Member::name)
    ).apply(i, (id, name) -> new Member(id.orElse(null), name)));

    public boolean hasName(String other) {
        return name.toLowerCase(Locale.ROOT).equals(other.toLowerCase(Locale.ROOT));
    }
}
