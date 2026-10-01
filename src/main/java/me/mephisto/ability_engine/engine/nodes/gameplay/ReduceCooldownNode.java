package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Take {@code ticks} off one of the caster's cooldowns: an ability by id ({@code ability: alchemist_ab1}),
 * or whatever sits in one of their character's slots ({@code slot: ability_1}). E.g. "hits refresh your
 * dash faster". A cooldown that isn't running is left alone.
 */
public final class ReduceCooldownNode implements GraphNode {

    private final String ability; // null = use the slot
    private final String slot;
    private final int ticks;

    public ReduceCooldownNode(String ability, String slot, int ticks) {
        this.ability = ability;
        this.slot = slot;
        this.ticks = ticks;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        String id = ability != null ? ability
                : ctx.engine().loadouts().abilityIn(ctx.caster(), slot).orElse(null);
        if (id != null) ctx.engine().cooldowns().reduce(ctx.caster(), id, ticks);
        return NodeResult.NEXT;
    }
}
