package com.manuelaraujo.quantumchest.automation;

import com.manuelaraujo.quantumchest.QuantumConfig;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.storage.CompactRule;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The smelting upgrade: furnaces inside the chest. The player switches on
 * what to smelt (raw iron, sand, food...) and which fuels it may burn, in
 * order. Each item takes as long as in a vanilla furnace; the tier decides
 * how many cook side by side (one on Advanced, four on Elite, sixteen on
 * Quantum, or what the config says). Fuel burns like in a furnace too: a
 * coal lasts eight items, and only while something is cooking.
 *
 * Everything comes from the furnace recipes, so ores of other mods work too.
 */
public final class Smelter {
    private static List<SmeltOption> options;
    private static final Map<Item, Rule> RULES = new HashMap<>();
    private static RecipeManager cachedFor;

    private record Rule(Item result, int count, int cookTime) {
    }

    private Smelter() {
    }

    /** The smelter works once a second, so each pass is this many ticks of furnace time. */
    private static final int CYCLE = 20;

    /** How many items cook at the same time on this tier. */
    public static int furnaces(ChestTier tier) {
        return QuantumConfig.smeltFurnaces(tier);
    }

    private static void checkCache(ServerLevel level) {
        RecipeManager recipes = level.recipeAccess();
        if (recipes != cachedFor) {
            RULES.clear();
            options = null;
            cachedFor = recipes;
        }
    }

    /** Everything the furnace recipes can melt, for the screen. Kept until recipes reload. */
    @SuppressWarnings("deprecation") // Ingredient.items() is the only way to list what an ingredient takes
    public static List<SmeltOption> options(ServerLevel level) {
        checkCache(level);
        if (options != null) return options;
        Set<Item> seen = new LinkedHashSet<>();
        List<SmeltOption> found = new ArrayList<>();
        for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
            if (!(holder.value() instanceof SmeltingRecipe recipe)) continue;
            for (Holder<Item> option : (Iterable<Holder<Item>>) recipe.input().items().limit(64)::iterator) {
                Item item = option.value();
                if (!seen.add(item)) continue;
                Rule rule = rule(level, item);
                if (rule != null) {
                    found.add(new SmeltOption(BuiltInRegistries.ITEM.getKey(item), BuiltInRegistries.ITEM.getKey(rule.result()),
                            rule.count(), rule.cookTime()));
                }
            }
        }
        found.sort(Comparator.comparing((SmeltOption o) -> o.input().getNamespace().equals("minecraft") ? 0 : 1)
                .thenComparing(o -> o.input().toString()));
        options = List.copyOf(found);
        return options;
    }

    private static Rule rule(ServerLevel level, Item item) {
        if (RULES.containsKey(item)) return RULES.get(item);
        Rule rule = null;
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value().assemble(input);
            // Results with extra data (rare, but mods do it) can't be stored as a plain count
            if (!result.isEmpty() && !result.is(item) && result.getComponentsPatch().isEmpty()) {
                rule = new Rule(result.getItem(), result.getCount(), Math.max(1, recipe.get().value().cookingTime()));
            }
        }
        RULES.put(item, rule);
        return rule;
    }

    /** Something in the chest the smelter may work on, and how many of it. */
    private record Job(ItemKey key, Rule rule, long available) {
    }

    /**
     * One second of furnace time. The current batch cooks a bit more, burning
     * the heat it needs; when it's done, as many items as furnaces are working
     * turn into their result.
     */
    public static void run(ServerLevel level, QuantumStorage storage) {
        if (storage.smeltRules().isEmpty() || storage.fuels().isEmpty()) {
            storage.setSmeltStatus(0, 0, 0);
            return;
        }
        checkCache(level);
        List<Job> jobs = jobs(level, storage);
        long pending = jobs.stream().mapToLong(Job::available).sum();
        if (pending <= 0) {
            // Like a furnace with nothing in it: the half cooked batch is lost
            storage.setSmeltStatus(0, 0, 0);
            return;
        }
        int cookTime = jobs.getFirst().rule().cookTime();
        int active = (int) Math.min(furnaces(storage.tier()), pending);

        long heat = storage.fuelTicks();
        long need = (long) CYCLE * active;
        while (heat < need) {
            long burned = burnOne(level, storage);
            if (burned <= 0) break;
            heat += burned;
        }
        // Not enough fuel for every furnace: as many as the heat left can keep going
        active = (int) Math.min(active, heat / CYCLE);
        if (active <= 0) {
            storage.setFuelTicks(heat);
            storage.setSmeltStatus(storage.cookProgress(), 0, cookTime);
            return;
        }
        heat -= (long) CYCLE * active;

        int progress = storage.cookProgress() + CYCLE;
        boolean smelted = false;
        while (progress >= cookTime) {
            progress -= cookTime;
            // The fuel burned above may have used up some of the inputs (logs as fuel and as charcoal)
            smelted |= finishBatch(storage, jobs(level, storage), active);
        }
        storage.setFuelTicks(heat);
        storage.setSmeltStatus(progress, active, cookTime);
        if (smelted) storage.markChanged();
    }

    /**
     * What is switched on and stored above its "leave" amount, in the order the
     * rows were switched on. Only plain items, and only the rules are walked,
     * not the whole chest.
     */
    private static List<Job> jobs(ServerLevel level, QuantumStorage storage) {
        List<Job> jobs = new ArrayList<>();
        for (CompactRule setting : storage.activeSmeltRules()) {
            Item item = BuiltInRegistries.ITEM.getValue(setting.item());
            ItemKey key = ItemKey.of(new ItemStack(item));
            long stored = storage.inventory().count(key);
            if (stored <= setting.keep()) continue;
            Rule rule = rule(level, item);
            if (rule != null) jobs.add(new Job(key, rule, stored - setting.keep()));
        }
        return jobs;
    }

    /** Turns up to {@code count} items into their results. */
    private static boolean finishBatch(QuantumStorage storage, List<Job> jobs, int count) {
        QuantumInventory inventory = storage.inventory();
        boolean done = false;
        for (Job job : jobs) {
            long left = job.available();
            while (count > 0 && left > 0) {
                // Smelting gives back as many items as it takes (or more, for mods), so check the room
                long extra = job.rule().count() - 1;
                if (extra > 0 && inventory.totalItems() + extra > storage.tier().capacity()) return done;
                if (inventory.remove(job.key(), 1) <= 0) break;
                inventory.insert(new ItemStack(job.rule().result()), job.rule().count(), Long.MAX_VALUE);
                count--;
                left--;
                done = true;
            }
            if (count <= 0) break;
        }
        return done;
    }

    /**
     * Burns one item of the first allowed fuel the chest has. Lava buckets
     * leave an empty bucket behind, as in a furnace. Returns the heat gained.
     */
    private static long burnOne(ServerLevel level, QuantumStorage storage) {
        QuantumInventory inventory = storage.inventory();
        for (ItemMatch fuel : storage.fuels()) {
            ItemKey key = fuel.tag().isEmpty() ? plainKey(inventory, fuel) : firstMatching(inventory, fuel);
            if (key == null) continue;
            // Something that is also on the melt list (logs into charcoal) still burns if chosen as fuel
            ItemStack template = key.template();
            int burn = Services.PLATFORM.burnTime(template, level);
            if (burn <= 0 || inventory.remove(key, 1) <= 0) continue;
            ItemStack remainder = Services.PLATFORM.craftingRemainder(template);
            if (!remainder.isEmpty()) inventory.insert(remainder, remainder.getCount(), Long.MAX_VALUE);
            return burn;
        }
        return 0;
    }

    /** The plain stored copy of a fuel picked by item, or null. A straight lookup, no matter how big the chest. */
    private static ItemKey plainKey(QuantumInventory inventory, ItemMatch fuel) {
        ItemKey key = ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.getValue(fuel.item())));
        return inventory.count(key) > 0 ? key : null;
    }

    /** The first plain stored item of a fuel picked by tag, or null. */
    private static ItemKey firstMatching(QuantumInventory inventory, ItemMatch fuel) {
        for (ItemKey key : inventory.view().keySet()) {
            ItemStack template = key.template();
            if (template.getComponentsPatch().isEmpty() && fuel.matches(template)) return key;
        }
        return null;
    }
}
