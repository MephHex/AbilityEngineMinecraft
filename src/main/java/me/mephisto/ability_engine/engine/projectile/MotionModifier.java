package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;

/**
 * Changes a projectile's velocity each tick. Stack them: gravity + drag + homing.
 * Replaces the nullable gravity/homing fields and hasX() checks on ProjectileNode.
 */
public interface MotionModifier {
    Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx);
}
