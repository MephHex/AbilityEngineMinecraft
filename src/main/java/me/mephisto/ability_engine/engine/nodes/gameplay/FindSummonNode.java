package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.Set;

/** Does the caster have a summon with this name? Stores it as {@code store}; exits "found" or "none". */
public final class FindSummonNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.FOUND, Ports.NONE);

    private final String name;
    private final String store;

    public FindSummonNode(String name, String store) {
        this.name = name;
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var found = ctx.engine().summons().find(ctx.caster(), name).filter(ctx.engine().world()::isAlive); // not killed
        if (found.isEmpty()) return NodeResult.out(Ports.NONE);
        ctx.blackboard().putRaw(store, new EntityTarget(found.get()));
        return NodeResult.out(Ports.FOUND);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
