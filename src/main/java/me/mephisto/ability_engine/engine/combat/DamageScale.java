package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.effect.EffectContext;

/**
 * A damage effect with {@code scale_by: <key>} is multiplied by the number stored under that key, e.g. a
 * charged shot's power (a charge node stores 0..1). Missing key or no number: x1.
 * <p>{@code falloff: { status: <id>, per_stack: 0.05, min: 0.1 }}: less for each stack of that status the TARGET has
 * (x 1 - per_stack x stacks, never below min). Put the status on after the damage, and each hit in a row does less
 * than the one before: e.g. shards recalled through the same enemy, 100%, 95%, 90%... down to 10%.
 */
public final class DamageScale {

    public static double multiplier(EffectContext ctx) {
        return scaleBy(ctx) * falloff(ctx);
    }

    private static double scaleBy(EffectContext ctx) {
        String key = ctx.params().getString("scale_by", null);
        if (key == null || ctx.execution() == null) return 1.0;
        return ctx.execution().blackboard().raw(key) instanceof Number n ? Math.max(0, n.doubleValue()) : 1.0;
    }

    private static double falloff(EffectContext ctx) {
        if (!ctx.params().has("falloff") || !(ctx.target() instanceof me.mephisto.ability_engine.engine.target.EntityTarget t)) {
            return 1.0;
        }
        var f = ctx.params().getParams("falloff");
        int stacks = ctx.engine().statuses().find(t.id(), f.requireString("status")).map(s -> s.stacks()).orElse(0);
        return Math.max(f.getDouble("min", 0), 1 - f.getDouble("per_stack", 0.05) * stacks);
    }

    /** Load-time check of {@code falloff}. */
    public static void validate(me.mephisto.ability_engine.engine.data.Params p) {
        if (!p.has("falloff")) return;
        var f = p.getParams("falloff");
        f.requireString("status");
        double per = f.getDouble("per_stack", 0.05), min = f.getDouble("min", 0);
        if (per < 0 || per > 1) throw f.error("per_stack", "a share per stack, 0-1 (0.05 = 5% less a stack)");
        if (min < 0 || min > 1) throw f.error("min", "the least it goes down to, 0-1 (0.1 = 10%)");
    }

    private DamageScale() {}
}
