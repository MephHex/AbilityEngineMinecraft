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
 * <p>{@code at: cursor} places it where the caster's crosshair meets a block, at most {@code range} blocks away (default
 * 12; nothing there: that far along the aim), dropped onto the ground below. {@code face: owner} makes it look the way the
 * caster looks (default: toward them). {@code disguise: true} passes for the caster (their name tag, all their gear, it
 * moves like a body), {@code mirror: true} copies their every move (see Mirrors), {@code hits: 2} breaks it after that
 * many basic attacks (or any one ability hit; give it plenty of health), and {@code end_cue} plays where it is when it goes.
 * {@code beside: 1.5} places it that far to the caster's left or right (at random; a wall that side: the other), on the
 * ground there: so nobody can tell which one is which (Shaco's Hallucinate). {@code on_break: <status>}: whoever breaks it
 * by hitting it gets that status, from the caster (e.g. friendly fire for having been fooled).
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
    private double range = 12;
    private boolean faceOwner;
    private boolean disguise;
    private boolean mirror;
    private int hits;
    private String endCue;
    private double beside;
    private String onBreak;

    /** Whoever breaks it by hitting it gets this status (null = nothing). */
    public SummonCloneNode onBreak(String status) {
        this.onBreak = status;
        return this;
    }

    /** Placed this many blocks to the left or right of the caster (at random), instead of where they stand. */
    public SummonCloneNode beside(double blocks) {
        this.beside = Math.max(0, blocks);
        return this;
    }

    /** The options that came later (see the class comment). */
    public SummonCloneNode extras(double range, boolean faceOwner, boolean disguise, boolean mirror, int hits, String endCue) {
        this.range = range;
        this.faceOwner = faceOwner;
        this.disguise = disguise;
        this.mirror = mirror;
        this.hits = hits;
        this.endCue = endCue;
        return this;
    }

    /** "at: cursor": the spot the caster's crosshair is on. */
    public static final String CURSOR = "cursor";

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
        if (atKey == null && beside > 0) {
            center = besideCaster(ctx);
        } else if (atKey == null) {
            center = world.positionOf(new EntityTarget(of));
        } else if (CURSOR.equals(atKey)) { // where the crosshair meets a block (or that far), on the ground below it
            center = me.mephisto.ability_engine.engine.target.CursorQuery.point(ctx.engine(), ctx.caster(), range, false)
                    .map(p -> {
                        var aim = world.aimOf(ctx.caster());
                        // (a little back toward the caster: a spot on the face of a wall is in front of it, not in it)
                        var spot = aim.map(a -> p.position().subtract(a.direction().multiply(0.3))).orElse(p.position());
                        var ground = world.groundBelow(p.world(), spot.add(0, 0.1, 0), 40).orElse(spot);
                        return new me.mephisto.ability_engine.engine.target.PointTarget(p.world(), ground.add(0, HALF_HEIGHT, 0));
                    });
        } else {
            var at = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx, atKey);
            center = at.flatMap(world::positionOf).map(p -> at.get() instanceof EntityTarget ? p
                    : new me.mephisto.ability_engine.engine.target.PointTarget(p.world(), p.position().add(0, HALF_HEIGHT, 0)));
        }
        double hp = healthShare > 0 ? ctx.engine().stats().maxHealth(of) * healthShare : health;
        var options = new me.mephisto.ability_engine.engine.platform.CloneSpawner.Options(hp, glowing,
                hp > 0 ? of : null, disguise);
        java.util.UUID copyOf = of;
        var summons = ctx.engine().summons();
        center.ifPresent(p -> summons
                .summonClone(ctx.caster(), name, copyOf, p.world(), p.position(), lifetime, facing(ctx, p.position()), options)
                .ifPresent(id -> {
                    summons.breakAfter(id, hits);
                    summons.endCue(id, endCue);
                    summons.onBreak(id, onBreak);
                    if (mirror) ctx.engine().mirrors().add(ctx.caster(), id);
                    ctx.blackboard().putRaw(store, new EntityTarget(id));
                    ctx.blackboard().putRaw(store + "_at", p);
                }));
        return NodeResult.NEXT;
    }

    /**
     * {@code beside} blocks to the caster's left or right (at random, as they face), on the ground there. A wall that side:
     * the other side; walls both sides: right where they stand.
     */
    private java.util.Optional<me.mephisto.ability_engine.engine.target.PointTarget> besideCaster(ExecutionContext ctx) {
        var world = ctx.engine().world();
        var at = world.positionOf(new EntityTarget(ctx.caster()));
        if (at.isEmpty()) return at;
        var here = at.get().position();
        var look = world.aimOf(ctx.caster()).map(a -> a.direction()).orElse(new me.mephisto.ability_engine.engine.math.Vec3(1, 0, 0));
        var flat = new me.mephisto.ability_engine.engine.math.Vec3(look.x(), 0, look.z());
        if (flat.isZero()) flat = new me.mephisto.ability_engine.engine.math.Vec3(1, 0, 0);
        flat = flat.normalize();
        var left = new me.mephisto.ability_engine.engine.math.Vec3(flat.z(), 0, -flat.x());
        double first = ctx.engine().random().nextBoolean() ? 1 : -1;
        for (double side : new double[]{first, -first}) {
            var dir = left.multiply(side);
            var to = here.add(dir.multiply(beside));
            if (world.sweep(at.get().world(), here, to.add(dir.multiply(0.4)), 0, id -> true).isPresent()) continue; // a wall
            var ground = world.groundBelow(at.get().world(), to.add(0, 0.5, 0), 3);
            var center = ground.map(g -> g.add(0, HALF_HEIGHT, 0)).orElse(to);
            return java.util.Optional.of(new me.mephisto.ability_engine.engine.target.PointTarget(at.get().world(), center));
        }
        return at;
    }

    /**
     * Which way it looks when it appears: a mirroring clone right on its owner, the other way from them (it's their
     * half-turned copy, see Mirrors); with face: owner, their way (null); else toward them.
     */
    private me.mephisto.ability_engine.engine.math.Vec3 facing(ExecutionContext ctx, me.mephisto.ability_engine.engine.math.Vec3 at) {
        var world = ctx.engine().world();
        if (mirror) {
            var owner = world.positionOf(new EntityTarget(ctx.caster())).map(o -> o.position());
            var look = world.aimOf(ctx.caster()).map(a -> new me.mephisto.ability_engine.engine.math.Vec3(-a.direction().x(), 0,
                    -a.direction().z()));
            if (owner.isPresent() && look.isPresent() && !look.get().isZero()
                    && Math.hypot(owner.get().x() - at.x(), owner.get().z() - at.z()) <= 0.3) {
                return look.get().normalize();
            }
        }
        return faceOwner ? null : towardOwner(ctx, at);
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
