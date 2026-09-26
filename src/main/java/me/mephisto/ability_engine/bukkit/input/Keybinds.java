package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.loadout.Slots;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * InputAction -> character slot, from config.yml. Same for every player. Several actions may
 * point at the same slot (F and 7 both fire the ultimate).
 */
public final class Keybinds {

    /** Bump when the default scheme changes, so old config.yml files don't silently win. */
    public static final int VERSION = 3;

    private final Map<InputAction, String> slots = new EnumMap<>(InputAction.class);

    public Keybinds() {
        setDefaults();
    }

    private void setDefaults() {
        slots.clear();
        slots.put(InputAction.LEFT_CLICK, Slots.PRIMARY);
        slots.put(InputAction.RIGHT_CLICK, Slots.SECONDARY);
        slots.put(InputAction.HOTBAR_1, Slots.ABILITY_1);
        slots.put(InputAction.HOTBAR_2, Slots.ABILITY_2);
        slots.put(InputAction.HOTBAR_3, Slots.ABILITY_3);
        slots.put(InputAction.SWAP_HANDS, Slots.ULTIMATE);
        slots.put(InputAction.HOTBAR_7, Slots.ULTIMATE);
        // DROP (Q): free.
    }

    /** Load from the root of config.yml. An outdated or missing keybinds section keeps the defaults. */
    public void load(ConfigurationSection config, Logger log) {
        setDefaults();
        if (config == null) return;
        ConfigurationSection section = config.getConfigurationSection("keybinds");
        if (section == null) return;
        if (config.getInt("keybinds-version", 1) < VERSION) {
            log.warning("config.yml has keybinds from an older control scheme; using the new defaults "
                    + "(LMB primary, RMB secondary, 1/2/3 abilities, F ultimate). Delete config.yml to get the new file.");
            return;
        }
        slots.clear();
        for (String key : section.getKeys(false)) {
            Optional<InputAction> action = InputAction.fromConfig(key);
            String slot = section.getString(key);
            if (action.isEmpty()) {
                log.warning("keybinds." + key + ": unknown action (left_click, right_click, swap_hands, drop, hotbar_1..hotbar_9)");
            } else if ("none".equalsIgnoreCase(slot)) {
                slots.remove(action.get());
            } else if (!Slots.ALL.contains(slot)) {
                log.warning("keybinds." + key + ": unknown slot '" + slot + "', expected one of " + Slots.ALL + " or none");
            } else {
                slots.put(action.get(), slot);
            }
        }
    }

    public Optional<String> slotFor(InputAction action) { return Optional.ofNullable(slots.get(action)); }

    /** The first action bound to a slot, in InputAction order (so F beats 7 for the ultimate's label). */
    public Optional<InputAction> actionFor(String slot) {
        return slots.entrySet().stream().filter(e -> e.getValue().equals(slot)).map(Map.Entry::getKey).findFirst();
    }
}
