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
 * <p>{@code of: <key>} makes it a look-alike of someone else (placed where THEY stand unless {@code at}
 * says otherwise), e.g. an enemy's soul; it still belongs to the caster. {@code health: N} (design HP)
 * makes it vulnerable: it can be hit and killed (it's on {@code of}'s team, so their allies can't), see
 * await_summon; {@code health_share: 0.6} instead makes that 60% of {@code of}'s max HP (a soul as sturdy as
 * its owner); {@code glowing: true} outlines it. Also stores where it was placed as {@code <store>_at}.
 */
public final class SummonCloneNode implements GraphNode {

    private final String name;
    private final String store;
    private final int lifetime;
    private final String atKey; // null = where the caster stands
    /** A clone's half height: a ground point is its feet, so its centre is this far above. */
    private static final double HALF_HEIGHT = 0.9;

    private final String ofKey;  // null = the caster
    private final double health;
    private final double healthShare;
    private final boolean glowing;

    public SummonCloneNode(String name, String store, int lifetime, String atKey) {
        this(name, store, lifetime, atKey, null, 0, 0, false);
    }

    /** @param healthShare above 0: health is this share of {@code of}'s max HP instead of {@code health} */
    public SummonCloneNode(String name, String store, int lifetime, String atKey, String ofKey, double health,
                           double healthShare, boolean glowing) {
        this.healthShare = healthShare;
        this.name = name;
        this.store = store;
        this.lifetime = lifetime;
        this.atKey = atKey;
        this.ofKey = ofKey;
        this.health = health;
        this.glowing = glowing;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        java.util.UUID of = ctx.caster();
        if (ofKey != null) {
            if (!(me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx, ofKey).orElse(null) instanceof EntityTarget e)) {
                return NodeResult.NEXT; // nobody to copy
            }
            of = e.id();
        }
        java.util.Optional<me.mephisto.ability_engine.engine.target.PointTarget> center;
        if (atKey == null) {
            center = world.positionOf(new EntityTarget(of));
        } else {
            var at = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx, atKey);
            center = at.flatMap(world::positionOf).map(p -> at.get() instanceof EntityTarget ? p
                    : new me.mephisto.ability_engine.engine.target.PointTarget(p.world(), p.position().add(0, HALF_HEIGHT, 0)));
        }
        double hp = healthShare > 0 ? ctx.engine().stats().maxHealth(of) * healthShare : health;
        var options = new me.mephisto.ability_engine.engine.platform.CloneSpawner.Options(hp, glowing,
                hp > 0 ? of : null);
        java.util.UUID copyOf = of;
        center.ifPresent(p -> ctx.engine().summons()
                .summonClone(ctx.caster(), name, copyOf, p.world(), p.position(), lifetime, towardOwner(ctx, p.position()), options)
                .ifPresent(id -> {
                    ctx.blackboard().putRaw(store, new EntityTarget(id));
                    ctx.blackboard().putRaw(store + "_at", p);
                }));
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
