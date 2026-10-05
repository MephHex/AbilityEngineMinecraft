package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Let go of the caster's charge of another ability, as if its input were released: its {@code charge} node goes on
 * with the power it has. E.g. a key that starts charging (a volley, an ultimate) and LMB that fires it, through a form
 * that puts this in the primary slot while it charges. Continues out of "out".
 */
public final class ReleaseChargeNode implements GraphNode {

    private final String ability;

    public ReleaseChargeNode(String ability) {
        this.ability = ability;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().instances().release(ctx.caster(), ability);
        return NodeResult.NEXT;
    }
}
