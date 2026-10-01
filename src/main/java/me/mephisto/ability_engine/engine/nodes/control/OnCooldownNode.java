package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;

import java.util.Set;

/**
 * Is an ability of the caster's on cooldown ({@code ability}, default this one)? Exits "cooling" or "ready".
 * E.g. a recast (free by itself) that should wait for the cooldown: send "cooling" back to the await_recast.
 */
public final class OnCooldownNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.COOLING, Ports.READY);

    private final String ability;

    /** @param ability the ability to check (null = this one) */
    public OnCooldownNode(String ability) {
        this.ability = ability;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        String id = ability != null ? ability : ctx.instance().ability().id();
        return NodeResult.out(ctx.engine().cooldowns().isReady(ctx.caster(), id) ? Ports.READY : Ports.COOLING);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
