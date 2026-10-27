package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.MachinePayloads;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import com.manuelaraujo.quantumchest.storage.RefillMode;
import com.manuelaraujo.quantumchest.storage.RefillRule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-refill: 12 rules, each a ghost slot, how many you want to carry and
 * which way it works (an arrow: down = the chest gives, up = the chest
 * takes, both = always that many). With a terminal linked to this chest in
 * your inventory, it keeps you at that amount.
 */
public final class RefillPanel extends Panel {
    private static final int BOX_W = 25;
    private static final int MODE_W = 13;
    private static final int CLEAR_X = 150;
    private static final int CLEAR_Y = 127;
    private static final int CLEAR_W = 38;
    private static final long DEFAULT_AMOUNT = 64;

    public RefillPanel(QuantumChestMenu menu, Host host) {
        super(menu, host);
    }

    private List<RefillRule> rules() {
        return menu.clientConfig().refill();
    }

    @Override
    public Component title() {
        return Component.translatable("item.quantumchest.refill_upgrade");
    }

    @Override
    public boolean available() {
        return menu.hasUpgrade(ModItems.REFILL_UPGRADE.get());
    }

    @Override
    public ItemStack upgrade() {
        return new ItemStack(ModItems.REFILL_UPGRADE.get());
    }

    private static int slotX(int i) {
        return 9 + (i / 6) * 90;
    }

    private static int slotY(int i) {
        return 20 + (i % 6) * 18;
    }

    private static int modeX(int i) {
        return slotX(i) + 20 + numberWidth(BOX_W) + 1;
    }

    private static boolean onMode(int i, int mouseX, int mouseY) {
        return hover(modeX(i), slotY(i) + 2, MODE_W, 12, mouseX, mouseY);
    }

    private static int modeColor(RefillMode mode) {
        return switch (mode) {
            case REFILL -> 0xFF3C8A3C;
            case STORE -> 0xFFC07A22;
            case KEEP -> 0xFF2E6FB0;
        };
    }

    /** A small button with an arrow down (gives), up (takes) or both. */
    private void modeButton(GuiGraphicsExtractor graphics, int i, RefillMode mode, int mouseX, int mouseY) {
        int x = modeX(i);
        int y = slotY(i) + 2;
        boolean over = host.canManage() && onMode(i, mouseX, mouseY);
        graphics.fill(x, y, x + MODE_W, y + 12, over ? 0xFFFFFFFF : 0xFF000000);
        graphics.fill(x + 1, y + 1, x + MODE_W - 1, y + 11, modeColor(mode));
        int cx = x + MODE_W / 2;
        int color = 0xFFFFFFFF;
        if (mode.gives()) arrow(graphics, mode.takes() ? cx + 2 : cx, y + 2, true, color);
        if (mode.takes()) arrow(graphics, mode.gives() ? cx - 2 : cx, y + 2, false, color);
    }

    /** A 3 pixel wide arrow, 8 high, pointing down (into your inventory) or up (into the chest). */
    private static void arrow(GuiGraphicsExtractor graphics, int cx, int top, boolean down, int color) {
        graphics.fill(cx, top, cx + 1, top + 8, color);
        int head = down ? top + 5 : top;
        for (int i = 0; i < 3; i++) {
            int y = down ? head + i : head + 2 - i;
            graphics.fill(cx - (2 - i), y, cx + 3 - i, y + 1, color);
        }
    }

    private int slotAt(int mouseX, int mouseY) {
        for (int i = 0; i < RefillRule.MAX_RULES; i++) {
            if (hover(slotX(i) - 1, slotY(i) - 1, 18, 18, mouseX, mouseY)) return i;
        }
        return -1;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        background(graphics);
        titleText(graphics, title());
        List<RefillRule> rules = rules();
        for (int i = 0; i < RefillRule.MAX_RULES; i++) {
            RefillRule rule = i < rules.size() ? rules.get(i) : null;
            ghostSlot(graphics, slotX(i), slotY(i), rule != null ? rule.match() : null, mouseX, mouseY);
            if (rule != null) {
                numberControl(graphics, slotX(i) + 20, slotY(i) + 2, BOX_W, compact(rule.amount()), host.canManage(), mouseX, mouseY);
                modeButton(graphics, i, rule.mode(), mouseX, mouseY);
            } else if (i == rules.size()) {
                graphics.text(font, Component.translatable("gui.quantumchest.ghost.drop_here").getString(),
                        slotX(i) + 21, slotY(i) + 4, 0xFF808080, false);
            }
        }
        footerText(graphics, Component.translatable("gui.quantumchest.refill.footer"));
        button(graphics, CLEAR_X, CLEAR_Y, CLEAR_W, 11, Component.translatable("gui.quantumchest.panel.clear").getString(),
                host.canManage() && !rules.isEmpty(), mouseX, mouseY);
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        List<RefillRule> rules = rules();
        if (hover(CLEAR_X, CLEAR_Y, CLEAR_W, 11, mouseX, mouseY)) {
            return List.of(Component.translatable("gui.quantumchest.refill.clear_hint"));
        }
        for (int i = 0; i < rules.size(); i++) {
            RefillRule rule = rules.get(i);
            if (numberPart(slotX(i) + 20, slotY(i) + 2, BOX_W, mouseX, mouseY) != NumberPart.NONE) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.quantumchest.refill.amount", full(rule.amount())));
                lines.add(Component.translatable(rule.mode().translationKey() + ".amount", full(rule.amount()))
                        .withStyle(ChatFormatting.GRAY));
                lines.addAll(numberHelp("gui.quantumchest.refill.amount").subList(1, 3));
                return lines;
            }
            if (onMode(i, mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(rule.mode().translationKey()).withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable(rule.mode().translationKey() + ".hint").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(host.canManage() ? "gui.quantumchest.face.cycle_hint" : "gui.quantumchest.panel.owner_only")
                        .withStyle(host.canManage() ? ChatFormatting.DARK_GRAY : ChatFormatting.RED));
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
            return List.of(Component.translatable("gui.quantumchest.refill.hint").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.quantumchest.refill.hint2").withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    @Override
    public boolean click(int mouseX, int mouseY, int button, boolean shift) {
        List<RefillRule> rules = new ArrayList<>(rules());
        if (!host.canManage()) return slotAt(mouseX, mouseY) >= 0;
        if (hover(CLEAR_X, CLEAR_Y, CLEAR_W, 11, mouseX, mouseY)) {
            update(List.of());
            return true;
        }
        for (int i = 0; i < rules.size(); i++) {
            int index = i;
            if (clickNumber(slotX(i) + 20, slotY(i) + 2, BOX_W, rules.get(i).amount(), shift, mouseX, mouseY,
                    amount -> setAmount(index, amount))) return true;
            if (onMode(i, mouseX, mouseY)) {
                RefillMode mode = rules.get(i).mode();
                rules.set(i, rules.get(i).withMode(button == 1 ? mode.previous() : mode.next()));
                update(rules);
                return true;
            }
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot < 0) return false;
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && button == 0) {
            ItemMatch match = ItemMatch.of(carried);
            if (rules.stream().anyMatch(r -> r.match().equals(match))) return true;
            // A stack to start with: 64 torches, 16 ender pearls, one bucket
            long amount = Math.min(DEFAULT_AMOUNT, carried.getMaxStackSize());
            if (slot < rules.size()) rules.set(slot, rules.get(slot).withMatch(match));
            else rules.add(new RefillRule(match, amount, RefillMode.REFILL));
        } else if (slot < rules.size()) {
            if (button == 1) rules.set(slot, rules.get(slot).withMatch(nextTag(rules.get(slot).match())));
            else rules.remove(slot);
        } else {
            return true;
        }
        update(rules);
        return true;
    }

    @Override
    public boolean scroll(int mouseX, int mouseY, double amount) {
        List<RefillRule> rules = rules();
        for (int i = 0; i < rules.size(); i++) {
            int index = i;
            if (scrollNumber(slotX(i) + 20, slotY(i) + 2, BOX_W, rules.get(i).amount(), amount, mouseX, mouseY,
                    value -> setAmount(index, value))) return true;
        }
        return false;
    }

    @Override
    public boolean pickFromInventory(ItemStack stack, int button) {
        if (!host.canManage()) return true;
        List<RefillRule> rules = new ArrayList<>(rules());
        ItemMatch match = ItemMatch.of(stack);
        if (rules.size() >= RefillRule.MAX_RULES || rules.stream().anyMatch(r -> r.match().equals(match))) return true;
        rules.add(new RefillRule(match, Math.min(DEFAULT_AMOUNT, stack.getMaxStackSize()), RefillMode.REFILL));
        update(rules);
        return true;
    }

    private void setAmount(int index, long amount) {
        if (!host.canManage()) return;
        List<RefillRule> rules = new ArrayList<>(rules());
        if (index >= rules.size()) return;
        rules.set(index, rules.get(index).withAmount(Math.max(0, Math.min(RefillRule.MAX_AMOUNT, amount))));
        update(rules);
    }

    private void update(List<RefillRule> rules) {
        send(new MachinePayloads.SetRefillRules(menu.containerId, rules));
    }
}
