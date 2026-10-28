package com.manuelaraujo.quantumchest.automation;

import com.manuelaraujo.quantumchest.block.ChestNetwork;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.RefillRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Auto-refill: a player carrying a terminal linked to a chest with this
 * upgrade keeps the chosen amount of each item on them, twice a second, as
 * long as the terminal could open the chest from where they stand. Each rule
 * works one way or both: hand out what's missing (torches while you place
 * them), put away what's above the amount (dirt while you dig), or both.
 * Renamed or enchanted items are never handed out or taken.
 */
public final class Refiller {
    // Player and storage of the last refill, so two terminals linked to the same chest don't double up
    private static final Map<UUID, Long> LAST_RUN = new HashMap<>();

    private Refiller() {
    }

    /** Ticks between two passes. Half a second: quick enough that a torch comes back before you need the next one. */
    public static final int INTERVAL = 10;

    /** Called from the terminal while it sits in a player inventory. Does the work at most every {@link #INTERVAL} ticks. */
    public static void tick(ServerPlayer player, QuantumStorage storage) {
        long time = player.level().getGameTime();
        UUID key = new UUID(player.getUUID().getMostSignificantBits() ^ storage.id().getMostSignificantBits(),
                player.getUUID().getLeastSignificantBits() ^ storage.id().getLeastSignificantBits());
        Long last = LAST_RUN.get(key);
        if (last != null && time >= last && time - last < INTERVAL) return;
        LAST_RUN.put(key, time);
        if (LAST_RUN.size() > 4096) LAST_RUN.clear();

        if (player.isSpectator() || storage.refillRules().isEmpty()) return;
        if (!storage.hasUpgrade(ModItems.REFILL_UPGRADE.get()) || !storage.canUse(player)) return;
        if (ChestNetwork.reach(storage, player) != ChestNetwork.Reach.OK) return;

        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (RefillRule rule : storage.refillRules()) {
            long have = 0;
            for (int slot : slots(inventory)) {
                ItemStack stack = inventory.getItem(slot);
                if (rule.match().matches(stack)) have += stack.getCount();
            }
            if (rule.mode().gives() && have < rule.amount()) {
                changed |= give(player, storage, rule, rule.amount() - have);
            } else if (rule.mode().takes() && have > rule.amount()) {
                changed |= take(player, storage, rule, have - rule.amount());
            }
        }
        if (changed) {
            inventory.setChanged();
            storage.markChanged();
        }
    }

    /**
     * The player's slots, in the order items are put away: the main inventory
     * from the bottom up, then the hotbar, the slot in hand and the off hand
     * last, so the stack you're placing from is the one that stays.
     */
    private static int[] slots(Inventory inventory) {
        int selected = inventory.getSelectedSlot();
        int[] order = new int[Inventory.INVENTORY_SIZE + 1];
        int n = 0;
        for (int i = Inventory.INVENTORY_SIZE - 1; i >= Inventory.SELECTION_SIZE; i--) order[n++] = i;
        for (int i = Inventory.SELECTION_SIZE - 1; i >= 0; i--) {
            if (i != selected) order[n++] = i;
        }
        order[n++] = selected;
        order[n] = Inventory.SLOT_OFFHAND;
        return order;
    }

    /** Puts up to {@code extra} matching items from the player into the chest, as far as its capacity allows. */
    private static boolean take(ServerPlayer player, QuantumStorage storage, RefillRule rule, long extra) {
        Inventory inventory = player.getInventory();
        long capacity = storage.tier().capacity();
        boolean took = false;
        for (int slot : slots(inventory)) {
            if (extra <= 0) break;
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty() || !rule.match().matches(stack)
                    || !storage.canHold(stack)) continue;
            long stored = storage.inventory().insert(stack, Math.min(extra, stack.getCount()), capacity);
            // The chest is full: what's left stays with the player
            if (stored <= 0) break;
            stack.shrink((int) stored);
            extra -= stored;
            took = true;
        }
        return took;
    }

    /**
     * Hands out up to {@code missing} matching items. For a tag rule it
     * prefers the kind the player already carries, so the torches in the
     * hotbar stay the same torches.
     */
    private static boolean give(ServerPlayer player, QuantumStorage storage, RefillRule rule, long missing) {
        QuantumInventory inventory = storage.inventory();
        // Never hand out renamed or enchanted copies: those were put away on purpose.
        // A rule for one item is a single lookup; only tags need to look through the chest
        List<ItemKey> candidates = new ArrayList<>();
        if (rule.match().tag().isEmpty()) {
            ItemKey plain = ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.getValue(rule.match().item())));
            if (inventory.count(plain) > 0) candidates.add(plain);
        } else {
            for (ItemKey key : inventory.view().keySet()) {
                ItemStack template = key.template();
                if (template.getComponentsPatch().isEmpty() && rule.match().matches(template)) candidates.add(key);
            }
        }
        if (candidates.isEmpty()) return false;
        candidates.sort((a, b) -> Boolean.compare(carries(player, b), carries(player, a)));

        boolean gave = false;
        for (ItemKey key : candidates) {
            while (missing > 0) {
                ItemStack taken = inventory.extract(key, missing);
                if (taken.isEmpty()) break;
                int count = taken.getCount();
                player.getInventory().add(taken);
                // Inventory full: what didn't fit goes back, it came from here so it always fits
                if (!taken.isEmpty()) {
                    inventory.insert(taken, taken.getCount(), Long.MAX_VALUE);
                    return gave || taken.getCount() < count;
                }
                missing -= count;
                gave = true;
            }
            if (missing <= 0) break;
        }
        return gave;
    }

    private static boolean carries(ServerPlayer player, ItemKey key) {
        for (ItemStack stack : player.getInventory()) {
            if (ItemStack.isSameItemSameComponents(stack, key.template())) return true;
        }
        return false;
    }
}
