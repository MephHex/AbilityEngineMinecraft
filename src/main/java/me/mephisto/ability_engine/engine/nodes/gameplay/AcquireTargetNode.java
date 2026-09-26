package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.Key;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.Target;
import me.mephisto.ability_engine.engine.target.TargetQuery;

import java.util.List;
import java.util.Set;

/** Run a query and store the first result (was CastTargetNode). Exits "hit" or "miss". */
public final class AcquireTargetNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.HIT, Ports.MISS);

    private final TargetQuery query;
    private final Key<Target> store;

    public AcquireTargetNode(TargetQuery query, Key<Target> store) {
        this.query = query;
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        List<Target> found = query.find(ctx);
        if (found.isEmpty()) return NodeResult.out(Ports.MISS);
        ctx.put(store, found.get(0));
        return NodeResult.out(Ports.HIT);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
