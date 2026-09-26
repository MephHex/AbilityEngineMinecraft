package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;

/** Blocks/tick² of downward acceleration. Vanilla arrows are ~0.05. */
public record Gravity(double perTick) implements MotionModifier {
    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) {
        return velocity.add(0, -perTick, 0);
    }
}
