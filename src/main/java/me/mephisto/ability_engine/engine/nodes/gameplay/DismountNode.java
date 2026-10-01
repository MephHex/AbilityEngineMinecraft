package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/** Get the caster off whatever they ride (see mount). Quietly: the ride's "off" branch doesn't run. */
public final class DismountNode implements GraphNode {
    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().rides().dismount(ctx.caster());
        return NodeResult.NEXT;
    }
}
