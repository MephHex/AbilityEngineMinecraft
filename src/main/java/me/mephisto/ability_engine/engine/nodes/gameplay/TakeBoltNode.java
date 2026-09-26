package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.quiver.Bolt;

import java.util.Optional;
import java.util.Set;

/**
 * Take the bolt loaded in the caster's weapon (it's being fired) and store it as "bolt", so a later
 * {@code apply_effects} with {@code infusions: true} applies its infusions. Exits "out", or "empty" if
 * nothing was loaded (a dry fire).
 */
public final class TakeBoltNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.EMPTY);

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<Bolt> bolt = ctx.engine().quivers().take(ctx.caster());
        if (bolt.isEmpty()) return NodeResult.out(Ports.EMPTY);
        ctx.put(Keys.BOLT, bolt.get());
        return NodeResult.NEXT;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
