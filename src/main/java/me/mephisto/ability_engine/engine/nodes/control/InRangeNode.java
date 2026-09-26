package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.Optional;
import java.util.Set;

/**
 * Is {@code target} (a key, default the caster) within {@code radius} of {@code center} (a key)?
 * Exits "inside" or "outside" ("outside" too if either is gone). E.g. "was I caught in my own explosion?".
 */
public final class InRangeNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.INSIDE, Ports.OUTSIDE);

    private final String centerKey;
    private final String targetKey; // null = caster
    private final double radius;

    public InRangeNode(String centerKey, String targetKey, double radius) {
        this.centerKey = centerKey;
        this.targetKey = targetKey;
        this.radius = radius;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        Optional<Target> target = targetKey == null ? Optional.of(new EntityTarget(ctx.caster())) : KeyQuery.read(ctx, targetKey);
        var center = KeyQuery.read(ctx, centerKey).flatMap(world::positionOf);
        var at = target.flatMap(world::positionOf);
        boolean inside = center.isPresent() && at.isPresent()
                && center.get().world().equals(at.get().world())
                && center.get().position().distance(at.get().position()) <= radius;
        return NodeResult.out(inside ? Ports.INSIDE : Ports.OUTSIDE);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
