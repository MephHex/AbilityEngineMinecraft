package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

/**
 * Charge up while the input is held: continues out of "out" when it's let go (the platform calls
 * {@code instances().release(caster)}) or when it's fully charged after {@code ticks}, whichever
 * comes first. Stores the power under {@code store}: {@code from} (let go at once) up to 1.0 (full),
 * e.g. for a damage effect's {@code scale_by}. The charge fills the cast bar.
 */
public final class ChargeNode implements GraphNode {

    private final int ticks;
    private final double from;
    private final String store;

    public ChargeNode(int ticks, double from, String store) {
        this.ticks = Math.max(1, ticks);
        this.from = Math.max(0, Math.min(1, from));
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        new Charge(ctx, ctx.suspend()).start();
        return NodeResult.SUSPENDED;
    }

    private final class Charge {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final AbilityInstance instance;
        private final Runnable handler = this::fire;
        private final long startedAt;
        private AbilityInstance.CastProgress bar;
        private TaskHandle task;
        private boolean done;

        Charge(ExecutionContext ctx, Resumer resumer) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.instance = ctx.instance();
            this.startedAt = ctx.engine().clock().now();
        }

        void start() {
            instance.setReleaseHandler(handler);
            bar = instance.showProgress(ticks);
            task = ctx.engine().scheduler().after(ticks, this::fire); // fully charged: fires by itself
            instance.onEnd(() -> {
                done = true;
                task.cancel();
            });
        }

        private void fire() {
            if (done) return;
            done = true;
            task.cancel();
            instance.clearReleaseHandler(handler);
            instance.clearProgress(bar);
            double held = Math.min(1, (ctx.engine().clock().now() - startedAt) / (double) ticks);
            ctx.blackboard().putRaw(store, from + (1 - from) * held);
            resumer.resume(Ports.OUT);
        }
    }
}
