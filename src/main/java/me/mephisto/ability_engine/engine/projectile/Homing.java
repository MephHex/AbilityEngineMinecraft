package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.KeyQuery;

/**
 * Steers toward the target stored under {@code targetKey}. Keeps speed constant — the old
 * version added acceleration without limit, so homing projectiles sped up forever.
 *
 * @param turnRate 0..1, how hard it turns per tick (0.1 = lazy curve, 1 = snaps on)
 */
public record Homing(String targetKey, double turnRate) implements MotionModifier {
    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) {
        var target = KeyQuery.read(ctx, targetKey).flatMap(ctx.engine().world()::positionOf);
        if (target.isEmpty()) return velocity;
        double speed = velocity.length();
        Vec3 desired = target.get().position().subtract(position).normalize();
        Vec3 dir = velocity.normalize().add(desired.multiply(turnRate)).normalize();
        return dir.multiply(speed);
    }
}
