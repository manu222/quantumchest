package com.manuelaraujo.quantumchest.block;

import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * Temporary controls until the real screen exists:
 * - right click holding something: stores the whole stack
 * - right click with an empty hand: shows what is inside
 * - sneak + right click with an empty hand: takes one stack out
 */
public class QuantumChestBlock extends BaseEntityBlock {
    public static final MapCodec<QuantumChestBlock> CODEC = simpleCodec(QuantumChestBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ChestTier> TIER = EnumProperty.create("tier", ChestTier.class);

    public QuantumChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TIER, ChestTier.BASIC));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumChestBlockEntity(pos, state);
    }

    // ---- ownership -------------------------------------------------------

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) return;
        if (level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest) {
            chest.claim(player);
        }
    }

    /**
     * Called by the loaders' block break events. Only the owner and server
     * operators may break a chest; on the client we always say yes and let
     * the server decide.
     */
    public static boolean canBreak(LevelAccessor level, BlockPos pos, Player player) {
        if (level.isClientSide() || player == null) return true;
        if (!(level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest)) return true;
        QuantumStorage storage = chest.existingStorage();
        boolean allowed = storage == null || storage.canManage(player);
        if (!allowed) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.locked", storage.ownerName()));
        }
        return allowed;
    }

    /**
     * Mining speed 0 for anyone who may not break the chest. This has to hold on
     * the client too: if the client thinks it finished mining, the server keeps
     * the break pending and completes it as soon as the permission changes.
     * The client only knows the owner, not who is an operator, so operators
     * can only break other players' chests in creative.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest) {
            boolean allowed;
            if (player.level().isClientSide()) {
                UUID owner = chest.cachedOwner();
                allowed = owner == null || owner.equals(player.getUUID());
            } else {
                QuantumStorage storage = chest.existingStorage();
                allowed = storage == null || storage.canManage(player);
            }
            if (!allowed) return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * Blocks broken in creative don't run their loot table, so a full chest
     * would vanish. Shulker boxes have the same problem and solve it like this.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()
                && level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest) {
            QuantumStorage storage = chest.existingStorage();
            if (storage != null && !storage.inventory().isEmpty()) {
                ItemStack drop = new ItemStack(this);
                drop.applyComponents(chest.collectComponents());
                drop.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(TIER, state.getValue(TIER)));
                ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ---- temporary controls ------------------------------------------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest)) return InteractionResult.PASS;
        if (!checkAccess(chest, player)) return InteractionResult.SUCCESS;

        Component name = stack.getHoverName();
        long stored = chest.insert(stack);
        if (stored > 0) {
            stack.shrink((int) stored);
            player.sendOverlayMessage(Component.translatable("message.quantumchest.stored", stored, name));
        } else {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.full"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest)) return InteractionResult.PASS;
        if (!checkAccess(chest, player)) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            ItemStack taken = chest.extractFirst();
            if (taken.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.quantumchest.empty"));
            } else if (!player.getInventory().add(taken)) {
                Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), taken);
            }
        } else {
            QuantumStorage storage = chest.storage();
            ChestTier tier = state.getValue(TIER);
            Component capacity = tier.isUnlimited()
                    ? Component.translatable("message.quantumchest.unlimited")
                    : Component.literal(String.valueOf(tier.capacity()));
            player.sendOverlayMessage(Component.translatable("message.quantumchest.summary",
                    Component.translatable(tier.translationKey()),
                    storage.inventory().totalItems(), capacity, storage.inventory().typeCount(),
                    storage.ownerName()));
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean checkAccess(QuantumChestBlockEntity chest, Player player) {
        QuantumStorage storage = chest.storage();
        if (storage.canUse(player)) return true;
        player.sendOverlayMessage(Component.translatable("message.quantumchest.locked", storage.ownerName()));
        return false;
    }
}
