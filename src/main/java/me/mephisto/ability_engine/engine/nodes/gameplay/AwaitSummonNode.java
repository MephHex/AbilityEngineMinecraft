package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

import java.util.Set;
import java.util.UUID;

/**
 * Wait (while the cast runs) for the caster's summon {@code summon} to end: "destroyed" if it was killed
 * (a vulnerable clone, e.g. a soul; it's then cleared away), "gone" if it expired or was dismissed.
 * No such summon: "gone" at once.
 */
public final class AwaitSummonNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.DESTROYED, Ports.GONE);

    private final String name;

    public AwaitSummonNode(String name) {
        this.name = name;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var summons = ctx.engine().summons();
        var found = summons.find(ctx.caster(), name);
        if (found.isEmpty()) return NodeResult.out(Ports.GONE);
        UUID entity = found.get();
        Resumer resumer = ctx.suspend();
        TaskHandle[] task = new TaskHandle[1];
        boolean[] done = new boolean[1];
        task[0] = ctx.engine().scheduler().every(1, 1, () -> {
            if (done[0]) return;
            boolean stillOurs = summons.find(ctx.caster(), name).map(entity::equals).orElse(false);
            String port = !stillOurs ? Ports.GONE : !ctx.engine().world().isAlive(entity) ? Ports.DESTROYED : null;
            if (port == null) return;
            done[0] = true;
            task[0].cancel();
            if (port.equals(Ports.DESTROYED)) summons.dismiss(ctx.caster(), name); // clear the remains
            resumer.resume(port);
        });
        ctx.instance().onEnd(() -> {
            done[0] = true;
            task[0].cancel();
        });
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
