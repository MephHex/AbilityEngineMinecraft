package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.projectile.ProjectileHandle;
import me.mephisto.ability_engine.engine.target.CursorQuery;

import java.util.Optional;
import java.util.Set;

/**
 * Turn the caster's latest projectile from {@code ability} toward their crosshair, a little per
 * call ({@code turn} 0..1, like homing), keeping its speed. Marks it as guided, which pauses its
 * range while guidance continues. Exits "out", or "gone" if there is nothing in flight.
 * Meant to run every tick from a hold (see HoldActivation).
 */
public final class SteerProjectileNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.GONE);

    private final String ability;
    private final double turn;
    private final double cursorRange;

    public SteerProjectileNode(String ability, double turn, double cursorRange) {
        this.ability = ability;
        this.turn = turn;
        this.cursorRange = cursorRange;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<ProjectileHandle> found = ctx.engine().projectiles().latest(ctx.caster(), ability);
        if (found.isEmpty()) return NodeResult.out(Ports.GONE);
        ProjectileHandle p = found.get();
        p.markGuided();

        CursorQuery.point(ctx, cursorRange).ifPresent(cursor -> {
            Vec3 desired = cursor.position().subtract(p.position()).normalize();
            if (desired.isZero()) return;
            double speed = p.velocity().length();
            Vec3 dir = p.velocity().normalize().add(desired.multiply(turn)).normalize();
            p.redirect(dir.multiply(speed));
        });
        return NodeResult.NEXT;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}
