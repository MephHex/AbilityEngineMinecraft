package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;
import java.util.UUID;

/**
 * Swap places with the entity in {@code with} (both keep looking where they were looking). A flight (below) exits "none"
 * when it can't: nobody there, another world, or they can't be moved ({@code block.displace}); the instant swap just goes
 * on without swapping.
 * <p>Instant by default. With {@code speed} (blocks a tick) it's a flight instead (Switcheroo): both float up {@code lift}
 * blocks and fly to where the other one was, taking distance / speed ticks (at least {@code min_ticks}, at most
 * {@code max_ticks}), curving {@code side} blocks apart on the way so they pass each other, then land exactly on the
 * other's spot, facing each other. {@code status} is on both meanwhile (what they can't do, e.g. act or be targeted) and comes off when they
 * land. They fly through anything in the way. The cast continues ("next") once they've landed; it ending early (the
 * caster dies) drops both where they are.
 */
public final class SwapNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.NONE);
    /** How much of the flight it takes to float up to {@code lift}, and to come down again at the end. */
    private static final double RISE = 0.2;

    private final String withKey;
    private final double speed;     // 0 = instant
    private final double lift;
    private final double side;
    private final int minTicks;
    private final int maxTicks;
    private final String status;    // null = none

    public SwapNode(String withKey) {
        this(withKey, 0, 0, 0, 1, 1, null);
    }

    public SwapNode(String withKey, double speed, double lift, double side, int minTicks, int maxTicks, String status) {
        this.withKey = withKey;
        this.speed = speed;
        this.lift = lift;
        this.side = side;
        this.minTicks = Math.max(1, minTicks);
        this.maxTicks = Math.max(this.minTicks, maxTicks);
        this.status = status;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var engine = ctx.engine();
        var world = engine.world();
        var other = KeyQuery.read(ctx, withKey).filter(t -> t instanceof EntityTarget).map(t -> (EntityTarget) t);
        var me = world.positionOf(new EntityTarget(ctx.caster()));
        var them = other.flatMap(world::positionOf);
        boolean unmovable = other.filter(o -> !o.id().equals(ctx.caster()))
                .map(o -> engine.tags().has(o.id(), Tags.BLOCK_DISPLACE)).orElse(false);
        if (unmovable || me.isEmpty() || them.isEmpty() || !me.get().world().equals(them.get().world())
                || other.get().id().equals(ctx.caster())) {
            return speed > 0 ? NodeResult.out(Ports.NONE) : NodeResult.NEXT; // (instant: on as before, nothing swapped)
        }
        if (speed <= 0) {
            engine.movement().teleport(ctx.caster(), them.get().position());
            engine.movement().teleport(other.get().id(), me.get().position());
            return NodeResult.NEXT;
        }
        new Flight(ctx, other.get().id(), me.get().position(), them.get().position()).begin();
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    /** Both of them on their way: the caster from {@code a} to {@code b}, the other from {@code b} to {@code a}. */
    private final class Flight {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final UUID caster, other;
        private final Vec3 a, b;
        private final int ticks;
        private int tick;
        private TaskHandle task;
        private boolean done;

        Flight(ExecutionContext ctx, UUID other, Vec3 a, Vec3 b) {
            this.ctx = ctx;
            this.resumer = ctx.suspend();
            this.caster = ctx.caster();
            this.other = other;
            this.a = a;
            this.b = b;
            this.ticks = (int) Math.max(minTicks, Math.min(maxTicks, Math.round(a.distance(b) / speed)));
        }

        void begin() {
            var engine = ctx.engine();
            for (UUID id : new UUID[]{caster, other}) {
                engine.movement().dismount(id);
                engine.movement().stop(id);
                if (status != null) engine.statuses().apply(id, status, ticks + 20, caster); // (taken off on landing)
            }
            ctx.instance().onEnd(this::land);
            task = engine.scheduler().every(1, 1, this::step);
        }

        private void step() {
            if (done) return;
            tick++;
            if (tick >= ticks) {
                land();
                resumer.resume(Ports.OUT);
                return;
            }
            double before = (double) (tick - 1) / ticks, t = (double) tick / ticks;
            move(caster, a, b, before, t);
            move(other, b, a, before, t);
        }

        /** One tick of someone flying {@code from} -> {@code to}: from a share {@code before} of the way to {@code t}. */
        private void move(UUID id, Vec3 from, Vec3 to, double before, double t) {
            var engine = ctx.engine();
            if (!engine.world().isAlive(id)) return;
            engine.movement().flyStep(id, pathAt(from, to, before), pathAt(from, to, t));
        }

        /** Where someone flying {@code from} -> {@code to} is a share {@code t} of the way. */
        private Vec3 pathAt(Vec3 from, Vec3 to, double t) {
            Vec3 d = to.subtract(from);
            Vec3 flat = new Vec3(d.x(), 0, d.z());
            Vec3 ahead = flat.isZero() ? new Vec3(1, 0, 0) : flat.normalize();
            Vec3 left = new Vec3(ahead.z(), 0, -ahead.x()); // (each flies round its own side: they pass each other)
            double s = smooth(t);
            double up = lift * smooth(Math.min(1, t / RISE)) * smooth(Math.min(1, (1 - t) / RISE));
            return from.add(d.multiply(s)).add(0, up, 0).add(left.multiply(side * Math.sin(Math.PI * t)));
        }

        /** Both put down: on the other's spot when the flight is over, where they are if it ends early. */
        private void land() {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            var engine = ctx.engine();
            boolean arrived = tick >= ticks;
            for (UUID id : new UUID[]{caster, other}) {
                if (!engine.world().isAlive(id)) continue;
                if (arrived) { // exactly on the other's spot, facing them (they've turned round each other on the way)
                    Vec3 spot = id.equals(caster) ? b : a, them = id.equals(caster) ? a : b;
                    Vec3 look = new Vec3(them.x() - spot.x(), 0, them.z() - spot.z());
                    engine.movement().teleport(id, spot, look.isZero() ? null : look);
                }
                engine.movement().stop(id);
                if (status != null) engine.statuses().remove(id, status);
            }
        }
    }

    /** Eases in and out: 0 -> 1, slow at both ends. */
    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
