package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Get the caster off whatever they ride (see mount). From the ride's own cast it's quiet (the ride's "off"
 * branch doesn't run); from another cast the ride's "off" branch runs, as if it ended by itself.
 */
public final class DismountNode implements GraphNode {
    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().rides().dismount(ctx.caster(), ctx.instance());
        return NodeResult.NEXT;
    }
}
