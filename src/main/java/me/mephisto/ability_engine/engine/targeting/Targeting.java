package me.mephisto.ability_engine.engine.targeting;

/**
 * An ability's aiming preview. Abilities with one open a targeting session on press: a preview
 * follows the crosshair; LMB confirms, RMB cancels (the ability's own key again does nothing).
 * The confirmed spot is written to the blackboard as "aim".
 * Cooldown and costs are only spent on confirm.
 *
 * @param range        max distance of the aim point from the caster's eyes
 * @param radius       circle size (shape CIRCLE)
 * @param width        line width (shape LINE); the wall's length (shape WALL: a wall across the aim, centred on the
 *                     spot)
 * @param angle        full cone width in degrees (shape CONE)
 * @param timeoutTicks the session cancels itself after this long; 0 = never (default, like Overwatch)
 * @param ground       project the aim straight down onto the ground below the crosshair
 * @param maxDrop      deepest landing below the caster's feet (default 6, for every ground ability).
 *                     Deeper ground under the crosshair falls back to the nearest valid spot toward
 *                     the caster (a cliff edge).
 * @param arc          a thrown projectile (the ability's own projectile node): the preview shows where it would
 *                     come down if thrown now, instead of where the crosshair is (null = the crosshair). See
 *                     {@link Trajectory}.
 */
public record Targeting(Shape shape, double range, double radius, double width, double angle, int timeoutTicks,
                        boolean ground, double maxDrop, me.mephisto.ability_engine.engine.projectile.ProjectileSpec arc) {

    public enum Shape { CIRCLE, LINE, CONE, POINT, WALL }

    public Targeting(Shape shape, double range, double radius, double width, double angle, int timeoutTicks,
                     boolean ground, double maxDrop) {
        this(shape, range, radius, width, angle, timeoutTicks, ground, maxDrop, null);
    }
}
