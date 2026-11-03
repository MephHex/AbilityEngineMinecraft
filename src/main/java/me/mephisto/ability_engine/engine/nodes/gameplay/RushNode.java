package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.AbilityInstance.CastProgress;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A charge on foot (the Warrior's ultimate): the caster runs forward by themselves, speeding up from {@code start_speed}
 * to {@code max_speed} (blocks a tick) over {@code accelerate} ticks (the XP bar fills meanwhile), turning toward where
 * they look by at most {@code turn} degrees a tick (0: at once). It lasts at most {@code duration} ticks.
 * <ul>
 *   <li>{@code bump}: every enemy it runs into (once each) as its own branch, with hit = them (e.g. knocked up).</li>
 *   <li>{@code full}: once, as its own branch, the moment it's at full speed (e.g. a cue that it's dangerous now).</li>
 *   <li>{@code pin}: at full speed, the first enemy it runs into is pinned instead of bumped: its own branch with hit =
 *       them, and from then on they're carried {@code pin_distance} blocks ahead of the caster (not the unmovable,
 *       block.displace: they're bumped).</li>
 *   <li>{@code slam}: running into terrain with someone pinned, as its own branch with hit = them (crushed into the wall),
 *       just before "wall".</li>
 *   <li>{@code wall}: it ran into terrain (a wall, a block a full step up, an enemy barrier) or got stuck: it's over.</li>
 *   <li>{@code out}: its time ran out.</li>
 * </ul>
 * Rooted or stunned, it stops (it's a movement ability). Whoever runs has {@code state.dashing} meanwhile.
 */
public final class RushNode implements GraphNode {

    public static final String BUMP = "bump", FULL = "full", PIN = "pin", SLAM = "slam", WALL = "wall";
    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, BUMP, FULL, PIN, SLAM, WALL);

    /** Heights (from the body's middle) terrain is looked for ahead: above a step (slabs and stairs are run over)... */
    private static final double[] HEIGHTS = {-0.15, 0.6};
    /** ...and across: both shoulders and the middle, so it can't slip past a corner. */
    private static final double[] ACROSS = {-0.3, 0, 0.3};
    /** How far ahead of the body (past this tick's move) terrain counts as in the way. */
    private static final double BODY = 0.35;
    /** Enemies this far below / above the caster's middle can be run into. */
    private static final double REACH_UP = 1.4;
    /** A player's position only moves when their client says so: this many ticks in a row without progress is stuck. */
    private static final int STUCK_TICKS = 5;
    /** Before the caster has moved at all, wait longer (the first push has to get to their client and back). */
    private static final int START_GRACE_TICKS = 10;

    private final double startSpeed;
    private final double maxSpeed;
    private final int accelerate;
    private final int duration;
    private final double turn;
    private final double radius;
    private final double pinDistance;

    public RushNode(double startSpeed, double maxSpeed, int accelerate, int duration, double turn, double radius,
                    double pinDistance) {
        this.startSpeed = startSpeed;
        this.maxSpeed = Math.max(startSpeed, maxSpeed);
        this.accelerate = Math.max(1, accelerate);
        this.duration = Math.max(1, duration);
        this.turn = turn;
        this.radius = radius;
        this.pinDistance = pinDistance;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        AbilityEngine engine = ctx.engine();
        var aim = engine.world().aimOf(ctx.caster());
        var at = engine.world().positionOf(new EntityTarget(ctx.caster()));
        if (aim.isEmpty() || at.isEmpty()) return NodeResult.NEXT;
        Vec3 dir = flat(aim.get().direction());
        if (dir == null) return NodeResult.NEXT;
        new Run(ctx, ctx.suspend(), dir).begin();
        return NodeResult.SUSPENDED;
    }

    /** Level and unit, or null if it's straight up or down. */
    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x(), 0, v.z());
        return f.lengthSquared() < 1e-6 ? null : f.normalize();
    }

    private final class Run {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final UUID caster;
        private final Set<UUID> bumped = new HashSet<>();
        private Vec3 dir;
        private TaskHandle task;
        private CastProgress bar;
        private UUID pinned;
        private boolean full;
        private boolean done;
        private boolean moving;
        private int ticks;
        private int stillTicks;
        private Vec3 last;

        Run(ExecutionContext ctx, Resumer resumer, Vec3 dir) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.caster = ctx.caster();
            this.dir = dir;
        }

        void begin() {
            ctx.instance().onEnd(() -> { // cancelled (stunned, rooted, died): stop running, let go of whoever is pinned
                if (done) return;
                done = true;
                cleanUp();
            });
            ctx.engine().tags().grant(caster, Tags.DASHING);
            bar = ctx.instance().showProgress(accelerate); // the XP bar: how close to full speed
            tick();
            if (!done) task = ctx.engine().scheduler().every(1, 1, this::tick);
        }

        /** How fast it runs this tick: from the start speed up to full over {@code accelerate} ticks. */
        private double speed() {
            return startSpeed + (maxSpeed - startSpeed) * Math.min(1, (double) ticks / accelerate);
        }

        private void tick() {
            if (done || !ctx.instance().isActive()) return;
            AbilityEngine engine = ctx.engine();
            Optional<PointTarget> pos = engine.world().positionOf(new EntityTarget(caster));
            if (pos.isEmpty()) {
                finish(Ports.OUT);
                return;
            }
            String world = pos.get().world();
            Vec3 here = pos.get().position();
            if (ticks >= duration) {
                finish(Ports.OUT);
                return;
            }
            steer();
            double speed = speed();
            if (last != null) { // stuck against something the look-ahead missed (a fence, a mob in the way)
                if (Math.hypot(here.x() - last.x(), here.z() - last.z()) >= speed * 0.2) {
                    moving = true;
                    stillTicks = 0;
                } else {
                    stillTicks++;
                }
            }
            last = here;
            if (stillTicks >= (moving ? STUCK_TICKS : START_GRACE_TICKS) || blocked(world, here, speed)) {
                crash();
                return;
            }
            if (!full && ticks >= accelerate) {
                full = true;
                branch(FULL, null);
                if (done || !ctx.instance().isActive()) return;
            }
            runInto(world, here, speed);
            if (done || !ctx.instance().isActive()) return;
            engine.movement().stride(caster, dir.multiply(speed));
            carryPinned(world, here, speed);
            ticks++;
        }

        /** Turn toward where they look, at most {@code turn} degrees a tick. */
        private void steer() {
            var aim = ctx.engine().world().aimOf(caster);
            if (aim.isEmpty()) return;
            Vec3 want = flat(aim.get().direction());
            if (want == null) return;
            if (turn <= 0) {
                dir = want;
                return;
            }
            double cur = Math.atan2(dir.z(), dir.x()), goal = Math.atan2(want.z(), want.x());
            double diff = Math.IEEEremainder(goal - cur, Math.PI * 2);
            double max = Math.toRadians(turn);
            double a = cur + Math.max(-max, Math.min(max, diff));
            dir = new Vec3(Math.cos(a), 0, Math.sin(a));
        }

        /** Terrain (or an enemy's barrier) right ahead: what this tick's move would run into. */
        private boolean blocked(String world, Vec3 here, double speed) {
            AbilityEngine engine = ctx.engine();
            Vec3 ahead = here.add(dir.multiply(speed + BODY));
            if (engine.barriers().cross(world, here, ahead, radius, caster).isPresent()) return true;
            Vec3 side = new Vec3(-dir.z(), 0, dir.x());
            for (double dy : HEIGHTS) {
                for (double dx : ACROSS) {
                    Vec3 from = here.add(side.multiply(dx)).add(0, dy, 0);
                    Vec3 to = from.add(dir.multiply(speed + BODY));
                    if (engine.world().sweep(world, from, to, 0, id -> true).isPresent()) return true; // (blocks only)
                }
            }
            return false;
        }

        /** Enemies it reaches this tick: the first at full speed is pinned, everyone else bumped (once each). */
        private void runInto(String world, Vec3 here, double speed) {
            AbilityEngine engine = ctx.engine();
            Vec3 front = here.add(dir.multiply(speed));
            for (EntitySnapshot e : engine.world().livingEntitiesNear(new PointTarget(world, front), radius + 0.6)) {
                UUID id = e.id();
                if (id.equals(caster) || id.equals(pinned) || bumped.contains(id) || !isEnemy(id)) continue;
                if (Math.abs(e.center().y() - here.y()) > REACH_UP) continue;
                if (full && pinned == null && !engine.tags().has(id, Tags.BLOCK_DISPLACE)) {
                    pinned = id;
                    branch(PIN, id);
                } else {
                    bumped.add(id);
                    branch(BUMP, id);
                }
                if (done || !ctx.instance().isActive()) return;
            }
        }

        private boolean isEnemy(UUID id) {
            AbilityEngine engine = ctx.engine();
            return engine.world().isAlive(id) && engine.teams().enemies(caster, id)
                    && !engine.tags().has(id, Tags.UNTARGETABLE) && !engine.veils().blocks(caster, id);
        }

        /** Whoever is pinned is carried just ahead of the caster, where the caster will be next tick. */
        private void carryPinned(String world, Vec3 here, double speed) {
            if (pinned == null) return;
            AbilityEngine engine = ctx.engine();
            var at = engine.world().isAlive(pinned) ? engine.world().positionOf(new EntityTarget(pinned)) : Optional.<PointTarget>empty();
            if (at.isEmpty() || !at.get().world().equals(world)) {
                pinned = null; // dead or gone: nobody to carry
                return;
            }
            Vec3 to = here.add(dir.multiply(speed + pinDistance));
            engine.movement().flyStep(pinned, at.get().position(), new Vec3(to.x(), here.y(), to.z()));
        }

        /** Ran into terrain: whoever is pinned is crushed against it, then it's over. */
        private void crash() {
            UUID crushed = pinned != null && ctx.engine().world().isAlive(pinned) ? pinned : null;
            if (crushed != null) branch(SLAM, crushed);
            finish(WALL);
        }

        /** A branch of its own out of {@code port} (hit = {@code who}, if anyone). */
        private void branch(String port, UUID who) {
            ExecutionContext b = ctx.fork();
            if (who != null) {
                b.put(Keys.HIT, new EntityTarget(who));
                ctx.engine().world().positionOf(new EntityTarget(caster)).ifPresent(p ->
                        b.blackboard().putRaw(Keys.HIT_FROM.name(), p)); // (it came from the runner)
            }
            b.suspend().resume(port);
        }

        private void cleanUp() {
            if (task != null) task.cancel();
            ctx.engine().tags().revoke(caster, Tags.DASHING);
            ctx.engine().movement().stop(caster);
            if (pinned != null) ctx.engine().movement().stop(pinned);
            pinned = null;
            if (bar != null) ctx.instance().clearProgress(bar);
        }

        private void finish(String port) {
            if (done) return;
            done = true;
            cleanUp();
            resumer.resume(port);
        }
    }
}
