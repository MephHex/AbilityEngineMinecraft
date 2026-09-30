package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

import java.util.Set;

/**
 * Is the ability's key still held? True when its input repeated (the key's auto-repeat, routed to a
 * charge with {@code release_gap} that's listening) within the last {@code within} ticks. A quick tap
 * never counts: only a key held past the OS repeat delay repeats. Exits "held" or "free".
 */
public final class InputHeldNode implements GraphNode {

    public static final String HELD = "held";
    public static final String FREE = "free";
    private static final Set<String> OUTPUTS = Set.of(HELD, FREE);

    private final int within;

    public InputHeldNode(int within) {
        this.within = Math.max(1, within);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        long last = ctx.instance().lastInputRepeat();
        boolean held = last != Long.MIN_VALUE && ctx.engine().clock().now() - last <= within;
        return NodeResult.out(held ? HELD : FREE);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
