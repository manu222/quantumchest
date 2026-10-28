package com.manuelaraujo.quantumchest.block;

import com.manuelaraujo.quantumchest.block.entity.QuantumChestBlockEntity;
import com.manuelaraujo.quantumchest.client.ChestEffects;
import com.manuelaraujo.quantumchest.entity.ChestCollapseEntity;
import com.manuelaraujo.quantumchest.item.QuantumEntanglerItem;
import com.manuelaraujo.quantumchest.item.TierUpgradeItem;
import com.manuelaraujo.quantumchest.registry.ModBlockEntities;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;


/**
 * The chest block. Right click opens the storage screen; the items live in
 * the world storage the block entity points to.
 */
public class QuantumChestBlock extends BaseEntityBlock {
    public static final MapCodec<QuantumChestBlock> CODEC = simpleCodec(QuantumChestBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ChestTier> TIER = EnumProperty.create("tier", ChestTier.class);
    /** Someone has the chest screen open: the front switches to its livelier animation. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public QuantumChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TIER, ChestTier.BASIC)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER, OPEN);
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

    // Server: automation, see QuantumChestBlockEntity#serverTick. Client: particles, see ChestEffects
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return createTickerHelper(type, ModBlockEntities.QUANTUM_CHEST.get(), ChestEffects::clientTick);
        return createTickerHelper(type, ModBlockEntities.QUANTUM_CHEST.get(), QuantumChestBlockEntity::serverTick);
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
     * Called by the loaders' block break events. Whoever may use the chest may
     * also break it (the contents travel inside the dropped item). On the
     * client we always say yes and let the server decide.
     */
    public static boolean canBreak(LevelAccessor level, BlockPos pos, Player player) {
        if (level.isClientSide() || player == null) return true;
        if (!(level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest)) return true;
        QuantumStorage storage = chest.existingStorage();
        boolean allowed = storage == null || storage.canBreak(player);
        if (!allowed) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.locked", storage.ownerName()));
        }
        return allowed;
    }

    /**
     * Mining speed 0 for anyone who may not break the chest. This has to hold on
     * the client too: if the client thinks it finished mining, the server keeps
     * the break pending and completes it as soon as the permission changes.
     * The client knows the owner, the mode and the members, but not who is an
     * operator, so operators can only break other players' private chests in creative.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest) {
            boolean allowed;
            if (player.level().isClientSide()) {
                allowed = chest.clientCanUse(player);
            } else {
                QuantumStorage storage = chest.existingStorage();
                allowed = storage == null || storage.canBreak(player);
            }
            if (!allowed) return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * Blocks broken in creative don't run their loot table, so a full chest
     * would vanish. Shulker boxes have the same problem and solve it like this.
     * Either way the chest collapses into itself first (see ChestCollapseEntity).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && player.preventsBlockDrops()
                && level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest) {
            QuantumStorage storage = chest.existingStorage();
            List<ItemStack> drops = new ArrayList<>();
            // Like a shulker box, an empty chest just goes; one with items or upgrades drops
            if (storage != null && storage.hasContents()) {
                ItemStack drop = new ItemStack(this);
                drop.applyComponents(chest.collectComponents());
                drop.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(TIER, state.getValue(TIER)));
                drops.add(drop);
            }
            ChestCollapseEntity.spawn(server, pos, state, drops);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Survival: the same drops as the loot table, but handed to the collapse
     * animation, which lets them go when the black hole closes.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity,
                              ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005f);
        if (level instanceof ServerLevel server) {
            ChestCollapseEntity.spawn(server, pos, state, getDrops(state, server, pos, blockEntity, player, tool));
        }
    }

    // ---- opening the chest -------------------------------------------------

    /**
     * Right click with an upgrade kit raises the tier of the chest and of every
     * chest entangled with it. The entangler does its own thing (see
     * QuantumEntanglerItem). With anything else in hand it opens the chest,
     * same as an empty hand.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof QuantumEntanglerItem) return InteractionResult.PASS;
        if (!(stack.getItem() instanceof TierUpgradeItem kit)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest)) return InteractionResult.PASS;

        QuantumStorage storage = chest.storage();
        if (!storage.canManage(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.upgrade.not_owner", storage.ownerName()));
            return InteractionResult.FAIL;
        }
        ChestTier current = storage.tier();
        if (current != kit.required()) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.upgrade.wrong_tier",
                    Component.translatable(kit.required().translationKey()),
                    Component.translatable(current.translationKey())));
            return InteractionResult.FAIL;
        }

        // Same block, so the block entities and everything they point to stay.
        // Chests in unloaded chunks follow when they load.
        storage.setTier(kit.target());
        ChestNetwork.matchTier(level.getServer(), storage);
        chest.storage();
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("message.quantumchest.upgrade.done",
                Component.translatable(kit.target().translationKey())));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof QuantumChestBlockEntity chest)) return InteractionResult.PASS;
        if (checkAccess(chest, player)) {
            player.openMenu(chest);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean checkAccess(QuantumChestBlockEntity chest, Player player) {
        QuantumStorage storage = chest.storage();
        if (!storage.canUse(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.locked", storage.ownerName()));
            return false;
        }
        if (!storage.worksIn(chest.getLevel().dimension())) {
            player.sendOverlayMessage(Component.translatable("message.quantumchest.tunnel.needed_here"));
            return false;
        }
        return true;
    }
}
