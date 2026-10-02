package me.mephisto.ability_engine.engine.targeting;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.SweepHit;
import me.mephisto.ability_engine.engine.projectile.Accelerate;
import me.mephisto.ability_engine.engine.projectile.Drag;
import me.mephisto.ability_engine.engine.projectile.Gravity;
import me.mephisto.ability_engine.engine.projectile.MotionModifier;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;
import java.util.UUID;

/**
 * Where a projectile thrown right now would come down (an aim preview with {@code arc}): it's flown ahead the way
 * the ProjectileSystem flies it (from the eyes along the aim, its gravity / drag / acceleration, bouncing off walls
 * with {@code bounce_walls}), against blocks only, until it touches the ground. Whoever it would hit on the way
 * isn't counted (like every aim preview, it looks at the ground). Never landing within its lifetime or range: where
 * it would be at the end.
 */
public final class Trajectory {

    /** As ProjectileNode: it starts half a block out from the eyes. */
    private static final double MUZZLE_OFFSET = 0.5;
    /** As ProjectileSystem: a surface facing up at least this much is ground. */
    private static final double GROUND_NORMAL_Y = 0.7;
    private static final double BOUNCE_NUDGE = 0.05;

    public static Optional<PointTarget> landing(AbilityEngine engine, UUID caster, ProjectileSpec spec) {
        Optional<Aim> aim = engine.world().aimOf(caster);
        if (aim.isEmpty()) return Optional.empty();
        Aim a = aim.get();
        Vec3 dir = a.direction();
        Vec3 pos = a.eye().add(dir.multiply(MUZZLE_OFFSET));
        Vec3 velocity = dir.multiply(spec.speed());
        Vec3 from = a.eye(); // the first check starts at the eyes, like the real throw
        double flown = 0;
        for (int tick = 0; tick < spec.lifetimeTicks(); tick++) {
            for (MotionModifier m : spec.motion()) {
                if (m instanceof Gravity || m instanceof Drag || m instanceof Accelerate) velocity = m.apply(pos, velocity, null);
            }
            Vec3 next = pos.add(velocity);
            Optional<SweepHit> hit = engine.world().sweep(a.world(), from, next, spec.size() / 2, id -> true);
            if (hit.isEmpty()) {
                flown += next.distance(pos);
                pos = next;
                from = pos;
                if (spec.range() > 0 && flown >= spec.range()) break;
                continue;
            }
            SweepHit h = hit.get();
            Vec3 n = h.normal();
            if (spec.bounceWalls() && n != null && n.y() < GROUND_NORMAL_Y) { // off a wall: on it goes
                Vec3 off = spec.bounce(velocity, n);
                velocity = off != null ? off : velocity.subtract(n.multiply(velocity.dot(n)));
                pos = h.position().add(n.multiply(BOUNCE_NUDGE));
                from = pos;
                continue;
            }
            return Optional.of(new PointTarget(a.world(), h.position()));
        }
        return Optional.of(new PointTarget(a.world(), pos));
    }

    private Trajectory() {}
}
