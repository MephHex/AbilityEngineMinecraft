package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Leave a look-alike of the caster where they stand (a mannequin with their skin), stored as
 * {@code store}. It outlives the cast: it lasts {@code lifetime} ticks, and a new one with the same
 * {@code summon} name replaces the old. Other abilities find it with find_summon.
 */
public final class SummonCloneNode implements GraphNode {

    private final String name;
    private final String store;
    private final int lifetime;

    public SummonCloneNode(String name, String store, int lifetime) {
        this.name = name;
        this.store = store;
        this.lifetime = lifetime;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        ctx.engine().world().positionOf(new EntityTarget(ctx.caster())).ifPresent(p ->
                ctx.engine().summons().summonClone(ctx.caster(), name, p.world(), p.position(), lifetime)
                        .ifPresent(id -> ctx.blackboard().putRaw(store, new EntityTarget(id))));
        return NodeResult.NEXT;
    }
}
