package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.projectile.ProjectileHandle;

import java.util.Set;

/**
 * Opens a recast window: pressing the ability's key again (a fresh press) within {@code window}
 * ticks exits "recast" instead of starting a new cast. Otherwise exits "timeout".
 * With {@code while: <key>}, the window also closes as soon as the projectile stored under that
 * key has hit or expired, so a late press doesn't get swallowed by a dead orb.
 */
public final class AwaitRecastNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.RECAST, Ports.TIMEOUT);

    private final int window;
    private final String whileKey;

    public AwaitRecastNode(int window, String whileKey) {
        this.window = Math.max(1, window);
        this.whileKey = whileKey;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        new Waiter(ctx, ctx.suspend()).start();
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    private final class Waiter {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final AbilityInstance instance;
        private final Runnable handler = this::onRecast;
        private TaskHandle task;
        private int waited;
        private boolean done;

        Waiter(ExecutionContext ctx, Resumer resumer) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.instance = ctx.instance();
        }

        void start() {
            instance.setRecastHandler(handler);
            task = ctx.engine().scheduler().every(1, 1, this::tick);
            instance.onEnd(() -> {
                done = true;
                task.cancel();
            });
        }

        private void tick() {
            if (done) return;
            // Pressed before the window opened: replay that press now, as a real one (same checks).
            if (instance.takeBufferedRecast()) {
                ctx.engine().activator().activate(instance.caster(), instance.ability(), true);
                if (done) return;
            }
            waited++;
            boolean anchorGone = whileKey != null
                    && !(ctx.blackboard().raw(whileKey) instanceof ProjectileHandle h && h.isAlive());
            if (anchorGone || waited >= window) finish(Ports.TIMEOUT);
        }

        private void onRecast() { finish(Ports.RECAST); }

        private void finish(String port) {
            if (done) return;
            done = true;
            task.cancel();
            instance.clearRecastHandler(handler);
            // Recast used or window over: now the cooldown runs (a manual one still waits for its start_cooldown).
            if (!instance.ability().manualCooldown()) instance.startDeferredCooldown();
            resumer.resume(port);
        }
    }
}
