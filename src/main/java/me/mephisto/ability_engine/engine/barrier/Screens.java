package me.mephisto.ability_engine.engine.barrier;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.Target;
import me.mephisto.ability_engine.engine.team.Teams;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Screens (the Arcanist's Arcane Screen): flat rectangles standing in the air, facing the way their owner looked when they
 * put them down. Projectiles that fly through one are changed by it, once per screen:
 * <ul>
 *   <li>an ENEMY projectile (or one nobody owns) stops inside it for {@code holdTicks}, then flies on as it was;</li>
 *   <li>the owner's and their allies' projectiles fly on {@code accelerate} times as fast, and whoever they hit afterwards
 *       (an enemy of the owner) gets {@code onBoostedHit} (bonus damage from the owner).</li>
 * </ul>
 * {@code catchCue} / {@code boostCue} (the owner's cues, or null) play where a projectile is caught / boosted.
 * The ProjectileSystem asks {@link #cross} every tick.
 */
public final class Screens {

    /**
     * @param center the middle of the rectangle
     * @param normal the way it faces (unit): the owner's look when it was put down
     * @param right  across it (unit, level unless it was put down looking straight up or down)
     * @param up     its own "up" (unit): along its height
     */
    public record Screen(UUID owner, String world, Vec3 center, Vec3 normal, Vec3 right, Vec3 up, double width,
                         double height, int holdTicks, double accelerate, Consumer<Target> onBoostedHit,
                         String catchCue, String boostCue) {

        /** Where the segment from {@code a} to {@code b} goes through it (0..1 along the segment), if it does. */
        public Optional<Double> crossing(String inWorld, Vec3 a, Vec3 b) {
            if (!world.equals(inWorld)) return Optional.empty();
            double da = a.subtract(center).dot(normal), db = b.subtract(center).dot(normal);
            if (da == 0 && db == 0) return Optional.empty();   // sliding along it: not through it
            if ((da > 0) == (db > 0) && da != 0 && db != 0) return Optional.empty(); // same side all along
            double t = da == db ? 0 : da / (da - db);
            Vec3 at = a.add(b.subtract(a).multiply(t));
            Vec3 d = at.subtract(center);
            if (Math.abs(d.dot(right)) > width / 2 || Math.abs(d.dot(up)) > height / 2) return Optional.empty();
            return Optional.of(t);
        }

        /** The four corners, in order round its edge (for drawing it). */
        public List<Vec3> corners() {
            Vec3 r = right.multiply(width / 2), u = up.multiply(height / 2);
            return List.of(center.subtract(r).subtract(u), center.add(r).subtract(u), center.add(r).add(u), center.subtract(r).add(u));
        }
    }

    /** Where a screen stands and which way it faces. */
    public record Frame(Vec3 center, Vec3 normal, Vec3 right, Vec3 up) {}

    /** How far short of a wall the crosshair is on a screen stands (so it isn't half inside it). */
    public static final double WALL_GAP = 0.6;

    /**
     * A screen facing along {@code look} with its middle on {@code spot}, pulled {@code back} blocks toward {@code eye}
     * (short of a wall the crosshair is on). Its width runs level across the look (looking straight up or down, across
     * {@code fallbackRight}); its height is the rest.
     */
    public static Frame frame(Vec3 eye, Vec3 look, Vec3 spot, double back, Vec3 fallbackRight) {
        Vec3 normal = look.isZero() ? new Vec3(1, 0, 0) : look.normalize();
        Vec3 right = normal.cross(Vec3.UP);
        if (right.lengthSquared() < 1e-6) right = fallbackRight == null || fallbackRight.isZero() ? new Vec3(0, 0, 1) : fallbackRight;
        right = right.normalize();
        Vec3 up = right.cross(normal).normalize();
        Vec3 toEye = eye.subtract(spot);
        double room = toEye.length();
        Vec3 center = back <= 0 || room < 1e-6 ? spot : spot.add(toEye.normalize().multiply(Math.min(back, room)));
        return new Frame(center, normal, right, up);
    }

    /** One projectile going through a screen this tick. */
    public record Crossing(Screen screen, double t, boolean friendly) {}

    private final Teams teams;
    private final List<Screen> active = new ArrayList<>();

    public Screens(Teams teams) {
        this.teams = teams;
    }

    /** Put one up; returns how to take it down (idempotent). */
    public Runnable add(Screen screen) {
        active.add(screen);
        return () -> active.remove(screen);
    }

    public List<Screen> active() { return List.copyOf(active); }

    /**
     * The first screen a projectile of {@code shooter}'s goes through between {@code a} and {@code b}, skipping the ones it
     * has been through already. {@code friendly}: the owner's or an ally's shot (a shot nobody owns counts as an enemy's).
     */
    public Optional<Crossing> cross(String world, Vec3 a, Vec3 b, UUID shooter, Set<Screen> passed) {
        Crossing best = null;
        for (Screen s : active) {
            if (passed.contains(s)) continue;
            Optional<Double> t = s.crossing(world, a, b);
            if (t.isEmpty() || (best != null && best.t() <= t.get())) continue;
            boolean friendly = shooter != null && (shooter.equals(s.owner()) || !teams.enemies(shooter, s.owner()));
            best = new Crossing(s, t.get(), friendly);
        }
        return Optional.ofNullable(best);
    }
}
