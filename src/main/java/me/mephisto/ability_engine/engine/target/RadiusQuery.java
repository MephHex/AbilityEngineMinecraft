package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.platform.WorldQuery;

import java.util.List;
import java.util.Optional;

/**
 * Everything within a sphere around a blackboard target, or around the caster (was RadialTargetSelector).
 * With {@code inner} it's a ring: only what's farther than that from the centre (e.g. one band of a
 * shockwave travelling outward).
 */
public final class RadiusQuery implements TargetQuery {

    private final String centerKey; // null = caster
    private final double radius;
    private final int maxTargets;   // <= 0 = unlimited
    private final boolean includeCaster;
    private final double inner;

    public RadiusQuery(String centerKey, double radius, int maxTargets, boolean includeCaster) {
        this(centerKey, radius, maxTargets, includeCaster, 0);
    }

    public RadiusQuery(String centerKey, double radius, int maxTargets, boolean includeCaster, double inner) {
        this.inner = inner;
        this.centerKey = centerKey;
        this.radius = radius;
        this.maxTargets = maxTargets;
        this.includeCaster = includeCaster;
    }

    /** Distance on the ground plane (a ring on the floor: height doesn't count). */
    private static double flat(me.mephisto.ability_engine.engine.math.Vec3 a, me.mephisto.ability_engine.engine.math.Vec3 b) {
        double dx = a.x() - b.x(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
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
                e -> e.center().distance(c) <= radius && (inner <= 0 || flat(e.center(), c) > inner),
                e -> e.center().distance(c),
                includeCaster, maxTargets);
    }
}
