package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

import java.util.Set;

/**
 * Do something several times: runs "each" as its own branch (like a fork) {@code times} times,
 * {@code every} ticks apart, then continues out of "out".
 * <ul>
 *   <li>{@code times}: a number, or a blackboard key holding one (e.g. a charge's power) multiplied by
 *       {@code scale} and rounded up; at least 1</li>
 *   <li>{@code spend: <resource>}: each time costs 1 of it; when it runs out, it stops early (a volley
 *       that empties the gun)</li>
 * </ul>
 * Each "each" branch gets {@code repeat_index}: 1 for the first time, 2 for the second... (e.g. a switch
 * that makes the first bullet of a volley different).
 */
public final class RepeatNode implements GraphNode {

    public static final String EACH = "each";
    public static final String INDEX_KEY = "repeat_index";
    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, EACH);

    private final Integer fixedTimes;
    private final String timesKey;
    private final double scale;
    private final int every;
    private final String spend;

    public RepeatNode(Integer fixedTimes, String timesKey, double scale, int every, String spend) {
        this.fixedTimes = fixedTimes;
        this.timesKey = timesKey;
        this.scale = scale;
        this.every = Math.max(0, every);
        this.spend = spend;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    private int times(ExecutionContext ctx) {
        if (fixedTimes != null) return Math.max(0, fixedTimes);
        Object raw = ctx.blackboard().raw(timesKey);
        double value = raw instanceof Number n ? n.doubleValue() : 0;
        return Math.max(1, (int) Math.ceil(value * scale - 1e-9));
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        new Loop(ctx, ctx.suspend(), times(ctx)).next();
        return NodeResult.SUSPENDED;
    }

    private final class Loop {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final int total;
        private int done;
        private TaskHandle task;
        private boolean over;

        Loop(ExecutionContext ctx, Resumer resumer, int total) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.total = total;
            ctx.instance().onEnd(() -> {
                over = true;
                if (task != null) task.cancel();
            });
        }

        void next() {
            while (!over) {
                if (done >= total || (spend != null && !ctx.engine().resources().has(ctx.caster(), spend, 1))) {
                    over = true;
                    resumer.resume(Ports.OUT);
                    return;
                }
                if (spend != null) ctx.engine().resources().consume(ctx.caster(), spend, 1);
                done++;
                ExecutionContext branch = ctx.fork();
                branch.blackboard().putRaw(INDEX_KEY, done);
                branch.suspend().resume(EACH);
                if (every > 0) {
                    task = ctx.engine().scheduler().after(every, this::next);
                    return;
                }
            }
        }
    }
}
