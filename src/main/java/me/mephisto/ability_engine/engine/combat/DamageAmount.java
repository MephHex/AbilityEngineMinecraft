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
 *       time like poison, and "% max HP" hits).</li>
 * </ul>
 * {@code ignore_armor: true} makes the whole hit ignore armor; {@code max_hp_armored: true} makes the max_hp part go
 * through armor like the rest (poison: % max HP, but not true damage).
 * E.g. {@code { id: damage, base: 1.2, max_hp: 0.1 }}: 120% base damage (armor reduces it) + 10% of their
 * max HP (it doesn't). Damage then gets backstab and {@code scale_by} multipliers, on both parts.
 */
public final class DamageAmount {

    /**
     * A hit in two parts: what armor reduces, and what goes straight through it (the max_hp part).
     */
    public record Parts(double armored, double pierce) {
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
        double pierce = maxHpPart(ctx, true);
        if (p.getBool("max_hp_armored", false)) { // the max_hp part is reduced by armor like the rest (e.g. poison)
            armored += pierce;
            pierce = 0;
        }
        double mult = Backstab.multiplier(ctx) * DamageScale.multiplier(ctx);
        if (p.getBool("ignore_armor", false)) return new Parts(0, (armored + pierce) * mult); // all of it goes through
        return new Parts(armored * mult, pierce * mult);
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

    /** Heal or shield: flat {@code amount} plus {@code max_hp} of the target's max HP. */
    public static double heal(EffectContext ctx) {
        return ctx.params().getDouble("amount", 0) + maxHpPart(ctx, false);
    }

    /**
     * {@code max_hp} x the target's max HP. For damage to anything that isn't a player (mobs, camp monsters), their max
     * HP counts as at most the stat sheets' mob cap (config.yml {@code max-hp-damage-cap-for-mobs}): % max HP damage
     * (poison, burns, executes) does to a big monster what it would to a player, not a huge chunk of its health.
     */
    private static double maxHpPart(EffectContext ctx, boolean damage) {
        Params p = ctx.params();
        if (!p.has("max_hp") || !(ctx.target() instanceof EntityTarget t)) return 0;
        double max = ctx.engine().stats().maxHealth(t.id());
        if (damage && !ctx.engine().world().isPlayer(t.id()) && !ctx.engine().stats().hasSheet(t.id())) { // a mob, not a character
            max = Math.min(max, ctx.engine().stats().mobMaxHpCap());
        }
        return max * p.getDouble("max_hp", 0);
    }

    /** Load-time check: at least one part, none negative. */
    public static void validate(Params p, boolean damage) {
        boolean any = p.has("amount") || p.has("max_hp") || (damage && p.has("base"));
        String ways = damage ? "amount: <flat>, base: <x base damage> and/or max_hp: <x target's max HP>"
                : "amount: <flat> and/or max_hp: <x target's max HP>";
        if (!any) throw p.error("amount", "give " + ways);
        if (!damage && p.has("base")) throw p.error("base", "base damage is for damage; heals and shields use amount or max_hp");
        for (String key : new String[]{"amount", "base", "max_hp"}) {
            if (p.has(key) && p.getDouble(key, 0) < 0) throw p.error(key, "must be >= 0");
        }
        if (damage) DamageScale.validate(p);
    }

    private DamageAmount() {}
}
