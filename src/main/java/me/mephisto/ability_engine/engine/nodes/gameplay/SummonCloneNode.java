package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Leave a look-alike of the caster where they stand, or at {@code at: <key>} (e.g. "aim" from an aim
 * preview: a ground spot, so it stands ON it), a mannequin with their skin, stored as
 * {@code store}. It outlives the cast: it lasts {@code lifetime} ticks, and a new one with the same
 * {@code summon} name replaces the old. Other abilities find it with find_summon.
 */
public final class SummonCloneNode implements GraphNode {

    private final String name;
    private final String store;
    private final int lifetime;
    private final String atKey; // null = where the caster stands
    /** A clone's half height: a ground point is its feet, so its centre is this far above. */
    private static final double HALF_HEIGHT = 0.9;

    public SummonCloneNode(String name, String store, int lifetime, String atKey) {
        this.name = name;
        this.store = store;
        this.lifetime = lifetime;
        this.atKey = atKey;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        java.util.Optional<me.mephisto.ability_engine.engine.target.PointTarget> center;
        if (atKey == null) {
            center = world.positionOf(new EntityTarget(ctx.caster()));
        } else {
            var at = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx, atKey);
            center = at.flatMap(world::positionOf).map(p -> at.get() instanceof EntityTarget ? p
                    : new me.mephisto.ability_engine.engine.target.PointTarget(p.world(), p.position().add(0, HALF_HEIGHT, 0)));
        }
        center.ifPresent(p -> ctx.engine().summons().summonClone(ctx.caster(), name, p.world(), p.position(), lifetime)
                .ifPresent(id -> ctx.blackboard().putRaw(store, new EntityTarget(id))));
        return NodeResult.NEXT;
    }
}
