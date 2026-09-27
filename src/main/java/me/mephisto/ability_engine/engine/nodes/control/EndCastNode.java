package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * End the whole cast now: every other branch stops (timers, recast windows), and what lasts "until the
 * cast ends" goes (barriers, looping cues, active tags). E.g. a shield rush that ends the shield.
 */
public final class EndCastNode implements GraphNode {
    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.instance().cancel("ended");
        return NodeResult.SUSPENDED; // nothing continues after the cast is over
    }
}
