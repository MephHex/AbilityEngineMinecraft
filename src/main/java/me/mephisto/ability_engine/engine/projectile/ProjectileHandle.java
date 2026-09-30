package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.List;

/**
 * A live projectile other nodes can steer (stored on the blackboard by {@code projectile: store: x}).
 * All methods are safe to call after it has hit or expired; they just do nothing.
 */
public interface ProjectileHandle {
    boolean isAlive();
    /** The world it flies in. */
    default String world() { return null; }
    Vec3 position();
    Vec3 velocity();
    /** Replace velocity (direction and speed) from the next tick on. */
    void redirect(Vec3 newVelocity);
    /** Replace the motion modifiers (e.g. drop gravity, add acceleration). */
    void setMotion(List<MotionModifier> motion);

    /** Being guided right now: its range doesn't count down this tick and the next. */
    void markGuided();

    /** Blocks travelled while NOT guided (what counts against its range). */
    double unguidedDistance();
}
