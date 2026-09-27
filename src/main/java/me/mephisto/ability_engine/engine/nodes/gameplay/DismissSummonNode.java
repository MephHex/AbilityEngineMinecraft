package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/** Remove the caster's summon {@code summon} now (if there is one). */
public final class DismissSummonNode implements GraphNode {

    private final String name;

    public DismissSummonNode(String name) {
        this.name = name;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().summons().dismiss(ctx.caster(), name);
        return NodeResult.NEXT;
    }
}
