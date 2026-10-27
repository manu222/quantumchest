package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.UpgradeConfigPayloads;
import com.manuelaraujo.quantumchest.storage.FilterRules;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The input filter: 18 ghost slots and a whitelist/blacklist switch. Built
 * into every chest, no upgrade needed.
 */
public final class FilterPanel extends Panel {
    private static final int SLOTS_X = 8;
    private static final int SLOTS_Y = 21;
    private static final int MODE_X = 118;
    private static final int MODE_Y = 3;
    private static final int MODE_W = 70;
    private static final int CLEAR_X = 150;
    private static final int CLEAR_Y = 127;
    private static final int CLEAR_W = 38;

    public FilterPanel(QuantumChestMenu menu, Host host) {
        super(menu, host);
    }

    private FilterRules rules() {
        return menu.clientConfig().filter();
    }

    @Override
    public Component title() {
        return Component.translatable("gui.quantumchest.filter");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ItemStack upgrade() {
        return ItemStack.EMPTY;
    }

    private int slotX(int i) {
        return SLOTS_X + (i % 9) * 18;
    }

    private int slotY(int i) {
        return SLOTS_Y + (i / 9) * 18;
    }

    private int slotAt(int mouseX, int mouseY) {
        for (int i = 0; i < FilterRules.MAX_ENTRIES; i++) {
            if (hover(slotX(i) - 1, slotY(i) - 1, 18, 18, mouseX, mouseY)) return i;
        }
        return -1;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        background(graphics);
        titleText(graphics, title());
        FilterRules rules = rules();
        colorButton(graphics, MODE_X, MODE_Y, MODE_W, 14, rules.blacklist() ? 0xFFB03A3A : 0xFF3F9E3F,
                Component.translatable(rules.blacklist() ? "gui.quantumchest.filter.blacklist" : "gui.quantumchest.filter.whitelist").getString(),
                host.canManage(), mouseX, mouseY);
        for (int i = 0; i < FilterRules.MAX_ENTRIES; i++) {
            ghostSlot(graphics, slotX(i), slotY(i), i < rules.entries().size() ? rules.entries().get(i) : null, mouseX, mouseY);
        }
        String[] help = {"gui.quantumchest.filter.help1", "gui.quantumchest.filter.help2", "gui.quantumchest.filter.help3",
                "gui.quantumchest.filter.help4", "gui.quantumchest.filter.help5"};
        for (int i = 0; i < help.length; i++) {
            graphics.text(font, font.plainSubstrByWidth(Component.translatable(help[i]).getString(), AREA_W - 6),
                    10, 63 + i * 11, 0xFFC8C8C8, false);
        }
        int count = rules.entries().size();
        footerText(graphics, count == 0
                ? Component.translatable("gui.quantumchest.filter.empty")
                : Component.translatable(rules.blacklist() ? "gui.quantumchest.filter.count_blocked" : "gui.quantumchest.filter.count_allowed", count));
        button(graphics, CLEAR_X, CLEAR_Y, CLEAR_W, 11, Component.translatable("gui.quantumchest.panel.clear").getString(),
                host.canManage() && count > 0, mouseX, mouseY);
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        FilterRules rules = rules();
        if (hover(MODE_X, MODE_Y, MODE_W, 14, mouseX, mouseY)) {
            return List.of(Component.translatable(rules.blacklist() ? "gui.quantumchest.filter.blacklist" : "gui.quantumchest.filter.whitelist")
                            .withStyle(ChatFormatting.YELLOW),
                    Component.translatable(rules.blacklist() ? "gui.quantumchest.filter.blacklist.hint" : "gui.quantumchest.filter.whitelist.hint")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.quantumchest.panel.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (hover(CLEAR_X, CLEAR_Y, CLEAR_W, 11, mouseX, mouseY)) {
            return List.of(Component.translatable("gui.quantumchest.filter.clear_hint"));
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            boolean filled = slot < rules.entries().size();
            List<Component> lines = new ArrayList<>();
            if (filled) lines.addAll(matchLines(rules.entries().get(slot)));
            lines.addAll(ghostHelp(filled));
            return lines;
        }
        return null;
    }

    @Override
    public boolean click(int mouseX, int mouseY, int button, boolean shift) {
        if (!host.canManage()) return slotAt(mouseX, mouseY) >= 0;
        FilterRules rules = rules();
        if (hover(MODE_X, MODE_Y, MODE_W, 14, mouseX, mouseY)) {
            update(new FilterRules(!rules.blacklist(), rules.entries()));
            return true;
        }
        if (hover(CLEAR_X, CLEAR_Y, CLEAR_W, 11, mouseX, mouseY)) {
            update(new FilterRules(rules.blacklist(), List.of()));
            return true;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot < 0) return false;
        List<ItemMatch> entries = new ArrayList<>(rules.entries());
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && button == 0) {
            ItemMatch match = ItemMatch.of(carried);
            if (entries.contains(match)) return true;
            if (slot < entries.size()) entries.set(slot, match);
            else entries.add(match);
        } else if (slot < entries.size()) {
            if (button == 1) entries.set(slot, nextTag(entries.get(slot)));
            else entries.remove(slot);
        } else {
            return true;
        }
        update(new FilterRules(rules.blacklist(), entries));
        return true;
    }

    @Override
    public boolean pickFromInventory(ItemStack stack, int button) {
        if (!host.canManage()) return true;
        FilterRules rules = rules();
        ItemMatch match = ItemMatch.of(stack);
        if (rules.entries().contains(match) || rules.entries().size() >= FilterRules.MAX_ENTRIES) return true;
        List<ItemMatch> entries = new ArrayList<>(rules.entries());
        entries.add(match);
        update(new FilterRules(rules.blacklist(), entries));
        return true;
    }

    private void update(FilterRules rules) {
        send(new UpgradeConfigPayloads.SetFilter(menu.containerId, rules));
    }
}
