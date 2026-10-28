package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.automation.CompactOption;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.UpgradeConfigPayloads;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.CompactRule;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import net.minecraft.ChatFormatting;
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
 * The compactor: every compacting the game's recipes allow, one row each,
 * with a switch and how many to leave loose. What is stored in the chest
 * comes first. The list comes from the server, since recipes live there.
 */
public final class CompactPanel extends Panel {
    private static final int ROWS = 6;
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

    private static boolean onlyStored;
    private String query = "";
    private int offset;

    // The list, the stored counts and the names are worked out again only when the
    // contents, the settings or the search change, not several times every frame
    private final Map<Identifier, String> names = new HashMap<>();
    private Object cachedOptions;
    private int cachedVersion = -1;
    private int cachedStateVersion = -1;
    private String cachedQuery;
    private boolean cachedOnly;
    private Map<Identifier, Long> cachedStored = Map.of();
    private List<CompactOption> cachedRows = List.of();

    public CompactPanel(QuantumChestMenu menu, Host host) {
        super(menu, host);
        send(new UpgradeConfigPayloads.RequestCompactOptions(menu.containerId));
    }

    @Override
    public Component title() {
        return Component.translatable("item.quantumchest.compactor_upgrade");
    }

    @Override
    public boolean available() {
        return menu.hasUpgrade(ModItems.COMPACTOR_UPGRADE.get());
    }

    @Override
    public ItemStack upgrade() {
        return new ItemStack(ModItems.COMPACTOR_UPGRADE.get());
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
        refresh();
        return cachedStored;
    }

    private List<CompactOption> rows() {
        refresh();
        return cachedRows;
    }

    private void refresh() {
        List<CompactOption> all = menu.clientCompactOptions();
        if (cachedVersion == menu.clientVersion() && cachedStateVersion == menu.clientStateVersion()
                && cachedOptions == all && query.equals(cachedQuery) && cachedOnly == onlyStored) return;
        cachedStored = countStored();
        cachedRows = buildRows(all, cachedStored);
        cachedVersion = menu.clientVersion();
        cachedStateVersion = menu.clientStateVersion();
        cachedOptions = all;
        cachedQuery = query;
        cachedOnly = onlyStored;
    }

    private Map<Identifier, Long> countStored() {
        Map<Identifier, Long> counts = new HashMap<>();
        for (Map.Entry<ItemKey, Long> entry : menu.clientItems().entrySet()) {
            counts.merge(BuiltInRegistries.ITEM.getKey(entry.getKey().template().getItem()), entry.getValue(), Long::sum);
        }
        return counts;
    }

    private CompactRule ruleFor(Identifier item) {
        for (CompactRule rule : menu.clientConfig().compact()) {
            if (rule.item().equals(item)) return rule;
        }
        return new CompactRule(item, false, 0);
    }

    /** Stored items first, then everything else; narrowed by the search and the "only stored" switch. */
    private List<CompactOption> buildRows(List<CompactOption> all, Map<Identifier, Long> stored) {
        if (all == null) return List.of();
        String q = query.toLowerCase(Locale.ROOT).strip();
        List<CompactOption> here = new ArrayList<>();
        List<CompactOption> rest = new ArrayList<>();
        for (CompactOption option : all) {
            if (!q.isEmpty() && !matchesQuery(option, q)) continue;
            if (stored.containsKey(option.input()) || stored.containsKey(option.result())) here.add(option);
            else if (!onlyStored) rest.add(option);
        }
        here.addAll(rest);
        return here;
    }

    private boolean matchesQuery(CompactOption option, String q) {
        return name(option.input()).toLowerCase(Locale.ROOT).contains(q)
                || name(option.result()).toLowerCase(Locale.ROOT).contains(q)
                || option.input().toString().contains(q);
    }

    private static ItemStack stack(Identifier id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    private String name(Identifier id) {
        return names.computeIfAbsent(id, i -> stack(i).getHoverName().getString());
    }

    private int maxOffset(int count) {
        return Math.max(0, count - ROWS);
    }

    // ---- drawing -----------------------------------------------------------

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        background(graphics);
        colorButton(graphics, ONLY_X, ONLY_Y, ONLY_W, 14, onlyStored ? 0xFF2E6FB0 : 0xFF3A3A3A,
                Component.translatable("gui.quantumchest.compact.only_stored").getString(), true, mouseX, mouseY);

        List<CompactOption> all = menu.clientCompactOptions();
        if (all == null) {
            centered(graphics, Component.translatable("gui.quantumchest.compact.loading"));
            footerText(graphics, Component.empty());
            return;
        }
        List<CompactOption> rows = rows();
        Map<Identifier, Long> stored = storedCounts();
        offset = Math.min(offset, maxOffset(rows.size()));
        if (rows.isEmpty()) {
            centered(graphics, Component.translatable(onlyStored ? "gui.quantumchest.compact.none_stored" : "gui.quantumchest.compact.none"));
        }
        for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
            CompactOption option = rows.get(offset + r);
            int y = ROW_Y + r * ROW_H;
            boolean here = stored.containsKey(option.input());
            if (here) graphics.fill(AREA_X + 1, y - 1, BAR_X - 2, y + 17, 0xFF363636);
            graphics.item(stack(option.input()), X0, y);
            graphics.text(font, ">", X0 + 18, y + 4, 0xFFC8C8C8, false);
            graphics.item(stack(option.result()), X0 + 25, y);
            graphics.text(font, option.ratio() + ":1", X0 + 43, y + 4, 0xFFAAAAAA, false);
            CompactRule rule = ruleFor(option.input());
            toggle(graphics, TOGGLE_X, y + 2, rule.enabled(), host.canManage(), mouseX, mouseY);
            graphics.text(font, Component.translatable("gui.quantumchest.compact.keep_short").getString(),
                    KEEP_LABEL_X, y + 4, 0xFFAAAAAA, false);
            numberControl(graphics, KEEP_X, y + 2, BOX_W, compact(rule.keep()), host.canManage(), mouseX, mouseY);
        }
        drawScrollbar(graphics, rows.size());
        long here = rows.stream().filter(o -> stored.containsKey(o.input())).count();
        boolean anyOn = menu.clientConfig().compact().stream().anyMatch(CompactRule::enabled);
        footerText(graphics, anyOn ? Component.translatable("gui.quantumchest.compact.footer", all.size(), here)
                : Component.translatable("gui.quantumchest.compact.start_hint"), true);
    }

    private void centered(GuiGraphicsExtractor graphics, Component text) {
        String s = text.getString();
        graphics.text(font, s, AREA_X + AREA_W / 2 - font.width(s) / 2, AREA_Y + AREA_H / 2 - 4, 0xFFAAAAAA, false);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int count) {
        graphics.fill(BAR_X, AREA_Y + 1, BAR_X + 8, AREA_Y + AREA_H - 1, 0xFF1A1A1A);
        if (count <= ROWS) return;
        int track = AREA_H - 2;
        int knobH = Math.max(12, track * ROWS / count);
        int knobY = AREA_Y + 1 + (track - knobH) * offset / maxOffset(count);
        graphics.fill(BAR_X, knobY, BAR_X + 8, knobY + knobH, 0xFFC6C6C6);
        graphics.fill(BAR_X + 7, knobY, BAR_X + 8, knobY + knobH, 0xFF8B8B8B);
    }

    // ---- input -------------------------------------------------------------

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (hover(ONLY_X, ONLY_Y, ONLY_W, 14, mouseX, mouseY)) {
            return List.of(Component.translatable(onlyStored ? "gui.quantumchest.compact.only_stored.on" : "gui.quantumchest.compact.only_stored.off"),
                    Component.translatable("gui.quantumchest.panel.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
        }
        List<CompactOption> rows = rows();
        Map<Identifier, Long> stored = storedCounts();
        for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
            CompactOption option = rows.get(offset + r);
            int y = ROW_Y + r * ROW_H;
            CompactRule rule = ruleFor(option.input());
            if (onToggle(TOGGLE_X, y + 2, mouseX, mouseY)) {
                return List.of(Component.translatable(rule.enabled() ? "gui.quantumchest.compact.on" : "gui.quantumchest.compact.off"),
                        Component.translatable("gui.quantumchest.panel.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (numberPart(KEEP_X, y + 2, BOX_W, mouseX, mouseY) != NumberPart.NONE) {
                return numberHelp(rule.keep() == 0 ? "gui.quantumchest.compact.keep_none" : "gui.quantumchest.compact.keep_what");
            }
            if (hover(X0, y, 60, 16, mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.quantumchest.compact.row", name(option.input()), name(option.result())));
                lines.add(Component.translatable("gui.quantumchest.compact.ratio", option.ratio()).withStyle(ChatFormatting.GRAY));
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
        List<CompactOption> rows = rows();
        if (hover(BAR_X, AREA_Y, 8, AREA_H, mouseX, mouseY) && rows.size() > ROWS) {
            offset = Math.round((mouseY - AREA_Y) / (float) AREA_H * maxOffset(rows.size()));
            return true;
        }
        if (!host.canManage()) return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
        for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
            CompactOption option = rows.get(offset + r);
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

    @Override
    public boolean scroll(int mouseX, int mouseY, double amount) {
        List<CompactOption> rows = rows();
        if (host.canManage()) {
            for (int r = 0; r < ROWS && offset + r < rows.size(); r++) {
                CompactOption option = rows.get(offset + r);
                CompactRule rule = ruleFor(option.input());
                if (scrollNumber(KEEP_X, ROW_Y + r * ROW_H + 2, BOX_W, rule.keep(), amount, mouseX, mouseY,
                        keep -> update(new CompactRule(option.input(), ruleFor(option.input()).enabled(), Math.max(0, keep))))) {
                    return true;
                }
            }
        }
        if (!hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY)) return false;
        offset = Math.max(0, Math.min(maxOffset(rows.size()), offset - (int) Math.signum(amount)));
        return true;
    }

    private void update(CompactRule rule) {
        send(new UpgradeConfigPayloads.SetCompactRule(menu.containerId, rule));
    }
}
