package com.manuelaraujo.quantumchest.client.panel;

import com.manuelaraujo.quantumchest.automation.ChestAutomation;
import com.manuelaraujo.quantumchest.automation.FaceMode;
import com.manuelaraujo.quantumchest.automation.FaceSetting;
import com.manuelaraujo.quantumchest.automation.RejectAction;
import com.manuelaraujo.quantumchest.menu.QuantumChestMenu;
import com.manuelaraujo.quantumchest.network.SetFacePayload;
import com.manuelaraujo.quantumchest.network.SetRejectPayload;
import com.manuelaraujo.quantumchest.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The six faces of the chest with the I/O upgrade, named as seen from the
 * front, plus what to do with turned away items that can't leave.
 */
public final class FacesPanel extends Panel {
    private static final String[] FACE_NAMES = {"front", "back", "left", "right", "up", "down"};
    private static final int LABEL_X = 11;
    private static final int MODE_X = 70;
    private static final int MODE_W = 54;
    private static final int TAB_X = 128;
    private static final int TAB_W = 58;
    private static final int ROW_Y = 20;
    private static final int REJECT_X = 72;
    private static final int REJECT_Y = 3;
    private static final int REJECT_W = 64;

    public FacesPanel(QuantumChestMenu menu, Host host) {
        super(menu, host);
    }

    @Override
    public Component title() {
        return Component.translatable("item.quantumchest.io_upgrade");
    }

    @Override
    public boolean available() {
        return menu.hasUpgrade(ModItems.IO_UPGRADE.get()) && menu.clientFaces().editable();
    }

    @Override
    public ItemStack upgrade() {
        return new ItemStack(ModItems.IO_UPGRADE.get());
    }

    private List<Direction> order() {
        Direction front = Direction.from3DDataValue(menu.clientFaces().facing());
        if (front.getAxis() == Direction.Axis.Y) front = Direction.NORTH;
        return List.of(front, front.getOpposite(), front.getClockWise(), front.getCounterClockWise(), Direction.UP, Direction.DOWN);
    }

    private FaceSetting face(Direction side) {
        for (FaceSetting face : menu.clientFaces().sides()) {
            if (face.side() == side) return face;
        }
        return new FaceSetting(side, FaceMode.INPUT, "");
    }

    private static int modeColor(FaceMode mode) {
        return switch (mode) {
            case INPUT -> 0xFF3C7A3C;
            case IMPORT -> 0xFF2E6FB0;
            case EXPORT -> 0xFFC07A22;
            case EJECT -> 0xFF7A45B8;
            case OFF -> 0xFF555555;
        };
    }

    private static int rejectColor(RejectAction action) {
        return switch (action) {
            case REFUSE -> 0xFF3A3A3A;
            case DROP -> 0xFF8A6A2A;
            case DESTROY -> 0xFF8A2A2A;
        };
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        background(graphics);
        graphics.text(font, Component.translatable("gui.quantumchest.reject").getString(), 9, TITLE_Y, 0xFFE0E0E0, false);
        RejectAction reject = menu.clientFaces().reject();
        colorButton(graphics, REJECT_X, REJECT_Y, REJECT_W, 14, rejectColor(reject),
                Component.translatable(reject.translationKey()).getString(), host.canManage(), mouseX, mouseY);
        List<Direction> order = order();
        for (int i = 0; i < order.size(); i++) {
            FaceSetting face = face(order.get(i));
            int y = ROW_Y + i * 18;
            graphics.text(font, Component.translatable("gui.quantumchest.face.side." + FACE_NAMES[i]).getString(),
                    LABEL_X, y + 3, 0xFFFFFFFF, false);
            colorButton(graphics, MODE_X, y, MODE_W, 14, modeColor(face.mode()),
                    Component.translatable(face.mode().translationKey()).getString(), host.canManage(), mouseX, mouseY);
            if (face.mode() == FaceMode.EXPORT) {
                String tab = face.tab().isEmpty() ? Component.translatable("gui.quantumchest.face.no_tab").getString() : face.tab();
                colorButton(graphics, TAB_X, y, TAB_W, 14, face.tab().isEmpty() ? 0xFF6B3030 : 0xFF3A3A3A, tab,
                        host.canManage(), mouseX, mouseY);
            }
        }
        footerText(graphics, Component.translatable("gui.quantumchest.faces.speed",
                ChestAutomation.Speed.of(menu.clientTier()).perSecond()), true);
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (hover(REJECT_X, REJECT_Y, REJECT_W, 14, mouseX, mouseY)) {
            RejectAction reject = menu.clientFaces().reject();
            return List.of(Component.translatable("gui.quantumchest.reject.title"),
                    Component.translatable(reject.translationKey()).withStyle(ChatFormatting.YELLOW),
                    Component.translatable(reject.translationKey() + ".hint").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.quantumchest.face.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        List<Direction> order = order();
        for (int i = 0; i < order.size(); i++) {
            FaceSetting face = face(order.get(i));
            int y = ROW_Y + i * 18;
            if (hover(MODE_X, y, MODE_W, 14, mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(face.mode().translationKey()).withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable(face.mode().translationKey() + ".hint").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.quantumchest.face.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
                return lines;
            }
            if (face.mode() == FaceMode.EXPORT && hover(TAB_X, y, TAB_W, 14, mouseX, mouseY)) {
                return List.of(Component.translatable("gui.quantumchest.face.tab_hint"),
                        Component.translatable("gui.quantumchest.face.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return null;
    }

    @Override
    public boolean click(int mouseX, int mouseY, int button, boolean shift) {
        if (!host.canManage()) return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
        if (hover(REJECT_X, REJECT_Y, REJECT_W, 14, mouseX, mouseY)) {
            RejectAction reject = menu.clientFaces().reject();
            send(new SetRejectPayload(menu.containerId, button == 1 ? reject.next().next() : reject.next()));
            return true;
        }
        List<Direction> order = order();
        for (int i = 0; i < order.size(); i++) {
            FaceSetting face = face(order.get(i));
            int y = ROW_Y + i * 18;
            if (hover(MODE_X, y, MODE_W, 14, mouseX, mouseY)) {
                FaceMode next = button == 1 ? face.mode().previous() : face.mode().next();
                send(new SetFacePayload(menu.containerId, new FaceSetting(face.side(), next, next == FaceMode.EXPORT ? face.tab() : "")));
                return true;
            }
            if (face.mode() == FaceMode.EXPORT && hover(TAB_X, y, TAB_W, 14, mouseX, mouseY)) {
                send(new SetFacePayload(menu.containerId, new FaceSetting(face.side(), FaceMode.EXPORT,
                        cycleTab(face.tab(), button == 1 ? -1 : 1))));
                return true;
            }
        }
        return hover(AREA_X, AREA_Y, AREA_W, AREA_H, mouseX, mouseY);
    }

    /** Next custom tab name after {@code current}, with "" (none) between the last and the first. */
    private String cycleTab(String current, int step) {
        List<String> names = new ArrayList<>();
        names.add("");
        menu.clientTabs().forEach(tab -> names.add(tab.name()));
        int at = Math.max(0, names.indexOf(current));
        return names.get(Math.floorMod(at + step, names.size()));
    }
}
