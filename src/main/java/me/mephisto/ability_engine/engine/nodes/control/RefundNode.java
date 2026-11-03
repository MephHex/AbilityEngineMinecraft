package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.stats.UltimateCharge;

/**
 * The cast costs nothing after all (e.g. an ultimate aimed at nobody): an ultimate's charge is given back full, anything
 * else (or an ultimate with ultimates on cooldowns) has its cooldown cleared. {@code share: 0.5}: only half of it: half
 * the charge back, or the cooldown starting over at half. {@code message} tells the caster why.
 */
public final class RefundNode implements GraphNode {

    private final String message; // null = say nothing
    private final double share;   // how much comes back: 1 = all of it

    public RefundNode(String message) {
        this(message, 1);
    }

    /** @param share how much of it comes back (0.5: half the ultimate's charge, half the cooldown off) */
    public RefundNode(String message, double share) {
        this.message = message;
        this.share = share;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var engine = ctx.engine();
        var caster = ctx.caster();
        String self = ctx.instance().ability().id();
        if (ctx.instance().isEcho()) return NodeResult.NEXT; // a second body's copy: nothing of its own was spent
        ctx.instance().releaseHeldUltimate(); // an ultimate that lasts its cast: nothing to spend when it ends
        if (engine.ultCharge().gates(caster, self)) {
            engine.ultCharge().set(caster, share * UltimateCharge.FULL); // (half back: it starts again from 50%)
        } else if (share >= 1) {
            engine.cooldowns().clear(caster, self);
        } else { // part of it: the cooldown starts over at what's left of it
            engine.cooldowns().start(caster, self, (int) Math.round(engine.stats().cooldownTicks(caster, self,
                    ctx.instance().ability().cooldownTicks()) * (1 - share)));
        }
        if (message != null) engine.notify(caster, message);
        return NodeResult.NEXT;
    }
}
