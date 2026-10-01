package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Start this ability's cooldown now. With {@code cooldown_starts: manual}, a cast that never reaches one
 * costs no cooldown (e.g. only a successful bond counts). Does nothing if the cooldown already started,
 * unless {@code restart: true}: then the full cooldown starts over from now (e.g. every time a fae perches,
 * not only the first).
 */
public final class StartCooldownNode implements GraphNode {

    private final boolean restart;

    public StartCooldownNode() {
        this(false);
    }

    public StartCooldownNode(boolean restart) {
        this.restart = restart;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var instance = ctx.instance();
        instance.startDeferredCooldown();
        if (restart) {
            String id = instance.ability().id();
            var engine = ctx.engine();
            engine.cooldowns().start(ctx.caster(), id, engine.stats().cooldownTicks(ctx.caster(), id, instance.ability().cooldownTicks()));
        }
        return NodeResult.NEXT;
    }
}
