package com.manuelaraujo.quantumchest.menu;

/** What the player did on the item grid. The server decides what actually happens. */
public enum ClickAction {
    /** Left click, empty cursor: pick up one stack */
    TAKE_STACK,
    /** Right click, empty cursor: pick up half a stack */
    TAKE_HALF,
    /** Shift + left click: one stack straight into the inventory */
    TAKE_TO_INVENTORY,
    /** Left click holding something: store everything on the cursor */
    INSERT_CARRIED,
    /** Right click holding something: store a single item */
    INSERT_ONE;

    private static final ClickAction[] VALUES = values();

    public static ClickAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : TAKE_STACK;
    }
}
