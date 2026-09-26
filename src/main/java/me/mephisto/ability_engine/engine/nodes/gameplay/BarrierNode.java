package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Raise a frontal barrier on the caster (see BarrierSystem). It lasts until the cast ends, so keep
 * the cast alive for as long as it should stand (e.g. a delay after this node).
 */
public final class BarrierNode implements GraphNode {

    private final double distance;
    private final double radius;

    public BarrierNode(double distance, double radius) {
        this.distance = distance;
        this.radius = radius;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Runnable lower = ctx.engine().barriers().raise(ctx.caster(), distance, radius);
        ctx.instance().onEnd(lower);
        return NodeResult.NEXT;
    }
}
