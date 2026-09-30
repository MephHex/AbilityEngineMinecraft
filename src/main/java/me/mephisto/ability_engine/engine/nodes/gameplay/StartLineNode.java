package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.projectile.ProjectileHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Optional;

/**
 * Keep drawing a line cue from the caster to something while the cast lasts, every {@code every} ticks
 * (e.g. a chain to a thrown hook). {@code to} is a blackboard key: a target (an entity or a spot), or a
 * projectile stored by {@code projectile: store:} (the line follows it in flight and stops when it lands).
 * Continues at once; cosmetic only.
 */
public final class StartLineNode implements GraphNode {

    private final String cue;
    private final String toKey;
    private final int every;

    public StartLineNode(String cue, String toKey, int every) {
        this.cue = cue;
        this.toKey = toKey;
        this.every = Math.max(1, every);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        TaskHandle[] task = new TaskHandle[1];
        task[0] = ctx.engine().scheduler().every(0, every, () -> {
            if (!ctx.instance().isActive()) {
                task[0].cancel();
                return;
            }
            var from = ctx.engine().world().positionOf(new EntityTarget(ctx.caster()));
            if (from.isEmpty()) return;
            Object raw = ctx.blackboard().raw(toKey);
            Optional<Vec3> to;
            if (raw instanceof ProjectileHandle projectile) {
                if (!projectile.isAlive()) {
                    task[0].cancel(); // it landed: whatever comes next draws its own line
                    return;
                }
                to = Optional.of(projectile.position());
            } else {
                to = KeyQuery.read(ctx, toKey).flatMap(ctx.engine().world()::positionOf).map(p -> p.position());
            }
            to.ifPresent(p -> ctx.engine().cuesFor(ctx.caster()).playLine(cue, from.get().world(), from.get().position(), p));
        });
        ctx.instance().onEnd(() -> task[0].cancel());
        return NodeResult.NEXT;
    }
}
