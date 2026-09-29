package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Refill one of the caster's resources: to its max (e.g. a reload), or by {@code amount} when given.
 */
public final class RefillNode implements GraphNode {

    private final String resource;
    private final Double amount;

    public RefillNode(String resource, Double amount) {
        this.resource = resource;
        this.amount = amount;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var res = ctx.engine().resources();
        if (amount != null) {
            res.add(ctx.caster(), resource, amount);
        } else {
            double max = res.definition(ctx.caster(), resource).map(d -> d.max()).orElse(0.0);
            if (max > 0) res.set(ctx.caster(), resource, max);
        }
        return NodeResult.NEXT;
    }
}
