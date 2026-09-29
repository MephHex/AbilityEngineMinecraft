package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;

import java.util.Set;

/**
 * Spend {@code amount} of one of the caster's resources (e.g. a bullet) and continue out of "out"; not
 * enough left: spend nothing and exit "empty" (a dry fire; ammo with a {@code reload} refills by itself).
 * With {@code peek: true} it only checks.
 */
public final class SpendNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.EMPTY);

    private final String resource;
    private final double amount;
    private final boolean peek;

    public SpendNode(String resource, double amount, boolean peek) {
        this.resource = resource;
        this.amount = amount;
        this.peek = peek;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var res = ctx.engine().resources();
        if (!res.has(ctx.caster(), resource, amount)) return NodeResult.out(Ports.EMPTY);
        if (!peek) res.consume(ctx.caster(), resource, amount);
        return NodeResult.NEXT;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
