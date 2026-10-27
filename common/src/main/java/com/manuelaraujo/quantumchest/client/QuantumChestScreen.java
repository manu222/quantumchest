package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.automation.ChestAutomation;
import com.manuelaraujo.quantumchest.automation.FaceMode;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.menu.ClickAction;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.ChestSettingsPayload;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.network.SetFacePayload;
import com.manuelaraujo.quantumchest.network.SetTabsPayload;
import com.manuelaraujo.quantumchest.network.SyncStatePayload;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.manuelaraujo.quantumchest.storage.CustomTab;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import com.manuelaraujo.quantumchest.storage.TabRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Direction;
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
import java.util.Set;
import java.util.function.Predicate;

/**
 * Screen for the chest. A 9x6 virtual grid with scroll and search, a row of
 * tabs on top and a column of buttons on the left. The grid is not made of
 * slots: it draws whatever {@link QuantumChestMenu#clientItems()} holds and
 * sends clicks to the server as packets.
 *
 * Four modes share the panel: normal use, editing a custom tab (the grid
 * becomes a picker), the member list of a private chest and the faces of the
 * I/O upgrade.
 */
public class QuantumChestScreen extends AbstractContainerScreen<QuantumChestMenu> {
    private static final Identifier TEXTURE = QuantumChest.id("textures/gui/quantum_chest.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int SCROLL_X = 175;
    private static final int SCROLL_Y = 18;
    private static final int SCROLL_H = 108;
    private static final int SIDE_X = -20;
    private static final int SORT_Y = 4;
    private static final int ORDER_Y = 24;
    private static final int SIDE_A_Y = 48;
    private static final int SIDE_B_Y = 68;
    private static final int SIDE_C_Y = 88;
    private static final int SIDE_D_Y = 108;
    // Faces panel: one row per face, a mode button and an export tab button
    private static final int FACE_MODE_X = GRID_X + 62;
    private static final int FACE_TAB_X = GRID_X + 112;
    private static final int FACE_BUTTON_W = 48;
    private static final int INFO_Y = 129;
    private static final int TAB_Y = -22;
    private static final int TAB_W = 22;
    private static final int ARROW_W = 12;
    // The tab row sits above the panel, so move the panel down a bit to make room
    private static final int TAB_ROOM = 11;

    private enum Mode { NORMAL, EDIT_TAB, MEMBERS, FACES }

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
    private EditBox limitBox;

    private Mode mode = Mode.NORMAL;
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

    // Tab editor
    private int editIndex = -1;
    private final Set<Identifier> editItems = new LinkedHashSet<>();
    private ItemStack editIcon = ItemStack.EMPTY;
    private TabRole editRole = TabRole.NORMAL;

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
        limitBox = box(116, 6, 54, 18, "gui.quantumchest.edit.limit");
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

    private void setMode(Mode newMode) {
        mode = newMode;
        nameBox.setVisible(mode == Mode.EDIT_TAB);
        tagsBox.setVisible(mode == Mode.EDIT_TAB);
        memberBox.setVisible(mode == Mode.MEMBERS);
        searchBox.setVisible(!panelMode());
        updateLimitBox();
        for (EditBox box : List.of(nameBox, tagsBox, memberBox, searchBox, limitBox)) {
            if (!box.isVisible() && box.isFocused()) {
                box.setFocused(false);
                setFocused(null);
            }
        }
        listDirty = true;
    }

    /** The two modes that replace the grid with a panel. */
    private boolean panelMode() {
        return mode == Mode.MEMBERS || mode == Mode.FACES;
    }

    /** The limit box only exists for destroy and compact tabs, and shares the top row with the search. */
    private void updateLimitBox() {
        boolean show = mode == Mode.EDIT_TAB && editRole.usesLimit();
        limitBox.setVisible(show);
        searchBox.setWidth(show ? 100 : 160);
        limitBox.setHint(Component.translatable(editRole == TabRole.COMPACT
                ? "gui.quantumchest.edit.limit.compact" : "gui.quantumchest.edit.limit.void").withStyle(ChatFormatting.DARK_GRAY));
    }

    private boolean facesAvailable() {
        return menu.clientFaces().editable() && menu.hasIoUpgrade();
    }

    private SyncStatePayload.Access access() {
        return menu.clientAccess();
    }

    // ---- the item list -----------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        if (mode == Mode.FACES && !facesAvailable()) setMode(Mode.NORMAL);
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
        return Math.max(0, rows - ROWS);
    }

    private int firstRow() {
        return Math.round(scroll * hiddenRows());
    }

    /** Index into {@link #visible} under the mouse, or -1. Coordinates are absolute. */
    private int indexAt(double mouseX, double mouseY) {
        int col = Mth.floor((mouseX - leftPos - GRID_X + 1) / 18);
        int row = Mth.floor((mouseY - topPos - GRID_Y + 1) / 18);
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return -1;
        int index = (firstRow() + row) * COLS + col;
        return index < visible.size() ? index : -1;
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return isHovering(GRID_X - 1, GRID_Y - 1, COLS * 18, ROWS * 18, mouseX, mouseY);
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

    // ---- drawing -----------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateScrollbarDrag(mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        boolean canScroll = hiddenRows() > 0 && !panelMode();
        int knobY = topPos + SCROLL_Y + (int) ((SCROLL_H - 15) * scroll);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, canScroll ? SCROLLER : SCROLLER_DISABLED,
                leftPos + SCROLL_X, knobY, 12, 15);
        for (EditBox box : List.of(searchBox, nameBox, tagsBox, memberBox, limitBox)) {
            if (box.isVisible()) box.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        drawTabs(graphics, mouseX, mouseY);
        drawSideButtons(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        drawUpgradeColumn(graphics);
        // Dark fields behind the text boxes that only exist in some modes
        if (mode == Mode.EDIT_TAB) {
            textField(graphics, 7, INFO_Y - 2, 82);
            textField(graphics, 91, INFO_Y - 2, 98);
        } else if (mode == Mode.MEMBERS) {
            textField(graphics, 7, INFO_Y - 2, 154);
        }
        if (limitBox.isVisible()) textField(graphics, 112, 4, 61);
    }

    /** The strip of upgrade slots to the right of the panel. Locked slots show a darker square. */
    private void drawUpgradeColumn(GuiGraphicsExtractor graphics) {
        int x = leftPos + QuantumChestMenu.UPGRADE_X - 1;
        int top = topPos + QuantumChestMenu.UPGRADE_Y - 1;
        int slots = QuantumChestMenu.UPGRADE_SLOTS;
        graphics.fill(x - 4, top - 4, x + 22, top + slots * 18 + 4, 0xFF000000);
        graphics.fill(x - 3, top - 3, x + 21, top + slots * 18 + 3, 0xFFC6C6C6);
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

    private void textField(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + 12, 0xFF000000);
    }

    /** Labels are drawn relative to the top left corner of the panel. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // The search box takes the top row, so the chest name goes on the row above the inventory
        if (mode == Mode.NORMAL) {
            graphics.text(font, title.getString(), 8, INFO_Y, 0xFF404040, false);
            String info = compact(menu.clientTotal()) + " / " + capacityText(menu.clientTier(), true);
            graphics.text(font, info, imageWidth - 8 - font.width(info), INFO_Y, 0xFF404040, false);
        } else if (mode == Mode.MEMBERS) {
            graphics.text(font, Component.translatable("gui.quantumchest.members", access().members().size()).getString(),
                    8, 6, 0xFF404040, false);
        } else if (mode == Mode.FACES) {
            graphics.text(font, Component.translatable("gui.quantumchest.faces").getString(), 8, 6, 0xFF404040, false);
            String speed = Component.translatable("gui.quantumchest.faces.speed",
                    ChestAutomation.Speed.of(menu.clientTier()).perSecond()).getString();
            graphics.text(font, speed, 8, INFO_Y, 0xFF404040, false);
        }

        hoveredIndex = -1;
        if (mode == Mode.MEMBERS) {
            drawMembers(graphics, mouseX, mouseY);
            return;
        }
        if (mode == Mode.FACES) {
            drawFaces(graphics, mouseX, mouseY);
            return;
        }
        int start = firstRow() * COLS;
        for (int i = 0; i < ROWS * COLS; i++) {
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

    /** The six faces, named as seen from the front of the chest. */
    private List<Direction> faceOrder() {
        Direction front = Direction.from3DDataValue(menu.clientFaces().facing());
        if (front.getAxis() == Direction.Axis.Y) front = Direction.NORTH;
        return List.of(front, front.getOpposite(), front.getClockWise(), front.getCounterClockWise(), Direction.UP, Direction.DOWN);
    }

    private static final String[] FACE_NAMES = {"front", "back", "left", "right", "up", "down"};

    private FaceSetting faceSetting(Direction side) {
        for (FaceSetting face : menu.clientFaces().sides()) {
            if (face.side() == side) return face;
        }
        return new FaceSetting(side, FaceMode.OFF, "");
    }

    private void drawFaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(GRID_X - 1, GRID_Y - 1, GRID_X - 1 + COLS * 18, GRID_Y - 1 + ROWS * 18, 0xFF2B2B2B);
        List<Direction> order = faceOrder();
        for (int i = 0; i < order.size(); i++) {
            FaceSetting face = faceSetting(order.get(i));
            int y = GRID_Y + 2 + i * 18;
            graphics.text(font, Component.translatable("gui.quantumchest.face.side." + FACE_NAMES[i]).getString(),
                    GRID_X + 3, y + 3, 0xFFFFFFFF, false);
            int modeColor = switch (face.mode()) {
                case OFF -> 0xFF555555;
                case IMPORT -> 0xFF2E6FB0;
                case EXPORT -> 0xFFC07A22;
            };
            faceButton(graphics, FACE_MODE_X, y, modeColor, Component.translatable(face.mode().translationKey()).getString(), mouseX, mouseY);
            if (face.mode() == FaceMode.EXPORT) {
                String tab = face.tab().isEmpty()
                        ? Component.translatable("gui.quantumchest.face.no_tab").getString() : face.tab();
                faceButton(graphics, FACE_TAB_X, y, face.tab().isEmpty() ? 0xFF6B3030 : 0xFF3A3A3A, tab, mouseX, mouseY);
            }
        }
    }

    private void faceButton(GuiGraphicsExtractor graphics, int x, int y, int color, String text, int mouseX, int mouseY) {
        boolean hover = isHovering(x, y, FACE_BUTTON_W, 14, mouseX, mouseY);
        graphics.fill(x, y, x + FACE_BUTTON_W, y + 14, hover ? 0xFFFFFFFF : 0xFF000000);
        graphics.fill(x + 1, y + 1, x + FACE_BUTTON_W - 1, y + 13, color);
        String shown = font.plainSubstrByWidth(text, FACE_BUTTON_W - 6);
        graphics.text(font, shown, x + FACE_BUTTON_W / 2 - font.width(shown) / 2, y + 3, 0xFFFFFFFF, false);
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
            boolean selected = tab.key().equals(selectedTab) && mode != Mode.EDIT_TAB
                    || mode == Mode.EDIT_TAB && tab.customIndex() == editIndex && editIndex >= 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, selected ? 22 : 0, 224, 22, 22, 256, 256);
            if (tab.key().equals(ChestTabs.ADD)) {
                graphics.text(font, "+", x + 11 - font.width("+") / 2, y + 7, 0xFF404040, false);
            } else {
                graphics.item(tab.icon(), x + 3, y + 3);
                int color = roleColor(roleOf(tab));
                if (color != 0) graphics.fill(x + 15, y + 3, x + 19, y + 7, color);
            }
        }
        if (tabsNeedPages()) {
            drawArrow(graphics, leftPos, y, "<");
            drawArrow(graphics, leftPos + imageWidth - ARROW_W, y, ">");
        }
    }

    private TabRole roleOf(ChestTabs.Tab tab) {
        if (!tab.isCustom() || tab.customIndex() >= menu.clientTabs().size()) return TabRole.NORMAL;
        return menu.clientTabs().get(tab.customIndex()).role();
    }

    /** The little square in the corner of a tab: green whitelist, red destroy, blue compact. */
    private static int roleColor(TabRole role) {
        return switch (role) {
            case NORMAL -> 0;
            case WHITELIST -> 0xFF3FB24A;
            case VOID -> 0xFFD03A3A;
            case COMPACT -> 0xFF3F7FD6;
        };
    }

    private static ItemStack roleIcon(TabRole role) {
        return new ItemStack(switch (role) {
            case NORMAL -> Items.BOOK;
            case WHITELIST -> Items.HOPPER;
            case VOID -> Items.CACTUS;
            case COMPACT -> Items.PISTON;
        });
    }

    /** The upgrade a role needs to do anything, or null. */
    private static ItemStack roleUpgrade(TabRole role) {
        return switch (role) {
            case VOID -> new ItemStack(ModItems.VOID_UPGRADE.get());
            case COMPACT -> new ItemStack(ModItems.COMPACTOR_UPGRADE.get());
            default -> ItemStack.EMPTY;
        };
    }

    private boolean hasUpgradeOnScreen(ItemStack upgrade) {
        for (int i = 0; i < menu.activeUpgradeSlots(); i++) {
            if (menu.slots.get(36 + i).getItem().is(upgrade.getItem())) return true;
        }
        return false;
    }

    private void drawArrow(GuiGraphicsExtractor graphics, int x, int y, String text) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 44, 224, ARROW_W, 22, 256, 256);
        graphics.text(font, text, x + ARROW_W / 2 - font.width(text) / 2 + 1, y + 7, 0xFF404040, false);
    }

    private void drawSideButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        textButton(graphics, SORT_Y, sortMode.icon(), mouseX, mouseY);
        textButton(graphics, ORDER_Y, reversed ? "↑" : "↓", mouseX, mouseY);
        if (mode == Mode.EDIT_TAB) {
            textButton(graphics, SIDE_A_Y, "✓", mouseX, mouseY);
            textButton(graphics, SIDE_B_Y, "✕", mouseX, mouseY);
            if (editIndex >= 0) itemButton(graphics, SIDE_C_Y, new ItemStack(Items.LAVA_BUCKET), mouseX, mouseY);
            itemButton(graphics, SIDE_D_Y, roleIcon(editRole), mouseX, mouseY);
        } else if (access().canManage()) {
            itemButton(graphics, SIDE_A_Y, new ItemStack(access().isPublic() ? Items.OAK_DOOR : Items.IRON_DOOR), mouseX, mouseY);
            itemButton(graphics, SIDE_B_Y, new ItemStack(Items.PLAYER_HEAD), mouseX, mouseY);
            if (facesAvailable()) itemButton(graphics, SIDE_C_Y, new ItemStack(ModItems.IO_UPGRADE.get()), mouseX, mouseY);
        }
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
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0) {
            ChestTabs.Tab t = tabs.get(tab);
            lines = new ArrayList<>(List.of(t.name()));
            if (t.isCustom()) lines.addAll(roleLines(menu.clientTabs().get(t.customIndex()).role(),
                    menu.clientTabs().get(t.customIndex()).limit()));
            if (t.isCustom() && access().canEditTabs()) {
                lines.add(Component.translatable("gui.quantumchest.tab.edit_hint").withStyle(ChatFormatting.GRAY));
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
        } else if (mode == Mode.EDIT_TAB && onSide(SIDE_D_Y, mouseX, mouseY)) {
            lines = new ArrayList<>(List.of(Component.translatable("gui.quantumchest.role")));
            lines.addAll(roleLines(editRole, -1));
            lines.add(Component.translatable("gui.quantumchest.role.toggle").withStyle(ChatFormatting.GRAY));
        } else if (mode != Mode.EDIT_TAB && access().canManage() && facesAvailable() && onSide(SIDE_C_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.faces"),
                    Component.translatable("gui.quantumchest.faces.hint").withStyle(ChatFormatting.GRAY));
        } else if (mode == Mode.FACES && faceTabAt(mouseX, mouseY) != null) {
            lines = List.of(Component.translatable("gui.quantumchest.face.tab_hint"));
        } else if (mode != Mode.EDIT_TAB && access().canManage() && onSide(SIDE_A_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable(access().isPublic() ? "gui.quantumchest.access.public" : "gui.quantumchest.access.private"),
                    Component.translatable("gui.quantumchest.access.toggle").withStyle(ChatFormatting.GRAY));
        } else if (mode != Mode.EDIT_TAB && access().canManage() && onSide(SIDE_B_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.members", access().members().size()));
        } else if (upgradeSlotAt(mouseX, mouseY) >= menu.activeUpgradeSlots()) {
            lines = List.of(Component.translatable("gui.quantumchest.upgrades.locked",
                    Component.translatable(tierForSlot(upgradeSlotAt(mouseX, mouseY)).translationKey())));
        } else if (upgradeSlotAt(mouseX, mouseY) >= 0 && menu.getCarried().isEmpty()
                && !menu.slots.get(36 + upgradeSlotAt(mouseX, mouseY)).hasItem()) {
            lines = List.of(Component.translatable("gui.quantumchest.upgrades.empty"));
        } else if (mode == Mode.NORMAL && isHovering(7, INFO_Y - 1, 181, 10, mouseX, mouseY)) {
            lines = infoTooltip();
        } else if (hoveredIndex >= 0 && menu.getCarried().isEmpty()) {
            lines = itemTooltip(visible.get(hoveredIndex));
        } else if (mode == Mode.EDIT_TAB && insideGrid(mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.quantumchest.edit.grid_hint"));
        }
        if (lines != null) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** Name, what it does, the limit and whether its upgrade is missing. {@code limit} -1 leaves it out. */
    private List<Component> roleLines(TabRole role, long limit) {
        List<Component> lines = new ArrayList<>();
        if (role == TabRole.NORMAL && limit >= 0) return lines;
        lines.add(Component.translatable(role.translationKey()).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable(role.translationKey() + ".hint").withStyle(ChatFormatting.GRAY));
        if (role.usesLimit() && limit >= 0) {
            lines.add(Component.translatable(role == TabRole.COMPACT ? "gui.quantumchest.role.keep_loose" : "gui.quantumchest.role.keep",
                    full(limit)).withStyle(ChatFormatting.GRAY));
        }
        ItemStack upgrade = roleUpgrade(role);
        if (!upgrade.isEmpty() && !hasUpgradeOnScreen(upgrade)) {
            lines.add(Component.translatable("gui.quantumchest.role.needs", upgrade.getHoverName()).withStyle(ChatFormatting.RED));
        }
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
                Component.translatable("gui.quantumchest.info.items", full(menu.clientTotal()), capacityText(tier, false)),
                Component.translatable("gui.quantumchest.info.types", full(menu.clientItems().size())),
                Component.translatable("gui.quantumchest.info.owner", owner),
                Component.translatable(access().isPublic() ? "gui.quantumchest.access.public" : "gui.quantumchest.access.private"));
    }

    // ---- input -------------------------------------------------------------

    private void updateScrollbarDrag(int mouseX, int mouseY) {
        boolean mouseDown = GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_RELEASE;
        if (!wasMouseDown && mouseDown && isHovering(SCROLL_X - 1, SCROLL_Y - 1, 14, SCROLL_H + 2, mouseX, mouseY)) {
            scrolling = hiddenRows() > 0;
        }
        if (!mouseDown) scrolling = false;
        wasMouseDown = mouseDown;
        if (scrolling) {
            scroll = Mth.clamp((mouseY - topPos - SCROLL_Y - 7.5f) / (SCROLL_H - 15f), 0f, 1f);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        int button = event.button();

        if (clickTabRow(x, y, button)) return true;
        if (clickSideButtons(x, y)) return true;
        // Locked upgrade slots aren't real slots for vanilla; without this a click
        // there would count as outside the screen and throw the cursor item away
        if (upgradeSlotAt(x, y) >= menu.activeUpgradeSlots()) return true;

        if (mode == Mode.MEMBERS) {
            if (insideGrid(x, y)) {
                clickMember(x, y);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (mode == Mode.FACES) {
            if (insideGrid(x, y)) {
                clickFace(x, y, button);
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
            startEditing(-1);
        } else if (button == 1 && tab.isCustom() && access().canEditTabs()) {
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
            if (onSide(SIDE_D_Y, x, y)) {
                editRole = editRole.next();
                updateLimitBox();
                return true;
            }
            return false;
        }
        if (access().canManage() && facesAvailable() && onSide(SIDE_C_Y, x, y)) {
            setMode(mode == Mode.FACES ? Mode.NORMAL : Mode.FACES);
            return true;
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

    /** Left click goes forward through the options, right click backwards. */
    private void clickFace(double mouseX, double mouseY, int button) {
        List<Direction> order = faceOrder();
        for (int i = 0; i < order.size(); i++) {
            FaceSetting face = faceSetting(order.get(i));
            int y = GRID_Y + 2 + i * 18;
            if (isHovering(FACE_MODE_X, y, FACE_BUTTON_W, 14, mouseX, mouseY)) {
                FaceMode next = button == 1 ? face.mode().next().next() : face.mode().next();
                sendFace(new FaceSetting(face.side(), next, next == FaceMode.EXPORT ? face.tab() : ""));
                return;
            }
            if (face.mode() == FaceMode.EXPORT && isHovering(FACE_TAB_X, y, FACE_BUTTON_W, 14, mouseX, mouseY)) {
                sendFace(new FaceSetting(face.side(), FaceMode.EXPORT, cycleTab(face.tab(), button == 1 ? -1 : 1)));
                return;
            }
        }
    }

    /** The export tab button under the mouse, or null. */
    private Direction faceTabAt(double mouseX, double mouseY) {
        List<Direction> order = faceOrder();
        for (int i = 0; i < order.size(); i++) {
            if (faceSetting(order.get(i)).mode() == FaceMode.EXPORT
                    && isHovering(FACE_TAB_X, GRID_Y + 2 + i * 18, FACE_BUTTON_W, 14, mouseX, mouseY)) return order.get(i);
        }
        return null;
    }

    /** Next custom tab name after {@code current}, with "" (none) between the last and the first. */
    private String cycleTab(String current, int step) {
        List<String> names = new ArrayList<>();
        names.add("");
        menu.clientTabs().forEach(tab -> names.add(tab.name()));
        int at = Math.max(0, names.indexOf(current));
        return names.get(Math.floorMod(at + step, names.size()));
    }

    private void sendFace(FaceSetting face) {
        send(new SetFacePayload(menu.containerId, face));
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
        editRole = TabRole.NORMAL;
        nameBox.setValue("");
        tagsBox.setValue("");
        limitBox.setValue("");
        if (customIndex >= 0 && customIndex < menu.clientTabs().size()) {
            CustomTab tab = menu.clientTabs().get(customIndex);
            editItems.addAll(tab.items());
            editIcon = tab.icon();
            editRole = tab.role();
            if (tab.limit() > 0) limitBox.setValue(Long.toString(tab.limit()));
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
        long limit = 0;
        String digits = limitBox.getValue().replaceAll("\\D", "");
        if (!digits.isEmpty()) {
            try {
                limit = Long.parseLong(digits);
            } catch (NumberFormatException tooBig) {
                limit = Long.MAX_VALUE;
            }
        }
        CustomTab tab = new CustomTab(name, editIcon, List.copyOf(editItems), tags, editRole, editRole.usesLimit() ? limit : 0);
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
            if (isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) return slot;
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
        int hidden = hiddenRows();
        boolean overGrid = insideGrid(mouseX, mouseY) || isHovering(SCROLL_X - 1, SCROLL_Y - 1, 14, SCROLL_H + 2, mouseX, mouseY);
        if (hidden > 0 && overGrid && !panelMode()) {
            scroll = Mth.clamp((float) (scroll - scrollY / hidden), 0f, 1f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (mode != Mode.NORMAL) {
                setMode(Mode.NORMAL);
            } else {
                onClose();
            }
            return true;
        }
        if (mode == Mode.MEMBERS && memberBox.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            String name = memberBox.getValue().strip();
            if (!name.isEmpty()) sendSettings(ChestSettingsPayload.Action.ADD_MEMBER, name);
            memberBox.setValue("");
            return true;
        }
        if (mode == Mode.EDIT_TAB && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            saveTab();
            return true;
        }
        // While typing, keys like E must not close the screen
        for (EditBox box : List.of(searchBox, nameBox, tagsBox, memberBox, limitBox)) {
            if (!box.isVisible()) continue;
            if (box.keyPressed(event) || box.canConsumeInput()) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // The limit only takes digits
        if (limitBox.isVisible() && limitBox.isFocused() && !Character.isDigit(event.codepoint())) return true;
        for (EditBox box : List.of(searchBox, nameBox, tagsBox, memberBox, limitBox)) {
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

    private static String capacityText(ChestTier tier, boolean compact) {
        if (tier.isUnlimited()) return "∞";
        return compact ? compact(tier.capacity()) : full(tier.capacity());
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
