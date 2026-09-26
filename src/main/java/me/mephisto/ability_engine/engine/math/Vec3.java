package me.mephisto.ability_engine.engine.math;

import java.util.random.RandomGenerator;

/** Immutable 3D vector. Engine-side replacement for Bukkit's Vector/Location. */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UP = new Vec3(0, 1, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }
    public Vec3 subtract(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 multiply(double s) { return new Vec3(x * s, y * s, z * s); }
    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double lengthSquared() { return dot(this); }
    public double length() { return Math.sqrt(lengthSquared()); }
    public double distance(Vec3 o) { return subtract(o).length(); }
    public boolean isZero() { return lengthSquared() < 1e-12; }

    /** Unit vector in the same direction, or ZERO if this vector has no length. */
    public Vec3 normalize() {
        double len = length();
        return len < 1e-9 ? ZERO : multiply(1.0 / len);
    }

    /** Mirror this vector off a surface with the given (unit) normal. */
    public Vec3 reflect(Vec3 normal) {
        return subtract(normal.multiply(2 * dot(normal)));
    }

    /** Angle between two vectors in radians (0..PI). */
    public double angleTo(Vec3 o) {
        double denom = length() * o.length();
        if (denom < 1e-12) return 0;
        return Math.acos(Math.max(-1, Math.min(1, dot(o) / denom)));
    }

    /**
     * Random unit vector within {@code maxAngleRad} of this direction, uniformly distributed
     * over the spherical cap. Used for projectile spread.
     */
    public Vec3 randomInCone(double maxAngleRad, RandomGenerator rng) {
        Vec3 d = normalize();
        if (maxAngleRad <= 0 || d.isZero()) return d;
        Vec3 helper = Math.abs(d.y) < 0.99 ? UP : new Vec3(1, 0, 0);
        Vec3 u = d.cross(helper).normalize();
        Vec3 v = d.cross(u);
        double cosTheta = 1 - rng.nextDouble() * (1 - Math.cos(maxAngleRad));
        double sinTheta = Math.sqrt(Math.max(0, 1 - cosTheta * cosTheta));
        double phi = rng.nextDouble() * Math.PI * 2;
        return d.multiply(cosTheta).add(u.multiply(Math.cos(phi) * sinTheta)).add(v.multiply(Math.sin(phi) * sinTheta));
    }
}
