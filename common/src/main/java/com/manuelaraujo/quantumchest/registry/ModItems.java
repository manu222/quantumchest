package com.manuelaraujo.quantumchest.registry;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.item.QuantumChestItem;
import com.manuelaraujo.quantumchest.item.QuantumEntanglerItem;
import com.manuelaraujo.quantumchest.item.QuantumTunnelItem;
import com.manuelaraujo.quantumchest.item.SuperpositionTerminalItem;
import com.manuelaraujo.quantumchest.item.TierUpgradeItem;
import com.manuelaraujo.quantumchest.item.UpgradeItem;
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

    public static final RegistryEntry<Item, QuantumEntanglerItem> QUANTUM_ENTANGLER = ITEMS.registerWithKey("quantum_entangler",
            key -> new QuantumEntanglerItem(new Item.Properties().setId(key).stacksTo(1)));

    public static final RegistryEntry<Item, SuperpositionTerminalItem> SUPERPOSITION_TERMINAL = ITEMS.registerWithKey("superposition_terminal",
            key -> new SuperpositionTerminalItem(new Item.Properties().setId(key).stacksTo(1)));

    public static final RegistryEntry<Item, QuantumTunnelItem> QUANTUM_TUNNEL = ITEMS.registerWithKey("quantum_tunnel",
            key -> new QuantumTunnelItem(new Item.Properties().setId(key).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.quantumchest.quantum_tunnel.lore").withStyle(ChatFormatting.GRAY),
                            Component.translatable("item.quantumchest.upgrade.min_tier",
                                    Component.translatable(ChestTier.ELITE.translationKey())).withStyle(ChatFormatting.DARK_GRAY))))));

    public static final RegistryEntry<Item, UpgradeItem> MAGNET_UPGRADE = upgrade("magnet_upgrade", ChestTier.BASIC);
    public static final RegistryEntry<Item, UpgradeItem> VOID_UPGRADE = upgrade("void_upgrade", ChestTier.BASIC);
    public static final RegistryEntry<Item, UpgradeItem> COMPACTOR_UPGRADE = upgrade("compactor_upgrade", ChestTier.REINFORCED);
    public static final RegistryEntry<Item, UpgradeItem> IO_UPGRADE = upgrade("io_upgrade", ChestTier.REINFORCED);
    public static final RegistryEntry<Item, UpgradeItem> CRAFTING_UPGRADE = upgrade("crafting_upgrade", ChestTier.BASIC);
    public static final RegistryEntry<Item, UpgradeItem> REFILL_UPGRADE = upgrade("refill_upgrade", ChestTier.ADVANCED);
    public static final RegistryEntry<Item, UpgradeItem> SMELTING_UPGRADE = upgrade("smelting_upgrade", ChestTier.ADVANCED);

    public static final List<RegistryEntry<Item, UpgradeItem>> UPGRADES = List.of(MAGNET_UPGRADE, VOID_UPGRADE,
            COMPACTOR_UPGRADE, IO_UPGRADE, CRAFTING_UPGRADE, REFILL_UPGRADE, SMELTING_UPGRADE);

    /** An upgrade with two lore lines: what it does and the tier it needs. */
    private static RegistryEntry<Item, UpgradeItem> upgrade(String name, ChestTier minTier) {
        return ITEMS.registerWithKey(name, key -> new UpgradeItem(minTier, new Item.Properties().setId(key).stacksTo(1)
                .component(DataComponents.LORE, new ItemLore(List.of(
                        Component.translatable("item.quantumchest." + name + ".lore").withStyle(ChatFormatting.GRAY),
                        Component.translatable("item.quantumchest.upgrade.min_tier",
                                Component.translatable(minTier.translationKey())).withStyle(ChatFormatting.DARK_GRAY))))));
    }

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
