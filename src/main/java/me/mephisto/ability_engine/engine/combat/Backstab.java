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
        if (mult == 1.0 || !(ctx.target() instanceof EntityTarget target) || target.id().equals(ctx.caster())) return 1.0;
        var world = ctx.engine().world();
        var facing = world.aimOf(target.id());
        var at = world.positionOf(target);
        Optional<PointTarget> from = Optional.empty();
        if (ctx.execution() != null) {
            from = KeyQuery.read(ctx.execution(), Keys.HIT_FROM.name()).flatMap(world::positionOf);
        }
        if (from.isEmpty()) from = world.positionOf(new EntityTarget(ctx.caster()));
        if (facing.isEmpty() || at.isEmpty() || from.isEmpty()) return 1.0;

        Vec3 f = flat(facing.get().direction());
        Vec3 toAttacker = flat(from.get().position().subtract(at.get().position()));
        if (f.isZero() || toAttacker.isZero()) return 1.0;
        if (Math.toDegrees(f.angleTo(toAttacker)) < BEHIND_DEGREES) return 1.0;
        ctx.engine().cues().play(CRIT_CUE, at.get().world(), at.get().position());
        return mult;
    }

    private static Vec3 flat(Vec3 v) {
        return new Vec3(v.x(), 0, v.z());
    }

    private Backstab() {}
}
