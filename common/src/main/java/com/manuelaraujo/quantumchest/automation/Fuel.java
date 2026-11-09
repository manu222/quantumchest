package com.manuelaraujo.quantumchest.automation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Burn times. Since 26.3 a fuel is a component on the item, and its burn time
 * is worked out from the world's data with the furnace it burns in (smokers
 * and blast furnaces burn it twice as fast). The smelter counts as a plain
 * furnace. Only the server can do this, so the screen gets a table.
 */
public final class Fuel {
    // A furnace has a block entity and a container too, but the vanilla burn times only look at the block
    private static final ContextKeySet PARAMS = new ContextKeySet.Builder()
            .optional(LootContextParams.BLOCK_STATE)
            .optional(LootContextParams.ORIGIN)
            .build();
    private static final int MAX_TABLE = 8192;

    private static MinecraftServer cachedFor;
    private static final Map<Item, Integer> TIMES = new HashMap<>();
    private static List<FuelTime> table;

    private Fuel() {
    }

    private static void checkCache(ServerLevel level) {
        if (cachedFor != level.getServer()) {
            cachedFor = level.getServer();
            TIMES.clear();
            table = null;
        }
    }

    /** Ticks a plain copy of this item burns for, 0 if it isn't fuel. */
    public static int burnTime(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty() || !stack.has(DataComponents.COOKING_FUEL)) return 0;
        checkCache(level);
        return TIMES.computeIfAbsent(stack.getItem(), item -> compute(new ItemStack(item), level));
    }

    private static int compute(ItemStack stack, ServerLevel level) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, Blocks.FURNACE.defaultBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(PARAMS);
        LootContext context = new LootContext.Builder(params).create(Optional.empty());
        return Math.max(0, ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0));
    }

    /** Every item that burns, for the smelter screen. Worked out once per world. */
    public static List<FuelTime> table(ServerLevel level) {
        checkCache(level);
        if (table != null) return table;
        List<FuelTime> found = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (found.size() >= MAX_TABLE) break;
            ItemStack stack = new ItemStack(item);
            int ticks = burnTime(stack, level);
            if (ticks > 0) found.add(new FuelTime(BuiltInRegistries.ITEM.getKey(item), ticks));
        }
        table = List.copyOf(found);
        return table;
    }
}
