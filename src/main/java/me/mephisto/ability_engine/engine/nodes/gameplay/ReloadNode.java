package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;

import java.util.Set;

/**
 * Load the caster's next bolt into their weapon, as if they had drawn it (see QuiverManager).
 * Exits "out", or "full" if something was already loaded (or they have no quiver): nothing moves then.
 */
public final class ReloadNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.FULL);

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        return ctx.engine().quivers().load(ctx.caster()) ? NodeResult.NEXT : NodeResult.out(Ports.FULL);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
