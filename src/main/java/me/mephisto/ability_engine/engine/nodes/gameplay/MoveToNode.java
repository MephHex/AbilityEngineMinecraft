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
 * Put the caster at {@code to} (a key; default "caster": where they are) raised by {@code up} blocks.
 * {@code look: down} turns their view toward the ground below (a view from above). {@code store} keeps
 * the spot they were at before. {@code return: true} puts them back there when the cast ends, however
 * it ends (finished, cancelled, a character change).
 */
public final class MoveToNode implements GraphNode {

    /** Looking down, tipped a little forward so the view keeps its heading (about 80 degrees down). */
    private static final double DOWN_TILT = 0.18;

    private final String toKey;
    private final double up;
    private final boolean lookDown;
    private final String store;
    private final boolean returnAfter;

    public MoveToNode(String toKey, double up, boolean lookDown, String store, boolean returnAfter) {
        this.toKey = toKey;
        this.up = up;
        this.lookDown = lookDown;
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

        Vec3 look = null;
        if (lookDown) {
            Vec3 heading = engine.world().aimOf(caster).map(a -> new Vec3(a.direction().x(), 0, a.direction().z()))
                    .filter(v -> !v.isZero()).map(Vec3::normalize).orElse(new Vec3(1, 0, 0));
            look = new Vec3(0, -1, 0).add(heading.multiply(DOWN_TILT));
        }
        engine.movement().teleport(caster, to.get().position().add(0, up, 0), look);
        if (returnAfter) {
            ctx.instance().onEnd(() -> {
                if (engine.world().isAlive(caster)) engine.movement().teleport(caster, start.position());
            });
        }
        return NodeResult.NEXT;
    }
}
