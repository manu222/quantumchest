package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.UpgradeConfigPayloads;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import com.manuelaraujo.quantumchest.storage.VoidRule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The destroyer: 12 rules in two columns, each a ghost slot and how many to
 * keep. Whatever arrives on its own past that number is destroyed.
 */
public final class VoidPanel extends Panel {
    private static final int BOX_W = 34;
    private static final int CLEAR_X = 150;
    private static final int CLEAR_Y = 127;
    private static final int CLEAR_W = 38;

    public VoidPanel(QuantumChestMenu menu, Host host) {
        super(menu, host);
    }

    private List<VoidRule> rules() {
        return menu.clientConfig().destroy();
    }

    @Override
    public Component title() {
        return Component.translatable("item.quantumchest.void_upgrade");
    }

    @Override
    public boolean available() {
        return menu.hasUpgrade(ModItems.VOID_UPGRADE.get());
    }

    @Override
    public ItemStack upgrade() {
        return new ItemStack(ModItems.VOID_UPGRADE.get());
    }

    private static int slotX(int i) {
        return 10 + (i / 6) * 91;
    }

    private static int slotY(int i) {
        return 20 + (i % 6) * 18;
    }

    private int slotAt(int mouseX, int mouseY) {
        for (int i = 0; i < VoidRule.MAX_RULES; i++) {
            if (hover(slotX(i) - 1, slotY(i) - 1, 18, 18, mouseX, mouseY)) return i;
        }
        return -1;
    }

    private String keepText(long keep) {
        return keep == 0 ? Component.translatable("gui.quantumchest.destroy.all").getString() : compact(keep);
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        background(graphics);
        titleText(graphics, title());
        List<VoidRule> rules = rules();
        for (int i = 0; i < VoidRule.MAX_RULES; i++) {
            VoidRule rule = i < rules.size() ? rules.get(i) : null;
            ghostSlot(graphics, slotX(i), slotY(i), rule != null ? rule.match() : null, mouseX, mouseY);
            if (rule != null) {
                numberControl(graphics, slotX(i) + 20, slotY(i) + 2, BOX_W, keepText(rule.keep()), host.canManage(), mouseX, mouseY);
            } else if (i == rules.size()) {
                graphics.text(font, Component.translatable("gui.quantumchest.ghost.drop_here").getString(),
                        slotX(i) + 21, slotY(i) + 4, 0xFF808080, false);
            }
        }
        footerText(graphics, Component.translatable("gui.quantumchest.destroy.footer"));
        button(graphics, CLEAR_X, CLEAR_Y, CLEAR_W, 11, Component.translatable("gui.quantumchest.panel.clear").getString(),
                host.canManage() && !rules.isEmpty(), mouseX, mouseY);
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        List<VoidRule> rules = rules();
        if (hover(CLEAR_X, CLEAR_Y, CLEAR_W, 11, mouseX, mouseY)) {
            return List.of(Component.translatable("gui.quantumchest.destroy.clear_hint"));
        }
        for (int i = 0; i < rules.size(); i++) {
            if (numberPart(slotX(i) + 20, slotY(i) + 2, BOX_W, mouseX, mouseY) != NumberPart.NONE) {
                long keep = rules.get(i).keep();
                List<Component> lines = new ArrayList<>();
                lines.add(keep == 0 ? Component.translatable("gui.quantumchest.destroy.keep_none")
                        : Component.translatable("gui.quantumchest.destroy.keep", full(keep)));
                lines.addAll(numberHelp("gui.quantumchest.destroy.keep_what").subList(1, 3));
                return lines;
            }
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            boolean filled = slot < rules.size();
            List<Component> lines = new ArrayList<>();
            if (filled) lines.addAll(matchLines(rules.get(slot).match()));
            lines.addAll(ghostHelp(filled));
            return lines;
        }
        if (hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY)) {
            return List.of(Component.translatable("gui.quantumchest.destroy.hint").withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    @Override
    public boolean click(int mouseX, int mouseY, int button, boolean shift) {
        List<VoidRule> rules = new ArrayList<>(rules());
        if (!host.canManage()) return slotAt(mouseX, mouseY) >= 0;
        if (hover(CLEAR_X, CLEAR_Y, CLEAR_W, 11, mouseX, mouseY)) {
            update(List.of());
            return true;
        }
        for (int i = 0; i < rules.size(); i++) {
            int index = i;
            VoidRule rule = rules.get(i);
            if (clickNumber(slotX(i) + 20, slotY(i) + 2, BOX_W, rule.keep(), shift, mouseX, mouseY,
                    keep -> setKeep(index, keep))) return true;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot < 0) return false;
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && button == 0) {
            ItemMatch match = ItemMatch.of(carried);
            if (rules.stream().anyMatch(r -> r.match().equals(match))) return true;
            if (slot < rules.size()) rules.set(slot, new VoidRule(match, rules.get(slot).keep()));
            else rules.add(new VoidRule(match, 0));
        } else if (slot < rules.size()) {
            if (button == 1) rules.set(slot, new VoidRule(nextTag(rules.get(slot).match()), rules.get(slot).keep()));
            else rules.remove(slot);
        } else {
            return true;
        }
        update(rules);
        return true;
    }

    @Override
    public boolean scroll(int mouseX, int mouseY, double amount) {
        List<VoidRule> rules = rules();
        for (int i = 0; i < rules.size(); i++) {
            int index = i;
            if (scrollNumber(slotX(i) + 20, slotY(i) + 2, BOX_W, rules.get(i).keep(), amount, mouseX, mouseY,
                    keep -> setKeep(index, keep))) return true;
        }
        return false;
    }

    @Override
    public boolean pickFromInventory(ItemStack stack, int button) {
        if (!host.canManage()) return true;
        List<VoidRule> rules = new ArrayList<>(rules());
        ItemMatch match = ItemMatch.of(stack);
        if (rules.size() >= VoidRule.MAX_RULES || rules.stream().anyMatch(r -> r.match().equals(match))) return true;
        rules.add(new VoidRule(match, 0));
        update(rules);
        return true;
    }

    private void setKeep(int index, long keep) {
        if (!host.canManage()) return;
        List<VoidRule> rules = new ArrayList<>(rules());
        if (index >= rules.size()) return;
        rules.set(index, new VoidRule(rules.get(index).match(), Math.max(0, keep)));
        update(rules);
    }

    private void update(List<VoidRule> rules) {
        send(new UpgradeConfigPayloads.SetVoidRules(menu.containerId, rules));
    }
}
