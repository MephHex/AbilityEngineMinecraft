package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Everyone within {@code width/2} of the segment between two blackboard points (e.g. where a dash
 * started and ended), ordered from the start. No line of sight needed: it follows a path you took.
 * {@code extend} reaches on past the end; {@code height} makes it a box with flat ends (a rectangle on the ground).
 */
public final class PathQuery implements TargetQuery {

    private static final double BODY = 0.4;

    private final String fromKey;
    private final String toKey;
    private final double width;
    private final double extend;
    private final double height;

    public PathQuery(String fromKey, String toKey, double width) {
        this(fromKey, toKey, width, 0, 0);
    }

    /**
     * @param extend it reaches this many blocks past {@code to}, along the path (e.g. a slash that overreaches the dash)
     * @param height above 0: a box this tall around the path, with flat ends, instead of a capsule (rounded ends): a
     *               rectangle on the ground, {@code width} wide
     */
    public PathQuery(String fromKey, String toKey, double width, double extend, double height) {
        this.fromKey = fromKey;
        this.toKey = toKey;
        this.width = width;
        this.extend = Math.max(0, extend);
        this.height = Math.max(0, height);
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        var world = ctx.engine().world();
        Optional<PointTarget> a = KeyQuery.read(ctx, fromKey).flatMap(world::positionOf);
        Optional<PointTarget> b = KeyQuery.read(ctx, toKey).flatMap(world::positionOf);
        if (a.isEmpty() || b.isEmpty()) return List.of();
        Vec3 start = a.get().position();
        Vec3 seg = b.get().position().subtract(start);
        if (extend > 0) { // (straight on past the end, the way it was going)
            Vec3 flat = height > 0 ? new Vec3(seg.x(), 0, seg.z()) : seg;
            if (flat.length() > 1e-6) seg = seg.add(flat.normalize().multiply(extend));
        }
        double len = seg.length();
        double reach = width / 2 + BODY;
        Vec3 mid = start.add(seg.multiply(0.5));
        Vec3 path = seg;
        return world.livingEntitiesNear(new PointTarget(a.get().world(), mid), len / 2 + width + height).stream()
                .filter(e -> !e.id().equals(ctx.caster()))
                .filter(e -> height > 0 ? inBox(start, path, e.center()) : inCapsule(start, path, len, reach, e.center()))
                .sorted(Comparator.comparingDouble(e -> e.center().distance(start)))
                .map(e -> (Target) new EntityTarget(e.id()))
                .toList();
    }

    private static boolean inCapsule(Vec3 start, Vec3 seg, double len, double reach, Vec3 c) {
        double t = len < 1e-6 ? 0 : Math.max(0, Math.min(1, c.subtract(start).dot(seg) / (len * len)));
        return start.add(seg.multiply(t)).distance(c) <= reach;
    }

    /**
     * Inside the box along the path (flat): from the start to the end along it, {@code width} across, {@code height}
     * tall around it, a body's width of slack each way. A flat path of no length is a box {@code width} square.
     */
    private boolean inBox(Vec3 start, Vec3 seg, Vec3 c) {
        Vec3 flat = new Vec3(seg.x(), 0, seg.z());
        double len = flat.length();
        Vec3 along = len < 1e-6 ? new Vec3(1, 0, 0) : flat.multiply(1 / len);
        Vec3 across = new Vec3(-along.z(), 0, along.x());
        Vec3 d = c.subtract(start);
        double a = d.x() * along.x() + d.z() * along.z();
        double side = d.x() * across.x() + d.z() * across.z();
        double t = len < 1e-6 ? 0 : Math.max(0, Math.min(1, a / len));
        double dy = c.y() - (start.y() + seg.y() * t);
        return a >= -BODY && a <= len + BODY && Math.abs(side) <= width / 2 + BODY && Math.abs(dy) <= height / 2 + BODY;
    }
}
