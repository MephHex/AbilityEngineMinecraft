package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

import java.util.Set;

/**
 * Block one spell: for {@code duration} ticks, the first enemy spell cast that reaches the caster (its
 * damage and its debuffs; see SpellShields.raiseBlock) doesn't land. Exits "blocked" the tick after,
 * or "expired" if nothing came. Basic attacks and damage over time pass as usual.
 */
public final class SpellBlockNode implements GraphNode {

    public static final String BLOCKED = "blocked";
    private static final Set<String> OUTPUTS = Set.of(BLOCKED, Ports.EXPIRED);

    private final int duration;

    public SpellBlockNode(int duration) {
        this.duration = Math.max(1, duration);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        new Watch(ctx, ctx.suspend()).start();
        return NodeResult.SUSPENDED;
    }

    private final class Watch {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private TaskHandle timer;
        private boolean done;

        Watch(ExecutionContext ctx, Resumer resumer) {
            this.ctx = ctx;
            this.resumer = resumer;
        }

        void start() {
            var shields = ctx.engine().spellShields();
            // Resume a tick later: the rest of the blocked hit (same cast, same tick) is still blocked meanwhile.
            shields.raiseBlock(ctx.caster(), () -> ctx.engine().scheduler().after(1, () -> finish(BLOCKED)));
            timer = ctx.engine().scheduler().after(duration, () -> {
                if (shields.hasBlock(ctx.caster())) finish(Ports.EXPIRED); // (a block in progress wins)
            });
            ctx.instance().onEnd(() -> {
                done = true;
                timer.cancel();
                shields.lowerBlock(ctx.caster());
            });
        }

        private void finish(String port) {
            if (done) return;
            done = true;
            timer.cancel();
            ctx.engine().spellShields().lowerBlock(ctx.caster());
            resumer.resume(port);
        }
    }
}
