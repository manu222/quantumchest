package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.entity.ChestCollapseEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final RegistrationProvider<EntityType<?>> ENTITIES =
            RegistrationProvider.get(Registries.ENTITY_TYPE, QuantumChest.MOD_ID);

    /** The animation of a broken chest. Short lived, so it only has to be seen from close by. */
    public static final RegistryEntry<EntityType<?>, EntityType<ChestCollapseEntity>> CHEST_COLLAPSE =
            ENTITIES.registerWithKey("chest_collapse", key -> EntityType.Builder
                    .<ChestCollapseEntity>of(ChestCollapseEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .noLootTable()
                    .fireImmune()
                    .build(key));

    private ModEntities() {
    }

    static void init() {
    }
}
