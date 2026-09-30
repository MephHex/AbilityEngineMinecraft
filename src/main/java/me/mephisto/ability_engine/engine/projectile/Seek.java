package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
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
 *       gone (expired).</li>
 * </ol>
 * The ProjectileSystem runs it itself ({@link #steer}); {@link #apply} is only for the MotionModifier type.
 */
public record Seek(double range, double speed, double base, double turnRate, double maxDistance, int hoverTicks)
        implements MotionModifier {

    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) { return velocity; }

    /** Set its velocity for this tick. Returns false when it has hovered its time out (it should expire). */
    boolean steer(Projectile p, ExecutionContext ctx) {
        if (p.heading == null) p.heading = p.velocity.isZero() ? new Vec3(1, 0, 0) : p.velocity.normalize();
        Optional<Vec3> target = target(p, ctx);
        if (target.isPresent()) {
            Vec3 desired = target.get().subtract(p.position).normalize();
            Vec3 dir = p.velocity.isZero() ? desired : p.velocity.normalize().add(desired.multiply(turnRate)).normalize();
            if (dir.isZero()) dir = desired;
            p.velocity = dir.multiply(speed);
            p.heading = dir;
            p.markGuided(); // homing: the distance doesn't count
            return true;
        }
        if (maxDistance > 0 && p.unguidedDistance >= maxDistance) {
            p.velocity = Vec3.ZERO;                      // hovers where it stopped
            return ++p.hoverTicks <= hoverTicks;
        }
        p.velocity = p.heading.multiply(base);
        return true;
    }

    private Optional<Vec3> target(Projectile p, ExecutionContext ctx) {
        var engine = ctx.engine();
        UUID caster = ctx.caster();
        Optional<Vec3> best = Optional.empty();
        double bestDist = Double.MAX_VALUE;
        var body = p.visual.body().orElse(null);           // never itself
        for (UUID enemy : engine.hearing().heard(caster)) { // heard: however far
            if (enemy.equals(body)) continue;
            var at = engine.world().positionOf(new EntityTarget(enemy));
            if (at.isEmpty() || !at.get().world().equals(p.world)) continue;
            double d = at.get().position().distance(p.position);
            if (d < bestDist) {
                bestDist = d;
                best = Optional.of(at.get().position());
            }
        }
        if (best.isPresent() || range <= 0) return best;
        for (var e : engine.world().livingEntitiesNear(new PointTarget(p.world, p.position), range)) {
            if (e.id().equals(caster) || e.id().equals(body) || engine.teams().allies(caster, e.id())) continue;
            double d = e.center().distance(p.position);
            if (d <= range && d < bestDist) {
                bestDist = d;
                best = Optional.of(e.center());
            }
        }
        return best;
    }
}
