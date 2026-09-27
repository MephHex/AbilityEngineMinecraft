package me.mephisto.ability_engine.engine.loadout;

import me.mephisto.ability_engine.engine.quiver.QuiverDef;
import me.mephisto.ability_engine.engine.state.ResourceDef;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A playable character: which ability sits in which slot, and which resource pools it has.
 * Named CharacterDef (like StatusDef) to avoid clashing with java.lang.Character.
 *
 * @param weapon platform-specific item id held in the locked main-hand slot; on Bukkit a Material name
 * @param quiver    a queue of bolts the weapon loads one at a time (null = none)
 * @param statusBar a status shown on the XP bar: bar = its time left, level = its stacks or the quiver's
 *                  reload speed (null = none)
 * @param forms     while the player has a form's tag, it changes the kit (first match wins)
 * @param traits    always-on behaviours the platform provides (see {@link #TRAITS}), e.g. a passive
 */
public record CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                           Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                           java.util.List<Form> forms, java.util.Set<String> traits) {

    /** Holding sneak while falling: slow falling (the Umbrella's parasol). */
    public static final String SNEAK_SLOW_FALL = "sneak_slow_fall";
    public static final java.util.Set<String> TRAITS = java.util.Set.of(SNEAK_SLOW_FALL);

    /**
     * A temporary kit change while the player has {@code tag} (e.g. an ultimate that replaces the primary).
     * Null weapon/statusBar keep the character's own; slots replace only the slots they name.
     */
    public record Form(String tag, String weapon, Map<String, String> slots, StatusBar statusBar) {
        public Form {
            slots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        }
    }

    /**
     * @param status the status whose remaining time fills the bar
     * @param level  what the level number shows
     */
    public record StatusBar(String status, Level level) {
        public enum Level {
            /** the status's stacks */ STACKS,
            /** the quiver's reload speed */ RELOAD_SPEED,
            /** no number, just the draining bar */ NONE
        }
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots, Map<String, ResourceDef> resources) {
        this(id, name, weapon, slots, resources, null, null);
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar) {
        this(id, name, weapon, slots, resources, quiver, statusBar, java.util.List.of());
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, java.util.Set.of());
    }

    public CharacterDef {
        slots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        resources = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        forms = java.util.List.copyOf(forms);
        traits = java.util.Set.copyOf(traits);
    }

    public boolean has(String trait) { return traits.contains(trait); }

    /** This character as it is while {@code form} is active. */
    public CharacterDef in(Form form) {
        Map<String, String> changed = new LinkedHashMap<>(slots);
        changed.putAll(form.slots());
        return new CharacterDef(id, name, form.weapon() != null ? form.weapon() : weapon, changed, resources, quiver,
                form.statusBar() != null ? form.statusBar() : statusBar, forms, traits);
    }

    /** Ability id in this slot, or null if the slot is empty. */
    public String abilityIn(String slot) { return slots.get(slot); }
}
