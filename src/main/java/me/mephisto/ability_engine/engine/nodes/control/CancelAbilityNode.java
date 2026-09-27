package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * End the caster's running casts of another ability (not this one), e.g. the last shot of Arcane
 * Barrage ends the ultimate that gave it. Continues out of "out".
 */
public final class CancelAbilityNode implements GraphNode {

    private final String ability;

    public CancelAbilityNode(String ability) {
        this.ability = ability;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        for (var instance : ctx.engine().instances().of(ctx.caster())) {
            if (instance != ctx.instance() && instance.ability().id().equals(ability)) instance.cancel("ended_by:" + ctx.instance().ability().id());
        }
        return NodeResult.NEXT;
    }
}
