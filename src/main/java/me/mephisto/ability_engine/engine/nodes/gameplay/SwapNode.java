package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

/** Swap places with the entity in {@code with} (both keep looking where they were looking). */
public final class SwapNode implements GraphNode {

    private final String withKey;

    public SwapNode(String withKey) {
        this.withKey = withKey;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        var other = KeyQuery.read(ctx, withKey).filter(t -> t instanceof EntityTarget).map(t -> (EntityTarget) t);
        var me = world.positionOf(new EntityTarget(ctx.caster()));
        var them = other.flatMap(world::positionOf);
        boolean unmovable = other.filter(o -> !o.id().equals(ctx.caster()))
                .map(o -> ctx.engine().tags().has(o.id(), Tags.BLOCK_DISPLACE)).orElse(false);
        if (!unmovable && me.isPresent() && them.isPresent() && me.get().world().equals(them.get().world())) {
            ctx.engine().movement().teleport(ctx.caster(), them.get().position());
            ctx.engine().movement().teleport(other.get().id(), me.get().position());
        }
        return NodeResult.NEXT;
    }
}
