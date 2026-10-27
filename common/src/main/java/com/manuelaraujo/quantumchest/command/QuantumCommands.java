package com.manuelaraujo.quantumchest.command;

import com.manuelaraujo.quantumchest.block.QuantumChestBlock;
import com.manuelaraujo.quantumchest.registry.ModDataComponents;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import com.manuelaraujo.quantumchest.storage.QuantumStorageData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Operator tools. The contents of a chest live in the world data, never in the
 * item, so even if a chest item falls into the void its storage is still there:
 * {@code /quantumchest list} finds it and {@code /quantumchest give} hands out a
 * chest that points to it again.
 */
public final class QuantumCommands {
    private static final int MAX_LISTED = 50;

    private QuantumCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quantumchest")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ""))
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "owner")))))
                .then(Commands.literal("give")
                        .then(Commands.argument("storage", UuidArgument.uuid())
                                .executes(ctx -> give(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> give(ctx, EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx, String ownerFilter) {
        CommandSourceStack source = ctx.getSource();
        String filter = ownerFilter.toLowerCase(Locale.ROOT);
        List<QuantumStorage> found = QuantumStorageData.get(source.getServer()).all().stream()
                .filter(s -> filter.isEmpty() || s.ownerName().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing(QuantumStorage::ownerName).thenComparing(s -> s.id().toString()))
                .toList();

        source.sendSuccess(() -> Component.translatable("command.quantumchest.list.header", found.size()), false);
        for (QuantumStorage storage : found.subList(0, Math.min(found.size(), MAX_LISTED))) {
            String owner = storage.ownerName().isEmpty() ? "-" : storage.ownerName();
            source.sendSuccess(() -> Component.translatable("command.quantumchest.list.entry",
                    storage.id().toString(), owner, Component.translatable(storage.tier().translationKey()),
                    storage.inventory().totalItems(), storage.inventory().typeCount()), false);
        }
        if (found.size() > MAX_LISTED) {
            source.sendSuccess(() -> Component.translatable("command.quantumchest.list.more", found.size() - MAX_LISTED), false);
        }
        return found.size();
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        UUID id = UuidArgument.getUuid(ctx, "storage");
        QuantumStorage storage = QuantumStorageData.get(source.getServer()).get(id);
        if (storage == null) {
            source.sendFailure(Component.translatable("command.quantumchest.give.unknown", id.toString()));
            return 0;
        }
        ItemStack chest = new ItemStack(ModItems.QUANTUM_CHEST.get());
        chest.set(ModDataComponents.STORAGE_ID.get(), storage.id());
        chest.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(QuantumChestBlock.TIER, storage.tier()));
        if (!target.getInventory().add(chest)) {
            target.drop(chest, false);
        }
        source.sendSuccess(() -> Component.translatable("command.quantumchest.give.done",
                target.getName(), id.toString()), true);
        return 1;
    }
}
