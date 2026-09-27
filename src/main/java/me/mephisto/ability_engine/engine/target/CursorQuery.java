package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.WorldQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The point under the caster's crosshair: the first block or entity the look ray touches, or the
 * point at {@code range} if it touches nothing. Never misses (unless the caster is gone), so
 * blinks and ground-targeted spells always have somewhere to go.
 */
public final class CursorQuery implements TargetQuery {

    private final double range;

    public CursorQuery(double range) {
        this.range = range;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        return point(ctx, range).map(p -> List.<Target>of(p)).orElse(List.of());
    }

    public static Optional<PointTarget> point(ExecutionContext ctx, double range) {
        return point(ctx.engine(), ctx.caster(), range, true);
    }

    public static Optional<PointTarget> point(AbilityEngine engine, UUID caster, double range) {
        return point(engine, caster, range, true);
    }

    /**
     * @param entities true: stop at enemy entities (steering at someone). false: blocks only, the ray
     *                 passes through every entity (placing something on the ground under someone).
     */
    public static Optional<PointTarget> point(AbilityEngine engine, UUID caster, double range, boolean entities) {
        WorldQuery world = engine.world();
        Optional<Aim> aim = world.aimOf(caster);
        if (aim.isEmpty()) return Optional.empty();
        Aim a = aim.get();
        Vec3 far = a.eye().add(a.direction().multiply(range));
        java.util.function.Predicate<UUID> passThrough = entities ? engine.teams().passThroughFor(caster) : id -> true;
        Vec3 hit = world.sweep(a.world(), a.eye(), far, 0.1, passThrough).map(h -> h.position()).orElse(far);
        return Optional.of(new PointTarget(a.world(), hit));
    }
}
