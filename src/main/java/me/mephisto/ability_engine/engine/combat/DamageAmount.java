package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * How much a damage, heal or shield effect is worth (design HP), before statuses and armor. Exactly one of:
 * <ul>
 *   <li>{@code amount: 50} - flat</li>
 *   <li>{@code base: 1.1} - 110% of the CASTER's base damage (damage only)</li>
 *   <li>{@code max_hp: 0.05} - 5% of the TARGET's max HP. Damage given this way ignores armor
 *       (damage over time like poison, and "% max HP" hits).</li>
 * </ul>
 * Damage then gets backstab and {@code scale_by} multipliers.
 */
public final class DamageAmount {

    public static double damage(EffectContext ctx) {
        return raw(ctx, true) * Backstab.multiplier(ctx) * DamageScale.multiplier(ctx);
    }

    /** Heal or shield: flat {@code amount}, or {@code max_hp} of the target's max HP. */
    public static double heal(EffectContext ctx) {
        return raw(ctx, false);
    }

    /** Damage from {@code max_hp} goes straight through armor. */
    public static boolean ignoresArmor(Params params) {
        return params.has("max_hp");
    }

    private static double raw(EffectContext ctx, boolean damage) {
        Params p = ctx.params();
        var stats = ctx.engine().stats();
        if (p.has("max_hp")) {
            if (!(ctx.target() instanceof EntityTarget t)) return 0;
            return stats.maxHealth(t.id()) * p.getDouble("max_hp", 0);
        }
        if (damage && p.has("base")) return stats.baseDamage(ctx.caster()) * p.getDouble("base", 0);
        return p.requireDouble("amount");
    }

    /** Load-time check: exactly one way of giving the amount, none negative. */
    public static void validate(Params p, boolean damage) {
        int given = (p.has("amount") ? 1 : 0) + (p.has("max_hp") ? 1 : 0) + (damage && p.has("base") ? 1 : 0);
        String ways = damage ? "amount: <flat>, base: <x base damage> or max_hp: <x target's max HP>"
                : "amount: <flat> or max_hp: <x target's max HP>";
        if (given != 1) throw p.error("amount", "give exactly one of " + ways);
        for (String key : new String[]{"amount", "base", "max_hp"}) {
            if (p.has(key) && p.getDouble(key, 0) < 0) throw p.error(key, "must be >= 0");
        }
    }

    private DamageAmount() {}
}
