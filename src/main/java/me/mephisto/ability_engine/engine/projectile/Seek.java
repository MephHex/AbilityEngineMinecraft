package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;
import java.util.UUID;

/**
 * A seeker (e.g. the Whisperer's Chorus Shade), in order:
 * <ol>
 *   <li>A target: the nearest enemy the caster HEARS (their hearing passive), however far; else the nearest
 *       enemy within {@code range} of the projectile. It homes on it at {@code speed}, and while it does the
 *       distance doesn't count: only its lifetime limits it.</li>
 *   <li>No target: it flies straight on at {@code base} until it has flown {@code max_distance} (0 = no
 *       limit), then hovers where it is for {@code hover} ticks, still looking for a target, and then it's
 *       gone (expired). With {@code to: <key>} it flies to that spot instead ({@code up} blocks above it) and
 *       hovers there.</li>
 * </ol>
 * {@code lock: true}: it keeps after the first enemy it found until they're gone (then it looks again).
 * {@code mark: <status>}: whoever it's after keeps that status meanwhile (e.g. glowing), and a little after.
 * The ProjectileSystem runs it itself ({@link #steer}); {@link #apply} is only for the MotionModifier type.
 */
public record Seek(double range, double speed, double base, double turnRate, double maxDistance, int hoverTicks,
                   String toKey, double up, boolean lock, String mark)
        implements MotionModifier {

    /** A mark is put on again this often, lasting {@link #MARK_LASTS}: it wears off soon after the chase. */
    private static final int MARK_EVERY = 5;
    private static final int MARK_LASTS = 10;

    public Seek(double range, double speed, double base, double turnRate, double maxDistance, int hoverTicks) {
        this(range, speed, base, turnRate, maxDistance, hoverTicks, null, 0, false, null);
    }

    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) { return velocity; }

    private record Quarry(UUID id, Vec3 at) {}

    /** Set its velocity for this tick. Returns false when it has hovered its time out (it should expire). */
    boolean steer(Projectile p, ExecutionContext ctx) {
        if (p.heading == null) p.heading = p.velocity.isZero() ? new Vec3(1, 0, 0) : p.velocity.normalize();
        Optional<Quarry> quarry = target(p, ctx);
        if (quarry.isPresent()) {
            markOn(p, ctx, quarry.get().id());
            Vec3 desired = quarry.get().at().subtract(p.position).normalize();
            Vec3 dir = p.velocity.isZero() ? desired : p.velocity.normalize().add(desired.multiply(turnRate)).normalize();
            if (dir.isZero()) dir = desired;
            p.velocity = dir.multiply(speed);
            p.heading = dir;
            p.markGuided(); // homing: the distance doesn't count
            return true;
        }
        Vec3 spot = spot(p, ctx);
        if (spot != null) { // to a spot, then it hovers there
            Vec3 left = spot.subtract(p.position);
            if (left.length() <= Math.max(base, 1e-3)) {
                p.velocity = left;                       // there this tick (or already)
                return !left.isZero() || ++p.hoverTicks <= hoverTicks;
            }
            p.heading = left.normalize();
            p.velocity = p.heading.multiply(base);
            return true;
        }
        if (maxDistance > 0 && p.unguidedDistance >= maxDistance) {
            p.velocity = Vec3.ZERO;                      // hovers where it stopped
            return ++p.hoverTicks <= hoverTicks;
        }
        p.velocity = p.heading.multiply(base);
        return true;
    }

    /** Where it's sent ({@code to}, {@code up} above it), found on its first tick; null without one. */
    private Vec3 spot(Projectile p, ExecutionContext ctx) {
        if (toKey == null) return null;
        if (p.seekSpot == null) {
            p.seekSpot = KeyQuery.read(ctx, toKey).flatMap(ctx.engine().world()::positionOf)
                    .filter(t -> t.world().equals(p.world)).map(t -> t.position().add(0, up, 0))
                    .orElse(p.position);                 // nowhere to go: it hovers where it is
        }
        return p.seekSpot;
    }

    /** {@code mark}: put on whoever it's after, again every few ticks, so it wears off soon after. */
    private void markOn(Projectile p, ExecutionContext ctx, UUID quarry) {
        if (mark == null) return;
        if (quarry.equals(p.marked) && p.ticksLived - p.markedAt < MARK_EVERY) return;
        ctx.engine().statuses().apply(quarry, mark, MARK_LASTS, ctx.caster());
        p.marked = quarry;
        p.markedAt = p.ticksLived;
    }

    private Optional<Quarry> target(Projectile p, ExecutionContext ctx) {
        var engine = ctx.engine();
        UUID caster = ctx.caster();
        if (lock && p.locked != null) { // still after the first one, while they're around
            Optional<PointTarget> at = engine.world().isAlive(p.locked)
                    ? engine.world().positionOf(new EntityTarget(p.locked)) : Optional.empty();
            if (at.isPresent() && at.get().world().equals(p.world)) return Optional.of(new Quarry(p.locked, at.get().position()));
            p.locked = null;
        }
        Optional<Quarry> best = Optional.empty();
        double bestDist = Double.MAX_VALUE;
        var body = p.visual.body().orElse(null);           // never itself
        for (UUID enemy : engine.hearing().heard(caster)) { // heard: however far
            if (enemy.equals(body)) continue;
            var at = engine.world().positionOf(new EntityTarget(enemy));
            if (at.isEmpty() || !at.get().world().equals(p.world)) continue;
            double d = at.get().position().distance(p.position);
            if (d < bestDist) {
                bestDist = d;
                best = Optional.of(new Quarry(enemy, at.get().position()));
            }
        }
        if (best.isEmpty() && range > 0) {
            for (var e : engine.world().livingEntitiesNear(new PointTarget(p.world, p.position), range)) {
                if (e.id().equals(caster) || e.id().equals(body) || engine.teams().allies(caster, e.id())) continue;
                double d = e.center().distance(p.position);
                if (d <= range && d < bestDist) {
                    bestDist = d;
                    best = Optional.of(new Quarry(e.id(), e.center()));
                }
            }
        }
        if (lock) best.ifPresent(q -> p.locked = q.id());
        return best;
    }
}
