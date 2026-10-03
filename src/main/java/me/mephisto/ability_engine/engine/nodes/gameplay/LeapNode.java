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
import java.util.Set;

/**
 * Leap in an arc: {@code speed} blocks/tick along the ground ({@code direction: aim} = where you look,
 * flattened; {@code movement} = where you walk; {@code up} = straight up) and {@code up} blocks/tick
 * upward at the start, losing {@code gravity} every tick. Exits "out" when you land
 * ({@code until: land}, default) or at the top of the arc ({@code until: apex}, e.g. to hover there).
 * Walls stop the forward part, not the fall. With {@code store: <name>}, where it ended is saved.
 * With {@code stop_at_enemies: true} it stops dead on running into an enemy (within {@code radius} of her path) and exits
 * "hit" instead, with them stored as "hit" (e.g. a leaping slash that lands on whoever it reaches).
 */
public final class LeapNode implements GraphNode {

    /** Feet this close to the ground count as landed (from the body's centre: half a player + a bit). */
    private static final double LAND_DISTANCE = 1.15;
    private static final int MIN_TICKS = 2;
    private static final int MAX_TICKS = 200;

    public enum Direction { AIM, MOVEMENT, UP }

    private final Direction direction;
    private final double speed;
    private final double up;
    private final double gravity;
    private final boolean untilApex;
    private final String store;
    private final boolean stopAtEnemies;
    private final double radius;

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT);
    /** stop_at_enemies: where along her body (blocks below its middle) the path is checked: the middle, the legs. */
    private static final double[] BODY = {0, 0.7};
    private static final Set<String> OUTPUTS_WITH_HIT = Set.of(Ports.OUT, Ports.HIT);

    public LeapNode(Direction direction, double speed, double up, double gravity, boolean untilApex, String store) {
        this(direction, speed, up, gravity, untilApex, store, false, 0.6);
    }

    /** @param stopAtEnemies stop on running into an enemy (within {@code radius} of the path): exits "hit" */
    public LeapNode(Direction direction, double speed, double up, double gravity, boolean untilApex, String store,
                    boolean stopAtEnemies, double radius) {
        this.direction = direction;
        this.speed = speed;
        this.up = up;
        this.gravity = gravity;
        this.untilApex = untilApex;
        this.store = store;
        this.stopAtEnemies = stopAtEnemies;
        this.radius = radius;
    }

    @Override
    public Set<String> outputs() { return stopAtEnemies ? OUTPUTS_WITH_HIT : OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        AbilityEngine engine = ctx.engine();
        Optional<Aim> aim = engine.world().aimOf(ctx.caster());
        if (aim.isEmpty()) return NodeResult.NEXT;
        Vec3 flat = Vec3.ZERO;
        if (direction != Direction.UP) {
            Vec3 d = aim.get().direction();
            flat = new Vec3(d.x(), 0, d.z());
            if (direction == Direction.MOVEMENT) {
                var moving = engine.world().movementOf(ctx.caster());
                if (moving.isPresent()) flat = new Vec3(moving.get().x(), 0, moving.get().z());
            }
            flat = flat.normalize();
        }
        new Leap(ctx, ctx.suspend(), flat.multiply(speed)).begin();
        return NodeResult.SUSPENDED;
    }

    private final class Leap {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private Vec3 forward;
        private double vy = up;
        private int ticks;
        private TaskHandle task;
        private boolean done;

        Leap(ExecutionContext ctx, Resumer resumer, Vec3 forward) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.forward = forward;
        }

        void begin() {
            ctx.instance().onEnd(() -> { // cancelled (stun, death): stop pushing
                if (done) return;
                done = true;
                if (task != null) task.cancel();
                ctx.engine().movement().stop(ctx.caster());
            });
            tick();
            if (!done) task = ctx.engine().scheduler().every(1, 1, this::tick);
        }

        private void tick() {
            if (done || !ctx.instance().isActive()) return;
            AbilityEngine engine = ctx.engine();
            Optional<PointTarget> pos = engine.world().positionOf(new EntityTarget(ctx.caster()));
            if (pos.isEmpty() || ++ticks > MAX_TICKS) {
                finish(pos);
                return;
            }
            if (untilApex && vy <= 0) {
                finish(pos);
                return;
            }
            boolean falling = vy < 0;
            if (ticks > MIN_TICKS && falling && onGround(pos.get())) {
                finish(pos);
                return;
            }
            if (stopAtEnemies) { // running into an enemy: stop right there
                Vec3 step = forward.add(0, vy, 0);
                for (double down : BODY) { // along her body, not just its middle: over their head still collides
                    Vec3 here = pos.get().position().add(0, -down, 0);
                    Optional<SweepHit> who = engine.world().sweep(pos.get().world(), here, here.add(step), radius,
                            engine.teams().passThroughFor(ctx.caster()));
                    if (who.isPresent() && who.get().target() instanceof EntityTarget enemy) {
                        ctx.put(Keys.HIT, enemy);
                        finish(pos, Ports.HIT);
                        return;
                    }
                }
            }
            if (!forward.isZero()) { // a wall ahead: keep falling, stop going forward
                Optional<SweepHit> wall = engine.world().sweep(pos.get().world(), pos.get().position(),
                        pos.get().position().add(forward), 0.3, id -> true);
                if (wall.isPresent() && wall.get().normal() != null && Math.abs(wall.get().normal().y()) < 0.5) {
                    forward = Vec3.ZERO;
                }
            }
            engine.movement().setVelocity(ctx.caster(), forward.add(0, vy, 0));
            vy -= gravity;
        }

        private boolean onGround(PointTarget at) {
            return ctx.engine().world().groundBelow(at.world(), at.position(), LAND_DISTANCE).isPresent();
        }

        private void finish(Optional<PointTarget> pos) { finish(pos, Ports.OUT); }

        private void finish(Optional<PointTarget> pos, String port) {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            ctx.engine().movement().stop(ctx.caster());
            if (store != null) pos.ifPresent(p -> ctx.blackboard().putRaw(store, p));
            resumer.resume(port);
        }
    }
}
