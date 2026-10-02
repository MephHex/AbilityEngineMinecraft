package me.mephisto.ability_engine.engine.barrier;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.CuePlayer;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.team.Teams;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontal barriers (a raised shield, a water wall): a flat disc standing {@code distance} in front of
 * its owner, facing where they look (horizontally), re-aimed every time it's checked. It blocks
 * ENEMY things coming TOWARD its front: projectiles, hitscans and dashes crossing it, and direct hits
 * (melee) from in front. Allies, and anything from behind or the sides, are unaffected.
 */
public final class BarrierSystem {

    /** Direct hits count as "from the front" within this angle of the owner's facing (each side). */
    private static final double FRONT_HALF_ANGLE = Math.toRadians(70);
    public static final String BLOCK_CUE = "barrier_block";

    /** @param projectilesOnly it only stops projectiles (not rays, dashes or melee) */
    private record Barrier(UUID owner, double distance, double radius, boolean projectilesOnly, Absorb onAbsorb) {}

    /** Told when a barrier absorbs an enemy projectile: in which world, where, and whose it was. */
    @FunctionalInterface
    public interface Absorb {
        void absorbed(String world, Vec3 at, UUID attacker);
    }

    /** Where a crossing path hits a barrier, and how far along the path that is. */
    public record Crossing(Vec3 position, double distance, Absorb onAbsorb) {
        public Crossing(Vec3 position, double distance) { this(position, distance, null); }
    }

    private final WorldQuery world;
    private final Teams teams;
    private final CuePlayer cues;
    private final List<Barrier> active = new ArrayList<>();

    public BarrierSystem(WorldQuery world, Teams teams, CuePlayer cues) {
        this.world = world;
        this.teams = teams;
        this.cues = cues;
    }

    /** Raise a barrier; returns how to lower it (idempotent). */
    public Runnable raise(UUID owner, double distance, double radius) {
        return raise(owner, distance, radius, false);
    }

    public Runnable raise(UUID owner, double distance, double radius, boolean projectilesOnly) {
        return raise(owner, distance, radius, projectilesOnly, null);
    }

    /** @param onAbsorb told whenever it absorbs an enemy projectile (null = nobody) */
    public Runnable raise(UUID owner, double distance, double radius, boolean projectilesOnly, Absorb onAbsorb) {
        Barrier b = new Barrier(owner, distance, radius, projectilesOnly, onAbsorb);
        active.add(b);
        return () -> active.remove(b);
    }

    public boolean has(UUID owner) {
        return active.stream().anyMatch(b -> b.owner().equals(owner));
    }

    /**
     * The first enemy barrier a path from {@code from} to {@code to} (a sphere of {@code thickness})
     * crosses while moving toward its front. {@code attacker} is whose shot/dash it is.
     */
    public Optional<Crossing> cross(String worldName, Vec3 from, Vec3 to, double thickness, UUID attacker) {
        return cross(worldName, from, to, thickness, attacker, false);
    }

    /** Like {@link #cross}, for a projectile: projectile-only barriers count too. */
    public Optional<Crossing> crossProjectile(String worldName, Vec3 from, Vec3 to, double thickness, UUID attacker) {
        return cross(worldName, from, to, thickness, attacker, true);
    }

    private Optional<Crossing> cross(String worldName, Vec3 from, Vec3 to, double thickness, UUID attacker, boolean projectile) {
        Vec3 d = to.subtract(from);
        Crossing best = null;
        for (Barrier b : List.copyOf(active)) {
            if (b.projectilesOnly() && !projectile) continue;
            if (!teams.enemies(attacker, b.owner())) continue;
            Optional<Plane> plane = plane(b);
            if (plane.isEmpty() || !plane.get().world.equals(worldName)) continue;
            Plane pl = plane.get();
            double denom = pl.normal.dot(d);
            if (denom >= -1e-9) continue; // parallel, or moving away from its front: not blocked
            double t = pl.normal.dot(pl.center.subtract(from)) / denom;
            if (t < 0 || t > 1) continue;
            Vec3 hit = from.add(d.multiply(t));
            if (hit.distance(pl.center) > b.radius() + thickness) continue;
            double dist = hit.distance(from);
            if (best == null || dist < best.distance()) best = new Crossing(hit, dist, b.onAbsorb());
        }
        return Optional.ofNullable(best);
    }

    /** Would a direct hit (melee) on {@code target} coming from {@code origin} be blocked by its barrier? */
    public boolean blocksDirectHit(UUID target, Vec3 origin, UUID attacker) {
        return blocksDirectHit(target, origin, attacker, false);
    }

    /** {@code projectile}: the hit is a projectile (a vanilla arrow), so projectile-only barriers count too. */
    public boolean blocksDirectHit(UUID target, Vec3 origin, UUID attacker, boolean projectile) {
        if (!teams.enemies(attacker, target)) return false;
        for (Barrier b : List.copyOf(active)) {
            if (!b.owner().equals(target) || (b.projectilesOnly() && !projectile)) continue;
            Optional<Plane> plane = plane(b);
            if (plane.isEmpty()) continue;
            Vec3 toOrigin = origin.subtract(plane.get().ownerCenter);
            Vec3 flat = new Vec3(toOrigin.x(), 0, toOrigin.z());
            if (flat.isZero() || plane.get().normal.angleTo(flat) <= FRONT_HALF_ANGLE) return true;
        }
        return false;
    }

    /** A projectile of {@code attacker}'s was absorbed at this crossing: tell the barrier, if it wants to know. */
    public void absorbed(String world, Crossing crossing, UUID attacker) {
        if (crossing.onAbsorb() != null) crossing.onAbsorb().absorbed(world, crossing.position(), attacker);
    }

    /** Splash where something was blocked. */
    public void blocked(String worldName, Vec3 at) {
        cues.play(BLOCK_CUE, worldName, at);
    }

    // ---- geometry ----------------------------------------------------------------------------

    private record Plane(String world, Vec3 ownerCenter, Vec3 center, Vec3 normal) {}

    private Optional<Plane> plane(Barrier b) {
        Optional<Aim> aim = world.aimOf(b.owner());
        Optional<PointTarget> body = world.positionOf(new EntityTarget(b.owner()));
        if (aim.isEmpty() || body.isEmpty()) return Optional.empty();
        Vec3 facing = new Vec3(aim.get().direction().x(), 0, aim.get().direction().z()).normalize();
        if (facing.isZero()) return Optional.empty(); // looking straight up/down: no clear front
        Vec3 center = body.get().position().add(facing.multiply(b.distance()));
        return Optional.of(new Plane(body.get().world(), body.get().position(), center, facing));
    }
}
