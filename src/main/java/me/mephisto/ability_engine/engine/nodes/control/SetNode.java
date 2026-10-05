package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * Write a string to the blackboard, e.g. {@code key: phase, value: empowered}, then read it with
 * a switch node. Branches forked earlier still see it (they read up through their parent scope)
 * unless they wrote the same key themselves.
 * <p>{@code value: stacks:<status>} writes the caster's stacks of that status right now (a number, 0 without it) instead:
 * e.g. how many shards a charge gathered, kept for when its volley lands (a projectile's {@code count_bonus}).
 */
public final class SetNode implements GraphNode {

    private static final String STACKS = "stacks:";

    private final String key;
    private final String value;

    public SetNode(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        if (value.startsWith(STACKS)) {
            int stacks = ctx.engine().statuses().find(ctx.caster(), value.substring(STACKS.length())).map(s -> s.stacks()).orElse(0);
            ctx.blackboard().putRaw(key, stacks);
        } else {
            ctx.blackboard().putRaw(key, value);
        }
        return NodeResult.NEXT;
    }
}
