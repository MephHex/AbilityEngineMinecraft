package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.barrier.SlowFields;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;

/**
 * Put down a field that slows ENEMY projectiles (see SlowFields) on the spot in {@code at} (default: aim): {@code length}
 * blocks along the way the caster faces it, {@code width} across, {@code height} tall; inside it they move at
 * {@code slow} of their speed. It stays until the cast ends, so keep the cast alive for as long as it should (a delay
 * after this node). Nothing there: it goes on without one.
 */
public final class SlowFieldNode implements GraphNode {

    private final String at;
    private final double length;
    private final double width;
    private final double height;
    private final double slow;

    public SlowFieldNode(String at, double length, double width, double height, double slow) {
        this.at = at;
        this.length = length;
        this.width = width;
        this.height = height;
        this.slow = slow;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        Optional<PointTarget> spot = KeyQuery.read(ctx, at).flatMap(world::positionOf);
        if (spot.isEmpty()) return NodeResult.NEXT;
        Vec3 c = spot.get().position();
        // Its length runs the way the caster looks at it (from them to the spot); right under them: where they look.
        Vec3 forward = world.positionOf(new EntityTarget(ctx.caster()))
                .map(me -> new Vec3(c.x() - me.position().x(), 0, c.z() - me.position().z()))
                .filter(v -> v.lengthSquared() > 0.25)
                .or(() -> world.aimOf(ctx.caster()).map(a -> new Vec3(a.direction().x(), 0, a.direction().z())))
                .filter(v -> !v.isZero())
                .orElse(new Vec3(1, 0, 0))
                .normalize();
        Runnable remove = ctx.engine().slowFields().add(new SlowFields.Field(ctx.caster(), spot.get().world(), c, forward,
                length, width, height, slow));
        ctx.instance().onEnd(remove);
        return NodeResult.NEXT;
    }
}
