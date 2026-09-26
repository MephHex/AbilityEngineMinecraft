package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

public interface ProjectileVisual {
    void moveTo(Vec3 position);
    /** Idempotent. */
    void remove();
}
