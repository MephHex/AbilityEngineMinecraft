package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.construct.ConstructHandle;
import me.mephisto.ability_engine.engine.construct.Strike;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;

import java.util.List;
import java.util.Optional;

/**
 * Hitscan and cone shots don't fly, so they can't touch a construct the way a projectile does. This
 * strikes the caster's OWN constructs inside the shot's shape, as if their projectile hit them (they
 * exit "struck", with {@code struck_by} = this ability), e.g. shooting your bomb sets it off.
 * <ul>
 *   <li>{@code angle}: a cone, that many degrees wide, {@code range} long (like a cone query)</li>
 *   <li>otherwise a line along the aim, {@code width} wide (default 0.6), {@code range} long</li>
 * </ul>
 * A construct counts when its hitbox reaches into the shape. Always continues out of "out".
 */
public final class StrikeConstructsNode implements GraphNode {

    private final double range;
    private final double angleDegrees;
    private final double width;

    public StrikeConstructsNode(double range, double angleDegrees, double width) {
        this.range = range;
        this.angleDegrees = angleDegrees;
        this.width = width;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<Aim> aim = ctx.engine().world().aimOf(ctx.caster());
        if (aim.isEmpty()) return NodeResult.NEXT;
        Aim a = aim.get();
        Object phase = ctx.blackboard().raw("phase");
        Strike strike = new Strike(ctx.caster(), ctx.instance().ability().id(), phase == null ? "none" : phase.toString());
        List<ConstructHandle> mine = ctx.engine().constructs().all().stream()
                .filter(c -> c.owner().equals(ctx.caster()) && c.world().equals(a.world()))
                .filter(c -> inside(a, c.position(), c.size()))
                .toList();
        for (ConstructHandle c : mine) ctx.engine().constructs().strike(c, strike);
        return NodeResult.NEXT;
    }

    private boolean inside(Aim a, Vec3 point, double size) {
        Vec3 to = point.subtract(a.eye());
        double dist = to.length();
        if (dist > range + size) return false;
        if (dist <= size) return true;
        if (angleDegrees > 0) {
            // widen the cone by the hitbox's angular size at that distance
            double slack = Math.asin(Math.min(1, size / dist));
            return a.direction().angleTo(to) <= Math.toRadians(angleDegrees / 2) + slack;
        }
        Vec3 dir = a.direction().normalize();
        double along = to.dot(dir);
        if (along < 0 || along > range + size) return false;
        double off = to.subtract(dir.multiply(along)).length();
        return off <= width / 2 + size;
    }
}
