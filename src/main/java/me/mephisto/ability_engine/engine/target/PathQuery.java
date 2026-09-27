package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Everyone within {@code width/2} of the segment between two blackboard points (e.g. where a dash
 * started and ended), ordered from the start. No line of sight needed: it follows a path you took.
 */
public final class PathQuery implements TargetQuery {

    private static final double BODY = 0.4;

    private final String fromKey;
    private final String toKey;
    private final double width;

    public PathQuery(String fromKey, String toKey, double width) {
        this.fromKey = fromKey;
        this.toKey = toKey;
        this.width = width;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        var world = ctx.engine().world();
        Optional<PointTarget> a = KeyQuery.read(ctx, fromKey).flatMap(world::positionOf);
        Optional<PointTarget> b = KeyQuery.read(ctx, toKey).flatMap(world::positionOf);
        if (a.isEmpty() || b.isEmpty()) return List.of();
        Vec3 start = a.get().position();
        Vec3 seg = b.get().position().subtract(start);
        double len = seg.length();
        double reach = width / 2 + BODY;
        Vec3 mid = start.add(seg.multiply(0.5));
        return world.livingEntitiesNear(new PointTarget(a.get().world(), mid), len / 2 + width).stream()
                .filter(e -> !e.id().equals(ctx.caster()))
                .filter(e -> {
                    double t = len < 1e-6 ? 0 : Math.max(0, Math.min(1, e.center().subtract(start).dot(seg) / (len * len)));
                    return start.add(seg.multiply(t)).distance(e.center()) <= reach;
                })
                .sorted(Comparator.comparingDouble(e -> e.center().distance(start)))
                .map(e -> (Target) new EntityTarget(e.id()))
                .toList();
    }
}
