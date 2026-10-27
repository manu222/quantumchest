package com.manuelaraujo.quantumchest.automation;

import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns items into their block form: 9 iron ingots into an iron block, 9
 * nuggets into an ingot. Only recipes that can be undone count (the block
 * crafts back into exactly the 9 or 4 it came from), so nothing ever ends up
 * as something you can't get back. Everything comes from the game's recipes,
 * so ores, ingots and gems of other mods work without any setup.
 */
public final class Compactor {
    private record Rule(Item result, int ratio) {
    }

    private static final Map<Item, Optional<Rule>> RULES = new HashMap<>();
    private static List<CompactOption> options;
    // Recipes change with /reload; a new manager means a fresh cache
    private static RecipeManager cachedFor;

    private Compactor() {
    }

    private static void checkCache(ServerLevel level) {
        RecipeManager recipes = level.recipeAccess();
        if (recipes != cachedFor) {
            RULES.clear();
            options = null;
            cachedFor = recipes;
        }
    }

    /** One pass over the storage, for the items the player switched on. */
    public static void run(ServerLevel level, QuantumStorage storage) {
        if (storage.compactRules().isEmpty()) return;
        checkCache(level);
        QuantumInventory inventory = storage.inventory();
        boolean changed = false;
        for (Map.Entry<ItemKey, Long> entry : new ArrayList<>(inventory.view().entrySet())) {
            ItemStack template = entry.getKey().template();
            // Only plain items: a renamed or enchanted copy must stay as it is
            if (!template.getComponentsPatch().isEmpty()) continue;
            long keep = storage.compactKeep(template.getItem());
            if (keep < 0) continue;
            Rule rule = rule(level, template.getItem());
            if (rule == null) continue;
            long blocks = (entry.getValue() - keep) / rule.ratio();
            if (blocks <= 0) continue;
            inventory.remove(entry.getKey(), blocks * rule.ratio());
            // The total only goes down, so this always fits
            inventory.insert(new ItemStack(rule.result()), blocks, Long.MAX_VALUE);
            changed = true;
        }
        if (changed) storage.markChanged();
    }

    /**
     * Everything that can be compacted in this world, for the compactor's
     * screen. Built once from the crafting recipes and kept until they reload.
     */
    public static List<CompactOption> options(ServerLevel level) {
        checkCache(level);
        if (options != null) return options;
        // Candidates: crafting recipes whose 4 or 9 ingredients are all the same
        Set<Item> candidates = new LinkedHashSet<>();
        for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
            if (holder.value().getType() != RecipeType.CRAFTING) continue;
            List<Ingredient> ingredients = holder.value().placementInfo().ingredients();
            if (ingredients.size() != 4 && ingredients.size() != 9) continue;
            Ingredient first = ingredients.getFirst();
            if (!ingredients.stream().allMatch(first::equals)) continue;
            first.items().limit(16).map(Holder::value).forEach(candidates::add);
        }
        List<CompactOption> found = new ArrayList<>();
        for (Item item : candidates) {
            Rule rule = rule(level, item);
            if (rule != null) {
                found.add(new CompactOption(BuiltInRegistries.ITEM.getKey(item), BuiltInRegistries.ITEM.getKey(rule.result()), rule.ratio()));
            }
        }
        found.sort(Comparator.comparing((CompactOption o) -> o.input().getNamespace().equals("minecraft") ? 0 : 1)
                .thenComparing(o -> o.input().toString()));
        options = List.copyOf(found);
        return options;
    }

    private static Rule rule(ServerLevel level, Item item) {
        return RULES.computeIfAbsent(item, i -> find(level, i)).orElse(null);
    }

    private static Optional<Rule> find(ServerLevel level, Item item) {
        for (int size : new int[]{3, 2}) {
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < size * size; i++) grid.add(new ItemStack(item));
            CraftingInput input = CraftingInput.of(size, size, grid);
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (recipe.isEmpty()) continue;
            ItemStack result = recipe.get().value().assemble(input);
            if (result.isEmpty() || result.getCount() != 1 || result.is(item) || !result.getComponentsPatch().isEmpty()) continue;

            CraftingInput back = CraftingInput.of(1, 1, List.of(result.copyWithCount(1)));
            Optional<RecipeHolder<CraftingRecipe>> undo = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, back, level);
            if (undo.isEmpty()) continue;
            ItemStack original = undo.get().value().assemble(back);
            if (original.is(item) && original.getCount() == size * size && original.getComponentsPatch().isEmpty()) {
                return Optional.of(new Rule(result.getItem(), size * size));
            }
        }
        return Optional.empty();
    }
}
