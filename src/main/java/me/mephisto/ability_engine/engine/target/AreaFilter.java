package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Shared post-processing for area queries: drop the caster, sort nearest-first, cap the count. */
final class AreaFilter {

    static List<Target> finish(ExecutionContext ctx, List<EntitySnapshot> found, Predicate<EntitySnapshot> inShape,
                               ToDoubleFunction<EntitySnapshot> distance, boolean includeCaster, int maxTargets) {
        return found.stream()
                .filter(e -> includeCaster || !e.id().equals(ctx.caster()))
                .filter(inShape)
                .sorted(Comparator.comparingDouble(distance))
                .limit(maxTargets <= 0 ? Long.MAX_VALUE : maxTargets)
                .map(e -> (Target) new EntityTarget(e.id()))
                .toList();
    }

    private AreaFilter() {}
}
