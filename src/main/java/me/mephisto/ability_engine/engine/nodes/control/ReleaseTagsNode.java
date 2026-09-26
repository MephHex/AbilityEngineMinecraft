package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Drop the ability's active_tags now instead of when the cast ends. For casts with a delayed
 * follow-up (a dash whose echo fires later): the caster is free to act again right away.
 */
public final class ReleaseTagsNode implements GraphNode {
    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.instance().releaseActiveTags();
        return NodeResult.NEXT;
    }
}
