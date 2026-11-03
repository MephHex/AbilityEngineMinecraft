package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;

/**
 * Backstab crits: a damage effect with {@code backstab: 1.5} deals x1.5 when it comes from behind the
 * target, i.e. more than {@link #BEHIND_DEGREES} away from where they face (horizontally). "Comes from"
 * is where the hit came from ("hit_from": a projectile's approach, a dash's end), else the caster.
 */
public final class Backstab {

    /** 110 degrees from their facing = the rear 140-degree arc. */
    public static final double BEHIND_DEGREES = 110;
    public static final String CRIT_CUE = "crit";

    public static double multiplier(EffectContext ctx) {
        double mult = ctx.params().getDouble("backstab", 1.0);
        if (mult == 1.0 || !behind(ctx)) return 1.0;
        cue(ctx);
        return mult;
    }

    /**
     * {@code backstab_bonus: 20} (and {@code backstab_crit: 50}: that much more per 100% of the caster's crit chance, so
     * +10 per 20%): from behind, the hit deals that flat amount more, and shows a crit. 0 otherwise.
     */
    public static double bonus(EffectContext ctx) {
        var p = ctx.params();
        if (!p.has("backstab_bonus") && !p.has("backstab_crit")) return 0;
        if (ctx.caster() == null || !behind(ctx)) return 0;
        if (p.getDouble("backstab", 1.0) == 1.0) cue(ctx); // (a multiplier too: it plays the crit already)
        return p.getDouble("backstab_bonus", 0) + p.getDouble("backstab_crit", 0) * ctx.engine().stats().critChance(ctx.caster());
    }

    private static void cue(EffectContext ctx) {
        if (ctx.target() instanceof EntityTarget target) {
            ctx.engine().world().positionOf(target).ifPresent(at ->
                    ctx.engine().cuesFor(ctx.caster()).play(CRIT_CUE, at.world(), at.position()));
        }
    }

    /** Does this hit come from behind its target (outside the front of where they face)? */
    private static boolean behind(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target) || target.id().equals(ctx.caster())) return false;
        var world = ctx.engine().world();
        var facing = world.aimOf(target.id());
        var at = world.positionOf(target);
        Optional<PointTarget> from = Optional.empty();
        if (ctx.execution() != null) {
            from = KeyQuery.read(ctx.execution(), Keys.HIT_FROM.name()).flatMap(world::positionOf);
        }
        if (from.isEmpty()) from = world.positionOf(new EntityTarget(ctx.caster()));
        if (facing.isEmpty() || at.isEmpty() || from.isEmpty()) return false;

        Vec3 f = flat(facing.get().direction());
        Vec3 toAttacker = flat(from.get().position().subtract(at.get().position()));
        if (f.isZero() || toAttacker.isZero()) return false;
        return Math.toDegrees(f.angleTo(toAttacker)) >= BEHIND_DEGREES;
    }

    private static Vec3 flat(Vec3 v) {
        return new Vec3(v.x(), 0, v.z());
    }

    private Backstab() {}
}
