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
 * With {@code peek: true} it only looks: "trigger" if counting now WOULD trigger, without counting
 * (e.g. "is the next swing the empowered one?", when only swings that hit should count).
 */
public final class CounterNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.TRIGGER);

    private final String counter;
    private final int every;
    private final boolean peek;

    public CounterNode(String counter, int every) {
        this(counter, every, false);
    }

    public CounterNode(String counter, int every, boolean peek) {
        this.counter = counter;
        this.every = Math.max(1, every);
        this.peek = peek;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var res = ctx.engine().resources();
        if (peek) return res.get(ctx.caster(), counter) + 1 >= every ? NodeResult.out(Ports.TRIGGER) : NodeResult.NEXT;
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
