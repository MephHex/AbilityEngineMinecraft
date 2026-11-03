package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * How much a damage, heal or shield effect is worth (design HP), before statuses and armor. Any of these,
 * added together (at least one):
 * <ul>
 *   <li>{@code amount: 50} - flat</li>
 *   <li>{@code base: 1.2} - 120% of the CASTER's base damage (damage only)</li>
 *   <li>{@code max_hp: 0.1} - 10% of the TARGET's max HP. On damage this part ignores armor (damage over
 *       time like poison, and "% max HP" hits), unless the hit has a {@code type} (below).</li>
 *   <li>{@code max_hp_sp: 0.0002} - a further share of the target's max HP per spell power point of the caster
 *       (0.0002 = +1% per 50 spell power).</li>
 *   <li>{@code sp: 0.9} - 90% of the caster's spell power (damage and heals).</li>
 *   <li>{@code bonus_hp: 0.1} - 10% of the CASTER's bonus max HP (above their character's own; damage only).</li>
 * </ul>
 * {@code type: physical | magic | true} is the hit's damage type. Physical and magic damage both go through armor, all
 * of it (max_hp parts too), and spell resistance on an ability; true damage ignores both, all of it. Without a type: as described below (the max_hp part
 * ignores armor, the rest doesn't).
 * {@code ignore_armor: true} makes the whole hit ignore armor; {@code max_hp_armored: true} makes the max_hp part go
 * through armor like the rest (poison: % max HP, but not true damage).
 * E.g. {@code { id: damage, base: 1.2, max_hp: 0.1 }}: 120% base damage (armor reduces it) + 10% of their
 * max HP (it doesn't). Damage then gets backstab and {@code scale_by} multipliers, on both parts.
 */
public final class DamageAmount {

    /** The damage types ({@code type:}). */
    public static final String PHYSICAL = "physical", MAGIC = "magic", TRUE = "true";
    public static final java.util.Set<String> TYPES = java.util.Set.of(PHYSICAL, MAGIC, TRUE);

    /**
     * A hit in two parts: what armor reduces, and what goes straight through it (the max_hp part).
     */
    public record Parts(double armored, double pierce, boolean trueDamage) {
        public Parts(double armored, double pierce) { this(armored, pierce, false); }

        public double total() { return armored + pierce; }

        /** Share of the hit that ignores armor (0..1). */
        public double pierceShare() {
            double t = total();
            return t <= 0 ? 0 : pierce / t;
        }
    }

    /** The damage of this effect on its target, split by armor. Plays the backstab crit cue if it crits. */
    public static Parts damage(EffectContext ctx) {
        Params p = ctx.params();
        var stats = ctx.engine().stats();
        double armored = p.getDouble("amount", 0);
        if (p.has("base")) armored += stats.baseDamage(ctx.caster()) * p.getDouble("base", 0);
        if (p.has("sp")) armored += spellPowerPart(ctx) * p.getDouble("sp", 0);
        if (p.has("bonus_hp") && ctx.caster() != null) armored += stats.bonusHealth(ctx.caster()) * p.getDouble("bonus_hp", 0);
        armored += Backstab.bonus(ctx); // backstab_bonus / backstab_crit: from behind, a flat amount more
        double pierce = maxHpPart(ctx, true);
        String type = p.getString("type", null);
        if (p.getBool("max_hp_armored", false) || PHYSICAL.equals(type) || MAGIC.equals(type)) { // the max_hp part is
            armored += pierce;                                                                   // reduced by armor too
            pierce = 0;
        }
        double mult = Backstab.multiplier(ctx) * DamageScale.multiplier(ctx) * crit(ctx) * slotMultiplier(ctx) * falloff(ctx);
        if (TRUE.equals(type)) return new Parts(0, (armored + pierce) * mult, true); // true damage: no defense reduces it
        if (p.getBool("ignore_armor", false)) return new Parts(0, (armored + pierce) * mult); // all of it goes through armor
        return new Parts(armored * mult, pierce * mult);
    }

    /**
     * {@code falloff_from: <key>}: the farther the target is from that spot, the less it deals: all of it there, down to
     * {@code falloff_edge} of it (default 0.5) at {@code falloff_radius} blocks and beyond (e.g. a blast: most at its
     * centre). 1 without one.
     */
    private static double falloff(EffectContext ctx) {
        Params p = ctx.params();
        String from = p.getString("falloff_from", null);
        if (from == null || ctx.execution() == null || !(ctx.target() instanceof EntityTarget t)) return 1;
        var world = ctx.engine().world();
        var center = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx.execution(), from).flatMap(world::positionOf);
        var at = world.positionOf(t);
        if (center.isEmpty() || at.isEmpty()) return 1;
        double radius = p.getDouble("falloff_radius", 0);
        double edge = p.getDouble("falloff_edge", 0.5);
        double f = radius <= 0 ? 0 : Math.min(1, center.get().position().distance(at.get().position()) / radius);
        return 1 - (1 - edge) * f;
    }

    /**
     * The caster's statuses' {@code slot_damage_dealt} for the slot this hit's ability is in (e.g. confused: basic attacks
     * and 1 at half damage). Not for a status's ticks (no ability).
     */
    private static double slotMultiplier(EffectContext ctx) {
        if (ctx.execution() == null || ctx.caster() == null) return 1;
        var engine = ctx.engine();
        String ability = ctx.execution().instance().ability().id();
        double m = 1;
        for (var s : engine.statuses().on(ctx.caster())) {
            for (var e : s.def().extras().slotDamage().entrySet()) {
                if (engine.loadouts().abilityIn(ctx.caster(), e.getKey()).map(ability::equals).orElse(false)) m *= e.getValue();
            }
        }
        return m;
    }

    /**
     * 100% spell power, for an {@code sp} part: the caster's spell power points, NOT boosted by their damage dealt (Strength,
     * +damage items). Damage dealt multiplies the whole hit later (DamageModifiers), so it's taken back out of this part here.
     */
    private static double spellPowerPart(EffectContext ctx) {
        if (ctx.caster() == null) return 0;
        var stats = ctx.engine().stats();
        double dealt = stats.damageDealtMultiplier(ctx.caster());
        return stats.spellPower(spellPowerOf(ctx)) / (dealt > 0 ? dealt : 1);
    }

    /** While a status's on-hit effects run: whoever put that status on (see {@link #withStatusSource}). */
    private static java.util.UUID statusSource;

    /**
     * Run a status's on-hit effects ({@code source}: whoever put it on its holder). An effect in there with
     * {@code sp_of: source} scales with that one's spell power instead of the holder's (e.g. the Fae's blessing on an ally:
     * the ally's basic attacks deal more with HER spell power).
     */
    public static void withStatusSource(java.util.UUID source, Runnable effects) {
        java.util.UUID before = statusSource;
        statusSource = source;
        try {
            effects.run();
        } finally {
            statusSource = before;
        }
    }

    /** While a status's on-hit effects run: whoever put that status on (null otherwise). */
    public static java.util.UUID statusSource() { return statusSource; }

    /** Whose spell power the {@code sp} / {@code max_hp_sp} parts use: the caster's, or (sp_of: source) the status's giver's. */
    private static java.util.UUID spellPowerOf(EffectContext ctx) {
        return SOURCE.equals(ctx.params().getString("sp_of", null)) && statusSource != null ? statusSource : ctx.caster();
    }

    private static final String SOURCE = "source";

    /**
     * A critical strike's multiplier, or 1. Basic attacks (primary / melee) can crit, and abilities too with a status's
     * {@code ability_crits}; status ticks never do. The cast rolls once (Crits), so everyone it hits gets the same.
     */
    private static double crit(EffectContext ctx) {
        if (ctx.execution() == null || ctx.caster() == null || !(ctx.target() instanceof EntityTarget t)
                || t.id().equals(ctx.caster()) || !critsHere(ctx)) return 1;
        var stats = ctx.engine().stats();
        if (!ctx.engine().crits().roll(ctx.caster(), ctx.execution().instance().id(), stats.critChance(ctx.caster()))) return 1;
        ctx.engine().world().positionOf(t)
                .ifPresent(at -> ctx.engine().cuesFor(ctx.caster()).play(Backstab.CRIT_CUE, at.world(), at.position()));
        boolean basic = stats.isBasicAttack(ctx.caster(), ctx.execution().instance().ability().id());
        var crits = ctx.engine().crits();
        return (basic ? crits.damage() : crits.abilityDamage()) + stats.critDamageBonus(ctx.caster());
    }

    /** Can this hit crit: a basic attack, or any ability of someone whose abilities crit. */
    public static boolean critsHere(EffectContext ctx) {
        if (ctx.execution() == null) return false;
        var stats = ctx.engine().stats();
        String ability = ctx.execution().instance().ability().id();
        return stats.isBasicAttack(ctx.caster(), ability)
                || (stats.abilitiesCrit(ctx.caster()) && !stats.isFire(ctx.caster(), ability));
    }

    /**
     * A heal: {@link #heal} x the healer's {@code ally_healing_dealt} when it's on someone else, an ally (not on
     * themselves, and not shields).
     */
    public static double healing(EffectContext ctx) {
        double amount = heal(ctx);
        if (ctx.target() instanceof EntityTarget t && ctx.caster() != null && !t.id().equals(ctx.caster())
                && ctx.engine().teams().allies(ctx.caster(), t.id())) {
            amount *= ctx.engine().stats().allyHealingMultiplier(ctx.caster());
        }
        return amount;
    }

    /**
     * Heal or shield: flat {@code amount} plus {@code max_hp} of the target's max HP.
     */
    public static double heal(EffectContext ctx) {
        double sp = ctx.params().has("sp") && ctx.caster() != null
                ? ctx.engine().stats().spellPower(spellPowerOf(ctx)) * ctx.params().getDouble("sp", 0) : 0;
        return ctx.params().getDouble("amount", 0) + maxHpPart(ctx, false) + sp + missingHpPart(ctx);
    }

    /** {@code missing_hp: 0.4} on a heal: 40% of the health the target is missing right now. */
    private static double missingHpPart(EffectContext ctx) {
        if (!ctx.params().has("missing_hp") || !(ctx.target() instanceof EntityTarget t)) return 0;
        double max = ctx.engine().stats().maxHealth(t.id());
        double now = ctx.engine().world().healthFraction(t.id()).orElse(1);
        return Math.max(0, max * (1 - now)) * ctx.params().getDouble("missing_hp", 0);
    }

    /**
     * {@code max_hp} x the target's max HP. For damage to anything that isn't a player (mobs, camp monsters), their max
     * HP counts as at most the stat sheets' mob cap (config.yml {@code max-hp-damage-cap-for-mobs}): % max HP damage
     * (poison, burns, executes) does to a big monster what it would to a player, not a huge chunk of its health.
     */
    private static double maxHpPart(EffectContext ctx, boolean damage) {
        Params p = ctx.params();
        if (!(p.has("max_hp") || (damage && (p.has("max_hp_sp") || p.has("max_hp_bonus_dmg") || p.has("max_hp_dmg"))))
                || !(ctx.target() instanceof EntityTarget t)) return 0;
        double max = ctx.engine().stats().maxHealth(t.id());
        if (damage && !ctx.engine().world().isPlayer(t.id()) && !ctx.engine().stats().hasSheet(t.id())) { // a mob, not a character
            max = Math.min(max, ctx.engine().stats().mobMaxHpCap());
        }
        double share = p.getDouble("max_hp", 0);
        if (damage && p.has("max_hp_sp") && ctx.caster() != null) {
            share += ctx.engine().stats().spellPower(spellPowerOf(ctx)) * p.getDouble("max_hp_sp", 0);
        }
        if (damage && p.has("max_hp_bonus_dmg") && ctx.caster() != null) { // more per point of the caster's bonus damage
            share += ctx.engine().stats().bonusDamage(ctx.caster()) * p.getDouble("max_hp_bonus_dmg", 0);
        }
        if (damage && p.has("max_hp_dmg") && ctx.caster() != null) { // more per point of the caster's damage (base x dealt)
            var stats = ctx.engine().stats();
            share += stats.baseDamage(ctx.caster()) * stats.damageDealtMultiplier(ctx.caster()) * p.getDouble("max_hp_dmg", 0);
        }
        return max * share;
    }

    /** Load-time check: at least one part, none negative. */
    public static void validate(Params p, boolean damage) {
        boolean any = p.has("amount") || p.has("max_hp") || p.has("sp")
                || (damage && (p.has("base") || p.has("bonus_hp") || p.has("max_hp_sp") || p.has("max_hp_bonus_dmg")
                        || p.has("max_hp_dmg") || p.has("backstab_bonus")))
                || (!damage && p.has("missing_hp"));
        String ways = damage ? "amount: <flat>, base: <x base damage>, sp: <x spell power>, bonus_hp: <x your bonus HP>,"
                + " max_hp: <x target's max HP> and/or max_hp_sp: <x target's max HP per spell power>"
                : "amount: <flat>, sp: <x spell power> and/or max_hp: <x target's max HP>";
        if (!any) throw p.error("amount", "give " + ways);
        for (String key : new String[]{"base", "bonus_hp", "max_hp_sp", "max_hp_bonus_dmg", "max_hp_dmg", "type",
                "backstab_bonus", "backstab_crit", "falloff_from"}) {
            if (!damage && p.has(key)) throw p.error(key, key + " is for damage; heals and shields use amount, sp or max_hp");
        }
        if (p.has("sp_of") && !java.util.Set.of("caster", SOURCE).contains(p.getString("sp_of", ""))) {
            throw p.error("sp_of", "whose spell power: caster (default) or source (who put the status on, in its on_hit)");
        }
        if (p.has("type") && !TYPES.contains(p.getString("type", ""))) {
            throw p.error("type", "the damage type: physical, magic or true");
        }
        for (String key : new String[]{"amount", "base", "sp", "max_hp", "bonus_hp", "max_hp_sp", "max_hp_bonus_dmg",
                "max_hp_dmg", "missing_hp", "backstab_bonus", "backstab_crit", "falloff_radius"}) {
            if (p.has(key) && p.getDouble(key, 0) < 0) throw p.error(key, "must be >= 0");
        }
        if (p.has("falloff_from") && p.getDouble("falloff_radius", 0) <= 0) {
            throw p.error("falloff_radius", "falloff_from needs falloff_radius: the blocks out to where it's weakest");
        }
        double edge = p.getDouble("falloff_edge", 0.5);
        if (edge < 0 || edge > 1) throw p.error("falloff_edge", "the share left at falloff_radius, 0 to 1");
        if (damage) DamageScale.validate(p);
    }

    private DamageAmount() {}
}
