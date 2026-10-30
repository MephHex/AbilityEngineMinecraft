package me.mephisto.ability_engine.engine.barrier;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.team.Teams;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fields that slow ENEMY projectiles down (a stasis field): a box on the ground, {@code length} along {@code forward}
 * and {@code width} across it, {@code height} tall. An enemy projectile inside one moves (and ages) at {@code factor}
 * of its speed: 0.15 = 15%. Its own side's shots, and the owner's, pass at full speed. Several overlapping: the
 * strongest counts.
 */
public final class SlowFields {

    /** Below the field's floor still counts this much (a shot skimming the ground). */
    private static final double BELOW = 0.5;

    /**
     * @param center  the middle of its floor
     * @param forward its length's direction (flat, unit length)
     */
    public record Field(UUID owner, String world, Vec3 center, Vec3 forward, double length, double width, double height,
                        double factor) {

        /** Is {@code p} inside it? */
        public boolean contains(String inWorld, Vec3 p) {
            if (!world.equals(inWorld)) return false;
            Vec3 d = p.subtract(center);
            if (d.y() < -BELOW || d.y() > height) return false;
            Vec3 flat = new Vec3(d.x(), 0, d.z());
            double along = flat.dot(forward);
            double across = flat.dot(right());
            return Math.abs(along) <= length / 2 && Math.abs(across) <= width / 2;
        }

        /** Across it (flat, unit length). */
        public Vec3 right() { return new Vec3(-forward.z(), 0, forward.x()); }
    }

    private final Teams teams;
    private final List<Field> active = new ArrayList<>();

    public SlowFields(Teams teams) {
        this.teams = teams;
    }

    /** Put a field down; returns how to take it away (idempotent). */
    public Runnable add(Field field) {
        active.add(field);
        return () -> active.remove(field);
    }

    /** Every field up right now (e.g. to draw them). */
    public List<Field> active() { return List.copyOf(active); }

    /**
     * How fast a projectile of {@code shooter}'s moves at {@code at}: the factor of the strongest enemy field it's in, 1
     * outside them. (A shot nobody owns counts as an enemy's.)
     */
    public double factorAt(String world, Vec3 at, UUID shooter) {
        double f = 1;
        for (Field field : active) {
            if (field.factor() >= f || !field.contains(world, at)) continue;
            if (shooter != null && (shooter.equals(field.owner()) || !teams.enemies(shooter, field.owner()))) continue;
            f = field.factor();
        }
        return f;
    }
}
