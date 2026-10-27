package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.platform.services.IPlatformHelper;
import com.manuelaraujo.quantumchest.platform.services.MenuFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.function.Supplier;

public final class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public CreativeModeTab creativeTab(Component title, Supplier<ItemStack> icon, Supplier<List<ItemStack>> contents) {
        return CreativeModeTab.builder()
                .title(title)
                .icon(icon)
                .displayItems((parameters, output) -> contents.get().forEach(output::accept))
                .build();
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
        return new MenuType<>(factory::create, FeatureFlags.VANILLA_SET);
    }

    @Override
    public int burnTime(ItemStack stack, Level level) {
        return stack.isEmpty() ? 0 : Math.max(0, stack.getBurnTime(RecipeType.SMELTING, level.fuelValues()));
    }

    @Override
    public ItemStack craftingRemainder(ItemStack stack) {
        ItemStackTemplate remainder = stack.getCraftingRemainder();
        return remainder != null ? remainder.create() : ItemStack.EMPTY;
    }
}
