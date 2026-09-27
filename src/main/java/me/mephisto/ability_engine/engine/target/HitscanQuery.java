package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.SweepHit;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Instant ray from the caster's eyes (was HitscanCast). Unlike the old version this respects
 * walls: a block in the way is a miss unless {@code includeBlocks} is set.
 */
public final class HitscanQuery implements TargetQuery {

    private final double range;
    private final double raySize;
    private final boolean includeBlocks;

    public HitscanQuery(double range, double raySize, boolean includeBlocks) {
        this.range = range;
        this.raySize = raySize;
        this.includeBlocks = includeBlocks;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        WorldQuery world = ctx.engine().world();
        Optional<Aim> aim = world.aimOf(ctx.caster());
        if (aim.isEmpty()) return List.of();

        Aim a = aim.get();
        Optional<SweepHit> hit = world.sweep(a.world(), a.eye(), a.eye().add(a.direction().multiply(range)), raySize,
                ctx.engine().teams().passThroughFor(ctx.caster()));
        // An enemy's frontal barrier stops the ray like a wall.
        Vec3 end = hit.map(SweepHit::position).orElse(a.eye().add(a.direction().multiply(range)));
        var barrier = ctx.engine().barriers().cross(a.world(), a.eye(), end, raySize, ctx.caster());
        if (barrier.isPresent()) {
            ctx.engine().barriers().blocked(a.world(), barrier.get().position());
            return includeBlocks ? List.of(new PointTarget(a.world(), barrier.get().position())) : List.of();
        }
        if (hit.isEmpty()) return List.of();
        if (hit.get().isBlock() && !includeBlocks) return List.of();
        return List.of(hit.get().target());
    }
}
