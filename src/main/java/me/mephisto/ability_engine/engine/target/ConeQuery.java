package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.WorldQuery;

import java.util.List;
import java.util.Optional;

/** Everything in a cone in front of the caster's eyes (was ConicTargetSelector, which was a stub). */
public final class ConeQuery implements TargetQuery {

    private final double range;
    private final double halfAngleRad;
    private final int maxTargets;

    /** @param angleDegrees full width of the cone, e.g. 90 = 45 degrees either side of the crosshair */
    public ConeQuery(double range, double angleDegrees, int maxTargets) {
        this.range = range;
        this.halfAngleRad = Math.toRadians(angleDegrees / 2.0);
        this.maxTargets = maxTargets;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        WorldQuery world = ctx.engine().world();
        Optional<Aim> aim = world.aimOf(ctx.caster());
        if (aim.isEmpty()) return List.of();

        Aim a = aim.get();
        return AreaFilter.finish(ctx, world.livingEntitiesNear(new PointTarget(a.world(), a.eye()), range),
                e -> {
                    var toEntity = e.center().subtract(a.eye());
                    return toEntity.length() <= range && a.direction().angleTo(toEntity) <= halfAngleRad;
                },
                e -> e.center().distance(a.eye()),
                false, maxTargets);
    }
}
