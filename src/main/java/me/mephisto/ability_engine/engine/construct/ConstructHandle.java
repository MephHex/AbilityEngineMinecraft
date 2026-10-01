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

    /** Projectiles and punches hit it. False for traps, which only an enemy walking in can set off. */
    default boolean solid() { return true; }

    /** A trap: can it be set off yet? Always true for constructs that aren't traps. */
    default boolean armed() { return true; }

    /** Only its owner and their allies may see it (a hidden trap): the platform hides it from everyone else. */
    default boolean hidden() { return false; }
}
