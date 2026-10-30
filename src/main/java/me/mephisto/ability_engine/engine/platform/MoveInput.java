package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

/**
 * How a player is trying to move right now, from the keys they hold (not how they're actually moving: knockback,
 * pulls and teleports aren't in it).
 *
 * @param move the walk they ask for: flat, in world space, in blocks per tick at their current speed (sprinting,
 *             sneaking, slows and haste all counted); zero with no movement keys held
 * @param jump they're jumping this tick (the jump key, on the ground)
 * @param sneak they hold sneak (SHIFT): crouched
 * @param jumpHeld they hold the jump key (whether or not they could jump right now: e.g. held in place)
 */
public record MoveInput(Vec3 move, boolean jump, boolean sneak, boolean jumpHeld) {
    public static final MoveInput NONE = new MoveInput(Vec3.ZERO, false, false, false);

    public MoveInput(Vec3 move, boolean jump) { this(move, jump, false, jump); }

    public MoveInput(Vec3 move, boolean jump, boolean sneak) { this(move, jump, sneak, jump); }
}
