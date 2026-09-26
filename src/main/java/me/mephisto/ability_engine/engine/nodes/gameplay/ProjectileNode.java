package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;

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
 */
public final class ProjectileNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.SPAWNED, Ports.HIT_ENTITY, Ports.HIT_BLOCK, Ports.EXPIRED);
    private static final double MUZZLE_OFFSET = 0.5;

    private final ProjectileSpec spec;
    private final String store; // null = don't store the handle

    public ProjectileNode(ProjectileSpec spec) {
        this(spec, null);
    }

    public ProjectileNode(ProjectileSpec spec, String store) {
        this.spec = spec;
        this.store = store;
    }

    public ProjectileSpec spec() { return spec; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<Aim> aim = ctx.engine().world().aimOf(ctx.caster());
        if (aim.isEmpty()) return NodeResult.out(Ports.EXPIRED);

        Aim a = aim.get();
        String tint = ctx.engine().infusions().tintOf(ctx.get(Keys.BOLT)); // a fired bolt shows its infusions
        double spread = Math.toRadians(spec.spreadDegrees());
        for (int i = 0; i < spec.count(); i++) {
            ExecutionContext branch = ctx.fork();
            Vec3 dir = a.direction().randomInCone(spread, ThreadLocalRandom.current());
            Vec3 start = a.eye().add(a.direction().multiply(MUZZLE_OFFSET));
            var handle = ctx.engine().projectiles().launch(spec, a.world(), start, dir.multiply(spec.speed()),
                    branch.suspend(), a.eye(), tint);
            if (store != null) ctx.blackboard().putRaw(store, handle);
        }
        return NodeResult.out(Ports.SPAWNED);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
