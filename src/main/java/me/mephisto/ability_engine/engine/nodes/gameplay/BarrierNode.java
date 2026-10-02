package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Set;

/**
 * Raise a frontal barrier on the caster (see BarrierSystem). It lasts until the cast ends, so keep
 * the cast alive for as long as it should stand (e.g. a delay after this node). Every enemy projectile it absorbs runs
 * {@code absorbed} as a branch of its own, with {@code absorbed_at} (where it was caught) and {@code absorbed_from}
 * (whose it was), e.g. to send one back.
 */
public final class BarrierNode implements GraphNode {

    public static final String ABSORBED = "absorbed";
    public static final String ABSORBED_AT = "absorbed_at";
    public static final String ABSORBED_FROM = "absorbed_from";
    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, ABSORBED);

    private final double distance;
    private final double radius;
    private final boolean projectilesOnly;

    public BarrierNode(double distance, double radius) {
        this(distance, radius, false);
    }

    /** @param projectilesOnly it only destroys projectiles (rays, dashes and melee pass) */
    public BarrierNode(double distance, double radius, boolean projectilesOnly) {
        this.distance = distance;
        this.radius = radius;
        this.projectilesOnly = projectilesOnly;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        String me = ctx.currentNode();
        Runnable lower = ctx.engine().barriers().raise(ctx.caster(), distance, radius, projectilesOnly, (world, at, attacker) -> {
            if (!ctx.instance().isActive()) return;
            ExecutionContext branch = ctx.forkAt(me);
            branch.blackboard().putRaw(ABSORBED_AT, new PointTarget(world, at));
            branch.blackboard().putRaw(ABSORBED_FROM, new EntityTarget(attacker));
            branch.suspend().resume(ABSORBED);
        });
        ctx.instance().onEnd(lower);
        return NodeResult.NEXT;
    }
}
