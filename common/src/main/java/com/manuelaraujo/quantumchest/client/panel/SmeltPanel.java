package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.automation.SmeltOption;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.MachinePayloads;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.CompactRule;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import com.manuelaraujo.quantumchest.storage.QuantumStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The smelter: one row per thing the furnace recipes can melt, with a switch
 * and how many to leave as they are, like the compactor. Under the list, how
 * far the current batch has cooked and how much the chosen fuel still gives.
 * Along the bottom, the fuels it may burn, tried left to right. Nothing is
 * burned that isn't chosen, so a chest full of logs keeps its logs.
 */
public final class SmeltPanel extends Panel {
    private static final int ROWS = 4;
    private static final int ROW_Y = 19;
    private static final int ROW_H = 18;
    private static final int X0 = 9;
    private static final int TOGGLE_X = X0 + 60;
    private static final int KEEP_LABEL_X = X0 + 88;
    private static final int KEEP_X = X0 + 116;
    private static final int BOX_W = 24;
    private static final int ONLY_X = 130;
    private static final int ONLY_Y = 3;
    private static final int ONLY_W = 58;
    private static final int BAR_X = 180;
    private static final int STATUS_Y = 94;
    private static final int PROGRESS_X = 9;
    private static final int PROGRESS_W = 42;
    private static final int FLAME_X = 92;
    private static final int GAUGE_X = 103;
    private static final int GAUGE_W = 83;
    private static final int FUEL_LABEL_Y = 114;
    private static final int FUEL_X = 78;
    private static final int FUEL_Y = 110;
    /** Ticks a furnace needs per item, to turn fuel into "items it can melt". */
    private static final int STANDARD_COOK = 200;

    private static boolean onlyStored;
    private String query = "";
    private int offset;

    public SmeltPanel(QuantumChestMenu menu, Host host) {
        super(menu, host);
        send(new MachinePayloads.RequestSmeltOptions(menu.containerId));
    }

    @Override
    public Component title() {
        return Component.translatable("item.quantumchest.smelting_upgrade");
    }

    @Override
    public boolean available() {
        return menu.hasUpgrade(ModItems.SMELTING_UPGRADE.get());
    }

    @Override
    public ItemStack upgrade() {
        return new ItemStack(ModItems.SMELTING_UPGRADE.get());
    }

    @Override
    public String searchHint() {
        return "gui.quantumchest.compact.search";
    }

    @Override
    public void search(String newQuery) {
        if (!newQuery.equals(query)) offset = 0;
        query = newQuery;
    }

    // ---- data --------------------------------------------------------------

    private Map<Identifier, Long> storedCounts() {
        Map<Identifier, Long> counts = new HashMap<>();
        for (Map.Entry<ItemKey, Long> entry : menu.clientItems().entrySet()) {
            counts.merge(BuiltInRegistries.ITEM.getKey(entry.getKey().template().getItem()), entry.getValue(), Long::sum);
        }
        return counts;
    }

    private CompactRule ruleFor(Identifier item) {
        for (CompactRule rule : menu.clientConfig().smelt()) {
            if (rule.item().equals(item)) return rule;
        }
        return new CompactRule(item, false, 0);
    }

    private List<ItemMatch> fuels() {
        return menu.clientConfig().fuels();
    }

    private List<SmeltOption> rows() {
        List<SmeltOption> all = menu.clientSmeltOptions();
        if (all == null) return List.of();
        Map<Identifier, Long> stored = storedCounts();
        String q = query.toLowerCase(Locale.ROOT).strip();
        List<SmeltOption> here = new ArrayList<>();
        List<SmeltOption> rest = new ArrayList<>();
        for (SmeltOption option : all) {
            if (!q.isEmpty() && !name(option.input()).toLowerCase(Locale.ROOT).contains(q)
                    && !name(option.result()).toLowerCase(Locale.ROOT).contains(q)
                    && !option.input().toString().contains(q)) continue;
            if (stored.containsKey(option.input())) here.add(option);
            else if (!onlyStored) rest.add(option);
        }
        here.addAll(rest);
        return here;
    }

    private static ItemStack stack(Identifier id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    private static String name(Identifier id) {
        return stack(id).getHoverName().getString();
    }

    private int maxOffset(int count) {
        return Math.max(0, count - ROWS);
    }

    private static int burnTime(ItemStack stack) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : Services.PLATFORM.burnTime(stack, level);
    }

    /** About how many items the chosen fuel in the chest, plus the heat left, can still melt. */
    private long fuelInChest() {
        long ticks = menu.clientSmelting().heat();
        for (Map.Entry<ItemKey, Long> entry : menu.clientItems().entrySet()) {
            ItemStack template = entry.getKey().template();
            if (!template.getComponentsPatch().isEmpty()) continue;
            if (fuels().stream().anyMatch(f -> f.matches(template))) ticks += burnTime(template) * entry.getValue();
        }
        return ticks / STANDARD_COOK;
    }

    /** Items switched on and stored above their "leave" amount: what is still to smelt. */
    private long pending() {
        Map<Identifier, Long> stored = storedCounts();
        long total = 0;
        for (CompactRule rule : menu.clientConfig().smelt()) {
            if (!rule.enabled()) continue;
            total += Math.max(0, stored.getOrDefault(rule.item(), 0L) - rule.keep());
        }
        return total;
    }

    private int fuelSlotAt(int mouseX, int mouseY) {
        for (int i = 0; i < QuantumStorage.MAX_FUELS; i++) {
            if (hover(FUEL_X + i * 18 - 1, FUEL_Y - 1, 18, 18, mouseX, mouseY)) return i;
        }
        return -1;
    }

    // ---- drawing -----------------------------------------------------------

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        background(graphics);
        colorButton(graphics, ONLY_X, ONLY_Y, ONLY_W, 14, onlyStored ? 0xFF2E6FB0 : 0xFF3A3A3A,
                Component.translatable("gui.quantumchest.compact.only_stored").getString(), true, mouseX, mouseY);
        drawFuels(graphics, mouseX, mouseY);

        List<SmeltOption> all = menu.clientSmeltOptions();
        if (all == null) {
            centered(graphics, Component.translatable("gui.quantumchest.compact.loading"));
            footerText(graphics, Component.empty());
            return;
        }
        List<SmeltOption> rows = rows();
        Map<Identifier, Long> stored = storedCounts();
        offset = Math.min(offset, maxOffset(rows.size()));
        if (rows.isEmpty()) {
            centered(graphics, Component.translatable(onlyStored ? "gui.quantumchest.smelt.none_stored" : "gui.quantumchest.compact.none"));
        }
        for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
            SmeltOption option = rows.get(offset + r);
            int y = ROW_Y + r * ROW_H;
            if (stored.containsKey(option.input())) graphics.fill(AREA_X + 1, y - 1, BAR_X - 2, y + 17, 0xFF363636);
            graphics.item(stack(option.input()), X0, y);
            graphics.text(font, ">", X0 + 18, y + 4, 0xFFC8C8C8, false);
            ItemStack result = stack(option.result()).copyWithCount(Math.max(1, option.count()));
            graphics.item(result, X0 + 25, y);
            graphics.itemDecorations(font, result, X0 + 25, y);
            CompactRule rule = ruleFor(option.input());
            toggle(graphics, TOGGLE_X, y + 2, rule.enabled(), host.canManage(), mouseX, mouseY);
            graphics.text(font, Component.translatable("gui.quantumchest.compact.keep_short").getString(),
                    KEEP_LABEL_X, y + 4, 0xFFAAAAAA, false);
            numberControl(graphics, KEEP_X, y + 2, BOX_W, compact(rule.keep()), host.canManage(), mouseX, mouseY);
        }
        drawScrollbar(graphics, rows.size());

        drawStatus(graphics);

        boolean anyOn = menu.clientConfig().smelt().stream().anyMatch(CompactRule::enabled);
        long fuel = fuelInChest();
        long pending = pending();
        Component footer;
        if (fuels().isEmpty()) {
            footer = Component.translatable("gui.quantumchest.smelt.no_fuel_chosen");
        } else if (fuel <= 0) {
            footer = Component.translatable("gui.quantumchest.smelt.no_fuel_stored");
        } else if (!anyOn) {
            footer = Component.translatable("gui.quantumchest.smelt.start_hint");
        } else if (pending <= 0) {
            footer = Component.translatable("gui.quantumchest.smelt.idle", full(fuel));
        } else {
            footer = Component.translatable("gui.quantumchest.smelt.footer", full(fuel), full(pending));
        }
        footerText(graphics, footer, true);
    }

    private float progress() {
        var smelting = menu.clientSmelting();
        return smelting.cookTime() <= 0 ? 0 : Math.min(1f, smelting.progress() / (float) smelting.cookTime());
    }

    /** Fuel gauge: full when the chosen fuel covers everything still to smelt. */
    private float fuelLevel() {
        long fuel = fuelInChest();
        long pending = pending();
        if (fuel <= 0) return 0;
        if (pending <= 0) return 1;
        return Math.min(1f, fuel / (float) pending);
    }

    /** The batch in the furnaces on the left, the fuel on the right. */
    private void drawStatus(GuiGraphicsExtractor graphics) {
        var smelting = menu.clientSmelting();
        int y = STATUS_Y;
        // Progress of the batch, filled like the arrow of a furnace
        graphics.fill(PROGRESS_X, y, PROGRESS_X + PROGRESS_W, y + 8, 0xFF000000);
        graphics.fill(PROGRESS_X + 1, y + 1, PROGRESS_X + PROGRESS_W - 1, y + 7, 0xFF3A3A3A);
        int filled = Math.round((PROGRESS_W - 2) * progress());
        if (filled > 0) graphics.fill(PROGRESS_X + 1, y + 1, PROGRESS_X + 1 + filled, y + 7, 0xFFE8E8E8);
        String furnaces = smelting.active() + "/" + menu.clientAccess().smeltFurnaces();
        graphics.text(font, furnaces, PROGRESS_X + PROGRESS_W + 4, y, smelting.active() > 0 ? 0xFFFFFFFF : 0xFF808080, false);

        // Flame, lit while something burns
        int flame = smelting.active() > 0 ? 0xFFFFA020 : 0xFF5A5A5A;
        int core = smelting.active() > 0 ? 0xFFFFE060 : 0xFF6A6A6A;
        String[] shape = {"..#....", "..##...", ".###.#.", ".#####.", "##+++##", "#+++++#", "#+++++#", ".#+++#."};
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                char c = shape[row].charAt(col);
                if (c == '.') continue;
                graphics.fill(FLAME_X + col, y - 1 + row, FLAME_X + col + 1, y + row, c == '+' ? core : flame);
            }
        }
        // Fuel gauge
        graphics.fill(GAUGE_X, y, GAUGE_X + GAUGE_W, y + 8, 0xFF000000);
        graphics.fill(GAUGE_X + 1, y + 1, GAUGE_X + GAUGE_W - 1, y + 7, 0xFF3A3A3A);
        float level = fuelLevel();
        int gauge = Math.round((GAUGE_W - 2) * level);
        int color = level >= 1 ? 0xFF4CC24C : level > 0.25f ? 0xFFE0A030 : 0xFFD04040;
        if (gauge > 0) graphics.fill(GAUGE_X + 1, y + 1, GAUGE_X + 1 + gauge, y + 7, color);
    }

    private void drawFuels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(AREA_X + 1, FUEL_Y - 2, AREA_X + AREA_W - 1, FUEL_Y - 1, 0xFF1A1A1A);
        graphics.text(font, Component.translatable("gui.quantumchest.smelt.fuel").getString(), X0, FUEL_LABEL_Y, 0xFFFFC060, false);
        List<ItemMatch> fuels = fuels();
        for (int i = 0; i < QuantumStorage.MAX_FUELS; i++) {
            ghostSlot(graphics, FUEL_X + i * 18, FUEL_Y, i < fuels.size() ? fuels.get(i) : null, mouseX, mouseY);
        }
    }

    private void centered(GuiGraphicsExtractor graphics, Component text) {
        String s = text.getString();
        graphics.text(font, s, AREA_X + (BAR_X - AREA_X) / 2 - font.width(s) / 2, ROW_Y + 2 * ROW_H + 4, 0xFFAAAAAA, false);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int count) {
        int top = AREA_Y + 1;
        int track = ROWS * ROW_H;
        graphics.fill(BAR_X, top, BAR_X + 8, top + track, 0xFF1A1A1A);
        if (count <= ROWS) return;
        int knobH = Math.max(12, track * ROWS / count);
        int knobY = top + (track - knobH) * offset / maxOffset(count);
        graphics.fill(BAR_X, knobY, BAR_X + 8, knobY + knobH, 0xFFC6C6C6);
        graphics.fill(BAR_X + 7, knobY, BAR_X + 8, knobY + knobH, 0xFF8B8B8B);
    }

    // ---- input -------------------------------------------------------------

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (hover(ONLY_X, ONLY_Y, ONLY_W, 14, mouseX, mouseY)) {
            return List.of(Component.translatable(onlyStored ? "gui.quantumchest.compact.only_stored.on" : "gui.quantumchest.smelt.only_stored.off"),
                    Component.translatable("gui.quantumchest.panel.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (hover(PROGRESS_X, STATUS_Y - 1, PROGRESS_W + 30, 10, mouseX, mouseY)) {
            var smelting = menu.clientSmelting();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.quantumchest.smelt.furnaces", smelting.active(), menu.clientAccess().smeltFurnaces()));
            if (smelting.active() > 0 && smelting.cookTime() > 0) {
                int left = Math.max(0, smelting.cookTime() - smelting.progress());
                lines.add(Component.translatable("gui.quantumchest.smelt.next_batch", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.quantumchest.smelt.furnaces_hint").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        if (hover(FLAME_X, STATUS_Y - 1, GAUGE_X + GAUGE_W - FLAME_X, 10, mouseX, mouseY)) {
            return List.of(Component.translatable("gui.quantumchest.smelt.gauge", full(fuelInChest())),
                    Component.translatable("gui.quantumchest.smelt.gauge_pending", full(pending())).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.quantumchest.smelt.gauge_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        int fuel = fuelSlotAt(mouseX, mouseY);
        if (fuel >= 0) {
            List<ItemMatch> fuels = fuels();
            List<Component> lines = new ArrayList<>();
            if (fuel < fuels.size()) {
                lines.addAll(matchLines(fuels.get(fuel)));
                lines.add(Component.translatable("gui.quantumchest.smelt.fuel_order", fuel + 1).withStyle(ChatFormatting.GRAY));
                lines.addAll(ghostHelp(true));
            } else {
                lines.add(Component.translatable("gui.quantumchest.smelt.fuel_empty").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.quantumchest.smelt.fuel_why").withStyle(ChatFormatting.DARK_GRAY));
            }
            return lines;
        }
        List<SmeltOption> rows = rows();
        Map<Identifier, Long> stored = storedCounts();
        for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
            SmeltOption option = rows.get(offset + r);
            int y = ROW_Y + r * ROW_H;
            CompactRule rule = ruleFor(option.input());
            if (onToggle(TOGGLE_X, y + 2, mouseX, mouseY)) {
                return List.of(Component.translatable(rule.enabled() ? "gui.quantumchest.smelt.on" : "gui.quantumchest.smelt.off"),
                        Component.translatable("gui.quantumchest.panel.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (numberPart(KEEP_X, y + 2, BOX_W, mouseX, mouseY) != NumberPart.NONE) {
                return numberHelp(rule.keep() == 0 ? "gui.quantumchest.smelt.keep_none" : "gui.quantumchest.smelt.keep_what");
            }
            if (hover(X0, y, 60, 16, mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.quantumchest.smelt.row", name(option.input()), name(option.result())));
                long count = stored.getOrDefault(option.input(), 0L);
                if (count > 0) {
                    lines.add(Component.translatable("gui.quantumchest.compact.stored", full(count)).withStyle(ChatFormatting.GREEN));
                }
                if (!option.input().getNamespace().equals("minecraft")) {
                    lines.add(Component.literal(option.input().getNamespace()).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
                }
                return lines;
            }
        }
        return null;
    }

    @Override
    public boolean click(int mouseX, int mouseY, int button, boolean shift) {
        if (hover(ONLY_X, ONLY_Y, ONLY_W, 14, mouseX, mouseY)) {
            onlyStored = !onlyStored;
            offset = 0;
            return true;
        }
        List<SmeltOption> rows = rows();
        if (hover(BAR_X, AREA_Y, 8, ROWS * ROW_H, mouseX, mouseY) && rows.size() > ROWS) {
            offset = Math.round((mouseY - AREA_Y) / (float) (ROWS * ROW_H) * maxOffset(rows.size()));
            return true;
        }
        if (!host.canManage()) return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
        int fuel = fuelSlotAt(mouseX, mouseY);
        if (fuel >= 0) {
            clickFuel(fuel, button);
            return true;
        }
        for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
            SmeltOption option = rows.get(offset + r);
            int y = ROW_Y + r * ROW_H;
            CompactRule rule = ruleFor(option.input());
            if (onToggle(TOGGLE_X, y + 2, mouseX, mouseY)) {
                update(new CompactRule(option.input(), !rule.enabled(), rule.keep()));
                return true;
            }
            if (clickNumber(KEEP_X, y + 2, BOX_W, rule.keep(), shift, mouseX, mouseY,
                    keep -> update(new CompactRule(option.input(), ruleFor(option.input()).enabled(), Math.max(0, keep))))) {
                return true;
            }
        }
        return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
    }

    private void clickFuel(int slot, int button) {
        List<ItemMatch> fuels = new ArrayList<>(fuels());
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && button == 0) {
            if (burnTime(carried) <= 0) return;
            ItemMatch match = ItemMatch.of(carried);
            if (fuels.contains(match)) return;
            if (slot < fuels.size()) fuels.set(slot, match);
            else fuels.add(match);
        } else if (slot < fuels.size()) {
            if (button == 1) fuels.set(slot, nextTag(fuels.get(slot)));
            else fuels.remove(slot);
        } else {
            return;
        }
        sendFuels(fuels);
    }

    /** Clicking an item of yours adds it as a fuel, if it burns. */
    @Override
    public boolean pickFromInventory(ItemStack stack, int button) {
        if (!host.canManage()) return true;
        if (burnTime(stack) <= 0) return false;
        List<ItemMatch> fuels = new ArrayList<>(fuels());
        ItemMatch match = ItemMatch.of(stack);
        if (fuels.size() >= QuantumStorage.MAX_FUELS || fuels.contains(match)) return true;
        fuels.add(match);
        sendFuels(fuels);
        return true;
    }

    @Override
    public boolean scroll(int mouseX, int mouseY, double amount) {
        List<SmeltOption> rows = rows();
        if (host.canManage()) {
            for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
                SmeltOption option = rows.get(offset + r);
                CompactRule rule = ruleFor(option.input());
                if (scrollNumber(KEEP_X, ROW_Y + r * ROW_H + 2, BOX_W, rule.keep(), amount, mouseX, mouseY,
                        keep -> update(new CompactRule(option.input(), ruleFor(option.input()).enabled(), Math.max(0, keep))))) {
                    return true;
                }
            }
        }
        if (!hover(AREA_X, AREA_Y, AREA_W, ROWS * ROW_H + 2, mouseX, mouseY)) return false;
        offset = Math.max(0, Math.min(maxOffset(rows.size()), offset - (int) Math.signum(amount)));
        return true;
    }

    private void update(CompactRule rule) {
        send(new MachinePayloads.SetSmeltRule(menu.containerId, rule));
    }

    private void sendFuels(List<ItemMatch> fuels) {
        send(new MachinePayloads.SetFuels(menu.containerId, fuels));
    }
}
