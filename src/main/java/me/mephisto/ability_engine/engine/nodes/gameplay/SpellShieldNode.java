package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Raise a spell shield around the caster until the cast ends: spell damage to them is absorbed and
 * stored as charge, up to {@code max} (see SpellShields). Read it with shield_charge.
 */
public final class SpellShieldNode implements GraphNode {

    private final double max;

    public SpellShieldNode(double max) {
        this.max = max;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var shields = ctx.engine().spellShields();
        shields.raise(ctx.caster(), max);
        ctx.instance().onEnd(() -> shields.lower(ctx.caster()));
        return NodeResult.NEXT;
    }
}
