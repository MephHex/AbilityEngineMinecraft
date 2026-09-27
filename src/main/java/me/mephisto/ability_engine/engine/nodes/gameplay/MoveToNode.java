package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.UUID;

/**
 * Put the caster at {@code to} (a key; default "caster": where they are) raised by {@code up} blocks and
 * moved {@code back} blocks behind (opposite the way they face; negative = ahead), for a view from the
 * side. {@code look: down} turns their view straight at the ground below; {@code look: spot} at the spot
 * they were lifted from. {@code store} keeps the spot they were at before. {@code return: true} puts them
 * back there when the cast ends, however it ends (finished, cancelled, a character change).
 */
public final class MoveToNode implements GraphNode {

    /** Looking down, tipped a little forward so the view keeps its heading (about 80 degrees down). */
    private static final double DOWN_TILT = 0.18;

    private final String toKey;
    private final double up;
    public enum Look { NONE, DOWN, SPOT }

    private final Look look;
    private final double back;
    private final String store;
    private final boolean returnAfter;

    public MoveToNode(String toKey, double up, double back, Look look, String store, boolean returnAfter) {
        this.toKey = toKey;
        this.up = up;
        this.back = back;
        this.look = look;
        this.store = store;
        this.returnAfter = returnAfter;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var engine = ctx.engine();
        UUID caster = ctx.caster();
        var from = engine.world().positionOf(new EntityTarget(caster));
        var to = KeyQuery.read(ctx, toKey).flatMap(engine.world()::positionOf);
        if (from.isEmpty() || to.isEmpty()) return NodeResult.NEXT;
        PointTarget start = from.get();
        if (store != null) ctx.blackboard().putRaw(store, start);

        Vec3 heading = engine.world().aimOf(caster).map(a -> new Vec3(a.direction().x(), 0, a.direction().z()))
                .filter(v -> !v.isZero()).map(Vec3::normalize).orElse(new Vec3(1, 0, 0));
        Vec3 destination = to.get().position().add(0, up, 0).subtract(heading.multiply(back));
        Vec3 view = switch (look) {
            case NONE -> null;
            case DOWN -> new Vec3(0, -1, 0).add(heading.multiply(DOWN_TILT));
            case SPOT -> {
                Vec3 d = start.position().subtract(destination);
                yield d.isZero() ? new Vec3(0, -1, 0).add(heading.multiply(DOWN_TILT)) : d;
            }
        };
        engine.movement().teleport(caster, destination, view);
        if (returnAfter) {
            ctx.instance().onEnd(() -> {
                if (engine.world().isAlive(caster)) engine.movement().teleport(caster, start.position());
            });
        }
        return NodeResult.NEXT;
    }
}
