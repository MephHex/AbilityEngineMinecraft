package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

import java.util.UUID;

/**
 * A boss bar (the ability's name) for the rest of the cast that empties as the caster gains stacks of {@code stacks_of}:
 * full with none, empty at {@code max}. For an ultimate that lasts a number of things rather than a time, e.g. bounces
 * left (a delay's {@code boss_bar: true} counts time down instead).
 */
public final class BossBarNode implements GraphNode {

    private final String status;
    private final int max;

    public BossBarNode(String status, int max) {
        this.status = status;
        this.max = Math.max(1, max);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        UUID caster = ctx.caster();
        var statuses = ctx.engine().statuses();
        ctx.instance().showTimerGauge(() -> 1 - statuses.find(caster, status).map(s -> s.stacks()).orElse(0) / (double) max);
        return NodeResult.NEXT;
    }
}
