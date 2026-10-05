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

    /** For % max HP damage, a mob's (non-player's) max HP counts as at most this (design HP). */
    private double mobMaxHpCap = 200;

    public void setMobMaxHpCap(double cap) { this.mobMaxHpCap = cap > 0 ? cap : Double.MAX_VALUE; }

    public double mobMaxHpCap() { return mobMaxHpCap; }

    public double armorConstant() { return armorConstant; }

    /** The sheet of the entity's character (or the form they're in, when it has stats), or the defaults. */
    public CharacterDef.Stats of(UUID entity) {
        return loadouts.sheetOf(entity).map(CharacterDef::stats).orElse(CharacterDef.Stats.DEFAULT);
    }

    public boolean hasSheet(UUID entity) { return loadouts.baseCharacterOf(entity).isPresent(); }

    /** Max HP (design HP): a character's sheet, else what the platform says, else the default. */
    public double maxHealth(UUID entity) {
        if (hasSheet(entity)) return of(entity).health() * maxHealthMultiplier(entity);
        return world.maxHealth(entity).orElse(CharacterDef.Stats.DEFAULT.health());
    }

    /** The sheet's armor, x the holder's statuses' {@code armor} (each to the power of its stacks: a shred). */
    public double armor(UUID entity) {
        if (!hasSheet(entity)) return 0;
        double armor = of(entity).armor();
        for (ActiveStatus s : statuses.on(entity)) {
            if (s.def().armor() != 1) armor *= Math.pow(s.def().armor(), Math.max(1, s.stacks()));
        }
        return armor;
    }

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
     * How much faster the entity's basic attacks come right now: its statuses' {@code attack_speed}, each to the power
     * of its stacks, multiplied together (Paralysis 0.6: 40% slower; Fervor's 1.08 x 5 stacks: 47% faster). 1 = normal.
     * Unstoppable, debuffs don't slow them.
     */
    public double attackSpeedMultiplier(UUID entity) {
        double m = 1;
        for (ActiveStatus s : statuses.on(entity)) {
            if (s.def().attackSpeed() < 1 && statuses.crowdControlSuppressed(entity, s)) continue;
            if (s.def().attackSpeed() != 1) m *= Math.pow(s.def().attackSpeed(), Math.max(1, s.stacks()));
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

    /**
     * How much of the healing the entity gets lands right now: its statuses' {@code healing_taken} multiplied together
     * (a poison's 0.6: 40% less). 1 = all of it. Every heal counts: heal effects, lifesteal, vanilla regeneration.
     */
    public double healingMultiplier(UUID entity) {
        double m = 1;
        for (ActiveStatus s : statuses.on(entity)) m *= s.def().healingTaken();
        return m;
    }

    /** The Jump Boost level the entity's statuses give it right now ({@code jump_boost}: the highest one); 0 = none. */
    public int jumpBoost(UUID entity) {
        int level = 0;
        for (ActiveStatus s : statuses.on(entity)) level = Math.max(level, s.def().jumpBoost());
        return level;
    }

    /** The holder's statuses' {@code max_health}, multiplied together (1 = none). Characters only. */
    public double maxHealthMultiplier(UUID entity) {
        double m = 1;
        for (ActiveStatus s : statuses.on(entity)) m *= s.def().extras().maxHealth();
        return m;
    }

    /**
     * What the holder's ability cooldowns are multiplied by: their statuses' {@code cooldown_reduction}, each to the power
     * of its stacks, multiplied together (two 0.95s: x0.9025). Always above 0; 1 = none.
     */
    public double cooldownMultiplier(UUID entity) {
        double m = 1;
        for (ActiveStatus s : statuses.on(entity)) {
            double cdr = s.def().extras().cooldownReduction();
            if (cdr != 1) m *= Math.pow(cdr, Math.max(1, s.stacks()));
        }
        return m;
    }

    /** What the healer's heals on others are multiplied by: their statuses' {@code ally_healing_dealt} (1 = none). */
    public double allyHealingMultiplier(UUID healer) {
        double m = 1;
        for (ActiveStatus s : statuses.on(healer)) m *= s.def().extras().allyHealingDealt();
        return m;
    }

    /** How much faster the entity's ultimate charges: their statuses' {@code ult_charge_rate} multiplied (1 = normal). */
    public double ultChargeMultiplier(UUID entity) {
        double m = 1;
        for (ActiveStatus s : statuses.on(entity)) m *= s.def().extras().ultChargeRate();
        return m;
    }

    /** Ultimates charge up (UltimateCharge) instead of having cooldowns. */
    private boolean ultimatesCharge;

    public void setUltimatesCharge(boolean charge) { this.ultimatesCharge = charge; }

    /** Is this ability whatever is in the caster's ultimate slot now? */
    public boolean isUltimate(UUID caster, String abilityId) {
        return loadouts.characterOf(caster).map(c -> abilityId.equals(c.abilityIn(Slots.ULTIMATE))).orElse(false);
    }

    /** The share of their ability damage the attacker heals (their statuses' {@code ability_lifesteal}, added up). */
    public double abilityLifesteal(UUID attacker) {
        double sum = 0;
        for (ActiveStatus s : statuses.on(attacker)) sum += s.def().extras().abilityLifesteal();
        return sum;
    }

    /**
     * Is this ability the caster's primary or secondary fire (or melee): whatever is in those slots now? The rest (1, 2,
     * 3, the ultimate) are their "abilities" for ability_lifesteal, ability_on_hit and ability_damage_taken.
     */
    public boolean isFire(UUID caster, String abilityId) {
        return loadouts.characterOf(caster)
                .map(c -> abilityId.equals(c.abilityIn(Slots.PRIMARY)) || abilityId.equals(c.abilityIn(Slots.SECONDARY))
                        || abilityId.equals(c.abilityIn(Slots.MELEE)))
                .orElse(false);
    }

    /** Is this effect's hit from the caster's primary / secondary fire (see {@link #isFire})? Status ticks aren't. */
    public boolean fromFire(me.mephisto.ability_engine.engine.effect.EffectContext ctx) {
        return ctx.execution() != null && isFire(ctx.caster(), ctx.execution().instance().ability().id());
    }

    /** Is this ability one of the caster's basic attacks: whatever is in their primary or melee slot now? */
    public boolean isBasicAttack(UUID caster, String abilityId) {
        return loadouts.characterOf(caster)
                .map(c -> abilityId.equals(c.abilityIn(Slots.PRIMARY)) || abilityId.equals(c.abilityIn(Slots.MELEE)))
                .orElse(false);
    }

    /**
     * Cooldown of an ability for this caster: the character's primary follows their attack speed
     * ({@code 20 / attack_speed} ticks) when the sheet has one; everything else its own cooldown.
     * Basic attacks (whatever is in the primary / melee slot now) are then divided by
     * {@link #attackSpeedMultiplier}; abilities (not primary / secondary fire or melee) multiplied by
     * {@link #cooldownMultiplier} (cooldown reduction). While ultimates charge up, the ultimate has none (0): its
     * charge is what it waits for.
     */
    public int cooldownTicks(UUID caster, String abilityId, int ownCooldown) {
        if (ultimatesCharge && isUltimate(caster, abilityId)) return 0;
        int ticks = loadouts.sheetOf(caster)
                .filter(c -> c.stats().attackSpeed() > 0 && abilityId.equals(c.abilityIn(Slots.PRIMARY)))
                .map(c -> Math.max(1, (int) Math.round(20 / c.stats().attackSpeed())))
                .orElse(ownCooldown);
        if (ticks <= 0) return ticks;
        if (isBasicAttack(caster, abilityId)) {
            double m = attackSpeedMultiplier(caster);
            return m == 1 ? ticks : Math.max(1, (int) Math.round(ticks / m));
        }
        if (isFire(caster, abilityId)) return ticks; // (secondary fire: neither)
        double cdr = cooldownMultiplier(caster);
        return cdr == 1 ? ticks : Math.max(1, (int) Math.round(ticks * cdr));
    }
}
