package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

/**
 * Pin down where {@code of} (a key; an entity or a point) is RIGHT NOW as a fixed spot in {@code store}.
 * An entity key follows the entity; the stored spot doesn't (e.g. a pool that stays where a flask burst,
 * not on the player it hit). {@code ground: true} (default) drops it onto the ground below (up to 8
 * blocks). Missing: nothing is stored.
 */
public final class RememberSpotNode implements GraphNode {

    private static final double MAX_DROP = 8;

    private final String of;
    private final String store;
    private final boolean ground;

    public RememberSpotNode(String of, String store, boolean ground) {
        this.of = of;
        this.store = store;
        this.ground = ground;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        KeyQuery.read(ctx, of).flatMap(world::positionOf).ifPresent(p -> {
            var at = ground ? world.groundBelow(p.world(), p.position(), MAX_DROP).orElse(p.position()) : p.position();
            ctx.blackboard().putRaw(store, new PointTarget(p.world(), at));
        });
        return NodeResult.NEXT;
    }
}
