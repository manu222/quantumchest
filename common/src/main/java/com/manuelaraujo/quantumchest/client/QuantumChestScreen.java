package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.client.panel.CompactPanel;
import com.manuelaraujo.quantumchest.client.panel.FacesPanel;
import com.manuelaraujo.quantumchest.client.panel.FilterPanel;
import com.manuelaraujo.quantumchest.client.panel.MagnetPanel;
import com.manuelaraujo.quantumchest.client.panel.Panel;
import com.manuelaraujo.quantumchest.client.panel.RefillPanel;
import com.manuelaraujo.quantumchest.client.panel.SmeltPanel;
import com.manuelaraujo.quantumchest.client.panel.VoidPanel;
import com.manuelaraujo.quantumchest.menu.ClickAction;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.ChestSettingsPayload;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.network.SetTabsPayload;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.FilterRules;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * Screen for the chest. A 9x6 virtual grid with scroll and search, a row of
 * tabs on top and a column of buttons on the left. The grid is not made of
 * slots: it draws whatever {@link QuantumChestMenu#clientItems()} holds and
 * sends clicks to the server as packets.
 *
 * Several modes share the panel: normal use, crafting (the grid shrinks to
 * three rows and a 3x3 appears below), editing a custom tab (the grid
 * becomes a picker), the member list of a private chest, and the settings
 * panels of the filter and the upgrades (see {@link Panel}). Each upgrade
 * that has settings gets a gear button next to its slot.
 */
public class QuantumChestScreen extends AbstractContainerScreen<QuantumChestMenu> implements Panel.Host {
    private static final Identifier TEXTURE = QuantumChest.id("textures/gui/quantum_chest.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private static final int COLS = 9;
    private static final int ROWS = 6;
    // With the crafting area open the grid keeps its first three rows
    private static final int CRAFT_ROWS = 3;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int SCROLL_X = 175;
    private static final int SCROLL_Y = 18;
    private static final int SIDE_X = -20;
    private static final int SORT_Y = 4;
    private static final int ORDER_Y = 24;
    private static final int SIDE_A_Y = 48;
    private static final int SIDE_B_Y = 68;
    private static final int SIDE_C_Y = 88;
    private static final int INFO_Y = 129;
    private static final int TAB_Y = -22;
    private static final int TAB_W = 22;
    private static final int ARROW_W = 12;
    // The tab row sits above the panel, so move the panel down a bit to make room
    private static final int TAB_ROOM = 11;
    // Gear buttons, to the right of each upgrade slot
    private static final int GEAR_X = QuantumChestMenu.UPGRADE_X + 19;
    private static final int GEAR_SIZE = 11;

    private enum Mode { NORMAL, CRAFT, EDIT_TAB, MEMBERS, PANEL }

    // Remembered while the game is running, so every chest opens the same way
    private static SortMode sortMode = SortMode.AMOUNT;
    private static boolean reversed;
    private static String lastSearch = "";
    private static String lastTabKey = ChestTabs.ALL;

    private final ItemSearch search = new ItemSearch();
    private ChestTabs chestTabs;
    private EditBox searchBox;
    private EditBox nameBox;
    private EditBox tagsBox;
    private EditBox memberBox;
    private EditBox numberBox;

    private Mode mode = Mode.NORMAL;
    private Panel panel;
    private String searchBeforePanel = "";
    private List<ChestTabs.Tab> tabs = List.of();
    private String selectedTab = lastTabKey;
    private int tabPage;
    private List<Map.Entry<ItemKey, Long>> visible = List.of();
    private int seenVersion = -1;
    private String seenQuery;
    private boolean listDirty = true;
    private float scroll;
    private boolean scrolling;
    private boolean wasMouseDown;
    private int hoveredIndex = -1;
    private int ticks;

    // Typing a number in a panel: where the box is and who gets the value
    private LongConsumer numberTarget;

    // Tab editor
    private int editIndex = -1;
    private final Set<Identifier> editItems = new LinkedHashSet<>();
    private ItemStack editIcon = ItemStack.EMPTY;

    public QuantumChestScreen(QuantumChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 195, 222);
        this.inventoryLabelY = INFO_Y;
    }

    @Override
    protected void init() {
        super.init();
        topPos += TAB_ROOM;
        chestTabs = new ChestTabs();

        searchBox = box(10, 6, 160, 100, "gui.quantumchest.search");
        searchBox.setValue(lastSearch);
        nameBox = box(9, INFO_Y, 78, CustomTab.MAX_NAME, "gui.quantumchest.edit.name");
        tagsBox = box(93, INFO_Y, 94, 200, "gui.quantumchest.edit.tags");
        memberBox = box(9, INFO_Y, 150, 16, "gui.quantumchest.members.add");
        numberBox = box(0, 0, 30, 18, "gui.quantumchest.number.hint");
        numberBox.setVisible(false);
        numberTarget = null;
        setMode(mode);
        listDirty = true;
    }

    private EditBox box(int x, int y, int width, int maxLength, String hint) {
        EditBox box = new EditBox(font, leftPos + x, topPos + y, width, font.lineHeight, Component.translatable(hint));
        box.setMaxLength(maxLength);
        box.setBordered(false);
        box.setTextColor(0xFFFFFFFF);
        box.setHint(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
        addWidget(box);
        return box;
    }

    private List<EditBox> boxes() {
        return List.of(searchBox, nameBox, tagsBox, memberBox, numberBox);
    }

    private void setMode(Mode newMode) {
        if (mode == Mode.PANEL && newMode != Mode.PANEL && panel != null) {
            // Back from a panel: give the item search back
            searchBox.setValue(searchBeforePanel);
            panel = null;
        }
        if (mode == Mode.CRAFT && newMode != Mode.CRAFT && !menu.craftGridEmpty()) {
            // Leaving the crafting area: what was on the 3x3 goes back into the chest
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, QuantumChestMenu.BUTTON_CLEAR_CRAFTING);
        }
        mode = newMode;
        menu.setCraftingVisible(mode == Mode.CRAFT);
        cancelNumber();
        nameBox.setVisible(mode == Mode.EDIT_TAB);
        tagsBox.setVisible(mode == Mode.EDIT_TAB);
        memberBox.setVisible(mode == Mode.MEMBERS);
        applySearchBox();
        listDirty = true;
    }

    /** The search box belongs to the item grid, or to a panel with a list (some panels only have one on one of their pages). */
    private void applySearchBox() {
        boolean panelSearch = mode == Mode.PANEL && panel != null && panel.searchHint() != null;
        searchBox.setVisible(gridMode() || mode == Mode.EDIT_TAB || panelSearch);
        searchBox.setWidth(panelSearch ? panel.searchWidth() : 160);
        searchBox.setHint(Component.translatable(panelSearch ? panel.searchHint() : "gui.quantumchest.search")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (EditBox box : boxes()) {
            if (!box.isVisible() && box.isFocused()) {
                box.setFocused(false);
                setFocused(null);
            }
        }
    }

    private void openPanel(Panel newPanel) {
        if (mode == Mode.PANEL && panel != null && panel.getClass() == newPanel.getClass()) {
            setMode(Mode.NORMAL);
            return;
        }
        if (mode == Mode.PANEL) setMode(Mode.NORMAL);
        panel = newPanel;
        searchBeforePanel = searchBox.getValue();
        searchBox.setValue("");
        setMode(Mode.PANEL);
    }

    /** Normal use, with or without the crafting area: the grid works the same in both. */
    private boolean gridMode() {
        return mode == Mode.NORMAL || mode == Mode.CRAFT;
    }

    /** Rows of the item grid on screen. */
    private int rows() {
        return mode == Mode.CRAFT ? CRAFT_ROWS : ROWS;
    }

    private int scrollHeight() {
        return rows() * 18;
    }

    /** The crafting gear opens a 3x3 under the grid instead of a settings panel. */
    private void toggleCrafting() {
        setMode(mode == Mode.CRAFT ? Mode.NORMAL : Mode.CRAFT);
        scroll = 0;
    }

    /** The modes that replace the grid. */
    private boolean panelMode() {
        return mode == Mode.MEMBERS || mode == Mode.PANEL;
    }

    private SyncStatePayload.Access access() {
        return menu.clientAccess();
    }

    // ---- Panel.Host --------------------------------------------------------

    @Override
    public boolean canManage() {
        return access().canManage();
    }

    @Override
    public int ticks() {
        return ticks;
    }

    @Override
    public void editNumber(int x, int y, int width, long value, LongConsumer onDone) {
        numberTarget = onDone;
        numberBox.setX(leftPos + x + 2);
        numberBox.setY(topPos + y + 2);
        numberBox.setWidth(width - 3);
        numberBox.setValue(Long.toString(value));
        numberBox.setVisible(true);
        setFocused(numberBox);
        numberBox.setFocused(true);
    }

    private void commitNumber() {
        if (numberTarget == null) return;
        String digits = numberBox.getValue().replaceAll("\\D", "");
        LongConsumer target = numberTarget;
        cancelNumber();
        if (digits.isEmpty()) return;
        long value;
        try {
            value = Long.parseLong(digits);
        } catch (NumberFormatException tooBig) {
            value = Long.MAX_VALUE;
        }
        target.accept(value);
    }

    private void cancelNumber() {
        numberTarget = null;
        if (numberBox == null) return;
        numberBox.setVisible(false);
        if (numberBox.isFocused()) {
            numberBox.setFocused(false);
            setFocused(null);
        }
    }

    // ---- the item list -----------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (mode == Mode.PANEL && (panel == null || !panel.available())) setMode(Mode.NORMAL);
        if (mode == Mode.CRAFT && !menu.hasUpgrade(ModItems.CRAFTING_UPGRADE.get())) setMode(Mode.NORMAL);
        if (mode == Mode.PANEL) {
            boolean wantsSearch = panel.searchHint() != null;
            if (wantsSearch != searchBox.isVisible() || wantsSearch && searchBox.getWidth() != panel.searchWidth()) {
                applySearchBox();
            }
            if (wantsSearch) panel.search(searchBox.getValue());
            panel.tick();
            return;
        }
        String query = searchBox.getValue();
        if (listDirty || menu.clientVersion() != seenVersion || !query.equals(seenQuery)) {
            if (!query.equals(seenQuery)) scroll = 0;
            rebuildList(query);
        }
    }

    private void rebuildList(String query) {
        tabs = chestTabs.build(menu.clientItems().keySet(), menu.clientTabs(), access().canEditTabs());
        ChestTabs.Tab tab = currentTab();
        selectedTab = tab.key();
        lastTabKey = selectedTab;

        Predicate<ItemKey> filter = search.parse(query);
        if (mode != Mode.EDIT_TAB) filter = filter.and(tab.filter());

        List<Map.Entry<ItemKey, Long>> list = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> entry : menu.clientItems().entrySet()) {
            if (filter.test(entry.getKey())) list.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        list.sort(sortMode.comparator(reversed));
        visible = list;
        seenVersion = menu.clientVersion();
        seenQuery = query;
        lastSearch = query;
        listDirty = false;
    }

    private ChestTabs.Tab currentTab() {
        for (ChestTabs.Tab tab : tabs) {
            if (tab.key().equals(selectedTab) && !tab.key().equals(ChestTabs.ADD)) return tab;
        }
        return tabs.getFirst();
    }

    private int hiddenRows() {
        int rows = (visible.size() + COLS - 1) / COLS;
        return Math.max(0, rows - rows());
    }

    private int firstRow() {
        return Math.round(scroll * hiddenRows());
    }

    /** Index into {@link #visible} under the mouse, or -1. Coordinates are absolute. */
    private int indexAt(double mouseX, double mouseY) {
        int col = Mth.floor((mouseX - leftPos - GRID_X + 1) / 18);
        int row = Mth.floor((mouseY - topPos - GRID_Y + 1) / 18);
        if (col < 0 || col >= COLS || row < 0 || row >= rows()) return -1;
        int index = (firstRow() + row) * COLS + col;
        return index < visible.size() ? index : -1;
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return isHovering(GRID_X - 1, GRID_Y - 1, COLS * 18, rows() * 18, mouseX, mouseY);
    }

    // ---- tab row layout ----------------------------------------------------

    private boolean tabsNeedPages() {
        return tabs.size() * TAB_W > imageWidth;
    }

    private int tabsPerPage() {
        return tabsNeedPages() ? (imageWidth - 2 * ARROW_W - 2) / TAB_W : tabs.size();
    }

    private int tabPages() {
        int perPage = Math.max(1, tabsPerPage());
        return (tabs.size() + perPage - 1) / perPage;
    }

    private int firstTabX() {
        return tabsNeedPages() ? ARROW_W + 1 : 0;
    }

    /** Index into {@link #tabs} of the tab under the mouse, or -1. */
    private int tabAt(double mouseX, double mouseY) {
        if (!isHovering(firstTabX(), TAB_Y, tabsPerPage() * TAB_W, TAB_W, mouseX, mouseY)) return -1;
        int slot = (int) ((mouseX - leftPos - firstTabX()) / TAB_W);
        int index = Mth.clamp(tabPage, 0, tabPages() - 1) * tabsPerPage() + slot;
        return index < tabs.size() ? index : -1;
    }

    // ---- gear buttons ------------------------------------------------------

    /** The settings panel for the upgrade in this slot, or null if it has none. */
    private Function<QuantumChestScreen, Panel> panelFor(ItemStack upgrade) {
        if (upgrade.is(ModItems.MAGNET_UPGRADE.get())) return s -> new MagnetPanel(s.menu, s);
        if (upgrade.is(ModItems.VOID_UPGRADE.get())) return s -> new VoidPanel(s.menu, s);
        if (upgrade.is(ModItems.COMPACTOR_UPGRADE.get())) return s -> new CompactPanel(s.menu, s);
        if (upgrade.is(ModItems.IO_UPGRADE.get())) return s -> new FacesPanel(s.menu, s);
        if (upgrade.is(ModItems.REFILL_UPGRADE.get())) return s -> new RefillPanel(s.menu, s);
        if (upgrade.is(ModItems.SMELTING_UPGRADE.get())) return s -> new SmeltPanel(s.menu, s);
        return null;
    }

    /** Magnet and faces belong to the chest itself, so they can't be set up from a terminal. */
    private boolean needsChest(ItemStack upgrade) {
        return upgrade.is(ModItems.MAGNET_UPGRADE.get()) || upgrade.is(ModItems.IO_UPGRADE.get());
    }

    private boolean gearEnabled(ItemStack upgrade) {
        return !needsChest(upgrade) || menu.clientFaces().editable();
    }

    private ItemStack upgradeIn(int slot) {
        return menu.slots.get(36 + slot).getItem();
    }

    private static boolean isCrafting(ItemStack upgrade) {
        return upgrade.is(ModItems.CRAFTING_UPGRADE.get());
    }

    private boolean hasGear(int slot) {
        ItemStack upgrade = upgradeIn(slot);
        return slot < menu.activeUpgradeSlots() && (isCrafting(upgrade) || panelFor(upgrade) != null);
    }

    private int gearY(int slot) {
        return QuantumChestMenu.UPGRADE_Y + slot * 18 + 3;
    }

    private int gearAt(double mouseX, double mouseY) {
        for (int i = 0; i < QuantumChestMenu.UPGRADE_SLOTS; i++) {
            if (hasGear(i) && isHovering(GEAR_X, gearY(i), GEAR_SIZE, GEAR_SIZE, mouseX, mouseY)) return i;
        }
        return -1;
    }

    private void drawGears(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String[] gear = {"..#.#..", ".#####.", "##...##", ".#...#.", "##...##", ".#####.", "..#.#.."};
        for (int i = 0; i < QuantumChestMenu.UPGRADE_SLOTS; i++) {
            if (!hasGear(i)) continue;
            ItemStack upgrade = upgradeIn(i);
            boolean enabled = gearEnabled(upgrade);
            boolean open = mode == Mode.PANEL && panel != null && ItemStack.isSameItem(panel.upgrade(), upgrade)
                    || mode == Mode.CRAFT && isCrafting(upgrade);
            int x = leftPos + GEAR_X;
            int y = topPos + gearY(i);
            boolean over = isHovering(GEAR_X, gearY(i), GEAR_SIZE, GEAR_SIZE, mouseX, mouseY);
            graphics.fill(x, y, x + GEAR_SIZE, y + GEAR_SIZE, open ? 0xFFFFD24A : over && enabled ? 0xFFFFFFFF : 0xFF000000);
            graphics.fill(x + 1, y + 1, x + GEAR_SIZE - 1, y + GEAR_SIZE - 1, enabled ? 0xFF6F6F6F : 0xFF4A4A4A);
            int color = enabled ? 0xFFE6E6E6 : 0xFF808080;
            for (int gy = 0; gy < gear.length; gy++) {
                for (int gx = 0; gx < gear[gy].length(); gx++) {
                    if (gear[gy].charAt(gx) == '#') graphics.fill(x + 2 + gx, y + 2 + gy, x + 3 + gx, y + 3 + gy, color);
                }
            }
        }
    }

    // ---- drawing -----------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateScrollbarDrag(mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (mode != Mode.PANEL) {
            boolean canScroll = hiddenRows() > 0 && !panelMode();
            int knobY = topPos + SCROLL_Y + (int) ((scrollHeight() - 15) * scroll);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, canScroll ? SCROLLER : SCROLLER_DISABLED,
                    leftPos + SCROLL_X, knobY, 12, 15);
        }
        for (EditBox box : boxes()) {
            if (box.isVisible()) box.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        drawTabs(graphics, mouseX, mouseY);
        drawSideButtons(graphics, mouseX, mouseY);
        drawGears(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        drawUpgradeColumn(graphics);
        if (mode == Mode.CRAFT) drawCraftingArea(graphics);
        // Dark fields behind the text boxes that only exist in some modes
        if (mode == Mode.EDIT_TAB) {
            textField(graphics, 7, INFO_Y - 2, 82);
            textField(graphics, 91, INFO_Y - 2, 98);
        } else if (mode == Mode.MEMBERS) {
            textField(graphics, 7, INFO_Y - 2, 154);
        }
    }

    /** The strip of upgrade slots to the right of the panel, wide enough for the gear buttons. */
    private void drawUpgradeColumn(GuiGraphicsExtractor graphics) {
        int x = leftPos + QuantumChestMenu.UPGRADE_X - 1;
        int top = topPos + QuantumChestMenu.UPGRADE_Y - 1;
        int slots = QuantumChestMenu.UPGRADE_SLOTS;
        graphics.fill(x - 4, top - 4, x + 36, top + slots * 18 + 4, 0xFF000000);
        graphics.fill(x - 3, top - 3, x + 35, top + slots * 18 + 3, 0xFFC6C6C6);
        int active = menu.activeUpgradeSlots();
        for (int i = 0; i < slots; i++) {
            int y = top + i * 18;
            if (i < active) {
                graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
                graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
            } else {
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF555555);
                graphics.fill(x + 6, y + 8, x + 12, y + 10, 0xFF3A3A3A);
            }
        }
    }

    private static final int CLEAR_CRAFT_X = 150;
    private static final int CLEAR_CRAFT_Y = 75;
    private static final int CLEAR_CRAFT_W = 38;
    private static final int CLEAR_CRAFT_H = 12;

    /**
     * Covers the lower half of the grid with a crafting table: the 3x3 on the
     * left, an arrow and the result on the right. The slots themselves are
     * real menu slots, vanilla draws their items.
     */
    private void drawCraftingArea(GuiGraphicsExtractor graphics) {
        int left = leftPos + 7;
        int top = topPos + GRID_Y + CRAFT_ROWS * 18;
        graphics.fill(left, top, leftPos + 189, topPos + INFO_Y - 2, 0xFFC6C6C6);
        // The scrollbar track ends with the shorter grid
        graphics.fill(leftPos + SCROLL_X - 1, top - 1, leftPos + SCROLL_X + 13, top, 0xFFFFFFFF);
        graphics.fill(left, top, leftPos + 189, top + 1, 0xFF8B8B8B);
        for (int i = 0; i < 9; i++) {
            slotFrame(graphics, QuantumChestMenu.CRAFT_X + (i % 3) * 18, QuantumChestMenu.CRAFT_Y + (i / 3) * 18, 18);
        }
        // Arrow
        int ax = leftPos + 92;
        int ay = topPos + QuantumChestMenu.RESULT_Y + 6;
        graphics.fill(ax, ay, ax + 16, ay + 4, 0xFF8B8B8B);
        for (int i = 0; i < 6; i++) graphics.fill(ax + 16 + i, ay - 4 + i, ax + 17 + i, ay + 8 - i, 0xFF8B8B8B);
        slotFrame(graphics, QuantumChestMenu.RESULT_X - 4, QuantumChestMenu.RESULT_Y - 4, 26);
    }

    /** A sunken square like the ones in the texture, {@code size} pixels wide, its item drawn 1 or 5 pixels in. */
    private void slotFrame(GuiGraphicsExtractor graphics, int x, int y, int size) {
        int ax = leftPos + x - (size == 18 ? 1 : 0);
        int ay = topPos + y - (size == 18 ? 1 : 0);
        graphics.fill(ax, ay, ax + size, ay + size, 0xFF373737);
        graphics.fill(ax + 1, ay + 1, ax + size, ay + size, 0xFFFFFFFF);
        graphics.fill(ax + 1, ay + 1, ax + size - 1, ay + size - 1, 0xFF8B8B8B);
    }

    private boolean onClearCraft(double mouseX, double mouseY) {
        return mode == Mode.CRAFT && isHovering(CLEAR_CRAFT_X, CLEAR_CRAFT_Y, CLEAR_CRAFT_W, CLEAR_CRAFT_H, mouseX, mouseY);
    }

    /** Labels of the crafting area: the Clear button and a reminder of what Shift does. */
    private void drawCraftingLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean enabled = !menu.craftGridEmpty();
        boolean over = enabled && onClearCraft(mouseX, mouseY);
        int x = CLEAR_CRAFT_X;
        int y = CLEAR_CRAFT_Y;
        graphics.fill(x, y, x + CLEAR_CRAFT_W, y + CLEAR_CRAFT_H, over ? 0xFFFFFFFF : 0xFF000000);
        graphics.fill(x + 1, y + 1, x + CLEAR_CRAFT_W - 1, y + CLEAR_CRAFT_H - 1, enabled ? 0xFF6F6F6F : 0xFF4A4A4A);
        String label = font.plainSubstrByWidth(Component.translatable("gui.quantumchest.panel.clear").getString(), CLEAR_CRAFT_W - 4);
        graphics.text(font, label, x + CLEAR_CRAFT_W / 2 - font.width(label) / 2, y + 2, enabled ? 0xFFFFFFFF : 0xFFA0A0A0, false);
        String hint = Component.translatable("gui.quantumchest.craft.shift_short").getString();
        graphics.text(font, font.plainSubstrByWidth(hint, 188 - 118), 118, QuantumChestMenu.RESULT_Y + 24, 0xFF404040, false);
    }

    private void textField(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + 12, 0xFF000000);
    }

    /** Labels are drawn relative to the top left corner of the panel. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hoveredIndex = -1;
        if (mode == Mode.PANEL) {
            panel.draw(graphics, mouseX - leftPos, mouseY - topPos);
            if (numberBox.isVisible()) {
                int bx = numberBox.getX() - leftPos - 2;
                int by = numberBox.getY() - topPos - 2;
                graphics.fill(bx, by, bx + numberBox.getWidth() + 3, by + 12, 0xFFFFFFFF);
                graphics.fill(bx + 1, by + 1, bx + numberBox.getWidth() + 2, by + 11, 0xFF000000);
            }
            return;
        }
        // The search box takes the top row, so the chest name goes on the row above the inventory
        if (gridMode()) {
            graphics.text(font, title.getString(), 8, INFO_Y, 0xFF404040, false);
            String info = compact(menu.clientTotal()) + " / " + capacityText(true);
            graphics.text(font, info, imageWidth - 8 - font.width(info), INFO_Y, 0xFF404040, false);
        } else if (mode == Mode.MEMBERS) {
            graphics.text(font, Component.translatable("gui.quantumchest.members", access().members().size()).getString(),
                    8, 6, 0xFF404040, false);
            drawMembers(graphics, mouseX, mouseY);
            return;
        }

        if (mode == Mode.CRAFT) drawCraftingLabels(graphics, mouseX, mouseY);
        int start = firstRow() * COLS;
        for (int i = 0; i < rows() * COLS; i++) {
            int index = start + i;
            int x = GRID_X + (i % COLS) * 18;
            int y = GRID_Y + (i / COLS) * 18;
            if (index < visible.size()) {
                Map.Entry<ItemKey, Long> entry = visible.get(index);
                ItemStack stack = entry.getKey().template();
                if (mode == Mode.EDIT_TAB && editItems.contains(itemId(stack))) {
                    graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF3FB24A);
                }
                graphics.item(stack, x, y);
                graphics.itemDecorations(font, stack, x, y);
                drawAmount(graphics, entry.getValue(), x, y);
            }
            if (isHovering(x - 1, y - 1, 18, 18, mouseX, mouseY)) {
                graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                if (index < visible.size()) hoveredIndex = index;
            }
        }
        if (mode == Mode.EDIT_TAB) {
            // Mark the player's items that are already in the tab
            for (Slot slot : menu.slots) {
                if (slot.hasItem() && editItems.contains(itemId(slot.getItem()))) {
                    graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x603FB24A);
                }
            }
        }
    }

    private void drawMembers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(GRID_X - 1, GRID_Y - 1, GRID_X - 1 + COLS * 18, GRID_Y - 1 + ROWS * 18, 0xFF2B2B2B);
        List<String> members = access().members();
        if (members.isEmpty()) {
            graphics.text(font, Component.translatable("gui.quantumchest.members.empty").getString(), GRID_X + 2, GRID_Y + 4, 0xFFAAAAAA, false);
        }
        for (int i = 0; i < members.size(); i++) {
            int x = GRID_X + 2 + (i / ROWS) * 81;
            int y = GRID_Y + 4 + (i % ROWS) * 18;
            boolean hover = isHovering(x - 1, y - 2, 10, 11, mouseX, mouseY);
            graphics.text(font, "✕", x, y, hover ? 0xFFFF5555 : 0xFFAA4444, false);
            graphics.text(font, members.get(i), x + 11, y, 0xFFFFFFFF, false);
        }
    }

    private void drawAmount(GuiGraphicsExtractor graphics, long amount, int x, int y) {
        if (amount <= 1) return;
        String text = compact(amount);
        float scale = 0.6f;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        int textX = (int) ((x + 16 - font.width(text) * scale) / scale);
        int textY = (int) ((y + 16 - 7 * scale) / scale);
        graphics.text(font, text, textX, textY, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int page = Mth.clamp(tabPage, 0, tabPages() - 1);
        int perPage = tabsPerPage();
        int y = topPos + TAB_Y;
        for (int slot = 0; slot < perPage; slot++) {
            int index = page * perPage + slot;
            if (index >= tabs.size()) break;
            ChestTabs.Tab tab = tabs.get(index);
            int x = leftPos + firstTabX() + slot * TAB_W;
            boolean selected = tab.key().equals(selectedTab) && gridMode()
                    || mode == Mode.EDIT_TAB && tab.customIndex() == editIndex && editIndex >= 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, selected ? 22 : 0, 224, 22, 22, 256, 256);
            if (tab.key().equals(ChestTabs.ADD)) {
                graphics.text(font, "+", x + 11 - font.width("+") / 2, y + 7, 0xFF404040, false);
            } else {
                graphics.item(tab.icon(), x + 3, y + 3);
            }
        }
        if (tabsNeedPages()) {
            drawArrow(graphics, leftPos, y, "<");
            drawArrow(graphics, leftPos + imageWidth - ARROW_W, y, ">");
        }
    }

    private void drawArrow(GuiGraphicsExtractor graphics, int x, int y, String text) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 44, 224, ARROW_W, 22, 256, 256);
        graphics.text(font, text, x + ARROW_W / 2 - font.width(text) / 2 + 1, y + 7, 0xFF404040, false);
    }

    private boolean filterOpen() {
        return mode == Mode.PANEL && panel instanceof FilterPanel;
    }

    private void drawSideButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        textButton(graphics, SORT_Y, sortMode.icon(), mouseX, mouseY);
        textButton(graphics, ORDER_Y, reversed ? "↑" : "↓", mouseX, mouseY);
        if (mode == Mode.EDIT_TAB) {
            textButton(graphics, SIDE_A_Y, "✓", mouseX, mouseY);
            textButton(graphics, SIDE_B_Y, "✕", mouseX, mouseY);
            if (editIndex >= 0) itemButton(graphics, SIDE_C_Y, new ItemStack(Items.LAVA_BUCKET), mouseX, mouseY);
        } else {
            if (access().canManage()) {
                itemButton(graphics, SIDE_A_Y, new ItemStack(access().isPublic() ? Items.OAK_DOOR : Items.IRON_DOOR), mouseX, mouseY);
                itemButton(graphics, SIDE_B_Y, new ItemStack(Items.PLAYER_HEAD), mouseX, mouseY);
            }
            itemButton(graphics, SIDE_C_Y, new ItemStack(Items.HOPPER), mouseX, mouseY);
            if (filterOpen()) outline(graphics, SIDE_X, SIDE_C_Y, 18, 18, 0xFFFFD24A);
            if (!menu.clientConfig().filter().entries().isEmpty()) {
                // A small dot when a filter is set, green for whitelist, red for blacklist
                int color = menu.clientConfig().filter().blacklist() ? 0xFFD03A3A : 0xFF3FB24A;
                graphics.fill(leftPos + SIDE_X + 13, topPos + SIDE_C_Y + 2, leftPos + SIDE_X + 16, topPos + SIDE_C_Y + 5, color);
            }
        }
    }

    private void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        int ax = leftPos + x;
        int ay = topPos + y;
        graphics.fill(ax - 1, ay - 1, ax + w + 1, ay, color);
        graphics.fill(ax - 1, ay + h, ax + w + 1, ay + h + 1, color);
        graphics.fill(ax - 1, ay, ax, ay + h, color);
        graphics.fill(ax + w, ay, ax + w + 1, ay + h, color);
    }

    private void textButton(GuiGraphicsExtractor graphics, int y, String icon, int mouseX, int mouseY) {
        int x = leftPos + SIDE_X;
        buttonBackground(graphics, y, mouseX, mouseY);
        graphics.text(font, icon, x + 9 - font.width(icon) / 2, topPos + y + 5, 0xFF404040, false);
    }

    private void itemButton(GuiGraphicsExtractor graphics, int y, ItemStack icon, int mouseX, int mouseY) {
        buttonBackground(graphics, y, mouseX, mouseY);
        graphics.item(icon, leftPos + SIDE_X + 1, topPos + y + 1);
    }

    private void buttonBackground(GuiGraphicsExtractor graphics, int y, int mouseX, int mouseY) {
        boolean hover = isHovering(SIDE_X, y, 18, 18, mouseX, mouseY);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + SIDE_X, topPos + y, 196, hover ? 18 : 0, 18, 18, 256, 256);
    }

    private boolean onSide(int y, double mouseX, double mouseY) {
        return isHovering(SIDE_X, y, 18, 18, mouseX, mouseY);
    }

    private void drawTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Component> lines = null;
        boolean replaceVanilla = false;
        int tab = tabAt(mouseX, mouseY);
        int gear = gearAt(mouseX, mouseY);
        if (tab >= 0) {
            ChestTabs.Tab t = tabs.get(tab);
            lines = new ArrayList<>(List.of(t.name()));
            if (t.isCustom() && access().canEditTabs()) {
                lines.add(Component.translatable("gui.quantumchest.tab.edit_hint").withStyle(ChatFormatting.GRAY));
            }
        } else if (gear >= 0) {
            ItemStack upgrade = upgradeIn(gear);
            lines = new ArrayList<>(List.of(Component.translatable("gui.quantumchest.gear", upgrade.getHoverName())));
            if (!gearEnabled(upgrade)) {
                lines.add(Component.translatable("gui.quantumchest.gear.needs_chest").withStyle(ChatFormatting.RED));
            } else if (!access().canManage()) {
                lines.add(Component.translatable("gui.quantumchest.panel.view_only").withStyle(ChatFormatting.GRAY));
            }
        } else if (onSide(SORT_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.sort"), Component.translatable(sortMode.translationKey()));
        } else if (onSide(ORDER_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable(reversed ? "gui.quantumchest.order.reversed" : "gui.quantumchest.order.normal"));
        } else if (mode == Mode.EDIT_TAB && onSide(SIDE_A_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.edit.save"));
        } else if (mode == Mode.EDIT_TAB && onSide(SIDE_B_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.edit.cancel"));
        } else if (mode == Mode.EDIT_TAB && editIndex >= 0 && onSide(SIDE_C_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.edit.delete"));
        } else if (mode != Mode.EDIT_TAB && access().canManage() && onSide(SIDE_A_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable(access().isPublic() ? "gui.quantumchest.access.public" : "gui.quantumchest.access.private"),
                    Component.translatable("gui.quantumchest.access.toggle").withStyle(ChatFormatting.GRAY));
        } else if (mode != Mode.EDIT_TAB && access().canManage() && onSide(SIDE_B_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.members", access().members().size()));
        } else if (mode != Mode.EDIT_TAB && onSide(SIDE_C_Y, mouseX, mouseY)) {
            lines = filterTooltip();
        } else if (upgradeSlotAt(mouseX, mouseY) >= menu.activeUpgradeSlots()) {
            lines = List.of(Component.translatable("gui.quantumchest.upgrades.locked",
                    Component.translatable(tierForSlot(upgradeSlotAt(mouseX, mouseY)).translationKey())));
        } else if (upgradeSlotAt(mouseX, mouseY) >= 0 && menu.getCarried().isEmpty()
                && !menu.slots.get(36 + upgradeSlotAt(mouseX, mouseY)).hasItem()) {
            lines = List.of(Component.translatable("gui.quantumchest.upgrades.empty"));
        } else if (mode == Mode.PANEL) {
            lines = panel.tooltip(mouseX - leftPos, mouseY - topPos);
            if (lines == null && isHovering(0, 0, imageWidth, 17, mouseX, mouseY)) {
                lines = List.of(panel.title(), Component.translatable("gui.quantumchest.panel.back").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (onClearCraft(mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.craft.clear_hint"));
        } else if (mode == Mode.CRAFT && hoveredSlot != null && hoveredSlot.index == QuantumChestMenu.RESULT_INDEX) {
            lines = craftResultTooltip();
            replaceVanilla = true;
        } else if (mode == Mode.CRAFT && hoveredSlot != null && hoveredSlot.index >= QuantumChestMenu.CRAFT_FIRST
                && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            lines = List.of(Component.translatable("gui.quantumchest.craft.slot_empty"),
                    Component.translatable("gui.quantumchest.craft.slot_refill").withStyle(ChatFormatting.GRAY));
        } else if (gridMode() && isHovering(7, INFO_Y - 1, 181, 10, mouseX, mouseY)) {
            lines = infoTooltip();
        } else if (hoveredIndex >= 0 && menu.getCarried().isEmpty()) {
            lines = itemTooltip(visible.get(hoveredIndex));
        } else if (mode == Mode.EDIT_TAB && insideGrid(mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.edit.grid_hint"));
        }
        if (lines == null) return;
        if (replaceVanilla) {
            // Vanilla already queued the plain item tooltip for this slot; ours adds how to take it
            graphics.setTooltipForNextFrame(font, lines.stream().map(Component::getVisualOrderText).toList(), Optional.empty(),
                    DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, true, null);
        } else {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /** The item, plus how to take it. Replaces vanilla's tooltip for that slot. */
    private List<Component> craftResultTooltip() {
        if (!hoveredSlot.hasItem()) {
            return List.of(Component.translatable(menu.craftGridEmpty() ? "gui.quantumchest.craft.empty" : "gui.quantumchest.craft.no_recipe")
                    .withStyle(ChatFormatting.GRAY));
        }
        Minecraft mc = Minecraft.getInstance();
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        List<Component> lines = new ArrayList<>(hoveredSlot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, flag));
        lines.add(Component.translatable("gui.quantumchest.craft.click_one").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("gui.quantumchest.craft.click_all").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("gui.quantumchest.craft.refills").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> filterTooltip() {
        FilterRules filter = menu.clientConfig().filter();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.quantumchest.filter"));
        if (filter.entries().isEmpty()) {
            lines.add(Component.translatable("gui.quantumchest.filter.empty").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(filter.blacklist() ? "gui.quantumchest.filter.count_blocked" : "gui.quantumchest.filter.count_allowed",
                    filter.entries().size()).withStyle(filter.blacklist() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("gui.quantumchest.filter.side_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** Index of the upgrade slot under the mouse (locked or not), or -1. */
    private int upgradeSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < QuantumChestMenu.UPGRADE_SLOTS; i++) {
            if (isHovering(QuantumChestMenu.UPGRADE_X - 1, QuantumChestMenu.UPGRADE_Y - 1 + i * 18, 18, 18, mouseX, mouseY)) return i;
        }
        return -1;
    }

    /** The lowest tier that unlocks a given upgrade slot. */
    private static ChestTier tierForSlot(int slot) {
        for (ChestTier tier : ChestTier.values()) {
            if (tier.upgradeSlots() > slot) return tier;
        }
        return ChestTier.QUANTUM;
    }

    private List<Component> itemTooltip(Map.Entry<ItemKey, Long> entry) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = entry.getKey().template();
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, flag));
        lines.add(Component.translatable("gui.quantumchest.amount", full(entry.getValue())));
        if (mode == Mode.EDIT_TAB) {
            // Shows what can be typed in the tags box
            stack.tags().limit(6).forEach(tag ->
                    lines.add(Component.literal("#" + tag.location()).withStyle(ChatFormatting.DARK_PURPLE)));
        }
        return lines;
    }

    private List<Component> infoTooltip() {
        ChestTier tier = menu.clientTier();
        String owner = access().owner().isEmpty() ? "-" : access().owner();
        return List.of(
                Component.translatable("gui.quantumchest.info.tier", Component.translatable(tier.translationKey())),
                Component.translatable("gui.quantumchest.info.items", full(menu.clientTotal()), capacityText(false)),
                Component.translatable("gui.quantumchest.info.types", full(menu.clientItems().size())),
                Component.translatable("gui.quantumchest.info.owner", owner),
                Component.translatable(access().isPublic() ? "gui.quantumchest.access.public" : "gui.quantumchest.access.private"));
    }

    // ---- input -------------------------------------------------------------

    private void updateScrollbarDrag(int mouseX, int mouseY) {
        boolean mouseDown = GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_RELEASE;
        if (!wasMouseDown && mouseDown && !panelMode() && isHovering(SCROLL_X - 1, SCROLL_Y - 1, 14, scrollHeight() + 2, mouseX, mouseY)) {
            scrolling = hiddenRows() > 0;
        }
        if (!mouseDown) scrolling = false;
        wasMouseDown = mouseDown;
        if (scrolling) {
            scroll = Mth.clamp((mouseY - topPos - SCROLL_Y - 7.5f) / (scrollHeight() - 15f), 0f, 1f);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        int button = event.button();

        if (numberTarget != null) {
            if (numberBox.isMouseOver(x, y)) return super.mouseClicked(event, doubleClick);
            // A click anywhere else confirms what was typed
            commitNumber();
        }
        if (clickTabRow(x, y, button)) return true;
        if (clickSideButtons(x, y)) return true;
        int gear = gearAt(x, y);
        if (gear >= 0) {
            ItemStack upgrade = upgradeIn(gear);
            if (isCrafting(upgrade)) {
                toggleCrafting();
            } else if (gearEnabled(upgrade)) {
                openPanel(panelFor(upgrade).apply(this));
            }
            return true;
        }
        if (onClearCraft(x, y)) {
            if (!menu.craftGridEmpty()) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, QuantumChestMenu.BUTTON_CLEAR_CRAFTING);
            }
            return true;
        }
        // Locked upgrade slots aren't real slots for vanilla; without this a click
        // there would count as outside the screen and throw the cursor item away
        if (upgradeSlotAt(x, y) >= menu.activeUpgradeSlots()) return true;

        if (mode == Mode.PANEL) {
            int relX = (int) Math.floor(x - leftPos);
            int relY = (int) Math.floor(y - topPos);
            if (panel.click(relX, relY, button, event.hasShiftDown())) return true;
            Slot slot = slotAt(x, y);
            // Clicking your own items picks them for filters, without moving them
            if (slot != null && slot.index < 36 && slot.hasItem() && menu.getCarried().isEmpty()
                    && panel.pickFromInventory(slot.getItem(), button)) {
                return true;
            }
            if (isHovering(Panel.AREA_X, Panel.AREA_Y, Panel.AREA_W, Panel.AREA_H, x, y)) return true;
            return super.mouseClicked(event, doubleClick);
        }
        if (mode == Mode.MEMBERS) {
            if (insideGrid(x, y)) {
                clickMember(x, y);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (insideGrid(x, y) && (button == 0 || button == 1)) {
            if (mode == Mode.EDIT_TAB) {
                int index = indexAt(x, y);
                if (index >= 0) pickForTab(visible.get(index).getKey().template(), button);
            } else {
                clickGrid(indexAt(x, y), button, event.hasShiftDown());
            }
            return true;
        }
        if (mode == Mode.EDIT_TAB && (button == 0 || button == 1)) {
            Slot slot = slotAt(x, y);
            if (slot != null && slot.hasItem()) {
                pickForTab(slot.getItem(), button);
                return true;
            }
        }
        if (button == 1 && searchBox.isVisible() && isHovering(7, 4, 166, 12, x, y)) {
            searchBox.setValue("");
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The upgrade column, the side buttons and the tab row also count as inside, so nothing gets thrown by mistake. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        int upgradesBottom = QuantumChestMenu.UPGRADE_Y + QuantumChestMenu.UPGRADE_SLOTS * 18 + 4;
        if (isHovering(QuantumChestMenu.UPGRADE_X - 5, QuantumChestMenu.UPGRADE_Y - 5, 40, upgradesBottom - QuantumChestMenu.UPGRADE_Y + 5, mouseX, mouseY)) {
            return false;
        }
        if (isHovering(SIDE_X, SORT_Y, 18, SIDE_C_Y + 18 - SORT_Y, mouseX, mouseY)) return false;
        if (isHovering(0, TAB_Y, imageWidth, -TAB_Y, mouseX, mouseY)) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    private boolean clickTabRow(double x, double y, int button) {
        if (tabsNeedPages() && isHovering(0, TAB_Y, ARROW_W, TAB_W, x, y)) {
            tabPage = Math.max(0, Mth.clamp(tabPage, 0, tabPages() - 1) - 1);
            return true;
        }
        if (tabsNeedPages() && isHovering(imageWidth - ARROW_W, TAB_Y, ARROW_W, TAB_W, x, y)) {
            tabPage = Math.min(tabPages() - 1, Mth.clamp(tabPage, 0, tabPages() - 1) + 1);
            return true;
        }
        int index = tabAt(x, y);
        if (index < 0) return false;
        if (mode == Mode.EDIT_TAB) return true;
        ChestTabs.Tab tab = tabs.get(index);
        if (tab.key().equals(ChestTabs.ADD)) {
            if (panelMode()) setMode(Mode.NORMAL);
            startEditing(-1);
        } else if (button == 1 && tab.isCustom() && access().canEditTabs()) {
            if (panelMode()) setMode(Mode.NORMAL);
            startEditing(tab.customIndex());
        } else {
            if (panelMode()) setMode(Mode.NORMAL);
            selectedTab = tab.key();
            scroll = 0;
            listDirty = true;
        }
        return true;
    }

    private boolean clickSideButtons(double x, double y) {
        if (onSide(SORT_Y, x, y)) {
            sortMode = sortMode.next();
            listDirty = true;
            return true;
        }
        if (onSide(ORDER_Y, x, y)) {
            reversed = !reversed;
            listDirty = true;
            return true;
        }
        if (mode == Mode.EDIT_TAB) {
            if (onSide(SIDE_A_Y, x, y)) {
                saveTab();
                return true;
            }
            if (onSide(SIDE_B_Y, x, y)) {
                setMode(Mode.NORMAL);
                return true;
            }
            if (editIndex >= 0 && onSide(SIDE_C_Y, x, y)) {
                deleteTab();
                return true;
            }
            return false;
        }
        if (access().canManage() && onSide(SIDE_A_Y, x, y)) {
            sendSettings(access().isPublic() ? ChestSettingsPayload.Action.MAKE_PRIVATE : ChestSettingsPayload.Action.MAKE_PUBLIC, "");
            return true;
        }
        if (access().canManage() && onSide(SIDE_B_Y, x, y)) {
            setMode(mode == Mode.MEMBERS ? Mode.NORMAL : Mode.MEMBERS);
            if (mode == Mode.MEMBERS) {
                memberBox.setValue("");
                setFocused(memberBox);
                memberBox.setFocused(true);
            }
            return true;
        }
        if (onSide(SIDE_C_Y, x, y)) {
            openPanel(new FilterPanel(menu, this));
            return true;
        }
        return false;
    }

    private void clickGrid(int index, int button, boolean shift) {
        if (!menu.getCarried().isEmpty()) {
            ClickAction action = button == 0 ? ClickAction.INSERT_CARRIED : ClickAction.INSERT_ONE;
            send(new ClickPayload(menu.containerId, action, ItemStack.EMPTY));
            return;
        }
        if (index < 0) return;
        ItemStack clicked = visible.get(index).getKey().template();
        ClickAction action;
        if (button == 1) {
            action = ClickAction.TAKE_HALF;
        } else {
            action = shift ? ClickAction.TAKE_TO_INVENTORY : ClickAction.TAKE_STACK;
        }
        send(new ClickPayload(menu.containerId, action, clicked));
    }

    private void clickMember(double mouseX, double mouseY) {
        if (!access().canManage()) return;
        List<String> members = access().members();
        for (int i = 0; i < members.size(); i++) {
            int x = GRID_X + 2 + (i / ROWS) * 81;
            int y = GRID_Y + 4 + (i % ROWS) * 18;
            if (isHovering(x - 1, y - 2, 10, 11, mouseX, mouseY)) {
                sendSettings(ChestSettingsPayload.Action.REMOVE_MEMBER, members.get(i));
                return;
            }
        }
    }

    // ---- tab editor --------------------------------------------------------

    private void startEditing(int customIndex) {
        editIndex = customIndex;
        editItems.clear();
        editIcon = ItemStack.EMPTY;
        nameBox.setValue("");
        tagsBox.setValue("");
        if (customIndex >= 0 && customIndex < menu.clientTabs().size()) {
            CustomTab tab = menu.clientTabs().get(customIndex);
            editItems.addAll(tab.items());
            editIcon = tab.icon();
            nameBox.setValue(tab.name());
            tagsBox.setValue(String.join(" ", tab.tags().stream().map(id -> "#" + id).toList()));
        }
        setMode(Mode.EDIT_TAB);
        setFocused(nameBox);
        nameBox.setFocused(true);
    }

    /** Left click adds or removes the item type, right click makes it the tab icon. */
    private void pickForTab(ItemStack stack, int button) {
        if (button == 1) {
            editIcon = stack.copyWithCount(1);
            return;
        }
        Identifier id = itemId(stack);
        if (!editItems.remove(id)) editItems.add(id);
    }

    private void saveTab() {
        String name = nameBox.getValue().strip();
        if (name.isEmpty()) {
            name = Component.translatable("gui.quantumchest.tab.default_name", menu.clientTabs().size() + 1).getString();
        }
        List<Identifier> tags = new ArrayList<>();
        for (String raw : tagsBox.getValue().split("[\\s,]+")) {
            String text = raw.startsWith("#") ? raw.substring(1) : raw;
            if (text.isEmpty()) continue;
            Identifier id = Identifier.tryParse(text.toLowerCase(Locale.ROOT));
            if (id != null && !tags.contains(id)) tags.add(id);
        }
        CustomTab tab = new CustomTab(name, editIcon, List.copyOf(editItems), tags);
        List<CustomTab> updated = new ArrayList<>(menu.clientTabs());
        if (editIndex >= 0 && editIndex < updated.size()) {
            updated.set(editIndex, tab);
            selectedTab = "custom:" + editIndex;
        } else {
            updated.add(tab);
            selectedTab = "custom:" + (updated.size() - 1);
        }
        send(new SetTabsPayload(menu.containerId, updated));
        setMode(Mode.NORMAL);
    }

    private void deleteTab() {
        List<CustomTab> updated = new ArrayList<>(menu.clientTabs());
        if (editIndex >= 0 && editIndex < updated.size()) {
            updated.remove(editIndex);
            send(new SetTabsPayload(menu.containerId, updated));
        }
        selectedTab = ChestTabs.ALL;
        setMode(Mode.NORMAL);
    }

    /** Player inventory slot under the mouse, or null. */
    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) return slot;
        }
        return null;
    }

    private static Identifier itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    // ---- keyboard and wheel ------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isHovering(0, TAB_Y, imageWidth, TAB_W, mouseX, mouseY) && tabsNeedPages()) {
            tabPage = Mth.clamp(tabPage - (int) Math.signum(scrollY), 0, tabPages() - 1);
            return true;
        }
        if (mode == Mode.PANEL) {
            if (numberTarget != null) commitNumber();
            if (panel.scroll((int) Math.floor(mouseX - leftPos), (int) Math.floor(mouseY - topPos), scrollY)) return true;
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int hidden = hiddenRows();
        boolean overGrid = insideGrid(mouseX, mouseY) || isHovering(SCROLL_X - 1, SCROLL_Y - 1, 14, scrollHeight() + 2, mouseX, mouseY);
        if (hidden > 0 && overGrid && !panelMode()) {
            scroll = Mth.clamp((float) (scroll - scrollY / hidden), 0f, 1f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER;
        if (numberTarget != null) {
            if (enter) {
                commitNumber();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelNumber();
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (mode != Mode.NORMAL) {
                setMode(Mode.NORMAL);
            } else {
                onClose();
            }
            return true;
        }
        if (mode == Mode.MEMBERS && memberBox.isFocused() && enter) {
            String name = memberBox.getValue().strip();
            if (!name.isEmpty()) sendSettings(ChestSettingsPayload.Action.ADD_MEMBER, name);
            memberBox.setValue("");
            return true;
        }
        if (mode == Mode.EDIT_TAB && enter) {
            saveTab();
            return true;
        }
        // While typing, keys like E must not close the screen
        for (EditBox box : boxes()) {
            if (!box.isVisible()) continue;
            if (box.keyPressed(event) || box.canConsumeInput()) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // Numbers only take digits
        if (numberBox.isVisible() && numberBox.isFocused() && !Character.isDigit(event.codepoint())) return true;
        for (EditBox box : boxes()) {
            if (box.isVisible() && box.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    // ---- sending -----------------------------------------------------------

    private void sendSettings(ChestSettingsPayload.Action action, String value) {
        send(new ChestSettingsPayload(menu.containerId, action, value));
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
    }

    // ---- numbers -----------------------------------------------------------

    /** The capacity as the server's config has it, not this game's defaults. */
    private String capacityText(boolean compact) {
        SyncStatePayload.Access access = access();
        if (access.unlimited()) return "∞";
        return compact ? compact(access.capacity()) : full(access.capacity());
    }

    /** 1234 -> 1.2k, 2500000 -> 2.5M */
    static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        String[] units = {"k", "M", "B", "T", "P", "E"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1_000 && unit < units.length - 1) {
            scaled /= 1_000;
            unit++;
        }
        String number = scaled >= 100 ? String.format(Locale.ROOT, "%.0f", scaled)
                : String.format(Locale.ROOT, "%.1f", scaled).replace(".0", "");
        return number + units[unit];
    }

    /** Full number with the player's grouping, e.g. 1.234.567 in Spanish */
    static String full(long value) {
        return String.format("%,d", value);
    }
}
