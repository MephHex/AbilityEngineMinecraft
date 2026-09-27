package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.UUID;

/**
 * Port for moving entities the way the game itself does (for players: their own client moves them,
 * so a server-set velocity looks smooth, unlike teleporting every tick).
 */
public interface MovementControl {

    /** Push: this velocity (blocks per tick) from now on, until changed. */
    void setVelocity(UUID entity, Vec3 velocity);

    /** Come to a halt: zero velocity, and forget any fall so a dash off a ledge doesn't hurt. */
    void stop(UUID entity);

    /** Put an entity's centre at {@code center}, keeping where it looks (a swap, a blink). */
    void teleport(UUID entity, Vec3 center);
}
