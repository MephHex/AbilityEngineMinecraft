package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

import java.util.List;

/** Reset the caster's cooldowns of the abilities in {@code slots} (ready at once, charges full). */
public final class ResetCooldownsNode implements GraphNode {

    private final List<String> slots;

    public ResetCooldownsNode(List<String> slots) {
        this.slots = List.copyOf(slots);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        for (String slot : slots) {
            ctx.engine().loadouts().abilityIn(ctx.caster(), slot)
                    .ifPresent(ability -> ctx.engine().cooldowns().clear(ctx.caster(), ability));
        }
        return NodeResult.NEXT;
    }
}
