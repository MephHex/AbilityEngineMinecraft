package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

import java.util.Set;
import java.util.UUID;

/**
 * Put {@code tags} on the caster from now until the cast ends, however it ends (finished, cancelled, interrupted, the
 * caster died): active_tags that start partway through. E.g. hanging in the air (state.anchored) only once
 * a leap up has reached its top: on from the start, they'd stop the leap.
 */
public final class GrantTagsNode implements GraphNode {

    private final Set<String> tags;

    public GrantTagsNode(Set<String> tags) {
        this.tags = Set.copyOf(tags);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        UUID caster = ctx.caster();
        var engine = ctx.engine();
        engine.tags().grantAll(caster, tags);
        ctx.instance().onEnd(() -> engine.tags().revokeAll(caster, tags));
        return NodeResult.NEXT;
    }
}
