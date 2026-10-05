package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.WorldQuery;

import java.util.List;
import java.util.Optional;

/**
 * Everything in a cone in front of the caster's eyes (was ConicTargetSelector, which was a stub).
 * <p>Or from somewhere else: {@code from: <key>} (e.g. where a shot hit), pointing {@code heading: aim} (the caster's aim)
 * or {@code flight} (on, the way the shot that hit was flying), starting {@code ahead} blocks further along. With
 * {@code sight: true} only those it can see from there count (no wall in between): a shatter against a wall doesn't
 * reach through it.
 */
public final class ConeQuery implements TargetQuery {

    private final double range;
    private final double halfAngleRad;
    private final int maxTargets;
    private final String from;    // null = the caster's eyes
    private final boolean flight; // point along the hit shot's flight (else the caster's aim)
    private final double ahead;
    private final boolean sight;

    /** @param angleDegrees full width of the cone, e.g. 90 = 45 degrees either side of the crosshair */
    public ConeQuery(double range, double angleDegrees, int maxTargets) {
        this(range, angleDegrees, maxTargets, null, false, 0, false);
    }

    public ConeQuery(double range, double angleDegrees, int maxTargets, String from, boolean flight, double ahead, boolean sight) {
        this.range = range;
        this.halfAngleRad = Math.toRadians(angleDegrees / 2.0);
        this.maxTargets = maxTargets;
        this.from = from;
        this.flight = flight;
        this.ahead = ahead;
        this.sight = sight;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        WorldQuery world = ctx.engine().world();
        Optional<Aim> aim = world.aimOf(ctx.caster());
        if (aim.isEmpty()) return List.of();

        Aim a = aim.get();
        if (from == null) {
            double search = range + Math.max(0, ctx.engine().meleeAssistReach());
            return AreaFilter.finish(ctx, world.livingEntitiesNear(new PointTarget(a.world(), a.eye()), search),
                    e -> {
                        var toEntity = e.center().subtract(a.eye());
                        return (toEntity.length() <= range && a.direction().angleTo(toEntity) <= halfAngleRad)
                                || ClickAssist.is(ctx, a, e, range); // the one the player's swing hit (lag, an edge)
                    },
                    e -> e.center().distance(a.eye()),
                    false, maxTargets);
        }
        Optional<PointTarget> at = KeyQuery.read(ctx, from).flatMap(world::positionOf);
        if (at.isEmpty()) return List.of();
        Vec3 dir = flight ? flightOf(ctx).orElse(a.direction()) : a.direction();
        Vec3 origin = at.get().position().add(dir.multiply(ahead));
        String w = at.get().world();
        return AreaFilter.finish(ctx, world.livingEntitiesNear(new PointTarget(w, origin), range),
                e -> {
                    var toEntity = e.center().subtract(origin);
                    if (toEntity.length() > range || dir.angleTo(toEntity) > halfAngleRad) return false;
                    // in sight: nothing solid between (entities don't block)
                    return !sight || world.sweep(w, origin, e.center(), 0.05, id -> !id.equals(e.id()))
                            .map(h -> !h.isBlock()).orElse(true);
                },
                e -> e.center().distance(origin),
                false, maxTargets);
    }

    /** The way the shot that hit was flying (from where it was a tick before the hit to the hit). */
    private static Optional<Vec3> flightOf(ExecutionContext ctx) {
        var world = ctx.engine().world();
        var hit = KeyQuery.read(ctx, Keys.HIT.name()).flatMap(world::positionOf);
        var before = KeyQuery.read(ctx, Keys.HIT_FROM.name()).flatMap(world::positionOf);
        if (hit.isEmpty() || before.isEmpty()) return Optional.empty();
        Vec3 d = hit.get().position().subtract(before.get().position());
        return d.isZero() ? Optional.empty() : Optional.of(d.normalize());
    }
}
