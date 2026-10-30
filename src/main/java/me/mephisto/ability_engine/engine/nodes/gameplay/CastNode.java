package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

/**
 * The caster casts another ability of theirs right now, as if they'd pressed its key (its own checks: cooldown,
 * blocked_by...). E.g. a channel that, full, fires the same volley its LMB can fire early. Always continues out of "out".
 * <p>{@code free: true}: none of its checks, costs or cooldown: it just goes, as a cast from whichever slot of theirs it
 * sits in (a basic attack is still a basic attack: on-hit effects, ultimate charge). E.g. a barrage of basic attacks
 * while the caster can't use them by themselves.
 */
public final class CastNode implements GraphNode {

    private final String ability;
    private final boolean free;

    public CastNode(String ability) {
        this(ability, false);
    }

    public CastNode(String ability, boolean free) {
        this.ability = ability;
        this.free = free;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        if (free) ctx.engine().activator().castFree(ctx.caster(), ability);
        else ctx.engine().activator().activate(ctx.caster(), ability);
        return NodeResult.NEXT;
    }
}
