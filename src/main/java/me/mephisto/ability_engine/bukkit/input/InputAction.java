package me.mephisto.ability_engine.bukkit.input;

import java.util.Locale;
import java.util.Optional;

/**
 * Vanilla actions the server can actually see. Raw keys never reach the server; players choose
 * which key performs each action in their own Controls menu (e.g. "Hotbar Slot 1" on a mouse button).
 * Declaration order matters: the first action bound to a slot is the one shown on its icon.
 */
public enum InputAction {
    LEFT_CLICK("LMB"),
    RIGHT_CLICK("RMB"),
    SWAP_HANDS("F"),
    DROP("Q"),
    HOTBAR_1("1"),
    HOTBAR_2("2"),
    HOTBAR_3("3"),
    HOTBAR_4("4"),
    HOTBAR_5("5"),
    HOTBAR_6("6"),
    HOTBAR_7("7"),
    HOTBAR_8("8"),
    HOTBAR_9("9");

    private final String defaultKey;

    InputAction(String defaultKey) {
        this.defaultKey = defaultKey;
    }

    /** Label for tooltips. The player may have rebound it, but this is what most will press. */
    public String defaultKey() { return defaultKey; }

    public String configName() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<InputAction> fromConfig(String name) {
        for (InputAction a : values()) if (a.configName().equalsIgnoreCase(name)) return Optional.of(a);
        return Optional.empty();
    }

    /** The action for a hotbar slot index (0-8). */
    public static InputAction hotbar(int index) {
        return values()[HOTBAR_1.ordinal() + index];
    }
}
