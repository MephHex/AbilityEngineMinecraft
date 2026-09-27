package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Wait (for as long as the cast runs) until the caster gets a kill, then exit "kill" with the victim
 * stored under {@code store}. {@code players_only: true} ignores mobs. Waits for ONE kill: chain two to
 * reward up to two, and run it in a fork next to the rest of the cast.
 */
public final class AwaitKillNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.KILL);

    private final boolean playersOnly;
    private final String store;

    public AwaitKillNode(boolean playersOnly, String store) {
        this.playersOnly = playersOnly;
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var resumer = ctx.suspend();
        AbilityInstance instance = ctx.instance();
        BiConsumer<UUID, Boolean>[] handler = new BiConsumer[1];
        handler[0] = (victim, isPlayer) -> {
            if (playersOnly && !isPlayer) {
                instance.setKillHandler(handler[0]); // not this one: keep waiting
                return;
            }
            ctx.put(me.mephisto.ability_engine.engine.graph.Keys.target(store), new EntityTarget(victim));
            resumer.resume(Ports.KILL);
        };
        instance.setKillHandler(handler[0]);
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
