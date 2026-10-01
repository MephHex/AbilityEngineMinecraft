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

    private final ProjectileSpec spec;
    private final String store; // null = don't store the handle
    private final Launch launch;

    public ProjectileNode(ProjectileSpec spec) {
        this(spec, null);
    }

    public ProjectileNode(ProjectileSpec spec, String store) {
        this(spec, store, Launch.EYES);
    }

    public ProjectileNode(ProjectileSpec spec, String store, Launch launch) {
        this.spec = spec;
        this.store = store;
        this.launch = launch;
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
        for (int i = 0; i < spec.count(); i++) {
            ExecutionContext branch = ctx.fork();
            Vec3 dir = aimDir.randomInCone(spread, ThreadLocalRandom.current());
            Vec3 start = origin != null ? origin : a.eye().add(aimDir.multiply(MUZZLE_OFFSET));
            var handle = ctx.engine().projectiles().launch(shown, world, start, dir.multiply(spec.speed()),
                    branch.suspend(), origin != null ? origin : a.eye(), tint);
            if (store != null) ctx.blackboard().putRaw(store, handle);
        }
        return NodeResult.out(Ports.SPAWNED);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
