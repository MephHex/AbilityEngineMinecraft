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
    private final boolean reflect;
    private final boolean around;

    public BarrierNode(double distance, double radius) {
        this(distance, radius, false);
    }

    /** @param projectilesOnly it only destroys projectiles (rays, dashes and melee pass) */
    public BarrierNode(double distance, double radius, boolean projectilesOnly) {
        this(distance, radius, projectilesOnly, false);
    }

    /** @param reflect projectiles it absorbs are sent back at their shooter: a copy, cast by the caster */
    public BarrierNode(double distance, double radius, boolean projectilesOnly, boolean reflect) {
        this(distance, radius, projectilesOnly, reflect, false);
    }

    /** @param around all the way around the caster (a sphere of {@code radius}) instead of a disc in front */
    public BarrierNode(double distance, double radius, boolean projectilesOnly, boolean reflect, boolean around) {
        this.around = around;
        this.distance = distance;
        this.radius = radius;
        this.projectilesOnly = projectilesOnly;
        this.reflect = reflect;
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
        }, reflect, around);
        ctx.instance().onEnd(lower);
        return NodeResult.NEXT;
    }
}
