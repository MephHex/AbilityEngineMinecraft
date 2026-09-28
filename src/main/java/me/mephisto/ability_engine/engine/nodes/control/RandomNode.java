package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

import java.util.List;
import java.util.Set;

/** Take one of its ports ({@code on: { a: x, b: y, c: z }}) at random, each equally likely. */
public final class RandomNode implements GraphNode {

    private final List<String> ports;

    public RandomNode(List<String> ports) {
        this.ports = List.copyOf(ports);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        return NodeResult.out(ports.get(ctx.engine().random().nextInt(ports.size())));
    }

    @Override
    public Set<String> outputs() { return Set.copyOf(ports); }
}
