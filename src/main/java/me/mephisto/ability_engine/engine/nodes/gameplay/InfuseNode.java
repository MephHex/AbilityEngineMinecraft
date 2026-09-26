package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Infuse the caster's next {@code count} QUEUED bolts with an infusion (front of the queue first).
 * The bolt already loaded in the weapon is never infused. Bolts keep every infusion they get.
 */
public final class InfuseNode implements GraphNode {

    private final String infusion;
    private final int count;

    public InfuseNode(String infusion, int count) {
        this.infusion = infusion;
        this.count = Math.max(1, count);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        int changed = ctx.engine().quivers().infuse(ctx.caster(), infusion, count);
        ctx.engine().log().debug(() -> "infuse " + infusion + ": " + changed + " bolt(s)");
        return NodeResult.NEXT;
    }
}
