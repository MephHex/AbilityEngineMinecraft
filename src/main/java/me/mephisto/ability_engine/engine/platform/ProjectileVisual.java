package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

public interface ProjectileVisual {
    void moveTo(Vec3 position);

    /** Moved this tick, flying with {@code velocity} (blocks per tick). Default: position only. */
    default void moveTo(Vec3 position, Vec3 velocity) { moveTo(position); }

    /** Idempotent. */
    void remove();
}
