package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;

import java.util.Set;

/**
 * Read the caster's spell shield charge into {@code store} (design HP absorbed, e.g. for a damage
 * effect's {@code scale_by}). Exits "full" at the shield's max, else "out".
 */
public final class ShieldChargeNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.FULL);

    private final String store;

    public ShieldChargeNode(String store) {
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var shields = ctx.engine().spellShields();
        double charge = shields.charge(ctx.caster());
        double max = shields.max(ctx.caster());
        ctx.blackboard().putRaw(store, charge);
        return NodeResult.out(max > 0 && charge >= max ? Ports.FULL : Ports.OUT);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
