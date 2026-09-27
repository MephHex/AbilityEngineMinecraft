package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.Set;

/** Is the caster's tether {@code name} up? Stores its other end as {@code store}. Exits "found" or "none". */
public final class FindLinkNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.FOUND, Ports.NONE);

    private final String name;
    private final String store;

    public FindLinkNode(String name, String store) {
        this.name = name;
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var link = ctx.engine().links().find(ctx.caster(), name);
        if (link.isEmpty()) return NodeResult.out(Ports.NONE);
        ctx.blackboard().putRaw(store, new EntityTarget(link.get().target()));
        return NodeResult.out(Ports.FOUND);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
