package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.QuantumConfig;
import com.manuelaraujo.quantumchest.automation.Refiller;
import com.manuelaraujo.quantumchest.block.ChestNetwork;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.menu.StorageHost;
import com.manuelaraujo.quantumchest.registry.ModDataComponents;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opens a chest from a distance. Sneak and use it on a chest to link it, then
 * use it anywhere. Reach depends on the chest: 64 blocks at Advanced, the
 * whole dimension at Elite and above, any dimension with a quantum tunnel.
 */
public class SuperpositionTerminalItem extends Item {
    public static final ChestTier MIN_TIER = ChestTier.ADVANCED;

    public SuperpositionTerminalItem(Properties properties) {
        super(properties);
    }

    /** Sneak + use on a chest: link to it. Without sneaking the chest just opens. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()
                || !(level.getBlockEntity(context.getClickedPos()) instanceof QuantumChestBlockEntity chest)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        QuantumStorage storage = chest.storage();
        if (!storage.canUse(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.locked", storage.ownerName()));
            return InteractionResult.FAIL;
        }
        if (!storage.tier().atLeast(MIN_TIER)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.terminal.min_tier",
                    Component.translatable(MIN_TIER.translationKey())));
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        stack.set(ModDataComponents.STORAGE_ID.get(), storage.id());
        stack.set(ModDataComponents.LINKED_TIER.get(), storage.tier());
        player.sendOverlayMessage(Component.translatable("message.quantumchest.terminal.linked",
                Component.translatable(QuantumChestItem.nameKey(storage.tier()))));
        level.playSound(null, chest.getBlockPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    /** Use: open the linked chest. Sneak + use away from a chest: unlink. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        UUID linked = stack.get(ModDataComponents.STORAGE_ID.get());
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.isSecondaryUseActive()) {
            if (linked != null) {
                stack.remove(ModDataComponents.STORAGE_ID.get());
                stack.remove(ModDataComponents.LINKED_TIER.get());
                player.sendOverlayMessage(Component.translatable("message.quantumchest.terminal.unlinked"));
            }
            return InteractionResult.SUCCESS;
        }
        if (linked == null) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.terminal.not_linked"));
            return InteractionResult.FAIL;
        }
        QuantumStorage storage = QuantumStorageData.get(level.getServer()).get(linked);
        if (storage == null) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.terminal.gone"));
            return InteractionResult.FAIL;
        }
        // Keep the item up to date if the chest was entangled or upgraded since
        if (!storage.id().equals(linked)) stack.set(ModDataComponents.STORAGE_ID.get(), storage.id());
        if (stack.get(ModDataComponents.LINKED_TIER.get()) != storage.tier()) {
            stack.set(ModDataComponents.LINKED_TIER.get(), storage.tier());
        }
        if (!storage.canUse(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.locked", storage.ownerName()));
            return InteractionResult.FAIL;
        }
        String problem = switch (ChestNetwork.reach(storage, player)) {
            case OK -> null;
            case TIER_TOO_LOW -> "message.quantumchest.terminal.min_tier";
            case NOT_PLACED -> "message.quantumchest.terminal.not_placed";
            case TOO_FAR -> "message.quantumchest.terminal.too_far";
            case OTHER_DIMENSION -> "message.quantumchest.terminal.other_dimension";
        };
        if (problem != null) {
            player.sendOverlayMessage(Component.translatable(problem,
                    problem.endsWith("min_tier") ? Component.translatable(MIN_TIER.translationKey()) : QuantumConfig.terminalRange()));
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            UUID id = storage.id();
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new QuantumChestMenu(containerId, inventory, new StorageHost.Remote(serverPlayer, id)),
                    Component.translatable(QuantumChestItem.nameKey(storage.tier()))));
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Carried around: keeps the player's items topped up or put away if the linked chest has the auto-refill upgrade. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
        if (!(owner instanceof ServerPlayer player) || (level.getGameTime() + player.getId()) % Refiller.INTERVAL != 0) return;
        UUID linked = stack.get(ModDataComponents.STORAGE_ID.get());
        if (linked == null) return;
        QuantumStorage storage = QuantumStorageData.get(level.getServer()).get(linked);
        if (storage != null) Refiller.tick(player, storage);
    }

    /** Whether the player still has a terminal linked to this storage, for closing the screen when it's dropped. */
    public static boolean carries(Player player, QuantumStorage storage) {
        QuantumStorageData data = QuantumStorageData.get(player.level().getServer());
        for (ItemStack stack : player.getInventory()) {
            if (stack.getItem() instanceof SuperpositionTerminalItem
                    && storage.id().equals(data.resolve(stack.get(ModDataComponents.STORAGE_ID.get())))) {
                return true;
            }
        }
        return false;
    }

    // Mojang marks this deprecated in favour of tooltip components, but still calls it
    // and it's the only way to show text that depends on the stack's own data
    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        ChestTier tier = stack.get(ModDataComponents.LINKED_TIER.get());
        if (tier == null) {
            builder.accept(Component.translatable("tooltip.quantumchest.terminal.unlinked",
                    Component.translatable(MIN_TIER.translationKey())).withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.quantumchest.terminal.linked",
                    Component.translatable(QuantumChestItem.nameKey(tier))).withStyle(ChatFormatting.AQUA));
            builder.accept(Component.translatable("tooltip.quantumchest.terminal.unlink").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
