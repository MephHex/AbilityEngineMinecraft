package me.mephisto.ability_engine.engine.construct;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.UUID;

/** A placed construct, as seen from outside the engine (renderers, hitbox listeners). */
public interface ConstructHandle {
    long id();
    UUID owner();
    String world();
    Vec3 position();
    /** Hitbox radius. */
    double size();
    boolean isAlive();
    /** True while enemies can still break it. */
    boolean isFragile();
    /** 0 at placement, 1 when the fuse runs out. */
    double progress();
}
