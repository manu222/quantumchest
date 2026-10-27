package com.manuelaraujo.quantumchest.item;

import com.manuelaraujo.quantumchest.block.ChestNetwork;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.registry.ModDataComponents;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Links chests of the same tier so they share one storage.
 *
 * <ul>
 *   <li>Use on a chest: the entangler holds it (the pearls light up).</li>
 *   <li>Use on a second chest of the same tier: the second one is poured into
 *       the first and from then on both show the same contents. The first
 *       chest's owner, access and members are the ones that stay.</li>
 *   <li>Sneak and use on an entangled chest: splits it off. The contents stay
 *       with the rest of the network and the chest starts empty.</li>
 *   <li>Sneak and use anywhere else: forget the held chest.</li>
 * </ul>
 */
public class QuantumEntanglerItem extends Item {
    public static final ChestTier MIN_TIER = ChestTier.REINFORCED;

    public QuantumEntanglerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof QuantumChestBlockEntity chest)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        QuantumStorage storage = chest.storage();
        if (!storage.canManage(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.not_owner", storage.ownerName()));
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isSecondaryUseActive()) return split(level, player, chest, storage);
        StoredChest stored = stack.get(ModDataComponents.STORED_CHEST.get());
        if (stored == null) return hold(level, player, stack, chest, storage);
        return entangle(level, player, stack, stored, chest, storage);
    }

    private InteractionResult hold(Level level, Player player, ItemStack stack, QuantumChestBlockEntity chest,
                                   QuantumStorage storage) {
        if (!storage.tier().atLeast(MIN_TIER)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.min_tier",
                    Component.translatable(MIN_TIER.translationKey())));
            return InteractionResult.FAIL;
        }
        stack.set(ModDataComponents.STORED_CHEST.get(), new StoredChest(storage.id(), storage.tier(), chest.anchor()));
        player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.held",
                Component.translatable(QuantumChestItem.nameKey(storage.tier()))));
        playSound(level, chest.getBlockPos(), SoundEvents.AMETHYST_BLOCK_CHIME);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult entangle(Level level, Player player, ItemStack stack, StoredChest stored,
                                       QuantumChestBlockEntity chest, QuantumStorage second) {
        MinecraftServer server = level.getServer();
        QuantumStorageData data = QuantumStorageData.get(server);
        QuantumStorage first = data.get(stored.storage());
        if (first == null) {
            stack.remove(ModDataComponents.STORED_CHEST.get());
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.gone"));
            return InteractionResult.FAIL;
        }
        if (first == second) {
            boolean sameChest = stored.pos().equals(chest.anchor());
            player.sendOverlayMessage(Component.translatable(sameChest
                    ? "message.quantumchest.entangler.same_chest"
                    : "message.quantumchest.entangler.already"));
            return InteractionResult.FAIL;
        }
        if (!first.canManage(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.not_owner", first.ownerName()));
            return InteractionResult.FAIL;
        }
        if (first.tier() != second.tier()) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.wrong_tier",
                    Component.translatable(first.tier().translationKey()),
                    Component.translatable(second.tier().translationKey())));
            return InteractionResult.FAIL;
        }
        if (needsTunnel(first, second) && !first.hasTunnel() && !second.hasTunnel()) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.dimension"));
            return InteractionResult.FAIL;
        }
        switch (first.checkAbsorb(second)) {
            case NO_ROOM -> {
                player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.no_room",
                        first.inventory().totalItems() + second.inventory().totalItems(), first.tier().capacity()));
                return InteractionResult.FAIL;
            }
            case NO_UPGRADE_ROOM -> {
                player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.no_upgrade_room"));
                return InteractionResult.FAIL;
            }
            case OK -> {
            }
        }

        // Anyone looking at the second storage would be holding a stale copy of it
        ChestNetwork.closeScreens(server, second.id());
        List<ItemStack> spare = data.merge(first, second);
        ChestNetwork.forEachLoadedChest(server, first, c -> c.relink(first));
        stack.remove(ModDataComponents.STORED_CHEST.get());
        // A second tunnel (or any repeated upgrade) would only waste a slot: it goes back to the player
        for (ItemStack upgrade : spare) {
            if (!player.getInventory().add(upgrade)) player.drop(upgrade, false);
        }
        if (!spare.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.quantumchest.entangler.returned", spare.size()));
        }
        player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.linked"));
        playSound(level, chest.getBlockPos(), SoundEvents.RESPAWN_ANCHOR_CHARGE);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult split(Level level, Player player, QuantumChestBlockEntity chest, QuantumStorage network) {
        MinecraftServer server = level.getServer();
        ChestNetwork.prune(server, network);
        if (network.anchors().size() < 2) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.not_linked"));
            return InteractionResult.FAIL;
        }
        ChestNetwork.closeScreens(server, network.id());
        QuantumStorage fresh = QuantumStorageData.get(server).split(network, chest.anchor());
        chest.relink(fresh);
        player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.split"));
        playSound(level, chest.getBlockPos(), SoundEvents.ENDER_EYE_DEATH);
        return InteractionResult.SUCCESS;
    }

    /** True if the joined network would have chests in more than one dimension. */
    private static boolean needsTunnel(QuantumStorage a, QuantumStorage b) {
        Set<ResourceKey<Level>> dimensions = new HashSet<>();
        for (GlobalPos pos : a.anchors()) dimensions.add(pos.dimension());
        for (GlobalPos pos : b.anchors()) dimensions.add(pos.dimension());
        return dimensions.size() > 1;
    }

    /** Sneak and use away from a chest: let go of the held chest. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !stack.has(ModDataComponents.STORED_CHEST.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            stack.remove(ModDataComponents.STORED_CHEST.get());
            player.sendOverlayMessage(Component.translatable("message.quantumchest.entangler.cleared"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        StoredChest stored = stack.get(ModDataComponents.STORED_CHEST.get());
        if (stored == null) {
            builder.accept(Component.translatable("tooltip.quantumchest.entangler.empty",
                    Component.translatable(MIN_TIER.translationKey())).withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos pos = stored.pos().pos();
            builder.accept(Component.translatable("tooltip.quantumchest.entangler.held",
                    Component.translatable(QuantumChestItem.nameKey(stored.tier())),
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.LIGHT_PURPLE));
            builder.accept(Component.translatable("tooltip.quantumchest.entangler.next").withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.quantumchest.entangler.clear").withStyle(ChatFormatting.DARK_GRAY));
        }
        builder.accept(Component.translatable("tooltip.quantumchest.entangler.split").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
