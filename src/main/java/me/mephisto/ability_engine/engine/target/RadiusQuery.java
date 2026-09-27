package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.platform.WorldQuery;

import java.util.List;
import java.util.Optional;

/** Everything within a sphere around a blackboard target, or around the caster (was RadialTargetSelector). */
public final class RadiusQuery implements TargetQuery {

    private final String centerKey; // null = caster
    private final double radius;
    private final int maxTargets;   // <= 0 = unlimited
    private final boolean includeCaster;

    public RadiusQuery(String centerKey, double radius, int maxTargets, boolean includeCaster) {
        this.centerKey = centerKey;
        this.radius = radius;
        this.maxTargets = maxTargets;
        this.includeCaster = includeCaster;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        WorldQuery world = ctx.engine().world();
        Optional<Target> centerTarget = centerKey == null
                ? Optional.of(new EntityTarget(ctx.caster()))
                : KeyQuery.read(ctx, centerKey);
        Optional<PointTarget> center = centerTarget.flatMap(world::positionOf);
        if (center.isEmpty()) return List.of();

        var c = center.get().position();
        return AreaFilter.finish(ctx, world.livingEntitiesNear(center.get(), radius),
                e -> e.center().distance(c) <= radius,
                e -> e.center().distance(c),
                includeCaster, maxTargets);
    }
}
