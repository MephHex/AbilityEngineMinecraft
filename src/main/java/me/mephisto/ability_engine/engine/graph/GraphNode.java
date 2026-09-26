package me.mephisto.ability_engine.engine.graph;

import java.util.Set;

/**
 * One step of an ability. Nodes are stateless definitions shared by every cast — keep per-cast
 * state on the blackboard, never in fields.
 */
public interface GraphNode {

    NodeResult execute(ExecutionContext ctx);

    /** Ports this node can exit through. Used to validate graph wiring at load time. */
    default Set<String> outputs() { return Set.of(Ports.OUT); }
}
