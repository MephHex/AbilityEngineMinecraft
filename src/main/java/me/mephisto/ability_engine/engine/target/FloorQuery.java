package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Everyone standing on the caster's own floor named {@code floor} right now, as that floor counts it (its square or disc,
 * its reach up and down), e.g. everyone inside their Trick Room. Nobody if they have no such floor down.
 */
public final class FloorQuery implements TargetQuery {

    private final String floor;

    public FloorQuery(String floor) {
        this.floor = floor;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        var engine = ctx.engine();
        List<EntitySnapshot> found = new ArrayList<>();
        for (var patch : engine.floors().of(ctx.caster())) {
            if (!floor.equals(patch.name())) continue;
            var center = new PointTarget(patch.world(), patch.center());
            // (a square's corner is radius x 1.42 out; its reach up and down a few blocks more)
            for (EntitySnapshot e : engine.world().livingEntitiesNear(center, patch.radius() * 1.5 + 4)) {
                if (patch.contains(patch.world(), e.center()) && found.stream().noneMatch(f -> f.id().equals(e.id()))) found.add(e);
            }
        }
        return AreaFilter.finish(ctx, found, e -> true, e -> 0, false, 0);
    }
}
