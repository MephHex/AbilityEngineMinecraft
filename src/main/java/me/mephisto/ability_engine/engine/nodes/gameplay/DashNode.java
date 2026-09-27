package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.SweepHit;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;
import java.util.UUID;
import java.util.Set;

/**
 * Dash the caster along their aim, pushing them every tick and checking the path just ahead:
 * <ul>
 *   <li>{@code hit}  — an enemy is in the way: stop in front of them, store them as "hit"</li>
 *   <li>{@code miss} — reached {@code range}, hit a wall, or got stuck on terrain</li>
 * </ul>
 * Allies are passed through. {@code pierce: true} passes through enemies too (only walls and range end
 * it). {@code store: <name>} writes where it started and ended as "<name>_start" / "<name>_end".
 * {@code flat: true} ignores the vertical part of the aim (ground dash);
 * false follows the aim fully (trident-style). {@code direction: movement} dashes the way the caster
 * is WALKING instead of where they look (strafe left = dash left; always flat), or straight ahead
 * (flat) when they stand still. The cast stays running while dashing, so the
 * ability's active_tags (e.g. block.ability) last exactly as long as the dash.
 */
public final class DashNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.HIT, Ports.MISS, Ports.PASSED);
    /** How far to look for the cursor point with toward: cursor (then capped at the dash's range). */
    private static final double CURSOR_RANGE = 40;
    /**
     * A player's server-side position only moves when their movement packets arrive, so with ping a tick
     * can show no progress even mid-dash. Only this many ticks IN A ROW without progress mean stuck.
     */
    private static final int STUCK_TICKS = 4;
    /** Before the caster has moved at all, wait longer: the first push has to reach their client and back. */
    private static final int START_GRACE_TICKS = 10;

    private final double speed;
    private final double range;
    private final double radius;
    private final boolean flat;
    private final boolean pierce;
    private final String store; // null = don't record the path
    private final boolean alongMovement;
    private final String moverKey; // null = the caster dashes; else whoever is in this key (an echo)
    private final boolean towardCursor; // dash toward the caster's cursor point instead of along their aim
    private String toKey;               // dash to the point in this key (stopping there), e.g. a chosen landing spot

    public DashNode(double speed, double range, double radius, boolean flat) {
        this(speed, range, radius, flat, false, null);
    }

    public DashNode(double speed, double range, double radius, boolean flat, boolean pierce, String store) {
        this(speed, range, radius, flat, pierce, store, false);
    }

    /** @param alongMovement dash the way the caster is moving instead of where they aim */
    public DashNode(double speed, double range, double radius, boolean flat, boolean pierce, String store,
                    boolean alongMovement) {
        this(speed, range, radius, flat, pierce, store, alongMovement, null, false);
    }

    public DashNode(double speed, double range, double radius, boolean flat, boolean pierce, String store,
                    boolean alongMovement, String moverKey) {
        this(speed, range, radius, flat, pierce, store, alongMovement, moverKey, false);
    }

    /**
     * @param moverKey     who dashes: null = the caster, else the entity in this key (an echo)
     * @param towardCursor dash toward the point the CASTER's cursor is on (stopping there if it's closer
     *                     than range), instead of along their aim. An echo and its owner then converge.
     */
    public DashNode(double speed, double range, double radius, boolean flat, boolean pierce, String store,
                    boolean alongMovement, String moverKey, boolean towardCursor, String toKey) {
        this(speed, range, radius, flat, pierce, store, alongMovement, moverKey, towardCursor);
        this.toKey = toKey;
    }

    public DashNode(double speed, double range, double radius, boolean flat, boolean pierce, String store,
                    boolean alongMovement, String moverKey, boolean towardCursor) {
        this.moverKey = moverKey;
        this.towardCursor = towardCursor;
        this.speed = speed;
        this.range = range;
        this.radius = radius;
        this.flat = flat;
        this.pierce = pierce;
        this.store = store;
        this.alongMovement = alongMovement;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        AbilityEngine engine = ctx.engine();
        Optional<Aim> aim = engine.world().aimOf(ctx.caster());
        UUID mover = ctx.caster();
        if (moverKey != null) {
            var target = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx, moverKey);
            if (target.isEmpty() || !(target.get() instanceof EntityTarget e)) return NodeResult.out(Ports.MISS);
            mover = e.id();
        }
        Optional<PointTarget> start = engine.world().positionOf(new EntityTarget(mover));
        if (aim.isEmpty() || start.isEmpty()) return NodeResult.out(Ports.MISS);

        Vec3 dir = aim.get().direction();
        if (flat || alongMovement) dir = new Vec3(dir.x(), 0, dir.z());
        if (alongMovement) {
            Optional<Vec3> moving = engine.world().movementOf(ctx.caster());
            if (moving.isPresent()) dir = new Vec3(moving.get().x(), 0, moving.get().z());
        }
        double maxRange = range;
        if (towardCursor) {
            var cursor = me.mephisto.ability_engine.engine.target.CursorQuery.point(engine, ctx.caster(), CURSOR_RANGE, true);
            if (cursor.isEmpty()) return NodeResult.out(Ports.MISS);
            dir = cursor.get().position().subtract(start.get().position());
            if (flat) dir = new Vec3(dir.x(), 0, dir.z());
            maxRange = Math.min(range, dir.length());
        }
        if (toKey != null) { // straight to a stored point (e.g. a landing spot), stopping there
            var point = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx, toKey).flatMap(engine.world()::positionOf);
            if (point.isEmpty()) return NodeResult.out(Ports.MISS);
            dir = point.get().position().subtract(start.get().position());
            if (flat) dir = new Vec3(dir.x(), 0, dir.z());
            maxRange = Math.min(range, dir.length());
        }
        dir = dir.normalize();
        if (dir.isZero()) return NodeResult.out(Ports.MISS);

        if (store != null) ctx.blackboard().putRaw(store + "_start", start.get());
        // Someone else dashing (an echo) turns to face where it's going first. Never the caster's own camera.
        if (!mover.equals(ctx.caster())) engine.movement().face(mover, dir);
        new Dash(ctx, ctx.suspend(), dir, start.get(), mover, maxRange).begin();
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    private final class Dash {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private Vec3 dir;               // can flatten mid-dash: meeting the floor turns it into a glide
        private final PointTarget start;
        private final int maxTicks;
        private TaskHandle task;
        private Vec3 last;
        private int ticks;
        private int stillTicks;   // ticks in a row without progress
        private boolean moving;   // has the caster moved at all yet?
        private boolean done;
        private final UUID mover;
        private final double maxRange;
        private final java.util.Set<UUID> passed = new java.util.HashSet<>(); // pierce: each enemy once

        Dash(ExecutionContext ctx, Resumer resumer, Vec3 dir, PointTarget start, UUID mover, double maxRange) {
            this.mover = mover;
            this.maxRange = maxRange;
            this.ctx = ctx;
            this.resumer = resumer;
            this.dir = dir;
            this.start = start;
            this.maxTicks = (int) Math.ceil(maxRange / speed) + START_GRACE_TICKS + 5; // safety net, ping included
        }

        void begin() {
            ctx.instance().onEnd(() -> { // cancelled (stun, death): stop pushing
                if (done) return;
                done = true;
                if (task != null) task.cancel();
                ctx.engine().movement().stop(mover);
            });
            tick();
            if (!done) task = ctx.engine().scheduler().every(1, 1, this::tick);
        }

        private void tick() {
            if (done || !ctx.instance().isActive()) return;
            AbilityEngine engine = ctx.engine();
            Optional<PointTarget> pos = engine.world().positionOf(new EntityTarget(mover));
            if (pos.isEmpty()) {
                finish(Ports.MISS);
                return;
            }
            Vec3 here = pos.get().position();
            if (last != null) {
                if (here.distance(last) >= speed * 0.2) {
                    moving = true;
                    stillTicks = 0;
                } else {
                    stillTicks++;
                }
            }
            boolean stuck = stillTicks >= (moving ? STUCK_TICKS : START_GRACE_TICKS);
            if (here.distance(start.position()) >= maxRange || ++ticks > maxTicks || stuck) {
                finish(Ports.MISS);
                return;
            }
            last = here;

            Vec3 ahead = here.add(dir.multiply(speed));
            var barrier = engine.barriers().cross(pos.get().world(), here, ahead, radius, ctx.caster());
            if (barrier.isPresent()) { // an enemy's frontal barrier: like hitting a wall
                engine.barriers().blocked(pos.get().world(), barrier.get().position());
                finish(Ports.MISS);
                return;
            }
            if (pierce) {
                // Through enemies: every enemy in the path ahead triggers "passed" (once each), walls stop it.
                var through = engine.teams().passThroughFor(ctx.caster());
                for (int i = 0; i < 16; i++) {
                    Optional<SweepHit> h = engine.world().sweep(pos.get().world(), here, ahead, radius,
                            id -> passed.contains(id) || id.equals(mover) || through.test(id));
                    if (h.isEmpty()) break;
                    if (!(h.get().target() instanceof EntityTarget enemy)) {
                        if (glideAlongFloor(h.get())) return; // try again next tick, flattened
                        finish(Ports.MISS); // a wall
                        return;
                    }
                    passed.add(enemy.id());
                    passedThrough(enemy);
                    if (done || !ctx.instance().isActive()) return;
                }
                engine.movement().setVelocity(mover, dir.multiply(speed));
                return;
            }
            Optional<SweepHit> hit = engine.world().sweep(pos.get().world(), here, ahead, radius,
                    engine.teams().passThroughFor(ctx.caster()));
            if (hit.isPresent()) {
                if (hit.get().target() instanceof EntityTarget enemy) {
                    ctx.put(Keys.HIT, enemy);
                    finish(Ports.HIT);
                } else if (!glideAlongFloor(hit.get())) {
                    finish(Ports.MISS); // a wall
                }
                return;
            }
            engine.movement().setVelocity(mover, dir.multiply(speed));
        }

        /**
         * Aiming a little down (at someone's body) sends the dash into the ground. Floors don't stop a
         * dash, walls do: drop the downward part and keep going along the floor. Returns false for a
         * wall (or looking straight down), which ends the dash.
         */
        private boolean glideAlongFloor(SweepHit block) {
            if (toKey != null) return false; // heading for a point on the ground: touching down is arriving
            if (block.normal() == null || block.normal().y() < 0.6 || dir.y() >= 0) return false;
            Vec3 flat = new Vec3(dir.x(), 0, dir.z());
            if (flat.length() < 0.2) return false;
            dir = flat.normalize();
            ctx.engine().movement().setVelocity(mover, dir.multiply(speed));
            return true;
        }

        /**
         * A pierced enemy: its own branch out of "passed", with hit = them. The hit counts as coming from
         * the side the dash is heading to (where the dasher ends up): backstabs, frontal blocks.
         */
        private void passedThrough(EntityTarget enemy) {
            ExecutionContext branch = ctx.fork();
            branch.put(Keys.HIT, enemy);
            ctx.engine().world().positionOf(enemy).ifPresent(p -> branch.blackboard().putRaw(Keys.HIT_FROM.name(),
                    new PointTarget(p.world(), p.position().add(dir.multiply(2)))));
            branch.suspend().resume(Ports.PASSED);
        }

        private void finish(String port) {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            ctx.engine().movement().stop(mover);
            if (store != null) {
                ctx.engine().world().positionOf(new EntityTarget(mover)).ifPresent(p -> {
                    ctx.blackboard().putRaw(store + "_end", p);
                    // Hits along this path come from where the dasher ended up (backstabs, frontal blocks).
                    ctx.blackboard().putRaw(me.mephisto.ability_engine.engine.graph.Keys.HIT_FROM.name(), p);
                });
            }
            resumer.resume(port);
        }
    }
}
