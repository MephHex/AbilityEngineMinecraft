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

    /**
     * One tick of a guided flight (a swap): it's at {@code from} on its path and should be at {@code to} next tick.
     * Default: there at once (a blink). A platform may make it smoother: on Bukkit a player is pushed along it, so their
     * own client moves them (no teleport every tick, which a player's camera shows as jitter).
     */
    default void flyStep(UUID entity, Vec3 from, Vec3 to) { blink(entity, to); }

    /**
     * Make it run one tick along {@code direction} (flat, unit) at its own running speed, whatever its keys say (someone
     * confused running at a teammate; the platform holds their own walking meanwhile). Default: a push.
     */
    default void run(UUID entity, Vec3 direction) { drive(entity, direction.multiply(0.25), false); }

    /** Turn an entity to look along {@code direction} (e.g. a clone about to dash). Default: nothing. */
    default void face(UUID entity, Vec3 direction) {}

    /**
     * Move a body by {@code step} this tick the way walking does (a mirroring clone): walls stop it on that axis, it falls
     * off edges by itself, and an upward step while it stands on the ground is a jump. Default: a push of that velocity.
     */
    default void stride(UUID entity, Vec3 step) { setVelocity(entity, step); }

    /**
     * Drive a body the way walking keys do (a mirroring clone): it accelerates toward {@code move} (blocks per tick, flat)
     * like a player does, so whatever else pushes it (knockback) still pushes it and wears off as usual, walls stop it and
     * it falls off edges; {@code jump} jumps if it's on the ground. Default: a push of exactly that (plus a jump).
     */
    default void drive(UUID entity, Vec3 move, boolean jump) {
        setVelocity(entity, jump ? move.add(0, 0.42, 0) : move);
    }

    /** Crouch (as if holding sneak) or stand up: just the pose (a clone copying its owner's sneak). Default: nothing. */
    default void crouch(UUID entity, boolean crouched) {}

    /** Swing its main arm (a clone copying a basic attack). Default: nothing. */
    default void swing(UUID entity) {}

    /**
     * Put its centre at {@code center} at once, keeping where it looks AND how it's moving (a blink mid-stride, e.g. in
     * Trick Room). Default: a plain teleport.
     */
    default void blink(UUID entity, Vec3 center) { teleport(entity, center); }

    /**
     * Seat {@code rider} on {@code vehicle}: it goes wherever the vehicle goes (on Bukkit, a passenger on their
     * head). Returns false if it can't be done. Default: riding isn't supported.
     */
    default boolean mount(UUID rider, UUID vehicle) { return false; }

    /** Like {@link #mount(UUID, UUID)}, the rider sitting {@code lift} blocks higher than usual (0 = as usual). */
    default boolean mount(UUID rider, UUID vehicle, double lift) { return mount(rider, vehicle); }

    /** Get {@code rider} off whatever it rides. */
    default void dismount(UUID rider) {}

    /** Dies now (the engine has already checked nothing saves them). */
    default void kill(UUID entity) {}

    /** Their health becomes this share of their max HP (0..1]. */
    default void setHealthShare(UUID entity, double share) {}

    /** What {@code rider} is riding right now, if anything. */
    default java.util.Optional<UUID> vehicleOf(UUID rider) { return java.util.Optional.empty(); }
}
