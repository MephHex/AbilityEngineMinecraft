package me.mephisto.ability_engine.engine.nodes.debug;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/** Logs a message to the server console. */
public final class PrintNode implements GraphNode {

    private final String message;

    public PrintNode(String message) {
        this.message = message;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().log().info("[" + ctx.graph().id() + "] " + message);
        return NodeResult.NEXT;
    }
}
