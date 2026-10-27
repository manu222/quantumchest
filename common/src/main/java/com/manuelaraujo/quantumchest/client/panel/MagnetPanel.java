package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.UpgradeConfigPayloads;
import com.manuelaraujo.quantumchest.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The magnet of the chest in front of the player: on or off, and how far it
 * reaches, up to what the tier allows. A little map shows the area.
 */
public final class MagnetPanel extends Panel {
    private static final int TOGGLE_X = 160;
    private static final int TOGGLE_Y = 22;
    private static final int RANGE_X = 118;
    private static final int RANGE_Y = 42;
    private static final int BOX_W = 30;
    private static final int MAP_X = 14;
    private static final int MAP_Y = 62;
    private static final int MAP_SIZE = 54;

    public MagnetPanel(QuantumChestMenu menu, Host host) {
        super(menu, host);
    }

    @Override
    public Component title() {
        return Component.translatable("item.quantumchest.magnet_upgrade");
    }

    @Override
    public boolean available() {
        return menu.hasUpgrade(ModItems.MAGNET_UPGRADE.get()) && menu.clientFaces().editable();
    }

    @Override
    public ItemStack upgrade() {
        return new ItemStack(ModItems.MAGNET_UPGRADE.get());
    }

    private int max() {
        return Math.max(1, menu.clientAccess().magnetMax());
    }

    private int range() {
        int chosen = menu.clientFaces().magnetRange();
        return chosen > 0 ? Math.min(chosen, max()) : max();
    }

    private boolean on() {
        return menu.clientFaces().magnetOn();
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        background(graphics);
        titleText(graphics, title());
        graphics.text(font, Component.translatable("gui.quantumchest.magnet.active").getString(), 12, 24, 0xFFFFFFFF, false);
        toggle(graphics, TOGGLE_X, TOGGLE_Y, on(), host.canManage(), mouseX, mouseY);
        graphics.text(font, Component.translatable("gui.quantumchest.magnet.range").getString(), 12, 44, on() ? 0xFFFFFFFF : 0xFF808080, false);
        numberControl(graphics, RANGE_X, RANGE_Y, BOX_W, range() + " / " + max(), host.canManage() && on(), mouseX, mouseY);

        // Top view: the chest in the middle and the reach around it
        int cells = 2 * max() + 1;
        // A config can raise the reach to 16, so small cells drop the gap between them
        int cell = Math.max(1, MAP_SIZE / cells);
        int gap = cell >= 3 ? 1 : 0;
        int size = cell * cells;
        int ox = MAP_X + (MAP_SIZE - size) / 2;
        int oy = MAP_Y + (MAP_SIZE - size) / 2;
        graphics.fill(ox - 1, oy - 1, ox + size + 1, oy + size + 1, 0xFF000000);
        for (int gx = 0; gx < cells; gx++) {
            for (int gy = 0; gy < cells; gy++) {
                int dx = Math.abs(gx - max());
                int dy = Math.abs(gy - max());
                boolean inside = on() && Math.max(dx, dy) <= range();
                int color = dx == 0 && dy == 0 ? 0xFF9B5FE0 : inside ? 0xFF3C6E3C : 0xFF363636;
                graphics.fill(ox + gx * cell, oy + gy * cell, ox + gx * cell + cell - gap, oy + gy * cell + cell - gap, color);
            }
        }
        String[] help = {"gui.quantumchest.magnet.help1", "gui.quantumchest.magnet.help2", "gui.quantumchest.magnet.help3",
                "gui.quantumchest.magnet.help4", "gui.quantumchest.magnet.help5"};
        for (int i = 0; i < help.length; i++) {
            graphics.text(font, Component.translatable(help[i]).getString(), 78, 64 + i * 11, 0xFFC8C8C8, false);
        }
        footerText(graphics, Component.translatable("gui.quantumchest.magnet.footer"), true);
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (onToggle(TOGGLE_X, TOGGLE_Y, mouseX, mouseY)) {
            return List.of(Component.translatable(on() ? "gui.quantumchest.magnet.on" : "gui.quantumchest.magnet.off"),
                    Component.translatable("gui.quantumchest.panel.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (numberPart(RANGE_X, RANGE_Y, BOX_W, mouseX, mouseY) != NumberPart.NONE) {
            return List.of(Component.translatable("gui.quantumchest.magnet.range_hint", range(), max()),
                    Component.translatable("gui.quantumchest.magnet.range_max").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (hover(MAP_X, MAP_Y, MAP_SIZE, MAP_SIZE, mouseX, mouseY)) {
            int side = 2 * range() + 1;
            return List.of(Component.translatable("gui.quantumchest.magnet.area", side, side));
        }
        return null;
    }

    @Override
    public boolean click(int mouseX, int mouseY, int button, boolean shift) {
        if (!host.canManage()) return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
        if (onToggle(TOGGLE_X, TOGGLE_Y, mouseX, mouseY)) {
            update(!on(), menu.clientFaces().magnetRange());
            return true;
        }
        if (!on()) return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
        switch (numberPart(RANGE_X, RANGE_Y, BOX_W, mouseX, mouseY)) {
            case MINUS -> setRange(range() - 1);
            case PLUS -> setRange(range() + 1);
            case VALUE -> host.editNumber(RANGE_X + 13, RANGE_Y, BOX_W, range(), value -> setRange((int) value));
            case NONE -> {
                return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
            }
        }
        return true;
    }

    @Override
    public boolean scroll(int mouseX, int mouseY, double amount) {
        if (!host.canManage() || !on() || numberPart(RANGE_X, RANGE_Y, BOX_W, mouseX, mouseY) == NumberPart.NONE) return false;
        setRange(range() + (amount > 0 ? 1 : -1));
        return true;
    }

    private void setRange(int value) {
        int clamped = Math.max(1, Math.min(max(), value));
        // At the top it follows the tier, so an upgraded chest reaches further on its own
        update(on(), clamped >= max() ? 0 : clamped);
    }

    private void update(boolean on, int range) {
        send(new UpgradeConfigPayloads.SetMagnet(menu.containerId, on, range));
    }
}
