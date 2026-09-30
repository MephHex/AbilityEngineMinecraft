package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Pull the caster and {@code target} into a veil for {@code duration} ticks (see Veils): they can only
 * affect, and see, each other. Exits "won" as soon as the target dies (or is gone), or "out" when the time
 * is up. However the cast ends (the caster dies too), the veil lifts. Shows the time as a boss bar.
 */
public final class VeilNode implements GraphNode {

    public static final String WON = "won";
    private static final Set<String> OUTPUTS = Set.of(WON, Ports.OUT);

    private final String targetKey;
    private final int duration;

    public VeilNode(String targetKey, int duration) {
        this.targetKey = targetKey;
        this.duration = Math.max(1, duration);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var target = KeyQuery.read(ctx, targetKey).filter(t -> t instanceof EntityTarget).map(t -> (EntityTarget) t);
        if (target.isEmpty() || !ctx.engine().world().isAlive(target.get().id())) return NodeResult.out(Ports.OUT);
        var veils = ctx.engine().veils();
        veils.enter(ctx.caster(), target.get().id());
        Resumer resumer = ctx.suspend();
        var timer = ctx.instance().showTimer(duration);
        boolean[] done = {false};
        TaskHandle[] task = new TaskHandle[1];
        Runnable lift = () -> {
            done[0] = true;
            if (task[0] != null) task[0].cancel();
            ctx.instance().clearTimer(timer);
            veils.leave(ctx.caster());
        };
        int[] ticks = {0};
        task[0] = ctx.engine().scheduler().every(1, 1, () -> {
            if (done[0]) return;
            boolean targetDown = !ctx.engine().world().isAlive(target.get().id());
            if (targetDown || ++ticks[0] >= duration) {
                lift.run();
                resumer.resume(targetDown ? WON : Ports.OUT);
            }
        });
        ctx.instance().onEnd(() -> {
            if (!done[0]) lift.run();
        });
        return NodeResult.SUSPENDED;
    }
}
