package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.projectile.MotionModifier;
import me.mephisto.ability_engine.engine.projectile.ProjectileHandle;
import me.mephisto.ability_engine.engine.target.CursorQuery;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turn a stored, still-flying projectile toward a point. Exits "out", or "gone" if it already
 * hit/expired.
 * <ul>
 *   <li>{@code toward: cursor} — whatever the caster's crosshair is on right now (entity or block,
 *       up to {@code range}), not just the look direction, so the projectile really converges on it</li>
 *   <li>{@code toward: <key>} — a blackboard target, e.g. "lock"</li>
 * </ul>
 * {@code speed} sets the new speed (omit to keep current); {@code motion} replaces its motion
 * modifiers (omit to keep them).
 */
public final class RedirectProjectileNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.GONE);

    private final String projectileKey;
    private final String toward;
    private final double range;
    private final Double speed;
    private final List<MotionModifier> motion;

    public RedirectProjectileNode(String projectileKey, String toward, double range, Double speed, List<MotionModifier> motion) {
        this.projectileKey = projectileKey;
        this.toward = toward;
        this.range = range;
        this.speed = speed;
        this.motion = motion == null ? null : List.copyOf(motion);
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        if (!(ctx.blackboard().raw(projectileKey) instanceof ProjectileHandle handle) || !handle.isAlive()) {
            return NodeResult.out(Ports.GONE);
        }
        Optional<Vec3> point = "cursor".equals(toward) ? cursor(ctx) : keyPoint(ctx);
        if (point.isEmpty()) return NodeResult.NEXT;

        Vec3 dir = point.get().subtract(handle.position()).normalize();
        if (dir.isZero()) dir = handle.velocity().normalize();
        double newSpeed = speed != null ? speed : handle.velocity().length();
        handle.redirect(dir.multiply(newSpeed));
        if (motion != null) handle.setMotion(motion);
        return NodeResult.NEXT;
    }

    private Optional<Vec3> cursor(ExecutionContext ctx) {
        return CursorQuery.point(ctx, range).map(p -> p.position());
    }

    private Optional<Vec3> keyPoint(ExecutionContext ctx) {
        return KeyQuery.read(ctx, toward).flatMap(ctx.engine().world()::positionOf).map(p -> p.position());
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
