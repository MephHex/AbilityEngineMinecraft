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
 * @param stats     max HP, armor, base damage, move speed, attack speed
 * @param ward      a passive debuff immunity that recharges out of combat (null = none)
 * @param statusItems statuses shown as hotbar items while the player has them (e.g. a weapon infusion)
 * @param whenHit   a passive reaction to enemy basic attacks landing on them (null = none)
 */
public record CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                           Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                           java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward,
                           java.util.List<StatusItem> statusItems, WhenHit whenHit) {

    /**
     * A status shown as a hotbar item while the player has it, its stack size = the status's stacks (e.g.
     * the infused shots left). Several may share a slot: the first one the player has shows.
     *
     * @param glintWeapon the weapon glints (looks enchanted) while the player has it
     */
    public record StatusItem(String status, int hotbarSlot, String icon, String name,
                             java.util.List<String> description, boolean glintWeapon) {
        public StatusItem {
            description = java.util.List.copyOf(description);
        }
    }

    /**
     * Debuff immunity: after {@code outOfCombatTicks} without dealing or taking damage, the next debuff
     * doesn't land; blocking one uses it up, and the out-of-combat timer starts over.
     *
     * @param hotbarSlot 1-9: where its item shows (glints when ready, counts down otherwise); 0 = hidden
     * @param absorb     0: blocks the next debuff. Above 0 it's a BARRIER instead: it takes this share off the
     *                   next hit's damage (0.5 = half), and debuffs land as usual
     */
    public record Ward(String name, int outOfCombatTicks, int hotbarSlot, String icon, java.util.List<String> description,
                       double absorb) {
        public Ward {
            description = java.util.List.copyOf(description);
        }

        public Ward(String name, int outOfCombatTicks, int hotbarSlot, String icon, java.util.List<String> description) {
            this(name, outOfCombatTicks, hotbarSlot, icon, description, 0);
        }

        public boolean isBarrier() { return absorb > 0; }
    }

    /**
     * A passive reaction to being hit by an enemy's BASIC attack (primary / secondary / melee): the holder's
     * cooldowns in {@code slots} go down by {@code reduceCooldownTicks}.
     */
    public record WhenHit(int reduceCooldownTicks, java.util.List<String> slots) {
        public WhenHit {
            slots = java.util.List.copyOf(slots);
        }
    }

    /**
     * A character's stat sheet (design HP, like damage).
     *
     * @param health      max HP
     * @param armor       damage taken x constant / (constant + armor), constant 100 by default
     * @param baseDamage  what {@code base: 1.1} on a damage effect is 110% of
     * @param moveSpeed   x vanilla walking speed; slows and haste multiply on top of it
     * @param attackSpeed basic attacks per second: the primary's cooldown becomes 20 / this ticks;
     *                    0 = the primary's own cooldown (e.g. a crossbow, whose draw is its fire rate)
     * @param scale       model size (1.0 = normal; the hitbox grows with it)
     */
    public record Stats(double health, double armor, double baseDamage, double moveSpeed, double attackSpeed,
                        double scale) {
        public static final Stats DEFAULT = new Stats(200, 0, 40, 1.0, 0, 1.0);

        public Stats(double health, double armor, double baseDamage, double moveSpeed, double attackSpeed) {
            this(health, armor, baseDamage, moveSpeed, attackSpeed, 1.0);
        }
    }

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

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, Stats.DEFAULT);
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits, Stats stats) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, stats, null);
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, stats, ward, java.util.List.of());
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward,
                        java.util.List<StatusItem> statusItems) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, stats, ward, statusItems, null);
    }

    public CharacterDef {
        slots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        resources = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        forms = java.util.List.copyOf(forms);
        traits = java.util.Set.copyOf(traits);
        if (stats == null) stats = Stats.DEFAULT;
        statusItems = statusItems == null ? java.util.List.of() : java.util.List.copyOf(statusItems);
    }

    public boolean has(String trait) { return traits.contains(trait); }

    /** This character as it is while {@code form} is active. */
    public CharacterDef in(Form form) {
        Map<String, String> changed = new LinkedHashMap<>(slots);
        changed.putAll(form.slots());
        return new CharacterDef(id, name, form.weapon() != null ? form.weapon() : weapon, changed, resources, quiver,
                form.statusBar() != null ? form.statusBar() : statusBar, forms, traits, stats, ward, statusItems, whenHit);
    }

    /** Ability id in this slot, or null if the slot is empty. */
    public String abilityIn(String slot) { return slots.get(slot); }
}
