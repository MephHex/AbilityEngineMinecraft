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
 * walls: a block in the way is a miss unless {@code includeBlocks} is set. It passes through whoever the caster
 * rides.
 */
public final class HitscanQuery implements TargetQuery {

    private final double range;
    private final double raySize;
    private final boolean includeBlocks;
    private final boolean allies; // aim at allies instead of enemies (enemies are passed through)

    public HitscanQuery(double range, double raySize, boolean includeBlocks) {
        this(range, raySize, includeBlocks, false);
    }

    /** @param allies hit the first ALLY on the ray (enemies and the caster are passed through), e.g. a bond */
    public HitscanQuery(double range, double raySize, boolean includeBlocks, boolean allies) {
        this.range = range;
        this.raySize = raySize;
        this.includeBlocks = includeBlocks;
        this.allies = allies;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        WorldQuery world = ctx.engine().world();
        Optional<Aim> aim = world.aimOf(ctx.caster());
        if (aim.isEmpty()) return List.of();

        Aim a = aim.get();
        var teams = ctx.engine().teams();
        java.util.function.Predicate<java.util.UUID> through = allies
                ? id -> id.equals(ctx.caster()) || !teams.allies(ctx.caster(), id)
                        || ctx.engine().tags().has(id, me.mephisto.ability_engine.engine.tag.Tags.UNTARGETABLE)
                        || ctx.engine().veils().blocks(ctx.caster(), id)
                : teams.passThroughFor(ctx.caster());
        // Whoever they ride is right under their eyes: aiming past them, a thick ray would always hit them.
        var mount = ctx.engine().rides().mountOf(ctx.caster());
        if (mount.isPresent()) through = through.or(mount.get()::equals);
        Optional<SweepHit> hit = world.sweep(a.world(), a.eye(), a.eye().add(a.direction().multiply(range)), raySize, through);
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
