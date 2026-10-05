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
 * <p>{@code angle} (degrees, 0 = off): a forgiving aim for basic attacks. The ray comes first; if it hits nobody, the
 * target is whoever is closest to the crosshair within {@code range} and {@code angle / 2} of it, in sight (no wall in
 * between). Frontal barriers still stop it.
 */
public final class HitscanQuery implements TargetQuery {

    private final double range;
    private final double raySize;
    private final boolean includeBlocks;
    private final boolean allies; // aim at allies instead of enemies (enemies are passed through)
    private final double halfAngleRad; // 0: the ray only

    public HitscanQuery(double range, double raySize, boolean includeBlocks) {
        this(range, raySize, includeBlocks, false);
    }

    /** @param allies hit the first ALLY on the ray (enemies and the caster are passed through), e.g. a bond */
    public HitscanQuery(double range, double raySize, boolean includeBlocks, boolean allies) {
        this(range, raySize, includeBlocks, allies, 0);
    }

    /** @param angleDegrees missed with the ray: whoever is within this cone (full width) of the crosshair, nearest it */
    public HitscanQuery(double range, double raySize, boolean includeBlocks, boolean allies, double angleDegrees) {
        this.range = range;
        this.raySize = raySize;
        this.includeBlocks = includeBlocks;
        this.allies = allies;
        this.halfAngleRad = Math.toRadians(Math.max(0, angleDegrees) / 2.0);
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
        Vec3 far = a.eye().add(a.direction().multiply(range));
        Optional<SweepHit> hit = Optional.empty();
        if (raySize > EXACT) {
            // A thick ray is a forgiving aim, not a different one: whoever they aim right at comes first...
            Optional<SweepHit> exact = world.sweep(a.world(), a.eye(), far, EXACT, through);
            if (exact.isPresent() && !exact.get().isBlock()) hit = exact;
        }
        if (hit.isEmpty()) {
            // ...and it starts at their eyes, so it already touches anyone close around them: never those behind.
            var behind = raySize > EXACT ? behind(world, a) : (java.util.function.Predicate<java.util.UUID>) id -> false;
            hit = world.sweep(a.world(), a.eye(), far, raySize, through.or(behind));
        }
        if (halfAngleRad > 0 && (hit.isEmpty() || hit.get().isBlock())) { // missed: the nearest to the crosshair in the angle
            Optional<SweepHit> near = inAngle(world, a, through);
            if (near.isPresent()) hit = near;
        }
        if (hit.isEmpty() || hit.get().isBlock()) { // missed, but the player's swing hit someone: them (lag, an edge)
            var finalThrough = through;
            Optional<SweepHit> clicked = ClickAssist.clicked(ctx, a, range).filter(e -> !finalThrough.test(e.id()))
                    .map(e -> new SweepHit(new EntityTarget(e.id()), e.center(), null));
            if (clicked.isPresent()) hit = clicked;
        }
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

    /** Whoever is within the angle and range, nearest the crosshair (then nearest them), with nothing solid between. */
    private Optional<SweepHit> inAngle(WorldQuery world, Aim a, java.util.function.Predicate<java.util.UUID> through) {
        return world.livingEntitiesNear(new PointTarget(a.world(), a.eye()), range).stream()
                .filter(e -> !through.test(e.id()))
                .filter(e -> {
                    Vec3 to = e.center().subtract(a.eye());
                    return to.length() <= range && a.direction().angleTo(to) <= halfAngleRad;
                })
                .sorted(java.util.Comparator.<me.mephisto.ability_engine.engine.platform.EntitySnapshot>comparingDouble(
                                e -> a.direction().angleTo(e.center().subtract(a.eye())))
                        .thenComparingDouble(e -> e.center().distance(a.eye())))
                .map(e -> world.sweep(a.world(), a.eye(), e.center(), EXACT, id -> !id.equals(e.id()))) // in sight?
                .filter(s -> s.isPresent() && !s.get().isBlock())
                .map(Optional::get)
                .findFirst();
    }

    /** A thick ray's exact aim: this thin, it's where they really point. */
    private static final double EXACT = 0.1;

    /** Entities behind the eyes (not in the direction they look). */
    private static java.util.function.Predicate<java.util.UUID> behind(WorldQuery world, Aim a) {
        return id -> world.positionOf(new EntityTarget(id))
                .map(at -> at.position().subtract(a.eye()).dot(a.direction()) <= 0)
                .orElse(false);
    }
}
