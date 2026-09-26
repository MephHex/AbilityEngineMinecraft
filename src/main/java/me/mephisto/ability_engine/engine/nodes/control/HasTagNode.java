package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Does an entity ({@code target: <key>}, default the caster) have a tag right now? Exits "has" or "lacks".
 * E.g. an ability that does something extra while its caster is in an ultimate's state.
 */
public final class HasTagNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.HAS, Ports.LACKS);

    private final String tag;
    private final String targetKey; // null = caster

    public HasTagNode(String tag, String targetKey) {
        this.tag = tag;
        this.targetKey = targetKey;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var entity = targetKey == null ? java.util.Optional.of(ctx.caster())
                : KeyQuery.read(ctx, targetKey).filter(t -> t instanceof EntityTarget).map(t -> ((EntityTarget) t).id());
        boolean has = entity.isPresent() && ctx.engine().tags().has(entity.get(), tag);
        return NodeResult.out(has ? Ports.HAS : Ports.LACKS);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
