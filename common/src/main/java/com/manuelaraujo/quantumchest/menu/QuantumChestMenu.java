package com.manuelaraujo.quantumchest.menu;

import com.manuelaraujo.quantumchest.automation.ChestAutomation;
import com.manuelaraujo.quantumchest.automation.CompactOption;
import com.manuelaraujo.quantumchest.automation.Compactor;
import com.manuelaraujo.quantumchest.automation.SmeltOption;
import com.manuelaraujo.quantumchest.automation.Smelter;
import com.manuelaraujo.quantumchest.automation.FaceMode;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import com.manuelaraujo.quantumchest.automation.RejectAction;
import com.manuelaraujo.quantumchest.block.ChestNetwork;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.item.ChestUpgrade;
import com.manuelaraujo.quantumchest.network.ChestSettingsPayload;
import com.manuelaraujo.quantumchest.network.MachinePayloads;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload;
import com.manuelaraujo.quantumchest.network.SyncItemsPayload.ItemAmount;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import com.manuelaraujo.quantumchest.network.UpgradeConfigPayloads;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.registry.ModMenus;
import com.manuelaraujo.quantumchest.storage.AccessMode;
import com.manuelaraujo.quantumchest.storage.CompactRule;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.FilterRules;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import com.manuelaraujo.quantumchest.storage.RefillRule;
import com.manuelaraujo.quantumchest.storage.QuantumInventory;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.VoidRule;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The chest screen. Only the player inventory uses real slots: the item grid
 * is drawn by the screen from a list the server keeps in sync. Clicks on the
 * grid travel as {@link com.manuelaraujo.quantumchest.network.ClickPayload}
 * and are resolved here, on the server.
 *
 * The same menu serves a placed chest and a remote terminal: see {@link StorageHost}.
 */
public class QuantumChestMenu extends AbstractContainerMenu {
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 140;
    public static final int UPGRADE_X = 200;
    public static final int UPGRADE_Y = 18;
    public static final int UPGRADE_SLOTS = ChestTier.QUANTUM.upgradeSlots();
    private static final int PLAYER_SLOTS = 36;
    // Crafting upgrade: where the 3x3 and the result sit, under a grid cut down to three rows
    public static final int CRAFT_X = 30;
    public static final int CRAFT_Y = 74;
    public static final int RESULT_X = 124;
    public static final int RESULT_Y = 92;
    public static final int CRAFT_FIRST = PLAYER_SLOTS + UPGRADE_SLOTS;
    public static final int RESULT_INDEX = CRAFT_FIRST + 9;
    /** Button id of "put the 3x3 back into the chest", sent with the vanilla menu button packet. */
    public static final int BUTTON_CLEAR_CRAFTING = 0;
    /** Shift on the result crafts at most this many times, so one click can't stall the server. */
    private static final int MAX_SHIFT_CRAFTS = 64 * 36;
    private static final int MAX_ENTRIES_PER_PACKET = 512;
    // Clients refuse packets over 1 MiB. Big item data (full shulker boxes, books) can add up fast
    private static final int MAX_BYTES_PER_PACKET = 512 * 1024;
    // The compactor and smelter lists go out in one packet each
    private static final int MAX_OPTIONS = 8192;

    // Server side: what is being viewed. Null on the client.
    private final StorageHost host;
    private final Player player;

    // Server side: what this player's client has been told so far
    private final Map<ItemKey, Long> sent = new HashMap<>();
    private int sentVersion = -1;
    private int sentStateVersion = -1;
    private int sentFaceVersion = -1;
    private int sentMachineVersion = -1;

    // Client side: the chest contents and settings as last received
    private final Map<ItemKey, Long> clientItems = new LinkedHashMap<>();
    private SyncStatePayload.Access clientAccess = SyncStatePayload.Access.NONE;
    private List<CustomTab> clientTabs = List.of();
    private SyncStatePayload.Faces clientFaces = SyncStatePayload.Faces.NONE;
    private SyncStatePayload.Config clientConfig = SyncStatePayload.Config.NONE;
    private SyncStatePayload.Smelting clientSmelting = SyncStatePayload.Smelting.NONE;
    private List<CompactOption> clientCompactOptions = null;
    private List<SmeltOption> clientSmeltOptions = null;

    // Crafting upgrade: a real 3x3 and its result. The items in it are real
    // items, so they go back to the chest when the screen closes.
    private final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    // Client side: the screen shows the 3x3 only while the crafting gear is open
    private boolean craftingVisible;
    // Server side: while a craft moves items around, the result is worked out once at the end
    private boolean craftingInProgress;
    private boolean craftingBatch;
    private int clientVersion;
    private int clientStateVersion;

    /** Client constructor, used by the MenuType when the server opens the screen. */
    public QuantumChestMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public QuantumChestMenu(int containerId, Inventory inventory, StorageHost host) {
        super(ModMenus.QUANTUM_CHEST.get(), containerId);
        this.host = host;
        this.player = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
        // Upgrade slots, in a column to the right of the panel. On the client a
        // plain container stands in; vanilla slot sync fills it from the server.
        Container upgrades = host != null ? host.storage().upgrades() : new SimpleContainer(UPGRADE_SLOTS);
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            addSlot(new UpgradeSlot(upgrades, i, UPGRADE_X, UPGRADE_Y + i * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new CraftSlot(craftSlots, col + row * 3, CRAFT_X + col * 18, CRAFT_Y + row * 18));
            }
        }
        addSlot(new CraftResultSlot(player, craftSlots, resultSlots, RESULT_X, RESULT_Y));
        // The chest's front comes alive while someone has it open
        if (host != null && host.chest() != null) host.chest().startOpen(player);
    }

    /** Tier of the storage on screen, on either side. */
    public ChestTier tier() {
        QuantumStorage storage = host != null ? host.storage() : null;
        return storage != null ? storage.tier() : clientTier();
    }

    /** How many upgrade slots the current tier unlocks. */
    public int activeUpgradeSlots() {
        return tier().upgradeSlots();
    }

    /** True if this screen shows that storage. Server side. */
    public boolean shows(UUID storageId) {
        QuantumStorage storage = host != null ? host.storage() : null;
        return storage != null && storage.id().equals(storageId);
    }

    private class UpgradeSlot extends Slot {
        UpgradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() < activeUpgradeSlots();
        }

        // One of each upgrade per chest: a second one would do nothing.
        // Only the owner (and operators) fit or take upgrades: in a public chest
        // or for a guest the slots are locked, or anyone could walk off with them
        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && manages() && stack.getItem() instanceof ChestUpgrade upgrade && tier().atLeast(upgrade.minTier())
                    && !alreadyInstalled(stack, this);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (!manages()) return false;
            QuantumStorage storage = host != null ? host.storage() : null;
            if (storage == null || storage.canRemoveUpgrade(getItem())) return true;
            player.sendOverlayMessage(Component.translatable("message.quantumchest.tunnel.in_use"));
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        // The I/O upgrade opens and closes every face, so pipes have to look again
        @Override
        public void setChanged() {
            super.setChanged();
            QuantumStorage storage = host != null ? host.storage() : null;
            if (storage != null && player instanceof ServerPlayer serverPlayer) {
                ChestNetwork.invalidateFaces(serverPlayer.level().getServer(), storage);
            }
        }
    }

    /** The player looking at this screen may change the upgrades. Works on both sides. */
    private boolean manages() {
        if (host == null) return clientAccess.canManage();
        QuantumStorage storage = host.storage();
        return storage != null && storage.canManage(player);
    }

    private boolean alreadyInstalled(ItemStack stack, Slot except) {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            Slot slot = slots.get(PLAYER_SLOTS + i);
            if (slot != except && slot.getItem().is(stack.getItem())) return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host == null) return true;
        return host.storage() != null && host.stillValid(player);
    }

    /**
     * Shift click. On the crafting result: craft as many as fit in the
     * inventory. From the 3x3: back into the chest. From an upgrade slot: back
     * to the inventory. From the inventory: upgrades go to a free upgrade
     * slot, everything else into the chest.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (host == null || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (index == RESULT_INDEX) {
            craftAll(player);
            return ItemStack.EMPTY;
        }
        if (index >= CRAFT_FIRST) {
            ItemStack taken = slot.remove(stack.getCount());
            returnToChest(player, taken);
            return ItemStack.EMPTY;
        }
        if (index >= PLAYER_SLOTS) {
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            moveItemStackTo(stack, 0, PLAYER_SLOTS, true);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (stack.getItem() instanceof ChestUpgrade
                && moveItemStackTo(stack, PLAYER_SLOTS, PLAYER_SLOTS + activeUpgradeSlots(), false)) {
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        QuantumStorage storage = host.storage();
        if (!storage.canHold(stack)) return ItemStack.EMPTY;
        long stored = storage.inventory().insert(stack, storage.tier().capacity());
        if (stored > 0) {
            stack.shrink((int) stored);
            storage.markChanged();
            slot.setChanged();
        }
        // Returning empty stops vanilla from calling us again in a loop
        return ItemStack.EMPTY;
    }

    // ---- crafting upgrade ----------------------------------------------------

    /** On both sides: the crafting upgrade is in an unlocked slot. */
    private boolean craftingInstalled() {
        if (host == null) return hasUpgrade(ModItems.CRAFTING_UPGRADE.get());
        QuantumStorage storage = host.storage();
        return storage != null && storage.hasUpgrade(ModItems.CRAFTING_UPGRADE.get());
    }

    /** Client side: the screen opened or closed the crafting area. Hidden slots can't be hovered or clicked. */
    public void setCraftingVisible(boolean visible) {
        craftingVisible = visible;
    }

    private boolean craftSlotsActive() {
        return host != null || craftingVisible;
    }

    public boolean craftGridEmpty() {
        return craftSlots.isEmpty();
    }

    private class CraftSlot extends Slot {
        CraftSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return craftSlotsActive();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return craftingInstalled();
        }
    }

    /**
     * The result of the 3x3. Taking it uses one of each ingredient like a
     * crafting table, and then puts the same item back from the chest, so the
     * grid stays ready for the next one. What a recipe leaves behind (an
     * empty bucket) goes into the chest.
     */
    private class CraftResultSlot extends ResultSlot {
        private final Player owner;

        CraftResultSlot(Player owner, TransientCraftingContainer grid, ResultContainer result, int x, int y) {
            super(owner, grid, result, 0, x, y);
            this.owner = owner;
        }

        @Override
        public boolean isActive() {
            return craftSlotsActive();
        }

        @Override
        public boolean mayPickup(Player player) {
            return craftingInstalled();
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            checkTakeAchievements(carried);
            if (!(player.level() instanceof ServerLevel level) || host == null || host.storage() == null) {
                // Client: leave the grid alone. The server refills it and sends it right
                // after, so guessing here would only make the numbers flicker
                return;
            }
            QuantumStorage storage = host.storage();
            craftingInProgress = true;
            try {
                useIngredients(level, storage);
            } finally {
                craftingInProgress = false;
            }
            updateCraftResult(!craftingBatch);
        }

        private void useIngredients(ServerLevel level, QuantumStorage storage) {
            CraftingInput.Positioned positioned = craftSlots.asPositionedCraftInput();
            CraftingInput input = positioned.input();
            NonNullList<ItemStack> remaining = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(recipe -> recipe.value().getRemainingItems(input))
                    .orElseGet(() -> NonNullList.withSize(input.size(), ItemStack.EMPTY));
            boolean changed = false;
            for (int y = 0; y < input.height(); y++) {
                for (int x = 0; x < input.width(); x++) {
                    int slot = x + positioned.left() + (y + positioned.top()) * craftSlots.getWidth();
                    ItemStack used = craftSlots.getItem(slot);
                    if (used.isEmpty()) continue;
                    ItemStack template = used.copyWithCount(1);
                    craftSlots.removeItem(slot, 1);
                    ItemStack leftover = remaining.get(x + y * input.width());
                    if (!leftover.isEmpty()) {
                        returnToChest(owner, leftover.copy());
                        changed = true;
                    }
                    // Same item back from the chest, so the next craft is ready
                    ItemStack refill = storage.inventory().extract(ItemKey.of(template), 1);
                    if (refill.isEmpty()) continue;
                    changed = true;
                    ItemStack now = craftSlots.getItem(slot);
                    if (now.isEmpty()) {
                        craftSlots.setItem(slot, refill);
                    } else if (ItemStack.isSameItemSameComponents(now, refill) && now.getCount() < now.getMaxStackSize()) {
                        now.grow(1);
                        craftSlots.setItem(slot, now);
                    } else {
                        storage.inventory().insert(refill, 1, Long.MAX_VALUE);
                    }
                }
            }
            if (changed) storage.markChanged();
        }
    }

    /** Server: works out what the 3x3 makes and shows it, like a crafting table. */
    @Override
    public void slotsChanged(Container container) {
        if (container == craftSlots && craftingInProgress) return;
        super.slotsChanged(container);
        if (container == craftSlots) updateCraftResult(true);
    }

    private void updateCraftResult(boolean send) {
        if (host == null || !(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) return;
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        if (craftingInstalled()) {
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (recipe.isPresent() && resultSlots.setRecipeUsed(serverPlayer, recipe.get())) {
                ItemStack made = recipe.get().value().assemble(input);
                if (made.isItemEnabled(level.enabledFeatures())) result = made;
            }
        }
        resultSlots.setItem(0, result);
        if (!send) return;
        setRemoteSlot(RESULT_INDEX, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT_INDEX, result));
    }

    /** Shift on the result: the same thing again and again, while the inventory has room and the chest has ingredients. */
    private void craftAll(Player player) {
        Slot result = slots.get(RESULT_INDEX);
        if (!result.hasItem() || !result.mayPickup(player)) return;
        Item target = result.getItem().getItem();
        craftingBatch = true;
        try {
            craftRepeatedly(player, result, target);
        } finally {
            craftingBatch = false;
        }
        updateCraftResult(true);
        broadcastChanges();
    }

    private void craftRepeatedly(Player player, Slot result, Item target) {
        for (int done = 0; done < MAX_SHIFT_CRAFTS; done++) {
            if (!result.hasItem() || !result.getItem().is(target)) break;
            ItemStack made = result.getItem().copy();
            if (!fitsInInventory(player, made)) break;
            ItemStack taken = result.remove(made.getCount());
            result.onTake(player, taken);
            moveItemStackTo(taken, 0, PLAYER_SLOTS, true);
            // Checked above, so this only happens if something changed in between
            if (!taken.isEmpty()) {
                player.drop(taken, false);
                break;
            }
        }
    }

    private boolean fitsInInventory(Player player, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            ItemStack in = slots.get(i).getItem();
            if (in.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(in, stack)) {
                room += Math.max(0, in.getMaxStackSize() - in.getCount());
            }
            if (room >= stack.getCount()) return true;
        }
        return false;
    }

    /** Into the chest if there is room, otherwise to the player, otherwise on the ground. */
    private void returnToChest(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        QuantumStorage storage = host != null ? host.storage() : null;
        if (storage != null && storage.canHold(stack)) {
            long stored = storage.inventory().insert(stack, stack.getCount(), storage.tier().capacity());
            stack.shrink((int) stored);
            if (stored > 0) storage.markChanged();
        }
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }

    private void emptyCraftGrid(Player player) {
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.removeItemNoUpdate(i);
            returnToChest(player, stack);
        }
        updateCraftResult(true);
    }

    /** The "Clear" button of the crafting area, through the vanilla menu button packet. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CLEAR_CRAFTING || host == null || !stillValid(player)) return false;
        emptyCraftGrid(player);
        broadcastChanges();
        return true;
    }

    /** Whatever is left in the 3x3 goes back to the chest. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (host != null && player instanceof ServerPlayer) emptyCraftGrid(player);
        if (host != null && host.chest() != null) host.chest().stopOpen(player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != resultSlots && super.canTakeItemForPickAll(carried, target);
    }

    // ---- server: clicks on the grid ----------------------------------------

    public void handleClick(ClickAction action, ItemStack clicked, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumStorage storage = host.storage();
        QuantumInventory inventory = storage.inventory();
        long capacity = storage.tier().capacity();
        ItemStack carried = getCarried();

        switch (action) {
            case TAKE_STACK -> {
                if (!carried.isEmpty() || clicked.isEmpty()) return;
                ItemKey key = ItemKey.of(clicked);
                setCarried(inventory.extract(key, key.maxStackSize()));
            }
            case TAKE_HALF -> {
                if (!carried.isEmpty() || clicked.isEmpty()) return;
                ItemKey key = ItemKey.of(clicked);
                long available = Math.min(inventory.count(key), key.maxStackSize());
                if (available <= 0) return;
                setCarried(inventory.extract(key, Math.max(1, (available + 1) / 2)));
            }
            case TAKE_TO_INVENTORY -> {
                if (clicked.isEmpty()) return;
                ItemKey key = ItemKey.of(clicked);
                ItemStack taken = inventory.extract(key, key.maxStackSize());
                if (taken.isEmpty()) return;
                moveItemStackTo(taken, 0, PLAYER_SLOTS, true);
                if (!taken.isEmpty()) {
                    // Inventory full: put the rest back, it came from here so it always fits
                    inventory.insert(taken, taken.getCount(), Long.MAX_VALUE);
                }
            }
            case INSERT_CARRIED -> {
                if (carried.isEmpty() || !storage.canHold(carried)) return;
                long stored = inventory.insert(carried, capacity);
                carried.shrink((int) stored);
                setCarried(carried);
            }
            case INSERT_ONE -> {
                if (carried.isEmpty() || !storage.canHold(carried)) return;
                long stored = inventory.insert(carried, 1, capacity);
                carried.shrink((int) stored);
                setCarried(carried);
            }
        }
        storage.markChanged();
        broadcastChanges();
    }

    // ---- server: keeping the client up to date -------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (host == null || !(player instanceof ServerPlayer serverPlayer)) return;
        QuantumStorage storage = host.storage();
        if (storage == null) return;
        syncState(storage, serverPlayer);
        syncItems(storage.inventory(), serverPlayer);
    }

    private void syncState(QuantumStorage storage, ServerPlayer serverPlayer) {
        QuantumChestBlockEntity chest = host.chest();
        int faceVersion = chest != null ? chest.faceVersion() : 0;
        if (storage.stateVersion() == sentStateVersion && faceVersion == sentFaceVersion
                && storage.machineVersion() == sentMachineVersion) return;
        sentStateVersion = storage.stateVersion();
        sentFaceVersion = faceVersion;
        sentMachineVersion = storage.machineVersion();
        SyncStatePayload.Access access = new SyncStatePayload.Access(
                storage.tier().ordinal(), storage.ownerName(), storage.isPublic(),
                storage.canManage(serverPlayer), storage.canEditTabs(serverPlayer), storage.memberNames(),
                storage.tier().capacity(), ChestAutomation.magnetRange(storage.tier()),
                ChestAutomation.Speed.of(storage.tier()).perSecond(), Smelter.furnaces(storage.tier()));
        SyncStatePayload.Faces faces = chest == null ? SyncStatePayload.Faces.NONE : new SyncStatePayload.Faces(
                storage.canManage(serverPlayer),
                chest.getBlockState().getValue(QuantumChestBlock.FACING).get3DDataValue(),
                chest.faces(),
                chest.rejectAction(),
                chest.magnetOn(),
                chest.magnetRange());
        SyncStatePayload.Config config = new SyncStatePayload.Config(storage.filter(), storage.voidRules(), storage.compactRules(),
                storage.refillRules(), storage.smeltRules(), storage.fuels());
        SyncStatePayload.Smelting smelting = new SyncStatePayload.Smelting(storage.cookProgress(), storage.smeltCookTime(),
                storage.smeltActive(), storage.fuelTicks());
        Services.NETWORK.sendToPlayer(serverPlayer, new SyncStatePayload(containerId, access, storage.tabs(), faces, config, smelting));
    }

    private void syncItems(QuantumInventory inventory, ServerPlayer serverPlayer) {
        boolean first = sentVersion == -1;
        if (!first && inventory.version() == sentVersion) return;

        List<ItemAmount> changes = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        var registries = serverPlayer.registryAccess();
        Map<ItemKey, Long> now = inventory.view();
        now.forEach((key, count) -> {
            Long before = sent.get(key);
            if (before == null || before.longValue() != count) {
                changes.add(new ItemAmount(key.template(), count));
                sizes.add(key.encodedSize(registries));
            }
        });
        for (ItemKey key : sent.keySet()) {
            if (!now.containsKey(key)) {
                changes.add(new ItemAmount(key.template(), 0));
                sizes.add(key.encodedSize(registries));
            }
        }
        sent.clear();
        sent.putAll(now);
        sentVersion = inventory.version();

        // Big chests go out in several packets so none of them gets too large:
        // at most 512 types each, and well under the size limit even with heavy item data
        int from = 0;
        do {
            int to = from;
            int bytes = 0;
            while (to < changes.size() && to - from < MAX_ENTRIES_PER_PACKET) {
                // The item plus its count
                int size = sizes.get(to) + 10;
                if (to > from && bytes + size > MAX_BYTES_PER_PACKET) break;
                bytes += size;
                to++;
            }
            Services.NETWORK.sendToPlayer(serverPlayer, new SyncItemsPayload(
                    containerId, first && from == 0, List.copyOf(changes.subList(from, to))));
            from = to;
        } while (from < changes.size());
    }

    // ---- server: settings and tabs -----------------------------------------

    public void handleSettings(ChestSettingsPayload.Action action, String value, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumStorage storage = host.storage();
        if (!storage.canManage(player)) return;

        switch (action) {
            case MAKE_PUBLIC -> storage.setAccessMode(AccessMode.PUBLIC);
            case MAKE_PRIVATE -> storage.setAccessMode(AccessMode.PRIVATE);
            case ADD_MEMBER -> {
                ServerPlayer online = player.level().getServer().getPlayerList().getPlayerByName(value.strip());
                String key = switch (storage.addMember(value, online)) {
                    case ADDED -> online != null ? "message.quantumchest.member.added" : "message.quantumchest.member.added_offline";
                    case INVALID_NAME -> "message.quantumchest.member.invalid";
                    case IS_OWNER -> "message.quantumchest.member.is_owner";
                    case ALREADY_MEMBER -> "message.quantumchest.member.already";
                    case FULL -> "message.quantumchest.member.full";
                };
                player.sendSystemMessage(Component.translatable(key, value.strip(), QuantumStorage.MAX_MEMBERS));
            }
            case REMOVE_MEMBER -> storage.removeMember(value.strip());
        }
        ChestNetwork.syncAccess(player.level().getServer(), storage);
        broadcastChanges();
    }

    /** One tab saved from the editor, or deleted when {@code tab} is null. */
    public void handleSetTab(int index, CustomTab tab, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumStorage storage = host.storage();
        if (!storage.canEditTabs(player)) return;
        if (tab == null) {
            storage.removeTab(index);
        } else {
            storage.setTab(index, tab);
        }
        broadcastChanges();
    }

    /** A face of the chest changes mode or export tab. Owner only, and the I/O upgrade has to be in. */
    public void handleFace(Direction side, FaceMode mode, String tab, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumChestBlockEntity chest = host.chest();
        QuantumStorage storage = host.storage();
        if (chest == null || !storage.canManage(player) || !storage.hasUpgrade(ModItems.IO_UPGRADE.get())) return;
        chest.setFace(side, mode, mode == FaceMode.EXPORT ? tab : "");
        broadcastChanges();
    }

    // ---- server: filter and upgrade settings ----------------------------------
    // The filter, destroyer and compactor belong to the network, so they can be
    // changed from a terminal too. The magnet and the faces need the chest itself.

    private QuantumStorage managed(ServerPlayer player) {
        if (host == null || !stillValid(player)) return null;
        QuantumStorage storage = host.storage();
        return storage != null && storage.canManage(player) ? storage : null;
    }

    public void handleFilter(FilterRules filter, ServerPlayer player) {
        QuantumStorage storage = managed(player);
        if (storage == null) return;
        storage.setFilter(filter);
        broadcastChanges();
    }

    public void handleVoidRules(List<VoidRule> rules, ServerPlayer player) {
        QuantumStorage storage = managed(player);
        if (storage == null) return;
        storage.setVoidRules(rules);
        broadcastChanges();
    }

    public void handleCompactRule(CompactRule rule, ServerPlayer player) {
        QuantumStorage storage = managed(player);
        if (storage == null) return;
        storage.setCompactRule(rule);
        broadcastChanges();
    }

    public void handleMagnet(boolean on, int range, ServerPlayer player) {
        QuantumStorage storage = managed(player);
        QuantumChestBlockEntity chest = host != null ? host.chest() : null;
        if (storage == null || chest == null) return;
        chest.setMagnet(on, range);
        broadcastChanges();
    }

    public void sendCompactOptions(ServerPlayer player) {
        if (host == null || !stillValid(player) || !(player.level() instanceof ServerLevel level)) return;
        Services.NETWORK.sendToPlayer(player, new UpgradeConfigPayloads.CompactOptions(containerId, capped(Compactor.options(level))));
    }

    // ---- server: crafting, auto-refill and smelter ------------------------------

    public void handleRefillRules(List<RefillRule> rules, ServerPlayer player) {
        QuantumStorage storage = managed(player);
        if (storage == null) return;
        storage.setRefillRules(rules);
        broadcastChanges();
    }

    public void handleSmeltRule(CompactRule rule, ServerPlayer player) {
        QuantumStorage storage = managed(player);
        if (storage == null) return;
        storage.setSmeltRule(rule);
        broadcastChanges();
    }

    public void handleFuels(List<ItemMatch> fuels, ServerPlayer player) {
        QuantumStorage storage = managed(player);
        if (storage == null || !(player.level() instanceof ServerLevel level)) return;
        // Only things that actually burn
        storage.setFuels(fuels.stream().filter(f -> f.isValid() && Services.PLATFORM.burnTime(f.icon(), level) > 0).toList());
        broadcastChanges();
    }

    public void sendSmeltOptions(ServerPlayer player) {
        if (host == null || !stillValid(player) || !(player.level() instanceof ServerLevel level)) return;
        Services.NETWORK.sendToPlayer(player, new MachinePayloads.SmeltOptions(containerId, capped(Smelter.options(level))));
    }

    /** Huge modpacks could list more than one packet takes. The first ones are vanilla's, the rest by mod. */
    private static <T> List<T> capped(List<T> options) {
        return options.size() <= MAX_OPTIONS ? options : options.subList(0, MAX_OPTIONS);
    }

    /** What the chest does with turned away items that can't leave. Same rules as the faces. */
    public void handleReject(RejectAction action, ServerPlayer player) {
        if (host == null || !stillValid(player)) return;
        QuantumChestBlockEntity chest = host.chest();
        QuantumStorage storage = host.storage();
        if (chest == null || !storage.canManage(player) || !storage.hasUpgrade(ModItems.IO_UPGRADE.get())) return;
        chest.setRejectAction(action);
        broadcastChanges();
    }

    // ---- client ------------------------------------------------------------

    public void applySync(SyncItemsPayload payload) {
        if (payload.reset()) clientItems.clear();
        for (ItemAmount entry : payload.entries()) {
            ItemKey key = ItemKey.of(entry.stack());
            if (entry.count() <= 0) {
                clientItems.remove(key);
            } else {
                clientItems.put(key, entry.count());
            }
        }
        clientVersion++;
    }

    public void applyState(SyncStatePayload payload) {
        clientAccess = payload.access();
        clientTabs = List.copyOf(payload.tabs());
        clientFaces = payload.faces();
        clientConfig = payload.config();
        clientSmelting = payload.smelting();
        clientStateVersion++;
        clientVersion++;
    }

    public Map<ItemKey, Long> clientItems() {
        return Collections.unmodifiableMap(clientItems);
    }

    public SyncStatePayload.Access clientAccess() {
        return clientAccess;
    }

    public ChestTier clientTier() {
        ChestTier[] tiers = ChestTier.values();
        return tiers[Math.max(0, Math.min(clientAccess.tier(), tiers.length - 1))];
    }

    public List<CustomTab> clientTabs() {
        return clientTabs;
    }

    public SyncStatePayload.Faces clientFaces() {
        return clientFaces;
    }

    public SyncStatePayload.Config clientConfig() {
        return clientConfig;
    }

    public SyncStatePayload.Smelting clientSmelting() {
        return clientSmelting;
    }

    public void applyCompactOptions(List<CompactOption> options) {
        clientCompactOptions = List.copyOf(options);
        clientStateVersion++;
    }

    /** Null until the server answered. */
    public List<CompactOption> clientCompactOptions() {
        return clientCompactOptions;
    }

    public void applySmeltOptions(List<SmeltOption> options) {
        clientSmeltOptions = List.copyOf(options);
    }

    /** Null until the server answered. */
    public List<SmeltOption> clientSmeltOptions() {
        return clientSmeltOptions;
    }

    /** True if an unlocked upgrade slot holds this item. Works on the client, where slots are synced. */
    public boolean hasUpgrade(net.minecraft.world.item.Item item) {
        for (int i = 0; i < activeUpgradeSlots(); i++) {
            if (slots.get(PLAYER_SLOTS + i).getItem().is(item)) return true;
        }
        return false;
    }

    /** True if an upgrade slot holds the I/O upgrade. Works on the client, where slots are synced. */
    public boolean hasIoUpgrade() {
        for (int i = 0; i < activeUpgradeSlots(); i++) {
            if (slots.get(PLAYER_SLOTS + i).getItem().is(ModItems.IO_UPGRADE.get())) return true;
        }
        return false;
    }

    /** Goes up every time new data arrives, so the screen knows when to rebuild its list. */
    public int clientVersion() {
        return clientVersion;
    }

    public int clientStateVersion() {
        return clientStateVersion;
    }

    // Drawn every frame, so it's only added up again when the contents change
    private long clientTotal;
    private int clientTotalVersion = -1;

    public long clientTotal() {
        if (clientTotalVersion != clientVersion) {
            long total = 0;
            for (long count : clientItems.values()) total += count;
            clientTotal = total;
            clientTotalVersion = clientVersion;
        }
        return clientTotal;
    }
}
