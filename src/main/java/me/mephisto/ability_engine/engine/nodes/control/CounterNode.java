package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;

import java.util.Set;

/**
 * "Every Nth time": adds 1 to the caster's counter (a plain resource, so it persists between casts)
 * and exits "trigger" when it reaches {@code every}, resetting it to 0; otherwise exits "out".
 * E.g. every third basic attack cleaves.
 */
public final class CounterNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.TRIGGER);

    private final String counter;
    private final int every;

    public CounterNode(String counter, int every) {
        this.counter = counter;
        this.every = Math.max(1, every);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var res = ctx.engine().resources();
        res.add(ctx.caster(), counter, 1);
        if (res.get(ctx.caster(), counter) >= every) {
            res.set(ctx.caster(), counter, 0);
            return NodeResult.out(Ports.TRIGGER);
        }
        return NodeResult.NEXT;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
