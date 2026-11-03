package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.graph.LocalTime;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

/**
 * Wait, then continue out of "out". Uses the main-thread scheduler (was a raw Thread).
 * With {@code cast_bar: true} the caster sees the wait as a filling cast bar (wind-ups, channels);
 * with {@code boss_bar: true} as a boss bar with the ability's name running out (an ultimate's duration).
 */
public final class DelayNode implements GraphNode {

    private final int ticks;
    private final boolean castBar;
    private final boolean bossBar;
    private String countOf;
    private String countLabel = "";

    /** With boss_bar: also show the caster's stacks of {@code status} on it, then {@code label} (e.g. "geometries"). */
    public DelayNode counting(String status, String label) {
        this.countOf = status;
        this.countLabel = label == null ? "" : label;
        return this;
    }

    public DelayNode(int ticks) {
        this(ticks, false);
    }

    public DelayNode(int ticks, boolean castBar) {
        this(ticks, castBar, false);
    }

    public DelayNode(int ticks, boolean castBar, boolean bossBar) {
        this.ticks = Math.max(1, ticks);
        this.castBar = castBar;
        this.bossBar = bossBar;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Resumer resumer = ctx.suspend();
        Wait wait = new Wait(ctx, resumer);
        wait.bar = castBar ? ctx.instance().showProgress(ticks) : null;
        wait.timer = bossBar ? ctx.instance().showTimer(ticks) : null;
        if (wait.timer != null && countOf != null) {
            var statuses = ctx.engine().statuses();
            java.util.UUID caster = ctx.caster();
            ctx.instance().setTimerNote(() -> {
                int n = statuses.find(caster, countOf).map(st -> st.stacks()).orElse(0);
                return n + " " + countLabel;
            });
        }
        wait.start();
        return NodeResult.SUSPENDED;
    }

    /**
     * The wait runs on the caster's own time (time_scale): while it runs normally it's a plain timer; once their time
     * runs differently (a time slow lands, even midway) it ends when their own time has passed {@code ticks}, the
     * bars following it.
     */
    private final class Wait {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final LocalTime local;
        AbilityInstance.CastProgress bar, timer;
        private TaskHandle plain, watch;
        private boolean bent, done;

        Wait(ExecutionContext ctx, Resumer resumer) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.local = new LocalTime(ctx.engine(), ctx.caster());
        }

        void start() {
            plain = ctx.engine().scheduler().after(ticks, this::finish);
            watch = ctx.engine().scheduler().every(1, 1, this::check);
            ctx.instance().onEnd(this::cancel);
        }

        private void check() {
            if (done) return;
            if (!bent && !local.bent()) return; // still a plain timer
            if (!bent) {
                bent = true;
                plain.cancel();
            }
            if (local.elapsed() >= ticks - 1e-9) {
                finish();
                return;
            }
            AbilityInstance instance = ctx.instance();
            if (bar != null) {
                instance.clearProgress(bar);
                bar = instance.showProgress(local.barStart(), ticks);
            }
            if (timer != null) {
                instance.clearTimer(timer);
                timer = instance.showTimer(local.barStart(), ticks);
            }
        }

        private void cancel() {
            done = true;
            if (plain != null) plain.cancel();
            if (watch != null) watch.cancel();
        }

        private void finish() {
            if (done) return;
            cancel();
            if (bar != null) ctx.instance().clearProgress(bar);
            if (timer != null) ctx.instance().clearTimer(timer);
            resumer.resume(Ports.OUT);
        }
    }
}
