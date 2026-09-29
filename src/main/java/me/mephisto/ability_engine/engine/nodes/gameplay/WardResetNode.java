package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/** The caster's ward (debuff immunity, see the character's ward:) is ready again, right now. */
public final class WardResetNode implements GraphNode {

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().wards().reset(ctx.caster());
        return NodeResult.NEXT;
    }
}
