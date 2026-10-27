package com.manuelaraujo.quantumchest.automation;

import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.TabRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns items of the compact tabs into their block form: 9 iron ingots into
 * an iron block, 9 nuggets into an ingot. Only recipes that can be undone
 * count (a block crafts back into exactly the 9 or 4 it came from), so
 * nothing is ever compacted into something you can't get back from. It reads
 * the game's recipes, so items of other mods work too.
 */
public final class Compactor {
    private record Rule(Item result, int ratio) {
    }

    private static final Map<Item, Optional<Rule>> RULES = new HashMap<>();
    // Recipes change with /reload; a new manager means a fresh cache
    private static RecipeManager cachedFor;

    private Compactor() {
    }

    public static void run(ServerLevel level, QuantumStorage storage) {
        List<CustomTab> tabs = storage.tabs().stream().filter(tab -> tab.role() == TabRole.COMPACT).toList();
        if (tabs.isEmpty()) return;
        RecipeManager recipes = level.recipeAccess();
        if (recipes != cachedFor) {
            RULES.clear();
            cachedFor = recipes;
        }

        QuantumInventory inventory = storage.inventory();
        boolean changed = false;
        for (Map.Entry<ItemKey, Long> entry : new ArrayList<>(inventory.view().entrySet())) {
            ItemStack template = entry.getKey().template();
            // Only plain items: a renamed or enchanted copy must stay as it is
            if (!template.getComponentsPatch().isEmpty()) continue;
            long keep = keepFor(tabs, template);
            if (keep < 0) continue;
            Rule rule = RULES.computeIfAbsent(template.getItem(), item -> find(level, item)).orElse(null);
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

    /** How many to leave loose, from the first compact tab the item is in, or -1 if none. */
    private static long keepFor(List<CustomTab> tabs, ItemStack stack) {
        for (CustomTab tab : tabs) {
            if (tab.matches(stack)) return tab.limit();
        }
        return -1;
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
