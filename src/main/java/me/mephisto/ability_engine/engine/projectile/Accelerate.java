package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;

/** Gains {@code perTick} blocks/tick of speed every tick, up to {@code maxSpeed}. Direction unchanged. */
public record Accelerate(double perTick, double maxSpeed) implements MotionModifier {
    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) {
        double speed = velocity.length();
        if (speed < 1e-9) return velocity;
        return velocity.multiply(Math.min(maxSpeed, speed + perTick) / speed);
    }
}
