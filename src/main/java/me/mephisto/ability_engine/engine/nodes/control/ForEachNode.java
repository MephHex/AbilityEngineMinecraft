package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.Target;
import me.mephisto.ability_engine.engine.target.TargetQuery;

import java.util.List;
import java.util.Set;

/**
 * Something for each of several enemies at once: runs {@code query} and, for each enemy it finds (nearest first, at
 * most {@code max}; allies, the untargetable and anyone across a veil skipped), starts "each" as its own branch (like a
 * fork) with that enemy stored as {@code store}. Then "out" continues, or "none" if there was nobody. E.g. a tether to
 * up to 3 enemies around the caster, each of which can break free on their own.
 */
public final class ForEachNode implements GraphNode {

    public static final String EACH = "each";
    public static final String NONE = "none";
    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, EACH, NONE);

    private final TargetQuery query;
    private final int max;
    private final String store;

    public ForEachNode(TargetQuery query, int max, String store) {
        this.query = query;
        this.max = max;
        this.store = store;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        List<Target> found = query.find(ctx).stream()
                .filter(t -> t instanceof EntityTarget e && isEnemy(ctx, e))
                .limit(max <= 0 ? Long.MAX_VALUE : max)
                .toList();
        if (found.isEmpty()) return NodeResult.out(NONE);
        for (Target t : found) {
            ExecutionContext branch = ctx.fork();
            branch.blackboard().putRaw(store, t);
            branch.suspend().resume(EACH);
        }
        return NodeResult.out(Ports.OUT);
    }

    private static boolean isEnemy(ExecutionContext ctx, EntityTarget e) {
        if (e.id().equals(ctx.caster())) return false;
        if (ctx.engine().tags().has(e.id(), Tags.UNTARGETABLE)) return false;
        if (ctx.engine().veils().blocks(ctx.caster(), e.id())) return false;
        return !ctx.engine().teams().allies(ctx.caster(), e.id());
    }
}
