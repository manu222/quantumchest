package com.manuelaraujo.quantumchest.neoforge;

import com.manuelaraujo.quantumchest.platform.services.IPlatformHelper;
import com.manuelaraujo.quantumchest.platform.services.MenuFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
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
}
