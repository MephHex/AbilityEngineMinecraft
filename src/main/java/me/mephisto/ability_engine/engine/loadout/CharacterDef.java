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
 * @param hover     a passive flight, at most so high above the ground (null = none)
 * @param lowHealth a passive status kept on them while they're low on health (null = none)
 */
public record CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                           Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                           java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward,
                           java.util.List<StatusItem> statusItems, WhenHit whenHit, Hearing hearing, Hover hover,
                           java.util.List<Variant> variants, LowHealth lowHealth) {

    /** While their health is below {@code below} (a share of max HP), {@code status} is kept on them (e.g. a frenzy). */
    public record LowHealth(double below, String status) {}

    /**
     * While they have the tag {@code whileTag}, their abilities look different: each cue {@code x} plays as
     * {@code x<cueSuffix>} where the platform has one (e.g. "_blue": blue flames instead of orange ones), and a
     * projectile shown as a key of {@code visuals} is shown as its value. The first variant that applies wins.
     */
    public record Variant(String whileTag, String cueSuffix, Map<String, String> visuals) {
        public Variant {
            visuals = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(visuals));
        }
    }

    /**
     * A passive hover (the platform's), one of three kinds:
     * <ul>
     *   <li>{@code height: 0}: no hover at all, just the {@code visual} at their feet (they walk as usual)</li>
     *   <li>{@code fly: false}: always floating {@code height} blocks (whole blocks) above the ground, walking
     *       at that level; no flying</li>
     *   <li>{@code fly: true}: flying at will, but at most {@code height} blocks above the ground below;
     *       higher, they're brought back down. It holds off during {@code state.dashing}, and they can't fly
     *       while they can't move</li>
     * </ul>
     * Free flight ({@code state.flying}) overrides either while it lasts.
     *
     * @param speed  flying speed (fly: true), x vanilla (creative) flight, scaled by their move speed and slows
     * @param visual what they ride, shown under their feet (a platform visual id, e.g. an item; null = nothing)
     * @param visualSize its size, x the default (1.0: as wide as the character, by their scale)
     * @param visualLead how many ticks of their movement it's drawn ahead of them (it would trail behind: the
     *                   platform glides it to each new spot); 0 = right at their feet
     * @param visualTurn degrees it's turned about its upright axis, away from facing where they face (e.g. so a
     *                   petal isn't straight ahead of them); 0 = not turned
     */
    public record Hover(double height, double speed, String visual, boolean fly, double visualSize, double visualLead,
                        double visualTurn) {
        public static final double DEFAULT_LEAD = 2;

        public Hover(double height, double speed, String visual, boolean fly, double visualSize, double visualLead) {
            this(height, speed, visual, fly, visualSize, visualLead, 0);
        }
        public Hover(double height, double speed, String visual, boolean fly, double visualSize) {
            this(height, speed, visual, fly, visualSize, DEFAULT_LEAD);
        }
        public Hover(double height, double speed, String visual, boolean fly) { this(height, speed, visual, fly, 1.0); }
        public Hover(double height, double speed, String visual) { this(height, speed, visual, true); }
    }

    /**
     * A passive that "hears" wounded enemies: enemies below {@code belowHealth} (a share of max HP) within
     * {@code range} blocks are heard. Moving toward a heard enemy within {@code towardRange} keeps
     * {@code towardStatus} on the holder (e.g. faster). The platform shows them (a glow only the holder
     * sees) and the count in hotbar slot {@code hotbarSlot}.
     */
    public record Hearing(double belowHealth, double range, double towardRange, String towardStatus,
                          int hotbarSlot, String icon, String name, java.util.List<String> description) {
        public Hearing {
            description = java.util.List.copyOf(description);
        }
    }

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
                       double absorb, String cast, int cooldownTicks) {
        public Ward {
            description = java.util.List.copyOf(description);
        }

        public Ward(String name, int outOfCombatTicks, int hotbarSlot, String icon, java.util.List<String> description) {
            this(name, outOfCombatTicks, hotbarSlot, icon, description, 0);
        }

        public Ward(String name, int outOfCombatTicks, int hotbarSlot, String icon, java.util.List<String> description,
                    double absorb) {
            this(name, outOfCombatTicks, hotbarSlot, icon, description, absorb, null, 0);
        }

        public boolean isBarrier() { return absorb > 0; }

        /**
         * A REFLEX ({@code cast:}): instead of blocking a debuff, the next enemy hit that lands casts this ability
         * (at whoever hit them). Ready again {@code cooldownTicks} after it went off, or sooner after
         * {@code outOfCombatTicks} out of combat.
         */
        public boolean isReflex() { return cast != null; }

        /** The plain kind: debuff immunity (neither a barrier nor a reflex). */
        public boolean isImmunity() { return !isBarrier() && !isReflex(); }
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
     * A kit change while the player has {@code tag} (e.g. an ultimate that replaces the primary, or a stage of growth).
     * Null weapon/statusBar/stats keep the character's own; slots replace only the slots they name, and a slot
     * mapped to null is emptied (none).
     */
    public record Form(String tag, String weapon, Map<String, String> slots, StatusBar statusBar, Stats stats) {
        public Form {
            slots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        }

        public Form(String tag, String weapon, Map<String, String> slots, StatusBar statusBar) {
            this(tag, weapon, slots, statusBar, null);
        }
    }

    /**
     * @param status the status shown
     * @param level  what the level number shows
     * @param fill   what fills the bar
     */
    public record StatusBar(String status, Level level, Fill fill) {
        public enum Level {
            /** the status's stacks */ STACKS,
            /** the quiver's reload speed */ RELOAD_SPEED,
            /** no number, just the draining bar */ NONE,
            /** the seconds it has left (a countdown) */ SECONDS
        }

        public enum Fill {
            /** its time left, draining */ TIME,
            /** its stacks out of its max_stacks (a gauge) */ STACKS
        }

        public StatusBar(String status, Level level) {
            this(status, level, Fill.TIME);
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

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward,
                        java.util.List<StatusItem> statusItems, WhenHit whenHit) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, stats, ward, statusItems, whenHit, null);
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward,
                        java.util.List<StatusItem> statusItems, WhenHit whenHit, Hearing hearing) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, stats, ward, statusItems, whenHit,
                hearing, null);
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward,
                        java.util.List<StatusItem> statusItems, WhenHit whenHit, Hearing hearing, Hover hover) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, stats, ward, statusItems, whenHit,
                hearing, hover, java.util.List.of());
    }

    public CharacterDef(String id, String name, String weapon, Map<String, String> slots,
                        Map<String, ResourceDef> resources, QuiverDef quiver, StatusBar statusBar,
                        java.util.List<Form> forms, java.util.Set<String> traits, Stats stats, Ward ward,
                        java.util.List<StatusItem> statusItems, WhenHit whenHit, Hearing hearing, Hover hover,
                        java.util.List<Variant> variants) {
        this(id, name, weapon, slots, resources, quiver, statusBar, forms, traits, stats, ward, statusItems, whenHit,
                hearing, hover, variants, null);
    }

    public CharacterDef {
        variants = variants == null ? java.util.List.of() : java.util.List.copyOf(variants);
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
        form.slots().forEach((slot, ability) -> {
            if (ability == null) changed.remove(slot); // none: this form has nothing there
            else changed.put(slot, ability);
        });
        return new CharacterDef(id, name, form.weapon() != null ? form.weapon() : weapon, changed, resources, quiver,
                form.statusBar() != null ? form.statusBar() : statusBar, forms, traits, form.stats() != null ? form.stats() : stats,
                ward, statusItems, whenHit, hearing, hover, variants, lowHealth);
    }

    /** Ability id in this slot, or null if the slot is empty. */
    public String abilityIn(String slot) { return slots.get(slot); }
}
