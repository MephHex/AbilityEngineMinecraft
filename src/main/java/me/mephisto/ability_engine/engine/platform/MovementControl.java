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

    /** Teleport and look along {@code look} (e.g. down at the arena below). Default: teleport, then face. */
    default void teleport(UUID entity, Vec3 center, Vec3 look) {
        teleport(entity, center);
        if (look != null && !look.isZero()) face(entity, look);
    }

    /** Turn an entity to look along {@code direction} (e.g. a clone about to dash). Default: nothing. */
    default void face(UUID entity, Vec3 direction) {}

    /**
     * Seat {@code rider} on {@code vehicle}: it goes wherever the vehicle goes (on Bukkit, a passenger on their
     * head). Returns false if it can't be done. Default: riding isn't supported.
     */
    default boolean mount(UUID rider, UUID vehicle) { return false; }

    /** Like {@link #mount(UUID, UUID)}, the rider sitting {@code lift} blocks higher than usual (0 = as usual). */
    default boolean mount(UUID rider, UUID vehicle, double lift) { return mount(rider, vehicle); }

    /** Get {@code rider} off whatever it rides. */
    default void dismount(UUID rider) {}

    /** What {@code rider} is riding right now, if anything. */
    default java.util.Optional<UUID> vehicleOf(UUID rider) { return java.util.Optional.empty(); }
}
