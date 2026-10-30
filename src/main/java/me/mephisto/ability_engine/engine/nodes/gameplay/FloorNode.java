package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.floor.Floors;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;

/**
 * Put down a floor of the caster's (see Floors) at {@code at} (a spot or an entity, default: aim), on the ground below it
 * (at most {@code drop} blocks down; none there: right where it is): a disc {@code radius} blocks wide. While the
 * caster's enemies stand on it they have {@code enemy_status}, while the caster does {@code owner_status}. A new one
 * with the same {@code name} replaces their old one. It stays for {@code duration} ticks, or until the cast ends if that's
 * sooner (no duration: until the cast ends, so keep the cast alive for as long as it should, e.g. a delay after this
 * node). Nothing at {@code at}: it goes on without one. {@code drawn: false}: the platform doesn't draw it (something
 * else shows it, e.g. real blocks). {@code sight: true}: it doesn't reach behind walls (only where its centre can see).
 * {@code shape: square}: a square instead, {@code radius} blocks from its centre to each side (a 9x9 room: 4.5), on the
 * block grid: an odd size centred on the middle of the block below {@code at}, an even one on the nearest block corner. {@code up: 2} / {@code down: 2}: someone
 * whose feet are up to that many blocks above / below it still counts as on it (default: about a block up, a hair down).
 */
public final class FloorNode implements GraphNode {

    private final String at;
    private final double radius;
    private final String name;
    private final String enemyStatus;
    private final String ownerStatus;
    private final double drop;
    private final int duration;
    private final boolean drawn;
    private final boolean sight;
    private boolean square;
    private double up = Double.NaN, down = Double.NaN;

    /** Feet this far above / below it still count as on it (NaN = the default). */
    public FloorNode reach(double up, double down) {
        this.up = up;
        this.down = down;
        return this;
    }

    /** A square ({@code radius} from its centre to each side) on the block grid, instead of a disc. */
    public FloorNode square(boolean square) {
        this.square = square;
        return this;
    }

    public FloorNode(String at, double radius, String name, String enemyStatus, String ownerStatus, double drop, int duration,
                     boolean drawn, boolean sight) {
        this.at = at;
        this.radius = radius;
        this.name = name;
        this.enemyStatus = enemyStatus;
        this.ownerStatus = ownerStatus;
        this.drop = drop;
        this.duration = duration;
        this.drawn = drawn;
        this.sight = sight;
    }

    private String over;    // laid exactly over the caster's floor of this name (null: at {@code at})
    private boolean lit;
    private boolean sticky;
    private int enemyDelay;

    /** {@code enemy_delay: 6}: an enemy has to stand on it that many ticks before its enemy_status goes on them. */
    public FloorNode enemyDelay(int ticks) {
        this.enemyDelay = Math.max(0, ticks);
        return this;
    }

    /** {@code sticky: true}: its enemy_status stays on an enemy once they've stood on it, until the floor goes. */
    public FloorNode sticky(boolean sticky) {
        this.sticky = sticky;
        return this;
    }

    /**
     * {@code over: <floor name>}: laid exactly over the caster's own floor of that name (its spot, size, shape and reach)
     * instead of at {@code at}, e.g. a room taken over for a moment; they have none: nothing. {@code lit: true}: drawn lit
     * up, the whole of it glowing.
     */
    public FloorNode over(String over, boolean lit) {
        this.over = over;
        this.lit = lit;
        return this;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var world = ctx.engine().world();
        if (over != null) {
            var under = ctx.engine().floors().of(ctx.caster()).stream().filter(f -> over.equals(f.name())).findFirst();
            if (under.isEmpty()) return NodeResult.NEXT;
            var u = under.get();
            put(ctx, new Floors.Patch(ctx.caster(), name, u.world(), u.center(), u.radius(), enemyStatus, ownerStatus, drawn,
                    sight, u.square(), u.up(), u.down(), lit, over, sticky, enemyDelay));
            return NodeResult.NEXT;
        }
        Optional<PointTarget> spot = KeyQuery.read(ctx, at).flatMap(world::positionOf);
        if (spot.isEmpty()) return NodeResult.NEXT;
        Vec3 c = spot.get().position();
        // (from a little above: a spot right on the ground is its own ground)
        Vec3 ground = world.groundBelow(spot.get().world(), c.add(0, 0.5, 0), drop + 0.5).orElse(c);
        // A square sits on the block grid: an odd size (9x9) centred on the middle of the block below, an even one
        // (10x10) on the nearest block corner, so its edges run along block edges either way
        if (square) {
            boolean even = Math.abs(Math.round(radius * 2) % 2) == 0 && Math.abs(radius * 2 - Math.round(radius * 2)) < 1e-9;
            ground = even ? new Vec3(Math.round(ground.x()), ground.y(), Math.round(ground.z()))
                    : new Vec3(Math.floor(ground.x()) + 0.5, ground.y(), Math.floor(ground.z()) + 0.5);
        }
        put(ctx, new Floors.Patch(ctx.caster(), name, spot.get().world(), ground, radius,
                enemyStatus, ownerStatus, drawn, sight, square, up, down, lit, null, sticky, enemyDelay));
        return NodeResult.NEXT;
    }

    /** Down for {@code duration} ticks, or until the cast ends if that's sooner. */
    private void put(ExecutionContext ctx, Floors.Patch patch) {
        Runnable remove = ctx.engine().floors().add(patch);
        ctx.instance().onEnd(remove);
        if (duration > 0) ctx.engine().scheduler().after(duration, remove);
    }
}
