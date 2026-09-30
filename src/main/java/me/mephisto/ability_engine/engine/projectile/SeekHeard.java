package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.Optional;
import java.util.UUID;

/**
 * Homes on the nearest enemy the caster HEARS (their hearing passive: wounded enemies) within {@code range}
 * of the projectile, flying at {@code speed} while it does; with none, it flies straight on at
 * {@code base}. Skipped while the projectile is being steered (steer_projectile): steering wins.
 */
public record SeekHeard(double range, double speed, double base, double turnRate) implements MotionModifier {

    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) {
        Optional<Vec3> target = Optional.empty();
        double best = range;
        for (UUID enemy : ctx.engine().hearing().heard(ctx.caster())) {
            var at = ctx.engine().world().positionOf(new EntityTarget(enemy));
            if (at.isEmpty()) continue;
            double d = at.get().position().distance(position);
            if (d <= best) {
                best = d;
                target = Optional.of(at.get().position());
            }
        }
        Vec3 dir = velocity.isZero() ? new Vec3(1, 0, 0) : velocity.normalize();
        if (target.isEmpty()) return dir.multiply(base);
        Vec3 desired = target.get().subtract(position).normalize();
        if (desired.isZero()) return dir.multiply(speed);
        return dir.add(desired.multiply(turnRate)).normalize().multiply(speed);
    }
}
