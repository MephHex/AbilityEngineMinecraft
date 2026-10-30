package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.barrier.Screens;
import me.mephisto.ability_engine.engine.effect.EffectConfig;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Put up a screen (see Screens) on the spot in {@code at} (default: aim), facing the way the caster looks right now,
 * {@code width} across and {@code height} tall, pulled a little short of a wall the spot is on. Enemy projectiles that fly
 * through it are held in it for {@code hold} ticks; the caster's and their allies' fly on {@code accelerate} times as fast,
 * and whoever they hit afterwards gets {@code effects} from the caster (e.g. bonus damage). It stays until the cast ends:
 * keep the cast alive with a delay after this node. Its middle is stored as {@code store} (default "screen").
 * {@code distance: 3} instead of {@code at}: straight ahead of the caster that far along their look (no aim needed), or
 * short of the first block in the way.
 */
public final class ScreenNode implements GraphNode {

    private final String at;
    private final String store;
    private final double width;
    private final double height;
    private final int hold;
    private final double accelerate;
    private final List<EffectConfig> effects;
    private final String catchCue;
    private final String boostCue;
    private double distance;

    /** Put it this far ahead along the caster's look instead of at {@code at} (0 = at {@code at}). */
    public ScreenNode ahead(double distance) {
        this.distance = distance;
        return this;
    }

    public ScreenNode(String at, String store, double width, double height, int hold, double accelerate,
                      List<EffectConfig> effects, String catchCue, String boostCue) {
        this.at = at;
        this.store = store;
        this.width = width;
        this.height = height;
        this.hold = hold;
        this.accelerate = accelerate;
        this.effects = List.copyOf(effects);
        this.catchCue = catchCue;
        this.boostCue = boostCue;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var engine = ctx.engine();
        Optional<PointTarget> spot = distance > 0
                ? me.mephisto.ability_engine.engine.target.CursorQuery.point(engine, ctx.caster(), distance + Screens.WALL_GAP, false)
                : KeyQuery.read(ctx, at).flatMap(engine.world()::positionOf);
        var aim = engine.world().aimOf(ctx.caster());
        if (spot.isEmpty() || aim.isEmpty()) return NodeResult.NEXT;
        Vec3 look = aim.get().direction();
        Screens.Frame f = Screens.frame(aim.get().eye(), look, spot.get().position(), Screens.WALL_GAP, null);
        Consumer<Target> bonus = effects.isEmpty() ? null : target -> {
            for (EffectConfig c : effects) {
                engine.effects().require(c.effectId()).apply(new EffectContext(engine, ctx, ctx.caster(), target, c.params()));
            }
        };
        Runnable remove = engine.screens().add(new Screens.Screen(ctx.caster(), spot.get().world(), f.center(), f.normal(),
                f.right(), f.up(), width, height, hold, accelerate, bonus, catchCue, boostCue));
        ctx.blackboard().putRaw(store, new PointTarget(spot.get().world(), f.center()));
        ctx.instance().onEnd(remove);
        return NodeResult.NEXT;
    }
}
