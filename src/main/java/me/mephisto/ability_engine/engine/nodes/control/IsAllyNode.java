package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Is the entity in {@code target} (a key, default "hit") on the caster's side? Exits "ally" (the caster
 * counts as their own ally) or "enemy"; "none" if the key holds no entity. E.g. a seed that latches onto
 * whoever it hits: heal an ally, blow up an enemy.
 */
public final class IsAllyNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.ALLY, Ports.ENEMY, Ports.NONE);

    private final String targetKey;

    public IsAllyNode(String targetKey) {
        this.targetKey = targetKey;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        if (!(KeyQuery.read(ctx, targetKey).orElse(null) instanceof EntityTarget e)) return NodeResult.out(Ports.NONE);
        return NodeResult.out(ctx.engine().teams().allies(ctx.caster(), e.id()) ? Ports.ALLY : Ports.ENEMY);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
