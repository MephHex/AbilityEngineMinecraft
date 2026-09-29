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
 * {@code instances().release(caster)}). Full after {@code ticks}; with {@code fire_when_full: true}
 * (the default) it then goes by itself, with {@code false} it stays full until let go. Stores the power
 * under {@code store}: {@code from} (let go at once) up to 1.0 (full), e.g. for a damage effect's
 * {@code scale_by}. Let go before {@code min} ticks: exits "early" instead (nothing fired). The charge
 * fills the cast bar.
 * <p>With {@code release_gap: N} it doesn't wait for the platform to report the let-go: a held input
 * repeats (the activator passes each repeat on), and N ticks without a repeat means it was let go. The
 * time held is then counted up to the last repeat, so a quick click counts as 0 ticks. A fresh press of
 * the same input while it charges also lets it go (the activator then starts a new cast).
 * <p>With {@code load: { resource, every, max, start }} it loads a resource instead of charging power:
 * {@code start} of it on the press itself, then every {@code every} ticks held 1 more (up to {@code max}, or until it runs out), so the player sees
 * it go down, and {@code store} gets how many were loaded. Let go with none loaded: "early". While any
 * are loaded the resource doesn't start reloading (it's still in use).
 */
public final class ChargeNode implements GraphNode {

    private final int ticks;
    private final double from;
    private final String store;
    private final int min;
    private final boolean fireWhenFull;
    private final int releaseGap;
    private final Load load;

    /** Loading a resource while held (see the class comment); {@code start} are loaded on the press itself. */
    public record Load(String resource, int every, int max, int start) {
        public Load(String resource, int every, int max) { this(resource, every, max, 0); }
    }

    private static final java.util.Set<String> OUTPUTS = java.util.Set.of(Ports.OUT, Ports.EARLY);

    public ChargeNode(int ticks, double from, String store) {
        this(ticks, from, store, 0, true);
    }

    public ChargeNode(int ticks, double from, String store, int min, boolean fireWhenFull) {
        this(ticks, from, store, min, fireWhenFull, 0);
    }

    public ChargeNode(int ticks, double from, String store, int min, boolean fireWhenFull, int releaseGap) {
        this(ticks, from, store, min, fireWhenFull, releaseGap, null);
    }

    public ChargeNode(int ticks, double from, String store, int min, boolean fireWhenFull, int releaseGap, Load load) {
        this.load = load;
        this.releaseGap = Math.max(0, releaseGap);
        this.ticks = Math.max(1, ticks);
        this.from = Math.max(0, Math.min(1, from));
        this.store = store;
        this.min = Math.max(0, min);
        this.fireWhenFull = fireWhenFull;
    }

    @Override
    public java.util.Set<String> outputs() { return OUTPUTS; }

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
        private TaskHandle watcher;
        private final Runnable onRepeat = this::repeated;
        private long lastInput;
        private int loaded;
        private boolean done;

        Charge(ExecutionContext ctx, Resumer resumer) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.instance = ctx.instance();
            this.startedAt = ctx.engine().clock().now();
            this.lastInput = startedAt;
        }

        void start() {
            instance.setReleaseHandler(handler);
            bar = instance.showProgress(ticks);
            // Fully charged: fires by itself, or (fire_when_full: false) just stays full until let go.
            if (fireWhenFull) task = ctx.engine().scheduler().after(ticks, this::fire);
            if (releaseGap > 0) instance.setInputRepeat(onRepeat);
            loadUpTo(startedAt); // what the press itself loads
            if (releaseGap > 0 || load != null) {
                watcher = ctx.engine().scheduler().every(1, 1, () -> {
                    if (releaseGap > 0 && ctx.engine().clock().now() - lastInput > releaseGap) {
                        fire();
                        return;
                    }
                    loadUpTo(heldUntil());
                });
            }
            instance.onEnd(() -> {
                done = true;
                if (task != null) task.cancel();
                if (watcher != null) watcher.cancel();
            });
        }

        private void repeated() {
            lastInput = ctx.engine().clock().now();
            loadUpTo(lastInput);
        }

        /** Held until when, as far as we know: the last repeat, or (the platform reports the let-go) now. */
        private long heldUntil() { return releaseGap > 0 ? lastInput : ctx.engine().clock().now(); }

        /** Loading: spend what the time held so far has loaded. */
        private void loadUpTo(long heldUntil) {
            if (load == null || done) return;
            var res = ctx.engine().resources();
            int want = (int) Math.min(load.max(), load.start() + (heldUntil - startedAt) / load.every());
            while (loaded < want && res.has(ctx.caster(), load.resource(), 1)) {
                res.consume(ctx.caster(), load.resource(), 1);
                loaded++;
            }
            if (loaded > 0) res.holdReload(ctx.caster(), load.resource()); // still in use: no reload yet
        }

        private void fire() {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            if (watcher != null) watcher.cancel();
            instance.clearReleaseHandler(handler);
            instance.clearInputRepeat(onRepeat);
            instance.clearProgress(bar);
            long now = ctx.engine().clock().now();
            if (load != null) {
                loadUpTo(heldUntil());
                ctx.blackboard().putRaw(store, (double) loaded);
                resumer.resume(loaded > 0 ? Ports.OUT : Ports.EARLY);
                return;
            }
            // Watching repeats: held until the last one (the gap after it is just us noticing the let-go).
            long heldTicks = releaseGap > 0 ? Math.min(now, lastInput) - startedAt : now - startedAt;
            if (heldTicks < min) {
                resumer.resume(Ports.EARLY); // let go too soon: not charged enough to fire
                return;
            }
            double held = Math.min(1, heldTicks / (double) ticks);
            ctx.blackboard().putRaw(store, from + (1 - from) * held);
            resumer.resume(Ports.OUT);
        }
    }
}
