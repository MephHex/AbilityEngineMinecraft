package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Is the caster WALKING toward {@code target} (a key) right now, within {@code angle} degrees (default
 * 45)? Exits "toward" or "away" (also when standing still). E.g. a speed boost while chasing a mark.
 */
public final class MovingTowardNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.TOWARD, Ports.AWAY);

    private final String targetKey;
    private final double maxAngleRad;

    public MovingTowardNode(String targetKey, double angleDegrees) {
        this.targetKey = targetKey;
        this.maxAngleRad = Math.toRadians(angleDegrees);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        var moving = world.movementOf(ctx.caster());
        var me = world.positionOf(new EntityTarget(ctx.caster()));
        var them = KeyQuery.read(ctx, targetKey).flatMap(world::positionOf);
        if (moving.isEmpty() || me.isEmpty() || them.isEmpty()) return NodeResult.out(Ports.AWAY);
        Vec3 to = them.get().position().subtract(me.get().position());
        Vec3 flat = new Vec3(to.x(), 0, to.z());
        Vec3 walk = new Vec3(moving.get().x(), 0, moving.get().z());
        if (flat.isZero() || walk.isZero()) return NodeResult.out(Ports.AWAY);
        return NodeResult.out(walk.angleTo(flat) <= maxAngleRad ? Ports.TOWARD : Ports.AWAY);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
