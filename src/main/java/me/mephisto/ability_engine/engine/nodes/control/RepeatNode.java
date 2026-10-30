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
 *   <li>{@code attack_speed: true}: {@code every} is divided by the caster's attack speed multiplier (their statuses'
 *       attack_speed, as it is when the repeat starts), e.g. a barrage of basic attacks that comes faster with a burst of
 *       attack speed. Fractions of a tick carry over, so +40% on 4 ticks is 2.86 ticks a time on average</li>
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
    private final boolean byAttackSpeed;

    public RepeatNode(Integer fixedTimes, String timesKey, double scale, int every, String spend) {
        this(fixedTimes, timesKey, scale, every, spend, false);
    }

    public RepeatNode(Integer fixedTimes, String timesKey, double scale, int every, String spend, boolean byAttackSpeed) {
        this.fixedTimes = fixedTimes;
        this.timesKey = timesKey;
        this.scale = scale;
        this.every = Math.max(0, every);
        this.spend = spend;
        this.byAttackSpeed = byAttackSpeed;
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
        double interval = every;
        if (byAttackSpeed && every > 0) {
            double m = ctx.engine().stats().attackSpeedMultiplier(ctx.caster());
            if (m > 0) interval = every / m;
        }
        new Loop(ctx, ctx.suspend(), times(ctx), interval).next();
        return NodeResult.SUSPENDED;
    }

    private final class Loop {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final int total;
        private final double interval;
        private double elapsed; // ticks since the first time, unrounded (fractions carry over)
        private int done;
        private TaskHandle task;
        private boolean over;

        Loop(ExecutionContext ctx, Resumer resumer, int total, double interval) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.total = total;
            this.interval = interval;
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
                if (interval > 0) {
                    long wait = Math.max(1, Math.round(elapsed + interval) - Math.round(elapsed));
                    elapsed += interval;
                    task = ctx.engine().scheduler().after(wait, this::next);
                    return;
                }
            }
        }
    }
}
