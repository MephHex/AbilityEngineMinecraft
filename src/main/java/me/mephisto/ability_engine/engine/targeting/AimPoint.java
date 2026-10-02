package me.mephisto.ability_engine.engine.targeting;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.target.CursorQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;
import java.util.UUID;

/**
 * Where an ability would land if cast now. Preview and cast both use this, so they always agree.
 * <ul>
 *   <li>Aim previews look at BLOCKS only: the ray passes through entities, so you can place things
 *       on the ground under someone.</li>
 *   <li>{@code ground: true}: the crosshair point is projected straight down onto the ground, but no
 *       deeper than {@code max_drop} below your feet. If the ground there is too deep (a cliff, the
 *       void), it walks back toward you and uses the last spot that IS valid: the cliff edge.
 *       Empty only if nothing on the way back is valid (red preview, confirm refused).</li>
 * </ul>
 */
public final class AimPoint {

    /** Range for abilities without a targeting block. */
    public static final double DEFAULT_RANGE = 60;
    /** Step back from the hit surface toward the caster so the ground search doesn't start inside a wall. */
    private static final double SURFACE_BACKOFF = 0.3;
    /** How far apart the cliff-edge search samples the ground, walking back toward the caster. */
    private static final double EDGE_STEP = 0.25;
    /** Player eye height: max_drop is measured from the feet. */
    private static final double EYE_HEIGHT = 1.62;

    public static Optional<PointTarget> resolve(AbilityEngine engine, UUID caster, Targeting t) {
        if (t != null && t.arc() != null) return Trajectory.landing(engine, caster, t.arc()); // where the throw lands
        Optional<PointTarget> raw = raw(engine, caster, t);
        if (raw.isEmpty() || t == null || !t.ground()) return raw;

        Optional<Aim> aim = engine.world().aimOf(caster);
        if (aim.isEmpty()) return Optional.empty();
        Aim a = aim.get();
        String world = raw.get().world();
        Vec3 hit = raw.get().position();
        double lowest = a.eye().y() - EYE_HEIGHT - t.maxDrop(); // deepest allowed landing height

        // 1) straight below the crosshair
        Vec3 start = hit.add(a.eye().subtract(hit).normalize().multiply(SURFACE_BACKOFF));
        Optional<Vec3> ground = groundAt(engine, world, start, lowest);
        if (ground.isPresent()) return Optional.of(new PointTarget(world, ground.get()));

        // 2) too deep or nothing below: walk back toward the caster to the last valid spot (cliff edge)
        Vec3 back = new Vec3(a.eye().x() - hit.x(), 0, a.eye().z() - hit.z());
        double dist = back.length();
        Vec3 dir = back.normalize();
        double top = Math.max(hit.y(), a.eye().y());
        for (double d = EDGE_STEP; d <= dist; d += EDGE_STEP) {
            Vec3 p = new Vec3(hit.x() + dir.x() * d, top, hit.z() + dir.z() * d);
            ground = groundAt(engine, world, p, lowest);
            if (ground.isPresent()) return Optional.of(new PointTarget(world, ground.get()));
        }
        return Optional.empty();
    }

    private static Optional<Vec3> groundAt(AbilityEngine engine, String world, Vec3 from, double lowest) {
        double drop = from.y() - lowest;
        return drop < 0 ? Optional.empty() : engine.world().groundBelow(world, from, drop);
    }

    /**
     * The unsnapped crosshair point. With a targeting block it's blocks-only (entities are ignored);
     * without one (quick "aim" for unaimed abilities) it stops at enemies.
     */
    public static Optional<PointTarget> raw(AbilityEngine engine, UUID caster, Targeting t) {
        return t == null
                ? CursorQuery.point(engine, caster, DEFAULT_RANGE, true)
                : CursorQuery.point(engine, caster, t.range(), false);
    }

    private AimPoint() {}
}
