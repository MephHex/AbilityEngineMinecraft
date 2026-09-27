package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.effect.EffectContext;

/**
 * A damage effect with {@code scale_by: <key>} is multiplied by the number stored under that key, e.g. a
 * charged shot's power (a charge node stores 0..1). Missing key or no number: x1.
 */
public final class DamageScale {

    public static double multiplier(EffectContext ctx) {
        String key = ctx.params().getString("scale_by", null);
        if (key == null || ctx.execution() == null) return 1.0;
        return ctx.execution().blackboard().raw(key) instanceof Number n ? Math.max(0, n.doubleValue()) : 1.0;
    }

    private DamageScale() {}
}
