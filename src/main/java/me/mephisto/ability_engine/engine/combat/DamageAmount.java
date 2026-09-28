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
        double pierce = maxHpPart(ctx);
        double mult = Backstab.multiplier(ctx) * DamageScale.multiplier(ctx);
        return new Parts(armored * mult, pierce * mult);
    }

    /** Heal or shield: flat {@code amount} plus {@code max_hp} of the target's max HP. */
    public static double heal(EffectContext ctx) {
        return ctx.params().getDouble("amount", 0) + maxHpPart(ctx);
    }

    private static double maxHpPart(EffectContext ctx) {
        Params p = ctx.params();
        if (!p.has("max_hp") || !(ctx.target() instanceof EntityTarget t)) return 0;
        return ctx.engine().stats().maxHealth(t.id()) * p.getDouble("max_hp", 0);
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
    }

    private DamageAmount() {}
}
