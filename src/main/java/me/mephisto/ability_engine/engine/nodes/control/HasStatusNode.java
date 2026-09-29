package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Does the caster (or {@code target: <key>}) have {@code status} right now, with at least
 * {@code min_stacks} stacks (default 1)? {@code mine: true}: only if the caster put it there (your own
 * mark, not someone else's). Exits "has" or "lacks".
 */
public final class HasStatusNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.HAS, Ports.LACKS);

    private final String status;
    private final String targetKey;
    private final int minStacks;
    private final boolean mine;

    public HasStatusNode(String status, String targetKey, int minStacks, boolean mine) {
        this.status = status;
        this.targetKey = targetKey;
        this.minStacks = Math.max(1, minStacks);
        this.mine = mine;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var entity = targetKey == null ? java.util.Optional.of(ctx.caster())
                : KeyQuery.read(ctx, targetKey).filter(t -> t instanceof EntityTarget).map(t -> ((EntityTarget) t).id());
        boolean has = entity.flatMap(id -> ctx.engine().statuses().find(id, status))
                .filter(s -> s.stacks() >= minStacks)
                .filter(s -> !mine || ctx.caster().equals(s.source()))
                .isPresent();
        return NodeResult.out(has ? Ports.HAS : Ports.LACKS);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
