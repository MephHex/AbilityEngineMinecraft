package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Restart a resource's reload (and regen delay) timer as if it was just spent, without spending any: e.g.
 * each bullet of a volley that was loaded (paid for) earlier, so the gun reloads after the LAST shot.
 */
public final class HoldReloadNode implements GraphNode {

    private final String resource;

    public HoldReloadNode(String resource) {
        this.resource = resource;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().resources().holdReload(ctx.caster(), resource);
        return NodeResult.NEXT;
    }
}
