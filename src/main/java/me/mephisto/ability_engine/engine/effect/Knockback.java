package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.math.Vec3;

/**
 * Knockback math, kept platform-free so it's testable. The push points horizontally away from
 * the centre, with a small upward lift; strength blends from {@code centerStrength} (at the
 * centre) to {@code edgeStrength} (at {@code radius}). With {@code vertical}, it points straight away from the
 * centre, up and down too: a blast under your feet throws you up (then the lift on top).
 */
public final class Knockback {

    public static Vec3 impulse(Vec3 center, Vec3 target, double radius, double centerStrength, double edgeStrength, double lift) {
        return impulse(center, target, radius, centerStrength, edgeStrength, lift, false);
    }

    public static Vec3 impulse(Vec3 center, Vec3 target, double radius, double centerStrength, double edgeStrength, double lift,
                               boolean vertical) {
        double f = radius <= 0 ? 1 : Math.max(0, Math.min(1, target.distance(center) / radius));
        double strength = centerStrength + (edgeStrength - centerStrength) * f;
        Vec3 away = vertical ? target.subtract(center) : new Vec3(target.x() - center.x(), 0, target.z() - center.z());
        Vec3 dir = away.isZero() ? Vec3.ZERO : away.normalize();
        return dir.multiply(strength).add(0, lift, 0);
    }

    private Knockback() {}
}
