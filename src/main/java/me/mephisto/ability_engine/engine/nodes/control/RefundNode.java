package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.stats.UltimateCharge;

/**
 * The cast costs nothing after all (e.g. an ultimate aimed at nobody): an ultimate's charge is given back full, anything
 * else (or an ultimate with ultimates on cooldowns) has its cooldown cleared. {@code message} tells the caster why.
 */
public final class RefundNode implements GraphNode {

    private final String message; // null = say nothing

    public RefundNode(String message) {
        this.message = message;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var engine = ctx.engine();
        var caster = ctx.caster();
        String self = ctx.instance().ability().id();
        if (engine.ultCharge().gates(caster, self)) engine.ultCharge().set(caster, UltimateCharge.FULL);
        else engine.cooldowns().clear(caster, self);
        if (message != null) engine.notify(caster, message);
        return NodeResult.NEXT;
    }
}
