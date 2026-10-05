package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fires {@code spec.count()} projectiles from the caster's eyes.
 * <ul>
 *   <li>"spawned" — immediately, once (wire a sound/cue here, or leave it empty)</li>
 *   <li>"hit_entity" / "hit_block" / "expired" — later, once PER projectile, each in its own
 *       forked branch so their "hit" values don't overwrite each other</li>
 * </ul>
 * With {@code store}, the projectile's handle is written to the blackboard of the branch that
 * continues from "spawned", so later nodes (redirect_projectile, await_recast) can reach it.
 * For count > 1 the last projectile is stored.
 * <p>
 * {@link Launch}: instead of the caster's eyes it can start at a key ({@code from}), {@code up} blocks above it
 * and {@code back} blocks back toward the caster (a meteor out of the sky), and fly at another key
 * ({@code toward}) instead of along the aim.
 * <p>{@link Pattern}: {@code fan: 120} spreads them evenly over that many degrees (side to side) instead of all one way;
 * {@code count_bonus: stacks:<status>} (or a key holding a number) fires that many more than {@code count} (with no
 * {@code count} given: exactly that many);
 * {@code heading: flight} (from a hit) flies on the way the shot that hit was flying, {@code back} the other way.
 */
public final class ProjectileNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.SPAWNED, Ports.HIT_ENTITY, Ports.HIT_BLOCK, Ports.EXPIRED,
            Ports.DESTROYED);
    private static final double MUZZLE_OFFSET = 0.5;

    /**
     * Where it starts and where it flies.
     *
     * @param from   a key to start at (null = the caster's eyes)
     * @param up     blocks above {@code from}
     * @param back   blocks from {@code from} back toward the caster (flat; negative = beyond it)
     * @param toward a key to fly at (null = along the caster's aim)
     */
    public record Launch(String from, double up, double back, String toward) {
        public static final Launch EYES = new Launch(null, 0, 0, null);
    }

    /** Which way it flies: along the caster's aim, or (launched from a hit) on along the hit shot's flight, or back. */
    public enum Heading { AIM, FLIGHT, BACK }

    /**
     * Several at once.
     *
     * @param fan        degrees (full width) they spread evenly over, side to side; 0 = all the same way (then spread)
     * @param countBonus "stacks:<status>" (the caster's stacks of it) or a key holding a number: that many more
     * @param heading    which way they fly
     * @param baseCount  with a bonus: how many before it (-1 = the spec's count)
     */
    public record Pattern(double fan, String countBonus, Heading heading, int baseCount) {
        public static final Pattern NONE = new Pattern(0, null, Heading.AIM, -1);
    }

    private final ProjectileSpec spec;
    private final String store; // null = don't store the handle
    private final Launch launch;
    private final Pattern pattern;

    public ProjectileNode(ProjectileSpec spec) {
        this(spec, null);
    }

    public ProjectileNode(ProjectileSpec spec, String store) {
        this(spec, store, Launch.EYES);
    }

    public ProjectileNode(ProjectileSpec spec, String store, Launch launch) {
        this(spec, store, launch, Pattern.NONE);
    }

    public ProjectileNode(ProjectileSpec spec, String store, Launch launch, Pattern pattern) {
        this.spec = spec;
        this.store = store;
        this.launch = launch;
        this.pattern = pattern == null ? Pattern.NONE : pattern;
    }

    /** How many: count, plus the bonus (the caster's stacks of a status, or a stored number). */
    private int count(ExecutionContext ctx) {
        String bonus = pattern.countBonus();
        if (bonus == null) return spec.count();
        int extra;
        if (bonus.startsWith("stacks:")) {
            extra = ctx.engine().statuses().find(ctx.caster(), bonus.substring("stacks:".length())).map(s -> s.stacks()).orElse(0);
        } else {
            extra = ctx.blackboard().raw(bonus) instanceof Number n ? n.intValue() : 0;
        }
        int base = pattern.baseCount() >= 0 ? pattern.baseCount() : spec.count();
        return Math.max(0, base + Math.max(0, extra));
    }

    /** The way the shot that hit was flying (from where it was a tick before the hit to the hit), if known. */
    private static Optional<Vec3> flight(ExecutionContext ctx) {
        var hit = KeyQuery.read(ctx, Keys.HIT.name()).flatMap(ctx.engine().world()::positionOf);
        var from = KeyQuery.read(ctx, Keys.HIT_FROM.name()).flatMap(ctx.engine().world()::positionOf);
        if (hit.isEmpty() || from.isEmpty()) return Optional.empty();
        Vec3 d = hit.get().position().subtract(from.get().position());
        return d.isZero() ? Optional.empty() : Optional.of(d.normalize());
    }

    /** {@code dir} turned {@code radians} about the vertical (to the side, keeping its pitch). */
    private static Vec3 yaw(Vec3 dir, double radians) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new Vec3(dir.x() * c - dir.z() * s, dir.y(), dir.x() * s + dir.z() * c);
    }

    public ProjectileSpec spec() { return spec; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<Aim> aim = ctx.engine().world().aimOf(ctx.caster());
        if (aim.isEmpty()) return NodeResult.out(Ports.EXPIRED);

        Aim a = aim.get();
        String world = a.world();
        Vec3 origin = null; // null: from the caster's eyes
        if (launch.from() != null) {
            var at = KeyQuery.read(ctx, launch.from()).flatMap(ctx.engine().world()::positionOf);
            if (at.isEmpty()) return NodeResult.out(Ports.EXPIRED);
            Vec3 heading = new Vec3(a.direction().x(), 0, a.direction().z());
            heading = heading.isZero() ? new Vec3(1, 0, 0) : heading.normalize();
            world = at.get().world();
            origin = at.get().position().add(0, launch.up(), 0).subtract(heading.multiply(launch.back()));
        }
        Vec3 aimDir = a.direction();
        if (pattern.heading() != Heading.AIM) {
            Vec3 along = flight(ctx).orElse(aimDir);
            aimDir = pattern.heading() == Heading.BACK ? along.multiply(-1) : along;
        }
        if (launch.toward() != null) {
            Vec3 from = origin != null ? origin : a.eye();
            aimDir = KeyQuery.read(ctx, launch.toward()).flatMap(ctx.engine().world()::positionOf)
                    .map(t -> t.position().subtract(from)).filter(d -> !d.isZero()).map(Vec3::normalize).orElse(aimDir);
        }
        // Blue flames instead of orange ones, say (the caster's character's variants)
        String visual = ctx.engine().visualFor(ctx.caster(), spec.visual());
        ProjectileSpec shown = java.util.Objects.equals(visual, spec.visual()) ? spec : spec.withVisual(visual);
        String tint = ctx.engine().infusions().tintOf(ctx.get(Keys.BOLT)); // a fired bolt shows its infusions
        double spread = Math.toRadians(spec.spreadDegrees());
        boolean primaryFire = launch.from() == null
                && me.mephisto.ability_engine.engine.loadout.Slots.PRIMARY.equals(ctx.blackboard().raw(Keys.SLOT.name()));
        int count = count(ctx);
        java.util.UUID fromEntity = launch.from() == null ? null : KeyQuery.read(ctx, launch.from())
                .filter(t -> t instanceof me.mephisto.ability_engine.engine.target.EntityTarget)
                .map(t -> ((me.mephisto.ability_engine.engine.target.EntityTarget) t).id())
                .filter(id -> !id.equals(ctx.caster())).orElse(null);
        double fan = Math.toRadians(pattern.fan());
        for (int i = 0; i < count; i++) {
            ExecutionContext branch = ctx.fork();
            Vec3 way = fan <= 0 || count < 2 ? aimDir
                    : yaw(aimDir, fan >= Math.PI * 2 - 1e-6 ? fan * i / count : -fan / 2 + fan * i / (count - 1));
            Vec3 dir = way.randomInCone(spread, ThreadLocalRandom.current());
            Vec3 start = origin != null ? origin : a.eye().add(aimDir.multiply(MUZZLE_OFFSET));
            var handle = ctx.engine().projectiles().launch(shown, world, start, dir.multiply(spec.speed()),
                    branch.suspend(), origin != null ? origin : a.eye(), tint);
            // Launched out of an entity (from: hit, the one a shot hit): it doesn't hit them again on its way out
            if (fromEntity != null) ctx.engine().projectiles().ignore(handle, fromEntity);
            // Primary fire from the eyes: a wider body against entities (config.yml primary-projectile-hitbox)
            if (primaryFire) ctx.engine().projectiles().widenForEntities(handle, ctx.engine().primaryHitbox());
            if (store != null) ctx.blackboard().putRaw(store, handle);
        }
        return NodeResult.out(Ports.SPAWNED);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
