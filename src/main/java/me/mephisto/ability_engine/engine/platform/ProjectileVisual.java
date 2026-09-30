package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

public interface ProjectileVisual {
    void moveTo(Vec3 position);

    /** Moved this tick, flying with {@code velocity} (blocks per tick). Default: position only. */
    default void moveTo(Vec3 position, Vec3 velocity) { moveTo(position); }

    /** Its body was killed (a projectile with health). The projectile then ends ("destroyed"). */
    default boolean destroyed() { return false; }

    /** The entity that is its hittable body, if it has one (its own shots fly through it). */
    default java.util.Optional<java.util.UUID> body() { return java.util.Optional.empty(); }

    /** Idempotent. */
    void remove();
}
