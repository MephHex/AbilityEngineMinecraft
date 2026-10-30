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

    /** Who has which tags (move_speed_unless); null = nobody's looked at. */
    private me.mephisto.ability_engine.engine.tag.TagManager tags;

    public void setTags(me.mephisto.ability_engine.engine.tag.TagManager tags) { this.tags = tags; }

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

    /** An additive total never takes a stat below this share (e.g. lots of cooldown reduction: 90% at most). */
    private static final double ADDITIVE_FLOOR = 0.1;

    /**
     * The holder's statuses' multipliers for one STAT, combined. Ordinary statuses multiply (with {@code perStack}, each
     * to the power of its stacks: a slow that builds up). Additive ones (items) add their bonuses up instead, one per
     * stack (two +20% = +40%; two 5% cooldown reductions = 10%), into one multiplier that then multiplies with the rest.
     *
     * @param skip statuses left out (e.g. a slow while unstoppable)
     */
    public static double combine(Iterable<ActiveStatus> on, java.util.function.ToDoubleFunction<ActiveStatus> stat, boolean perStack,
                                 java.util.function.Predicate<ActiveStatus> skip) {
        return combine(on, stat, perStack, true, skip);
    }

    /**
     * @param additiveStacks an additive status counts once per stack (a stat: two copies of an item, both bonuses);
     *                       false: once however many stacks (an effect: two copies of an item, the effect once)
     */
    private static double combine(Iterable<ActiveStatus> on, java.util.function.ToDoubleFunction<ActiveStatus> stat, boolean perStack,
                                  boolean additiveStacks, java.util.function.Predicate<ActiveStatus> skip) {
        double product = 1, bonus = 0;
        for (ActiveStatus s : on) {
            double v = stat.applyAsDouble(s);
            if (v == 1 || skip.test(s)) continue;
            int n = Math.max(1, s.stacks());
            if (s.def().extras().additive()) bonus += (v - 1) * (additiveStacks ? n : 1);
            else product *= perStack ? Math.pow(v, n) : v;
        }
        return product * Math.max(ADDITIVE_FLOOR, 1 + bonus);
    }

    /** A stat (armor, damage, move and attack speed, max HP, cooldown reduction, spell power): items add up per copy. */
    private double combine(UUID entity, java.util.function.ToDoubleFunction<ActiveStatus> stat, boolean perStack) {
        return combine(statuses.on(entity), stat, perStack, true, s -> false);
    }

    /** An effect (anything that isn't a stat): an item's counts once, however many copies of it are in the bag. */
    private double combineEffect(UUID entity, java.util.function.ToDoubleFunction<ActiveStatus> effect) {
        return combine(statuses.on(entity), effect, false, false, s -> false);
    }

    /** How many times a status's stat counts: its stacks (a stat builds up per stack, an item per copy). */
    private static int statStacks(ActiveStatus s) { return Math.max(1, s.stacks()); }

    /** The sheet's armor, x the holder's statuses' {@code armor} (each to the power of its stacks: a shred). */
    public double armor(UUID entity) {
        if (!hasSheet(entity)) return 0;
        return of(entity).armor() * combine(entity, s -> s.def().armor(), true);
    }

    /** What the holder's damage is multiplied by: their statuses' {@code damage_dealt} (Strength, items...). */
    public double damageDealtMultiplier(UUID entity) { return combine(entity, s -> s.def().damageDealt(), false); }

    /** What damage to the holder is multiplied by: their statuses' {@code damage_taken} (and ability_damage_taken). */
    public double damageTakenMultiplier(UUID entity, boolean ability) {
        double m = combineEffect(entity, s -> s.def().damageTaken());
        return ability ? m * combineEffect(entity, s -> s.def().extras().abilityDamageTaken()) : m;
    }

    /**
     * Spell power, in points: their statuses' {@code spell_power} added up (items per copy). 0 = none. Only abilities' {@code sp:}
     * damage parts scale with it (90% spell power = 0.9 x this).
     */
    public double spellPower(UUID entity) {
        double sum = 0;
        for (ActiveStatus s : statuses.on(entity)) sum += s.def().extras().combat().spellPower() * statStacks(s);
        return Math.max(0, sum);
    }

    /** The holder's chance (0..1) to critically strike: their statuses' {@code crit_chance}, added up (per stack). */
    public double critChance(UUID entity) {
        double sum = 0;
        for (ActiveStatus s : statuses.on(entity)) sum += s.def().extras().combat().critChance() * statStacks(s);
        return Math.max(0, Math.min(1, sum));
    }

    /** Extra damage on the holder's critical strikes: their statuses' {@code crit_damage}, added up (an effect: once). */
    public double critDamageBonus(UUID entity) {
        double sum = 0;
        for (ActiveStatus s : statuses.on(entity)) sum += s.def().extras().combat().critDamage();
        return sum;
    }

    /** Can the holder's abilities (not only basic attacks) critically strike? (a status's {@code ability_crits}) */
    public boolean abilitiesCrit(UUID entity) {
        for (ActiveStatus s : statuses.on(entity)) if (s.def().extras().combat().abilityCrits()) return true;
        return false;
    }

    /** Spell resistance points: their statuses' {@code spell_resistance}, added up (per stack). */
    public double spellResistance(UUID entity) {
        double sum = 0;
        for (ActiveStatus s : statuses.on(entity)) sum += s.def().extras().combat().spellResistance() * statStacks(s);
        return sum;
    }

    /** What's left of an ability's hit after spell resistance: x constant / (constant + points), like armor. */
    public double afterSpellResistance(UUID victim, double amount) {
        double points = spellResistance(victim);
        return points <= 0 ? amount : amount * armorConstant / (armorConstant + points);
    }

    /** Share of ability damage spell resistance takes off (0..1), for display. */
    public double spellResistanceReduction(UUID entity) {
        double points = spellResistance(entity);
        return points / (armorConstant + points);
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
        return combine(statuses.on(entity), s -> s.def().attackSpeed(), true,
                s -> s.def().attackSpeed() < 1 && statuses.crowdControlSuppressed(entity, s));
    }

    /**
     * The entity's statuses' {@code move_speed}, each to the power of its stacks, multiplied together
     * (a slow that builds up). 1 = normal. The platform applies it on top of the sheet's move speed. Unstoppable,
     * debuffs don't slow them (a slowing poison still poisons). A status with {@code move_speed_unless} doesn't count
     * while they have that tag. Statuses with a {@code slow_cap} count together first, and that part never slows them
     * more than the largest of their caps (Recall's slow and a crystal floor's: 40% at most).
     */
    public double moveSpeedMultiplier(UUID entity) {
        var on = statuses.on(entity);
        java.util.function.Predicate<ActiveStatus> off = s -> (s.def().moveSpeed() < 1 && statuses.crowdControlSuppressed(entity, s))
                || (s.def().extras().moveSpeedUnless() != null && tags != null && tags.has(entity, s.def().extras().moveSpeedUnless()));
        double rest = combine(on, s -> s.def().extras().slowCap() > 0 ? 1 : s.def().moveSpeed(), true, off);
        double cap = 0;
        for (ActiveStatus s : on) if (!off.test(s) && s.def().moveSpeed() < 1) cap = Math.max(cap, s.def().extras().slowCap());
        if (cap <= 0) return rest;
        double capped = combine(on, s -> s.def().extras().slowCap() > 0 ? s.def().moveSpeed() : 1, true, off);
        return Math.max(capped, 1 - cap) * rest;
    }

    /**
     * How much of the healing the entity gets lands right now: its statuses' {@code healing_taken} multiplied together
     * (a poison's 0.6: 40% less). 1 = all of it. Every heal counts: heal effects, lifesteal, vanilla regeneration.
     */
    public double healingMultiplier(UUID entity) {
        return combineEffect(entity, s -> s.def().healingTaken());
    }

    /** The Jump Boost level the entity's statuses give it right now ({@code jump_boost}: the highest one); 0 = none. */
    public int jumpBoost(UUID entity) {
        int level = 0;
        for (ActiveStatus s : statuses.on(entity)) level = Math.max(level, s.def().jumpBoost());
        return level;
    }

    /** The holder's statuses' {@code max_health}, multiplied together (1 = none). Characters only. */
    public double maxHealthMultiplier(UUID entity) {
        return combine(entity, s -> s.def().extras().maxHealth(), false);
    }

    /**
     * What the holder's ability cooldowns are multiplied by: their statuses' {@code cooldown_reduction}, each to the power
     * of its stacks, multiplied together (two 0.95s: x0.9025). Always above 0; 1 = none.
     */
    public double cooldownMultiplier(UUID entity) {
        return combine(entity, s -> s.def().extras().cooldownReduction(), true);
    }

    /** What the healer's heals on others are multiplied by: their statuses' {@code ally_healing_dealt} (1 = none). */
    public double allyHealingMultiplier(UUID healer) {
        return combineEffect(healer, s -> s.def().extras().allyHealingDealt());
    }

    /** How much faster the entity's ultimate charges: their statuses' {@code ult_charge_rate} multiplied (1 = normal). */
    public double ultChargeMultiplier(UUID entity) {
        return combineEffect(entity, s -> s.def().extras().ultChargeRate());
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
        for (ActiveStatus s : statuses.on(attacker)) sum += s.def().extras().abilityLifesteal(); // (an effect: once)
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
