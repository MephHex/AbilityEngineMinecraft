package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.KeyQuery;

/**
 * Set off the caster's own constructs from {@code ability} within {@code radius} blocks of {@code center} (a key,
 * default "hit"): they exit "triggered", as if someone had walked into them (armed or not, solid or not). E.g. a
 * seed bursting on an enemy sets off the traps near them. Always continues out of "out".
 */
public final class SetOffConstructsNode implements GraphNode {

    private final String ability;
    private final String centerKey;
    private final double radius;

    public SetOffConstructsNode(String ability, String centerKey, double radius) {
        this.ability = ability;
        this.centerKey = centerKey;
        this.radius = radius;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var center = KeyQuery.read(ctx, centerKey).flatMap(ctx.engine().world()::positionOf);
        center.ifPresent(at -> ctx.engine().constructs().setOff(ctx.caster(), ability, at.world(), at.position(), radius));
        return NodeResult.NEXT;
    }
}
