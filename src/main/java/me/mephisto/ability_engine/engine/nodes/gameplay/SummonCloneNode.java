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
 * It spawns facing its owner (placed where they stand: looking the way they look).
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
        center.ifPresent(p -> ctx.engine().summons()
                .summonClone(ctx.caster(), name, p.world(), p.position(), lifetime, towardOwner(ctx, p.position()))
                .ifPresent(id -> ctx.blackboard().putRaw(store, new EntityTarget(id))));
        return NodeResult.NEXT;
    }

    /** Horizontal direction from the clone to its owner; null if it's right on them (then: the owner's look). */
    private static me.mephisto.ability_engine.engine.math.Vec3 towardOwner(ExecutionContext ctx,
                                                                         me.mephisto.ability_engine.engine.math.Vec3 at) {
        return ctx.engine().world().positionOf(new EntityTarget(ctx.caster())).map(owner -> {
            var d = owner.position().subtract(at);
            var flat = new me.mephisto.ability_engine.engine.math.Vec3(d.x(), 0, d.z());
            return flat.length() < 0.5 ? null : flat.normalize();
        }).orElse(null);
    }
}
