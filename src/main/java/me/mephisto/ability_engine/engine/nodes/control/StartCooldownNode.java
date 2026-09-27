package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Start this ability's cooldown now. With {@code cooldown_starts: manual}, a cast that never reaches one
 * costs no cooldown (e.g. only a successful bond counts). Does nothing if the cooldown already started.
 */
public final class StartCooldownNode implements GraphNode {
    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.instance().startDeferredCooldown();
        return NodeResult.NEXT;
    }
}
