package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds the row of tabs: "All", the vanilla creative categories that have
 * something stored, "Other" for whatever fits none of them, and the custom
 * tabs of the chest. Items of other mods land in the vanilla categories when
 * those mods add them there, which most do.
 */
final class ChestTabs {
    static final String ALL = "all";
    static final String OTHER = "other";
    static final String ADD = "add";

    /** One tab in the row. {@code customIndex} is -1 for anything that isn't a custom tab. */
    record Tab(String key, Component name, ItemStack icon, Predicate<ItemKey> filter, int customIndex) {
        boolean isCustom() {
            return customIndex >= 0;
        }
    }

    private final List<CreativeModeTab> categories = new ArrayList<>();
    private final Map<Item, Set<CreativeModeTab>> categoriesByItem = new HashMap<>();

    ChestTabs() {
        Minecraft mc = Minecraft.getInstance();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null && id.getNamespace().equals("minecraft")) categories.add(tab);
        }
        // Tab contents only exist once the creative inventory has been opened. Build them if needed.
        boolean empty = categories.stream().allMatch(tab -> tab.getDisplayItems().isEmpty());
        if (empty && mc.level != null) {
            CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), false, mc.level.registryAccess());
        }
        for (CreativeModeTab tab : categories) {
            for (ItemStack stack : tab.getDisplayItems()) {
                categoriesByItem.computeIfAbsent(stack.getItem(), item -> new HashSet<>()).add(tab);
            }
        }
    }

    List<Tab> build(Iterable<ItemKey> stored, List<CustomTab> customTabs, boolean canAdd) {
        Set<CreativeModeTab> used = new HashSet<>();
        boolean hasOther = false;
        for (ItemKey key : stored) {
            Set<CreativeModeTab> tabs = categoriesByItem.get(key.template().getItem());
            if (tabs == null || tabs.isEmpty()) {
                hasOther = true;
            } else {
                used.addAll(tabs);
            }
        }

        List<Tab> result = new ArrayList<>();
        result.add(new Tab(ALL, Component.translatable("gui.quantumchest.tab.all"), new ItemStack(Items.CHEST), key -> true, -1));
        for (CreativeModeTab tab : categories) {
            if (!used.contains(tab)) continue;
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            result.add(new Tab("cat:" + id, tab.getDisplayName(), tab.getIconItem(),
                    key -> inCategory(key, tab), -1));
        }
        if (hasOther) {
            result.add(new Tab(OTHER, Component.translatable("gui.quantumchest.tab.other"), new ItemStack(Items.BUNDLE),
                    key -> !categoriesByItem.containsKey(key.template().getItem()), -1));
        }
        for (int i = 0; i < customTabs.size(); i++) {
            CustomTab custom = customTabs.get(i);
            ItemStack icon = custom.displayIcon();
            result.add(new Tab("custom:" + i, Component.literal(custom.name()),
                    icon.isEmpty() ? new ItemStack(Items.NAME_TAG) : icon,
                    key -> custom.matches(key.template()), i));
        }
        if (canAdd && customTabs.size() < CustomTab.MAX_TABS) {
            result.add(new Tab(ADD, Component.translatable("gui.quantumchest.tab.add"), ItemStack.EMPTY, key -> true, -1));
        }
        return result;
    }

    private boolean inCategory(ItemKey key, CreativeModeTab tab) {
        Set<CreativeModeTab> tabs = categoriesByItem.get(key.template().getItem());
        return tabs != null && tabs.contains(tab);
    }
}
