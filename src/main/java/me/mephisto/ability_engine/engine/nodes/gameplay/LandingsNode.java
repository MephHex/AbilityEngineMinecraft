package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Set;

/**
 * For {@code ticks}, watch the caster land: every time they come down after being in the air (a jump, a fall, a
 * knock-up), "landed" runs as its own branch, with the spot on the ground they landed on stored as {@code store}.
 * When the time is up it continues out of "out".
 */
public final class LandingsNode implements GraphNode {

    public static final String LANDED = "landed";
    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, LANDED);
    /** Feet this close to the ground count as on it (from the body's centre: half a player + a bit), as for leap. */
    private static final double LAND_DISTANCE = 1.15;
    /** In the air at least this long: a step down a block isn't a landing. */
    private static final int MIN_AIR_TICKS = 3;

    private final int ticks;
    private final String store;

    public LandingsNode(int ticks, String store) {
        this.ticks = Math.max(1, ticks);
        this.store = store;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        new Watch(ctx, ctx.suspend()).start();
        return NodeResult.SUSPENDED;
    }

    private final class Watch {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private TaskHandle task;
        private int elapsed;
        private int airborne;
        private boolean over;

        Watch(ExecutionContext ctx, Resumer resumer) {
            this.ctx = ctx;
            this.resumer = resumer;
            ctx.instance().onEnd(() -> {
                over = true;
                if (task != null) task.cancel();
            });
        }

        void start() {
            task = ctx.engine().scheduler().every(1, 1, this::tick);
        }

        private void tick() {
            if (over) return;
            if (++elapsed > ticks) {
                over = true;
                task.cancel();
                resumer.resume(Ports.OUT);
                return;
            }
            var at = ctx.engine().world().positionOf(new EntityTarget(ctx.caster()));
            if (at.isEmpty()) return;
            var ground = ctx.engine().world().groundBelow(at.get().world(), at.get().position(), LAND_DISTANCE);
            if (ground.isEmpty()) {
                airborne++;
                return;
            }
            if (airborne >= MIN_AIR_TICKS) {
                ExecutionContext branch = ctx.fork();
                if (store != null) branch.blackboard().putRaw(store, new PointTarget(at.get().world(), ground.get()));
                branch.suspend().resume(LANDED);
            }
            airborne = 0;
        }
    }
}
