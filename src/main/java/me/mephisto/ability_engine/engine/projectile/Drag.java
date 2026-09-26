package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;

/** Fraction of speed lost per tick (0.01 = 1%). Vanilla arrows are ~0.01. */
public record Drag(double perTick) implements MotionModifier {
    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) {
        return velocity.multiply(1 - perTick);
    }
}
