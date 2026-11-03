package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.ability.EchoPair;
import me.mephisto.ability_engine.engine.data.NodeTypes;
import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.data.Parsers;
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
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.target.Target;
import me.mephisto.ability_engine.engine.target.TargetQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Nodes for second bodies (Mitosis, see Bodies) and what its kit needs:
 * <ul>
 *   <li>{@code fuse_body} -> out / none: the caster's second body fuses back into them (none: they have none).</li>
 *   <li>{@code swap_body} -> out / none: the caster and their second body trade places (none: no body).</li>
 *   <li>{@code echo_meet { key, window, store }} -> met / alone / consumed: the caster's shot and its echo's (the second
 *       body's copy of this cast) meet up. Each one that gets here posts what's in {@code key} (default hit); the second
 *       to get here within {@code window} ticks of the first exits "met", with the first one's value in {@code store}
 *       (default other), and the first one exits "consumed" (the other one goes on for both). Nobody else within the
 *       window (or no echo): "alone".</li>
 *   <li>{@code same_target { a, b }} -> same / different: are the two keys the same entity?</li>
 *   <li>{@code midpoint { a, b, store }}: the spot halfway between two keys, right now.</li>
 *   <li>{@code carry { query, status, spread }}: every enemy the query finds (not the unmovable) is carried along inside
 *       the caster (moved to them every tick, a little apart) with {@code status} on them, until {@code uncarry} or the
 *       cast ends.</li>
 *   <li>{@code fly_to { to, ticks, height }}: the caster flies to the spot in {@code to} in exactly that many ticks, in
 *       an arc {@code height} blocks high, through anything in the way, and lands on it.</li>
 * </ul>
 */
public final class BodyNodes {

    public static final String MET = "met";
    public static final String ALONE = "alone";
    public static final String CONSUMED = "consumed";
    public static final String SAME = "same";
    public static final String DIFFERENT = "different";
    /** Where a carry keeps what it carries, for uncarry. */
    static final String CARRIED = "_carried";

    private BodyNodes() {}

    public static void register(NodeTypes t) {
        t.register("fuse_body", (p, e) -> new FuseBody());
        t.register("swap_body", (p, e) -> new SwapBody());
        t.register("echo_meet", (p, e) -> {
            int window = p.getInt("window", 10);
            if (window < 0) throw p.error("window", "ticks, at least 0");
            return new EchoMeet(p.getString("key", "hit"), window, p.getString("store", "other"));
        });
        t.register("same_target", (p, e) -> new SameTarget(p.requireString("a"), p.requireString("b")));
        t.register("midpoint", (p, e) -> new Midpoint(p.requireString("a"), p.requireString("b"), p.requireString("store")));
        t.register("carry", (p, e) -> new Carry(Parsers.query(p.requireParams("query")), status(p, "status", e),
                p.getDouble("spread", 0.5)));
        t.register("uncarry", (p, e) -> new Uncarry());
        t.register("fly_to", (p, e) -> {
            int ticks = p.requireInt("ticks");
            if (ticks < 1) throw p.error("ticks", "at least 1");
            return new FlyTo(p.requireString("to"), ticks, p.getDouble("height", 2));
        });
    }

    private static String status(Params p, String key, me.mephisto.ability_engine.engine.AbilityEngine e) {
        String status = p.getString(key, null);
        if (status != null && e.statusDefs().find(status).isEmpty()) {
            throw p.error(key, "unknown status '" + status + "' (define it under 'statuses:')");
        }
        return status;
    }

    // ---- fuse / swap ----------------------------------------------------------------------------------------------

    static final class FuseBody implements GraphNode {
        private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.NONE);

        @Override
        public NodeResult execute(ExecutionContext ctx) {
            return ctx.engine().bodies().fuse(ctx.caster()) ? NodeResult.NEXT : NodeResult.out(Ports.NONE);
        }

        @Override
        public Set<String> outputs() { return OUTPUTS; }
    }

    static final class SwapBody implements GraphNode {
        private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.NONE);

        @Override
        public NodeResult execute(ExecutionContext ctx) {
            return ctx.engine().bodies().swap(ctx.caster()) ? NodeResult.NEXT : NodeResult.out(Ports.NONE);
        }

        @Override
        public Set<String> outputs() { return OUTPUTS; }
    }

    // ---- a cast and its echo meeting up ---------------------------------------------------------------------------

    /** One side's arrival at an echo_meet: what it brought, when, and its waiting branch. */
    private static final class Arrival {
        final Object value;
        final long at;
        boolean taken;   // matched, timed out or its cast is over: nobody else can meet it
        Resumer waiting; // its branch, waiting to hear whether the other comes

        Arrival(Object value, long at) {
            this.value = value;
            this.at = at;
        }
    }

    static final class EchoMeet implements GraphNode {
        private static final Set<String> OUTPUTS = Set.of(MET, ALONE, CONSUMED);
        private final String key;
        private final int window;
        private final String store;

        EchoMeet(String key, int window, String store) {
            this.key = key;
            this.window = window;
            this.store = store;
        }

        @Override
        public Set<String> outputs() { return OUTPUTS; }

        @Override
        public NodeResult execute(ExecutionContext ctx) {
            EchoPair pair = ctx.instance().pair();
            Object mine = ctx.blackboard().raw(key);
            if (pair == null || mine == null || !pair.other(ctx.instance()).isActive() && pair.get(name(ctx, false)) == null) {
                return NodeResult.out(ALONE);
            }
            long now = ctx.engine().clock().now();
            if (pair.get(name(ctx, false)) instanceof Arrival theirs && !theirs.taken && now - theirs.at <= window) {
                theirs.taken = true;
                Arrival me = new Arrival(mine, now);
                me.taken = true;
                pair.post(name(ctx, true), me);
                ctx.blackboard().putRaw(store, theirs.value);
                Resumer them = theirs.waiting;
                theirs.waiting = null;
                if (them != null) them.resume(CONSUMED); // the other one goes on for both of them
                return NodeResult.out(MET);
            }
            Arrival me = new Arrival(mine, now);
            me.waiting = ctx.suspend();
            pair.post(name(ctx, true), me);
            TaskHandle timer = ctx.engine().scheduler().after(Math.max(1, window), () -> {
                if (me.taken) return;
                me.taken = true;
                Resumer r = me.waiting;
                me.waiting = null;
                if (r != null) r.resume(ALONE);
            });
            ctx.instance().onEnd(() -> {
                me.taken = true;
                me.waiting = null;
                timer.cancel();
            });
            return NodeResult.SUSPENDED;
        }

        /** The name this side (or the other) posts its arrival under: this node's, per side of the pair. */
        private static String name(ExecutionContext ctx, boolean mine) {
            boolean owner = ctx.instance().pair().isOwner(ctx.instance());
            return "meet:" + ctx.currentNode() + ":" + ((owner == mine) ? "owner" : "echo");
        }
    }

    static final class SameTarget implements GraphNode {
        private static final Set<String> OUTPUTS = Set.of(SAME, DIFFERENT);
        private final String a;
        private final String b;

        SameTarget(String a, String b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public Set<String> outputs() { return OUTPUTS; }

        @Override
        public NodeResult execute(ExecutionContext ctx) {
            Optional<Target> x = KeyQuery.read(ctx, a), y = KeyQuery.read(ctx, b);
            boolean same = x.isPresent() && y.isPresent() && x.get() instanceof EntityTarget ex
                    && y.get() instanceof EntityTarget ey && ex.id().equals(ey.id());
            return NodeResult.out(same ? SAME : DIFFERENT);
        }
    }

    static final class Midpoint implements GraphNode {
        private final String a;
        private final String b;
        private final String store;

        Midpoint(String a, String b, String store) {
            this.a = a;
            this.b = b;
            this.store = store;
        }

        @Override
        public NodeResult execute(ExecutionContext ctx) {
            var world = ctx.engine().world();
            var x = KeyQuery.read(ctx, a).flatMap(world::positionOf);
            var y = KeyQuery.read(ctx, b).flatMap(world::positionOf);
            if (x.isPresent() && y.isPresent() && x.get().world().equals(y.get().world())) {
                Vec3 mid = x.get().position().add(y.get().position()).multiply(0.5);
                ctx.blackboard().putRaw(store, new PointTarget(x.get().world(), mid));
            }
            return NodeResult.NEXT;
        }
    }

    // ---- carrying enemies inside you ------------------------------------------------------------------------------

    /** What a carry holds, and how to let go of it. */
    static final class Carried {
        final List<UUID> who = new ArrayList<>();
        TaskHandle task;
        Runnable release = () -> {};
        boolean done;

        void stop() {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            release.run();
        }
    }

    static final class Carry implements GraphNode {
        private final TargetQuery query;
        private final String status;
        private final double spread;

        Carry(TargetQuery query, String status, double spread) {
            this.query = query;
            this.status = status;
            this.spread = spread;
        }

        @Override
        public NodeResult execute(ExecutionContext ctx) {
            var engine = ctx.engine();
            UUID caster = ctx.caster();
            Carried carried = new Carried();
            for (Target t : query.find(ctx)) {
                if (!(t instanceof EntityTarget e) || e.id().equals(caster)) continue;
                if (engine.teams().allies(caster, e.id()) || engine.tags().has(e.id(), Tags.UNTARGETABLE)) continue;
                if (engine.tags().has(e.id(), Tags.BLOCK_DISPLACE)) continue; // the unmovable stay put
                if (engine.veils().blocks(caster, e.id())) continue;
                carried.who.add(e.id());
                if (status != null) engine.statuses().apply(e.id(), status, caster);
            }
            carried.release = () -> {
                if (status != null) for (UUID id : carried.who) engine.statuses().remove(id, status);
                for (UUID id : carried.who) engine.movement().stop(id);
            };
            ctx.blackboard().putRaw(CARRIED, carried);
            if (!carried.who.isEmpty()) {
                carried.task = engine.scheduler().every(1, 1, () -> {
                    var at = engine.world().positionOf(new EntityTarget(caster));
                    if (at.isEmpty()) return;
                    int n = carried.who.size();
                    for (int i = 0; i < n; i++) {
                        UUID id = carried.who.get(i);
                        if (!engine.world().isAlive(id)) continue;
                        var from = engine.world().positionOf(new EntityTarget(id));
                        if (from.isEmpty() || !from.get().world().equals(at.get().world())) continue;
                        double angle = 2 * Math.PI * i / n; // a little apart, round the middle
                        Vec3 to = at.get().position().add(n == 1 ? 0 : Math.cos(angle) * spread, 0, n == 1 ? 0 : Math.sin(angle) * spread);
                        engine.movement().flyStep(id, from.get().position(), to);
                    }
                });
            }
            ctx.instance().onEnd(carried::stop);
            return NodeResult.NEXT;
        }
    }

    static final class Uncarry implements GraphNode {
        @Override
        public NodeResult execute(ExecutionContext ctx) {
            if (ctx.blackboard().raw(CARRIED) instanceof Carried c) c.stop();
            return NodeResult.NEXT;
        }
    }

    // ---- flying to a spot in an arc ---------------------------------------------------------------------------------

    static final class FlyTo implements GraphNode {
        /** A ground spot is under the feet: the body's middle lands this far above it. */
        private static final double HALF_HEIGHT = 0.9;
        private final String to;
        private final int ticks;
        private final double height;

        FlyTo(String to, int ticks, double height) {
            this.to = to;
            this.ticks = ticks;
            this.height = height;
        }

        @Override
        public NodeResult execute(ExecutionContext ctx) {
            var engine = ctx.engine();
            UUID caster = ctx.caster();
            var start = engine.world().positionOf(new EntityTarget(caster));
            Optional<Target> target = KeyQuery.read(ctx, to);
            var spot = target.flatMap(engine.world()::positionOf);
            if (start.isEmpty() || spot.isEmpty() || !start.get().world().equals(spot.get().world())) return NodeResult.NEXT;
            Vec3 from = start.get().position();
            Vec3 end = target.get() instanceof EntityTarget ? spot.get().position() : spot.get().position().add(0, HALF_HEIGHT, 0);
            Resumer resumer = ctx.suspend();
            int[] tick = {0};
            boolean[] done = {false};
            TaskHandle[] task = new TaskHandle[1];
            engine.tags().grant(caster, Tags.SWAPPING); // flown through walls: no suffocation
            Runnable finish = () -> {
                if (done[0]) return;
                done[0] = true;
                if (task[0] != null) task[0].cancel();
                engine.tags().revoke(caster, Tags.SWAPPING);
            };
            task[0] = engine.scheduler().every(1, 1, () -> {
                if (done[0]) return;
                int t = ++tick[0];
                Vec3 now = at(from, end, t - 1), next = at(from, end, t);
                if (t >= ticks) {
                    engine.movement().teleport(caster, end);
                    engine.movement().stop(caster);
                    finish.run();
                    resumer.resume(Ports.OUT);
                    return;
                }
                engine.movement().flyStep(caster, now, next);
            });
            ctx.instance().onEnd(finish);
            return NodeResult.SUSPENDED;
        }

        /** Where the flight is after {@code t} ticks: along the straight line, raised in an arc. */
        private Vec3 at(Vec3 from, Vec3 end, int t) {
            double s = Math.min(1, t / (double) ticks);
            Vec3 line = from.add(end.subtract(from).multiply(s));
            return line.add(0, 4 * height * s * (1 - s), 0);
        }
    }
}
