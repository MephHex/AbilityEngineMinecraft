package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

import java.util.List;

/**
 * Infuse the caster's next {@code count} QUEUED bolts with an infusion (front of the queue first).
 * The bolt already loaded in the weapon is never infused. Bolts keep every infusion they get.
 * With {@code random: [a, b, c]} instead, each of those bolts gets one of them, picked at random.
 * {@code reroll: true}: a bolt that already has one of them gets it REPLACED by the new roll, so they
 * never pile up on one bolt.
 */
public final class InfuseNode implements GraphNode {

    private final List<String> choices;
    private final int count;
    private final boolean reroll;

    public InfuseNode(String infusion, int count) {
        this(List.of(infusion), count, false);
    }

    /**
     * @param choices one infusion (always that one), or several (each bolt gets a random one)
     * @param reroll  replace any of the choices a bolt already has instead of adding to them
     */
    public InfuseNode(List<String> choices, int count, boolean reroll) {
        this.choices = List.copyOf(choices);
        this.count = Math.max(1, count);
        this.reroll = reroll;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var quivers = ctx.engine().quivers();
        int changed = 0;
        int queued = quivers.queue(ctx.caster()).size();
        for (int i = 0; i < Math.min(count, queued); i++) {
            String infusion = choices.get(ctx.engine().random().nextInt(choices.size()));
            changed += quivers.infuseAt(ctx.caster(), i, infusion, reroll ? choices : List.of()) ? 1 : 0;
        }
        int total = changed;
        ctx.engine().log().debug(() -> "infuse " + choices + ": " + total + " bolt(s)");
        return NodeResult.NEXT;
    }
}
