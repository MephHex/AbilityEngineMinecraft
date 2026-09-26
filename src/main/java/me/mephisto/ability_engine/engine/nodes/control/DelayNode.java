package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

/**
 * Wait, then continue out of "out". Uses the main-thread scheduler (was a raw Thread).
 * With {@code cast_bar: true} the caster sees the wait as a filling cast bar (wind-ups, channels).
 */
public final class DelayNode implements GraphNode {

    private final int ticks;
    private final boolean castBar;

    public DelayNode(int ticks) {
        this(ticks, false);
    }

    public DelayNode(int ticks, boolean castBar) {
        this.ticks = Math.max(1, ticks);
        this.castBar = castBar;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Resumer resumer = ctx.suspend();
        var bar = castBar ? ctx.instance().showProgress(ticks) : null;
        TaskHandle task = ctx.engine().scheduler().after(ticks, () -> {
            if (bar != null) ctx.instance().clearProgress(bar);
            resumer.resume(Ports.OUT);
        });
        ctx.instance().onEnd(task::cancel);
        return NodeResult.SUSPENDED;
    }
}
