package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A wide beam from the caster's eyes: every living entity within {@code width/2} of the line, up to
 * {@code range}, nearest first. The beam stops at the first BLOCK (line of sight matters) and at an
 * enemy's frontal barrier. It passes through entities (it's a beam, not a bullet).
 * The beam's two ends are written to the blackboard as "beam_start" and "beam_end" for the visual
 * ({@code play_cue ... at: beam_start, to: beam_end}).
 */
public final class LineQuery implements TargetQuery {

    public static final String KEY_START = "beam_start";
    public static final String KEY_END = "beam_end";
    /** Rough body radius, so a beam grazing someone's side still counts. */
    private static final double BODY = 0.4;

    private final double range;
    private final double width;

    public LineQuery(double range, double width) {
        this.range = range;
        this.width = width;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        AbilityEngine engine = ctx.engine();
        Optional<Aim> aim = engine.world().aimOf(ctx.caster());
        if (aim.isEmpty()) return List.of();
        Aim a = aim.get();

        // Where the beam ends: the first block (entities don't stop it), or an enemy's barrier.
        Vec3 far = a.eye().add(a.direction().multiply(range));
        Vec3 end = engine.world().sweep(a.world(), a.eye(), far, 0.1, id -> true).map(h -> h.position()).orElse(far);
        var barrier = engine.barriers().cross(a.world(), a.eye(), end, width / 2, ctx.caster());
        if (barrier.isPresent()) {
            end = barrier.get().position();
            engine.barriers().blocked(a.world(), end);
        }
        ctx.blackboard().putRaw(KEY_START, new PointTarget(a.world(), a.eye()));
        ctx.blackboard().putRaw(KEY_END, new PointTarget(a.world(), end));

        Vec3 start = a.eye();
        Vec3 seg = end.subtract(start);
        double len = seg.length();
        if (len < 1e-6) return List.of();
        Vec3 mid = start.add(seg.multiply(0.5));
        List<EntitySnapshot> near = new java.util.ArrayList<>(
                engine.world().livingEntitiesNear(new PointTarget(a.world(), mid), len / 2 + width));
        ClickAssist.clicked(ctx, a, range).filter(c -> near.stream().noneMatch(e -> e.id().equals(c.id()))).ifPresent(near::add);
        double reach = width / 2 + BODY;
        return near.stream()
                .filter(e -> !e.id().equals(ctx.caster()))
                .filter(e -> {
                    if (ClickAssist.is(ctx, a, e, range)) return true; // the one the player's swing hit (lag, an edge)
                    double t = e.center().subtract(start).dot(seg) / (len * len);
                    if (t < 0 || t > 1) return false;
                    return start.add(seg.multiply(t)).distance(e.center()) <= reach;
                })
                .sorted(Comparator.comparingDouble(e -> e.center().distance(start)))
                .map(e -> (Target) new EntityTarget(e.id()))
                .toList();
    }
}
