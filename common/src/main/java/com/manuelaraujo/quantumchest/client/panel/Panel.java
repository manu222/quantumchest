package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.platform.Services;
import com.manuelaraujo.quantumchest.storage.ItemMatch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;

/**
 * A settings screen drawn over the item grid of the chest: the filter and
 * each configurable upgrade. Coordinates are relative to the top left corner
 * of the chest panel, like the vanilla label layer.
 *
 * Panels use the same look everywhere: vanilla buttons, a green or grey
 * switch, ghost slots for picking items, and a number with - and + next to
 * it. Every control has a tooltip, so nothing needs explaining elsewhere.
 */
public abstract class Panel {
    // The area a panel can use: the grid and the scrollbar column
    public static final int AREA_X = 7;
    public static final int AREA_Y = 17;
    public static final int AREA_W = 182;
    public static final int AREA_H = 109;
    public static final int TITLE_Y = 6;
    public static final int FOOTER_Y = 129;

    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");

    /** Values the - and + buttons jump between. Shift moves by one instead. */
    private static final long[] STEPS = {0, 1, 8, 16, 32, 64, 128, 256, 512, 1_000, 2_000, 5_000, 10_000,
            50_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    protected final QuantumChestMenu menu;
    protected final Host host;
    protected final Font font;

    /** What a panel needs from the screen around it. */
    public interface Host {
        /** Shows a text box over a number so the player can type it. Enter or a click elsewhere confirms. */
        void editNumber(int x, int y, int width, long value, LongConsumer onDone);

        /** True if the player may change settings here (owner or operator). */
        boolean canManage();

        /** Client ticks since the screen opened, for animations. */
        int ticks();
    }

    protected Panel(QuantumChestMenu menu, Host host) {
        this.menu = menu;
        this.host = host;
        this.font = Minecraft.getInstance().font;
    }

    // ---- what each panel provides ------------------------------------------

    public abstract Component title();

    /** Still makes sense to show (the upgrade is still installed, and so on). */
    public abstract boolean available();

    public abstract void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    public abstract List<Component> tooltip(int mouseX, int mouseY);

    public abstract boolean click(int mouseX, int mouseY, int button, boolean shift);

    public boolean scroll(int mouseX, int mouseY, double amount) {
        return false;
    }

    /** Clicking an item in the player inventory while the panel is open. Return true if the panel used it. */
    public boolean pickFromInventory(ItemStack stack, int button) {
        return false;
    }

    /** The upgrade item this panel configures, to highlight its gear button. Empty for the filter. */
    public abstract ItemStack upgrade();

    public void tick() {
    }

    /** Text box search for panels with a list. Null if the panel has none. */
    public String searchHint() {
        return null;
    }

    public void search(String query) {
    }

    /** Width of the search box, so a panel can keep buttons next to it. */
    public int searchWidth() {
        return 116;
    }

    // ---- helpers -----------------------------------------------------------

    protected void send(CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
    }

    protected static boolean hover(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected static boolean shiftDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    protected static boolean ctrlDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    protected void background(GuiGraphicsExtractor graphics) {
        graphics.fill(AREA_X, AREA_Y, AREA_X + AREA_W, AREA_Y + AREA_H, 0xFF2B2B2B);
    }

    /** Light title on the dark top row, where the search box usually is. */
    protected void titleText(GuiGraphicsExtractor graphics, Component text) {
        graphics.text(font, text.getString(), 9, TITLE_Y, 0xFFE0E0E0, false);
    }

    /** The line under the panel. Leaves room for a button on the right unless {@code wide}. */
    protected void footerText(GuiGraphicsExtractor graphics, Component text) {
        footerText(graphics, text, false);
    }

    protected void footerText(GuiGraphicsExtractor graphics, Component text, boolean wide) {
        graphics.text(font, font.plainSubstrByWidth(text.getString(), wide ? AREA_W - 2 : 140), 8, FOOTER_Y, 0xFF404040, false);
    }

    /** A vanilla button. */
    protected void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean enabled,
                          int mouseX, int mouseY) {
        Identifier sprite = !enabled ? BUTTON_OFF : hover(x, y, w, h, mouseX, mouseY) ? BUTTON_HOVER : BUTTON;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        String shown = font.plainSubstrByWidth(label, w - 4);
        graphics.text(font, shown, x + w / 2 - font.width(shown) / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFFA0A0A0, true);
    }

    /** A button filled with a colour that says its state (green whitelist, red blacklist...). */
    protected void colorButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color, String label,
                               boolean enabled, int mouseX, int mouseY) {
        boolean over = enabled && hover(x, y, w, h, mouseX, mouseY);
        graphics.fill(x, y, x + w, y + h, over ? 0xFFFFFFFF : 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, enabled ? color : 0xFF4A4A4A);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
        String shown = font.plainSubstrByWidth(label, w - 4);
        graphics.text(font, shown, x + w / 2 - font.width(shown) / 2, y + (h - 8) / 2, 0xFFFFFFFF, true);
    }

    /** On/off switch, 24 x 12. The knob sits right and the track is green when on. */
    protected void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean enabled, int mouseX, int mouseY) {
        boolean over = enabled && hover(x, y, 24, 12, mouseX, mouseY);
        graphics.fill(x, y, x + 24, y + 12, over ? 0xFFFFFFFF : 0xFF000000);
        graphics.fill(x + 1, y + 1, x + 23, y + 11, !enabled ? 0xFF4A4A4A : on ? 0xFF3F9E3F : 0xFF5A5A5A);
        int knob = on ? x + 13 : x + 1;
        graphics.fill(knob, y + 1, knob + 10, y + 11, 0xFFDDDDDD);
        graphics.fill(knob, y + 10, knob + 10, y + 11, 0xFF8A8A8A);
    }

    protected static boolean onToggle(int x, int y, int mouseX, int mouseY) {
        return hover(x, y, 24, 12, mouseX, mouseY);
    }

    /**
     * A slot that only remembers an item, without holding it. A blue tint
     * tells it apart from real slots. Tag entries cycle through their items and
     * show a gold #.
     */
    protected void ghostSlot(GuiGraphicsExtractor graphics, int x, int y, ItemMatch match, int mouseX, int mouseY) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF1C1C28);
        graphics.fill(x, y, x + 16, y + 16, 0xFF5A5F7A);
        if (match != null) {
            graphics.item(displayStack(match), x, y);
            if (match.tag().isPresent()) graphics.text(font, "#", x + 11, y - 1, 0xFFFFD24A, true);
        }
        if (hover(x - 1, y - 1, 18, 18, mouseX, mouseY)) graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
    }

    /** For tag entries, one item of the tag at a time, changing every second. */
    protected ItemStack displayStack(ItemMatch match) {
        if (match.tag().isEmpty()) return match.icon();
        List<Item> items = tagItems(match.tag().get());
        if (items.isEmpty()) return match.icon();
        return new ItemStack(items.get((host.ticks() / 20) % items.size()));
    }

    protected static List<Item> tagItems(Identifier tag) {
        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))) {
            items.add(holder.value());
        }
        return items;
    }

    /**
     * Right click on a ghost slot: exact item, then each of its tags, then
     * back to the exact item. Logs of any mod become one entry this way.
     */
    protected static ItemMatch nextTag(ItemMatch match) {
        List<Identifier> tags = match.icon().tags().map(TagKey::location)
                .sorted((a, b) -> a.toString().compareTo(b.toString())).toList();
        if (tags.isEmpty()) return match;
        if (match.tag().isEmpty()) return match.withTag(Optional.of(tags.getFirst()));
        int at = tags.indexOf(match.tag().get());
        return at < 0 || at + 1 >= tags.size() ? match.withTag(Optional.empty()) : match.withTag(Optional.of(tags.get(at + 1)));
    }

    /** Tooltip lines that describe a ghost slot entry. */
    protected List<Component> matchLines(ItemMatch match) {
        List<Component> lines = new ArrayList<>();
        if (match.tag().isPresent()) {
            lines.add(Component.literal("#" + match.tag().get()).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.quantumchest.ghost.tag", tagItems(match.tag().get()).size())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(match.icon().getHoverName());
        }
        return lines;
    }

    protected List<Component> ghostHelp(boolean filled) {
        List<Component> lines = new ArrayList<>();
        if (!host.canManage()) {
            lines.add(Component.translatable("gui.quantumchest.panel.owner_only").withStyle(ChatFormatting.RED));
            return lines;
        }
        if (filled) {
            lines.add(Component.translatable("gui.quantumchest.ghost.remove").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.quantumchest.ghost.cycle_tag").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("gui.quantumchest.ghost.empty").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    // ---- numbers -----------------------------------------------------------

    /** A number with - and + around it: 11 + 2 + {@code boxW} + 2 + 11 pixels wide, 12 high. */
    protected void numberControl(GuiGraphicsExtractor graphics, int x, int y, int boxW, String value, boolean enabled,
                                 int mouseX, int mouseY) {
        button(graphics, x, y, 11, 12, "-", enabled, mouseX, mouseY);
        int bx = x + 13;
        boolean over = enabled && hover(bx, y, boxW, 12, mouseX, mouseY);
        graphics.fill(bx, y, bx + boxW, y + 12, over ? 0xFFA0A0A0 : 0xFF000000);
        graphics.fill(bx + 1, y + 1, bx + boxW - 1, y + 11, 0xFF000000);
        String shown = font.plainSubstrByWidth(value, boxW - 2);
        graphics.text(font, shown, bx + boxW / 2 - font.width(shown) / 2, y + 2, 0xFFFFFFFF, false);
        button(graphics, bx + boxW + 2, y, 11, 12, "+", enabled, mouseX, mouseY);
    }

    public enum NumberPart { NONE, MINUS, VALUE, PLUS }

    protected static NumberPart numberPart(int x, int y, int boxW, int mouseX, int mouseY) {
        if (hover(x, y, 11, 12, mouseX, mouseY)) return NumberPart.MINUS;
        if (hover(x + 13, y, boxW, 12, mouseX, mouseY)) return NumberPart.VALUE;
        if (hover(x + 15 + boxW, y, 11, 12, mouseX, mouseY)) return NumberPart.PLUS;
        return NumberPart.NONE;
    }

    protected static int numberWidth(int boxW) {
        return 28 + boxW;
    }

    protected static long stepUp(long value, boolean fine) {
        if (fine) return value + 1;
        for (long step : STEPS) {
            if (step > value) return step;
        }
        return value >= Long.MAX_VALUE / 10 ? value : value * 10;
    }

    protected static long stepDown(long value, boolean fine) {
        if (fine) return Math.max(0, value - 1);
        for (int i = STEPS.length - 1; i >= 0; i--) {
            if (STEPS[i] < value) return STEPS[i];
        }
        return 0;
    }

    /**
     * Click on a number control: -/+ jump, Shift moves by one, a click on the
     * value lets the player type it. Returns true if the click landed on it.
     */
    protected boolean clickNumber(int x, int y, int boxW, long value, boolean shift, int mouseX, int mouseY,
                                  LongConsumer onChange) {
        switch (numberPart(x, y, boxW, mouseX, mouseY)) {
            case MINUS -> onChange.accept(stepDown(value, shift));
            case PLUS -> onChange.accept(stepUp(value, shift));
            case VALUE -> host.editNumber(x + 13, y, boxW, value, onChange);
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    /** The wheel over a number moves it by one, or along the steps with Shift. */
    protected boolean scrollNumber(int x, int y, int boxW, long value, double amount, int mouseX, int mouseY,
                                   LongConsumer onChange) {
        if (numberPart(x, y, boxW, mouseX, mouseY) == NumberPart.NONE) return false;
        boolean fine = !shiftDown();
        onChange.accept(amount > 0 ? stepUp(value, fine) : stepDown(value, fine));
        return true;
    }

    protected List<Component> numberHelp(String what) {
        return List.of(Component.translatable(what),
                Component.translatable("gui.quantumchest.number.help").withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("gui.quantumchest.number.type").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 1234567 -> 1.234.567 with the player's grouping. */
    protected static String full(long value) {
        return String.format("%,d", value);
    }

    /** Short form for small boxes: 1500 -> 1.5k. */
    protected static String compact(long value) {
        if (value < 10_000) return Long.toString(value);
        String[] units = {"k", "M", "B", "T"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1_000 && unit < units.length - 1) {
            scaled /= 1_000;
            unit++;
        }
        String number = scaled >= 100 ? String.format(java.util.Locale.ROOT, "%.0f", scaled)
                : String.format(java.util.Locale.ROOT, "%.1f", scaled).replace(".0", "");
        return number + units[unit];
    }
}
