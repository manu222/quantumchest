package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.QuantumChest;
import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.menu.ClickAction;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.ClickPayload;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.storage.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Screen for the chest: a 9x6 virtual grid with scroll, a search box and
 * two sort buttons on the left. The grid is not made of slots: it draws
 * whatever {@link QuantumChestMenu#clientItems()} holds and sends clicks
 * to the server as packets.
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
    private static final int SORT_BUTTON_X = -20;
    private static final int SORT_BUTTON_Y = 4;
    private static final int DIR_BUTTON_Y = 24;
    private static final int INFO_Y = 129;

    // Remembered while the game is running, so every chest opens the same way
    private static SortMode sortMode = SortMode.AMOUNT;
    private static boolean reversed;
    private static String lastSearch = "";

    private final ItemSearch search = new ItemSearch();
    private EditBox searchBox;
    private List<Map.Entry<ItemKey, Long>> visible = List.of();
    private int seenVersion = -1;
    private String seenQuery;
    private boolean listDirty = true;
    private float scroll;
    private boolean scrolling;
    private boolean wasMouseDown;
    private int hoveredIndex = -1;

    public QuantumChestScreen(QuantumChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 195, 222);
        this.inventoryLabelY = INFO_Y;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 82, topPos + 6, 88, font.lineHeight,
                Component.translatable("gui.quantumchest.search"));
        searchBox.setMaxLength(100);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setValue(lastSearch);
        addWidget(searchBox);
        listDirty = true;
    }

    // ---- the item list -----------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        String query = searchBox.getValue();
        if (listDirty || menu.clientVersion() != seenVersion || !query.equals(seenQuery)) {
            if (!query.equals(seenQuery)) scroll = 0;
            rebuildList(query);
        }
    }

    private void rebuildList(String query) {
        Predicate<ItemKey> filter = search.parse(query);
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

    // ---- drawing -----------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateScrollbarDrag(mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        boolean canScroll = hiddenRows() > 0;
        int knobY = topPos + SCROLL_Y + (int) ((SCROLL_H - 15) * scroll);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, canScroll ? SCROLLER : SCROLLER_DISABLED,
                leftPos + SCROLL_X, knobY, 12, 15);
        searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);

        drawSideButton(graphics, SORT_BUTTON_Y, sortMode.icon(), mouseX, mouseY);
        drawSideButton(graphics, DIR_BUTTON_Y, reversed ? "↑" : "↓", mouseX, mouseY);

        if (isHovering(SORT_BUTTON_X, SORT_BUTTON_Y, 18, 18, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.quantumchest.sort"),
                    Component.translatable(sortMode.translationKey())), mouseX, mouseY);
        } else if (isHovering(SORT_BUTTON_X, DIR_BUTTON_Y, 18, 18, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable(reversed ? "gui.quantumchest.order.reversed" : "gui.quantumchest.order.normal")),
                    mouseX, mouseY);
        } else if (isHovering(90, INFO_Y - 1, 98, 10, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, infoTooltip(), mouseX, mouseY);
        } else if (hoveredIndex >= 0 && menu.getCarried().isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, itemTooltip(visible.get(hoveredIndex)), mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    /** Labels are drawn relative to the top left corner of the panel. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        String info = compact(menu.clientTotal()) + " / " + capacityText(menu.clientTier(), true);
        graphics.text(font, info, imageWidth - 8 - font.width(info), INFO_Y, 0xFF404040, false);

        hoveredIndex = -1;
        int start = firstRow() * COLS;
        for (int i = 0; i < ROWS * COLS; i++) {
            int index = start + i;
            int x = GRID_X + (i % COLS) * 18;
            int y = GRID_Y + (i / COLS) * 18;
            if (index < visible.size()) {
                Map.Entry<ItemKey, Long> entry = visible.get(index);
                ItemStack stack = entry.getKey().template();
                graphics.item(stack, x, y);
                graphics.itemDecorations(font, stack, x, y);
                drawAmount(graphics, entry.getValue(), x, y);
            }
            if (isHovering(x - 1, y - 1, 18, 18, mouseX, mouseY)) {
                graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                if (index < visible.size()) hoveredIndex = index;
            }
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

    private void drawSideButton(GuiGraphicsExtractor graphics, int y, String icon, int mouseX, int mouseY) {
        boolean hover = isHovering(SORT_BUTTON_X, y, 18, 18, mouseX, mouseY);
        int x = leftPos + SORT_BUTTON_X;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, topPos + y, 196, hover ? 18 : 0, 18, 18, 256, 256);
        graphics.text(font, icon, x + 9 - font.width(icon) / 2, topPos + y + 5, 0xFF404040, false);
    }

    private List<Component> itemTooltip(Map.Entry<ItemKey, Long> entry) {
        Minecraft mc = Minecraft.getInstance();
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        List<Component> lines = new ArrayList<>(entry.getKey().template()
                .getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, flag));
        lines.add(Component.translatable("gui.quantumchest.amount", full(entry.getValue())));
        return lines;
    }

    private List<Component> infoTooltip() {
        ChestTier tier = menu.clientTier();
        String owner = menu.clientOwner().isEmpty() ? "-" : menu.clientOwner();
        return List.of(
                Component.translatable("gui.quantumchest.info.tier", Component.translatable(tier.translationKey())),
                Component.translatable("gui.quantumchest.info.items", full(menu.clientTotal()), capacityText(tier, false)),
                Component.translatable("gui.quantumchest.info.types", full(menu.clientItems().size())),
                Component.translatable("gui.quantumchest.info.owner", owner));
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

        if (isHovering(SORT_BUTTON_X, SORT_BUTTON_Y, 18, 18, x, y)) {
            sortMode = sortMode.next();
            listDirty = true;
            return true;
        }
        if (isHovering(SORT_BUTTON_X, DIR_BUTTON_Y, 18, 18, x, y)) {
            reversed = !reversed;
            listDirty = true;
            return true;
        }
        if (insideGrid(x, y) && (button == 0 || button == 1)) {
            clickGrid(indexAt(x, y), button, event.hasShiftDown());
            return true;
        }
        if (button == 1 && isHovering(80, 4, 93, 12, x, y)) {
            searchBox.setValue("");
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void clickGrid(int index, int button, boolean shift) {
        if (!menu.getCarried().isEmpty()) {
            ClickAction action = button == 0 ? ClickAction.INSERT_CARRIED : ClickAction.INSERT_ONE;
            send(action, ItemStack.EMPTY);
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
        send(action, clicked);
    }

    private void send(ClickAction action, ItemStack stack) {
        Services.NETWORK.sendToServer(new ClickPayload(menu.containerId, action, stack));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int hidden = hiddenRows();
        if (hidden > 0 && insideGrid(mouseX, mouseY) || hidden > 0 && isHovering(SCROLL_X - 1, SCROLL_Y - 1, 14, SCROLL_H + 2, mouseX, mouseY)) {
            scroll = Mth.clamp((float) (scroll - scrollY / hidden), 0f, 1f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        // While typing, keys like E must not close the screen
        if (searchBox.keyPressed(event) || searchBox.canConsumeInput()) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox.charTyped(event)) return true;
        return super.charTyped(event);
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
