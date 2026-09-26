package me.mephisto.ability_engine.engine.targeting;

/**
 * An ability's aiming preview. Abilities with one open a targeting session on press: a preview
 * follows the crosshair, LMB confirms, RMB cancels, the ability's own key does nothing (key repeat
 * makes it unsafe as a confirm). The confirmed spot is written to the blackboard as "aim".
 * Cooldown and costs are only spent on confirm.
 *
 * @param range        max distance of the aim point from the caster's eyes
 * @param radius       circle size (shape CIRCLE)
 * @param width        line width (shape LINE)
 * @param angle        full cone width in degrees (shape CONE)
 * @param timeoutTicks the session cancels itself after this long; 0 = never (default, like Overwatch)
 * @param ground       project the aim straight down onto the ground below the crosshair
 * @param maxDrop      deepest landing below the caster's feet (default 6, for every ground ability).
 *                     Deeper ground under the crosshair falls back to the nearest valid spot toward
 *                     the caster (a cliff edge).
 */
public record Targeting(Shape shape, double range, double radius, double width, double angle, int timeoutTicks,
                        boolean ground, double maxDrop) {

    public enum Shape { CIRCLE, LINE, CONE, POINT }
}
