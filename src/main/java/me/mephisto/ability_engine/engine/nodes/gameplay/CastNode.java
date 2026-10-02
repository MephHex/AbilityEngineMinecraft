package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * The caster casts another ability of theirs right now, as if they'd pressed its key (its own checks: cooldown,
 * blocked_by...). E.g. a channel that, full, fires the same volley its LMB can fire early. Always continues out of "out".
 */
public final class CastNode implements GraphNode {

    private final String ability;

    public CastNode(String ability) {
        this.ability = ability;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().activator().activate(ctx.caster(), ability);
        return NodeResult.NEXT;
    }
}
