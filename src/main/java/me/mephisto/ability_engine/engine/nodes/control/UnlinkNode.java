package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/** Break the caster's tether {@code name}, if it's up. */
public final class UnlinkNode implements GraphNode {

    private final String name;

    public UnlinkNode(String name) {
        this.name = name;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().links().unlink(ctx.caster(), name);
        return NodeResult.NEXT;
    }
}
