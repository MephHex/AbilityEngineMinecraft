package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.summon.Mirrors;

import java.util.List;

/**
 * The caster's mirroring clones go to their next mode (see Mirrors.Mode): copy -> still -> puppet -> copy. The caster is
 * told the new one: {@code messages} (copy, still, puppet; empty = nothing said). Without a mirroring clone, nothing.
 */
public final class CloneModeNode implements GraphNode {

    private final List<String> messages;

    public CloneModeNode(List<String> messages) {
        this.messages = List.copyOf(messages);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var mirrors = ctx.engine().mirrors();
        if (mirrors.clonesOf(ctx.caster()).isEmpty()) return NodeResult.NEXT;
        Mirrors.Mode mode = mirrors.cycle(ctx.caster());
        if (messages.size() == Mirrors.Mode.values().length) ctx.engine().notify(ctx.caster(), messages.get(mode.ordinal()));
        return NodeResult.NEXT;
    }
}
