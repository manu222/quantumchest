package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.item.QuantumChestItem;
import com.manuelaraujo.quantumchest.item.TierUpgradeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class ModItems {
    public static final RegistrationProvider<Item> ITEMS = RegistrationProvider.get(Registries.ITEM, QuantumChest.MOD_ID);

    public static final RegistryEntry<Item, QuantumChestItem> QUANTUM_CHEST = ITEMS.registerWithKey("quantum_chest",
            key -> new QuantumChestItem(ModBlocks.QUANTUM_CHEST.get(), new Item.Properties()
                    .setId(key)
                    .useBlockDescriptionPrefix()
                    .stacksTo(1)));

    public static final RegistryEntry<Item, TierUpgradeItem> REINFORCED_KIT = kit("reinforced_upgrade_kit", ChestTier.REINFORCED);
    public static final RegistryEntry<Item, TierUpgradeItem> ADVANCED_KIT = kit("advanced_upgrade_kit", ChestTier.ADVANCED);
    public static final RegistryEntry<Item, TierUpgradeItem> ELITE_KIT = kit("elite_upgrade_kit", ChestTier.ELITE);
    public static final RegistryEntry<Item, TierUpgradeItem> QUANTUM_KIT = kit("quantum_upgrade_kit", ChestTier.QUANTUM);

    public static final List<RegistryEntry<Item, TierUpgradeItem>> KITS = List.of(REINFORCED_KIT, ADVANCED_KIT, ELITE_KIT, QUANTUM_KIT);

    private static RegistryEntry<Item, TierUpgradeItem> kit(String name, ChestTier target) {
        return ITEMS.registerWithKey(name, key -> {
            Item.Properties properties = new Item.Properties().setId(key);
            ChestTier required = ChestTier.values()[target.ordinal() - 1];
            // The lore explains what the kit does without needing a tooltip hook
            properties.component(DataComponents.LORE, new ItemLore(List.of(
                    Component.translatable("item.quantumchest.upgrade_kit.lore",
                                    Component.translatable(required.translationKey()),
                                    Component.translatable(target.translationKey()))
                            .withStyle(ChatFormatting.GRAY))));
            return new TierUpgradeItem(target, properties);
        });
    }

    private ModItems() {
    }

    static void init() {
    }
}
