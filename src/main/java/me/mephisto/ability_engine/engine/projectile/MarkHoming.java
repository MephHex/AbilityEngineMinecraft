package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.UUID;

/**
 * A slight pull toward enemies the caster has marked: each tick it turns {@code turnRate} toward the nearest enemy within
 * {@code range} that has {@code status} from the caster and lies within {@code angle} degrees (full width) of where it's
 * flying, keeping its speed. No marked enemy ahead: it flies on as it was (e.g. the Arcanist's bolts curving into
 * whoever Arcane Missile marked).
 */
public record MarkHoming(String status, double range, double turnRate, double angle) implements MotionModifier {

    @Override
    public Vec3 apply(Vec3 position, Vec3 velocity, ExecutionContext ctx) {
        if (velocity.isZero()) return velocity;
        var engine = ctx.engine();
        UUID caster = ctx.caster();
        Vec3 heading = velocity.normalize();
        double cos = Math.cos(Math.toRadians(angle / 2));
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        String world = engine.world().aimOf(caster).map(a -> a.world()).orElse(null);
        if (world == null) return velocity;
        for (var e : engine.world().livingEntitiesNear(new PointTarget(world, position), range)) {
            if (e.id().equals(caster) || engine.teams().allies(caster, e.id())) continue;
            boolean marked = engine.statuses().find(e.id(), status).filter(s -> caster.equals(s.source())).isPresent();
            if (!marked) continue;
            Vec3 to = e.center().subtract(position);
            double d = to.length();
            if (d > range || d < 1e-6 || to.multiply(1 / d).dot(heading) < cos) continue;
            if (d < bestDist) {
                bestDist = d;
                best = to.multiply(1 / d);
            }
        }
        if (best == null) return velocity;
        Vec3 dir = heading.add(best.multiply(turnRate)).normalize();
        return dir.multiply(velocity.length());
    }
}
