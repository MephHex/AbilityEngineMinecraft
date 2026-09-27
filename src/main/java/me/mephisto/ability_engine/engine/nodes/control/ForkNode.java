package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;

import java.util.Set;

/**
 * Run two things at once: "also" starts as its own branch (with its own copy of the blackboard, like
 * a projectile's), then "out" continues. E.g. you dash while your echo dashes too. The cast lasts
 * until both are done.
 */
public final class ForkNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.ALSO);

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.fork().suspend().resume(Ports.ALSO);
        return NodeResult.NEXT;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
