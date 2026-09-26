package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.HashSet;
import java.util.Set;

/**
 * Branch on a blackboard value. Each case is an output port; anything else exits "default".
 * Targets switch on their kind ("entity"/"point"); missing values are "none".
 * The key is now a parameter (it was hardcoded to "hitType", which nothing wrote).
 */
public final class SwitchNode implements GraphNode {

    private final String key;
    private final Set<String> cases;
    private final Set<String> outputs;

    public SwitchNode(String key, Set<String> cases) {
        this.key = key;
        this.cases = Set.copyOf(cases);
        Set<String> out = new HashSet<>(cases);
        out.add(Ports.DEFAULT);
        this.outputs = Set.copyOf(out);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Object raw = ctx.blackboard().raw(key);
        String value = raw == null ? "none" : raw instanceof Target t ? t.kind() : String.valueOf(raw);
        return NodeResult.out(cases.contains(value) ? value : Ports.DEFAULT);
    }

    @Override
    public Set<String> outputs() { return outputs; }
}
