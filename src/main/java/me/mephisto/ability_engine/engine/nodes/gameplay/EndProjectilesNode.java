package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * End the caster's own projectiles from {@code ability} (only those its node {@code node} launched; null = any) right
 * where they are, mid-flight: each exits "expired" there, as if it had run its course. E.g. a recall that first stops
 * the shards still flying (they hang where they stop), then calls them all back. Always continues out of "out".
 */
public final class EndProjectilesNode implements GraphNode {

    private final String ability;
    private final String node;

    public EndProjectilesNode(String ability, String node) {
        this.ability = ability;
        this.node = node;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().projectiles().endFlying(ctx.caster(), ability, node);
        return NodeResult.NEXT;
    }
}
