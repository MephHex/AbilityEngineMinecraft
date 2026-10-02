package me.mephisto.ability_engine.engine.stats;

import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.ActiveStatus;
import me.mephisto.ability_engine.engine.status.StatusManager;

import java.util.UUID;

/**
 * Everyone's stats: a character's sheet ({@code stats:} in its kit), else the defaults (max HP from the
 * platform when it knows it: a mob's own health).
 * <p>Armor: damage taken x {@code constant / (constant + armor)}, so each armor point is +1% effective HP
 * (constant 100: 25 armor = 20% less damage, 100 armor = 50% less).
 */
public final class StatSheets {

    public static final double DEFAULT_ARMOR_CONSTANT = 100;

    private final LoadoutManager loadouts;
    private final WorldQuery world;
    private final StatusManager statuses;
    private double armorConstant = DEFAULT_ARMOR_CONSTANT;

    public StatSheets(LoadoutManager loadouts, WorldQuery world, StatusManager statuses) {
        this.loadouts = loadouts;
        this.world = world;
        this.statuses = statuses;
    }

    public void setArmorConstant(double constant) { this.armorConstant = constant > 0 ? constant : DEFAULT_ARMOR_CONSTANT; }

    public double armorConstant() { return armorConstant; }

    /** The sheet of the entity's character (or the form they're in, when it has stats), or the defaults. */
    public CharacterDef.Stats of(UUID entity) {
        return loadouts.sheetOf(entity).map(CharacterDef::stats).orElse(CharacterDef.Stats.DEFAULT);
    }

    public boolean hasSheet(UUID entity) { return loadouts.baseCharacterOf(entity).isPresent(); }

    /** Max HP (design HP): a character's sheet, else what the platform says, else the default. */
    public double maxHealth(UUID entity) {
        if (hasSheet(entity)) return of(entity).health();
        return world.maxHealth(entity).orElse(CharacterDef.Stats.DEFAULT.health());
    }

    public double armor(UUID entity) { return hasSheet(entity) ? of(entity).armor() : 0; }

    public double baseDamage(UUID entity) { return of(entity).baseDamage(); }

    /** What's left of a hit after armor: x constant / (constant + armor). */
    public double afterArmor(UUID victim, double amount) {
        double armor = armor(victim);
        return armor <= 0 ? amount : amount * armorConstant / (armorConstant + armor);
    }

    /** Share of damage armor takes off (0..1), for display. */
    public double armorReduction(UUID entity) {
        double armor = armor(entity);
        return armor / (armorConstant + armor);
    }

    /**
     * How much faster the entity's basic attacks come right now: its statuses' {@code attack_speed}
     * multiplied together (Paralysis 0.6: 40% slower). 1 = normal. Unstoppable, debuffs don't slow them.
     */
    public double attackSpeedMultiplier(UUID entity) {
        double m = 1;
        for (ActiveStatus s : statuses.on(entity)) {
            if (s.def().attackSpeed() < 1 && statuses.crowdControlSuppressed(entity, s)) continue;
            m *= s.def().attackSpeed();
        }
        return m;
    }

    /**
     * The entity's statuses' {@code move_speed}, each to the power of its stacks, multiplied together
     * (a slow that builds up). 1 = normal. The platform applies it on top of the sheet's move speed. Unstoppable,
     * debuffs don't slow them (a slowing poison still poisons).
     */
    public double moveSpeedMultiplier(UUID entity) {
        double m = 1;
        for (ActiveStatus s : statuses.on(entity)) {
            if (s.def().moveSpeed() < 1 && statuses.crowdControlSuppressed(entity, s)) continue;
            if (s.def().moveSpeed() != 1) m *= Math.pow(s.def().moveSpeed(), Math.max(1, s.stacks()));
        }
        return m;
    }

    /** The Jump Boost level the entity's statuses give it right now ({@code jump_boost}: the highest one); 0 = none. */
    public int jumpBoost(UUID entity) {
        int level = 0;
        for (ActiveStatus s : statuses.on(entity)) level = Math.max(level, s.def().jumpBoost());
        return level;
    }

    /**
     * Cooldown of an ability for this caster: the character's primary follows their attack speed
     * ({@code 20 / attack_speed} ticks) when the sheet has one; everything else its own cooldown.
     * Basic attacks (whatever is in the primary / melee slot now) are then divided by
     * {@link #attackSpeedMultiplier}.
     */
    public int cooldownTicks(UUID caster, String abilityId, int ownCooldown) {
        int ticks = loadouts.sheetOf(caster)
                .filter(c -> c.stats().attackSpeed() > 0 && abilityId.equals(c.abilityIn(Slots.PRIMARY)))
                .map(c -> Math.max(1, (int) Math.round(20 / c.stats().attackSpeed())))
                .orElse(ownCooldown);
        boolean basic = loadouts.characterOf(caster)
                .map(c -> abilityId.equals(c.abilityIn(Slots.PRIMARY)) || abilityId.equals(c.abilityIn(Slots.MELEE)))
                .orElse(false);
        double m = basic ? attackSpeedMultiplier(caster) : 1;
        return m == 1 || ticks <= 0 ? ticks : Math.max(1, (int) Math.round(ticks / m));
    }
}
