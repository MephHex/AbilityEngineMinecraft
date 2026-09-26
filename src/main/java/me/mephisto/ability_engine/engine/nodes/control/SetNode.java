package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Write a string to the blackboard, e.g. {@code key: phase, value: empowered}, then read it with
 * a switch node. Branches forked earlier still see it (they read up through their parent scope)
 * unless they wrote the same key themselves.
 */
public final class SetNode implements GraphNode {

    private final String key;
    private final String value;

    public SetNode(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.blackboard().putRaw(key, value);
        return NodeResult.NEXT;
    }
}
