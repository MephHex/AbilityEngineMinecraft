package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Is {@code target}'s health below {@code share} of its max (e.g. 0.1 = an execute threshold)?
 * Exits "below" or "above" (also when it's unknown or gone).
 */
public final class HealthBelowNode implements GraphNode {

    public static final String BELOW = "below";
    public static final String ABOVE = "above";
    private static final Set<String> OUTPUTS = Set.of(BELOW, ABOVE);

    private final String targetKey;
    private final double share;

    public HealthBelowNode(String targetKey, double share) {
        this.targetKey = targetKey;
        this.share = share;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var target = KeyQuery.read(ctx, targetKey);
        if (target.isEmpty() || !(target.get() instanceof EntityTarget e)) return NodeResult.out(ABOVE);
        var f = ctx.engine().world().healthFraction(e.id());
        return NodeResult.out(f.isPresent() && f.getAsDouble() < share ? BELOW : ABOVE);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
