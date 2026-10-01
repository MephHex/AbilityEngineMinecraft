package me.mephisto.ability_engine.engine.testkit;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.SweepHit;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Entities are spheres; the only block is an optional flat floor. */
public final class FakeWorld implements WorldQuery, me.mephisto.ability_engine.engine.platform.MovementControl,
        me.mephisto.ability_engine.engine.platform.CloneSpawner {

    public static final String WORLD = "world";
    private static final double ENTITY_RADIUS = 0.4;

    private final Map<UUID, Vec3> entities = new HashMap<>();
    private final Map<UUID, Vec3> looking = new HashMap<>();
    private Double floorY;

    public UUID spawn(Vec3 center) {
        UUID id = UUID.randomUUID();
        entities.put(id, center);
        return id;
    }

    public void move(UUID id, Vec3 center) { entities.put(id, center); }
    public void kill(UUID id) { entities.remove(id); }

    // ---- riding: a rider sits RIDE_HEIGHT above its vehicle's centre, wherever the vehicle is ----
    public static final double RIDE_HEIGHT = 1.5;
    /** Rider -> vehicle. */
    public final Map<UUID, UUID> riding = new HashMap<>();

    @Override
    public boolean mount(UUID rider, UUID vehicle) {
        if (!entities.containsKey(rider) || !entities.containsKey(vehicle) || rider.equals(vehicle)) return false;
        riding.put(rider, vehicle);
        return true;
    }

    @Override
    public void dismount(UUID rider) {
        UUID vehicle = riding.remove(rider);
        Vec3 at = vehicle == null ? null : entities.get(vehicle);
        if (at != null) entities.put(rider, at.add(0, RIDE_HEIGHT, 0)); // left where it sat
    }

    @Override
    public Optional<UUID> vehicleOf(UUID rider) { return Optional.ofNullable(riding.get(rider)); }

    /** The game takes a rider off (not the engine), e.g. a vanilla dismount. */
    public void eject(UUID rider) { dismount(rider); }

    /** Where an entity is: a rider sits on its vehicle. */
    private Vec3 at(UUID id) {
        UUID vehicle = riding.get(id);
        Vec3 v = vehicle == null ? null : entities.get(vehicle);
        if (v != null && entities.containsKey(id)) return v.add(0, RIDE_HEIGHT, 0);
        return entities.get(id);
    }

    private final Map<UUID, Integer> lag = new HashMap<>();
    private final Map<UUID, java.util.ArrayDeque<Vec3>> inFlight = new HashMap<>();

    /**
     * Simulate ping for a player: a push only shows up in their position {@code ticks} pushes later (the
     * velocity has to reach their client, and their moved position has to come back).
     */
    public void lag(UUID id, int ticks) { lag.put(id, ticks); }

    /** Fake physics: each push moves the entity by one tick of that velocity right away (or later, with lag). */
    @Override
    public void setVelocity(UUID id, Vec3 velocity) {
        Vec3 pos = entities.get(id);
        if (pos == null) return;
        int delay = lag.getOrDefault(id, 0);
        var queue = inFlight.computeIfAbsent(id, k -> new java.util.ArrayDeque<>());
        queue.addLast(velocity);
        while (queue.size() > delay) entities.put(id, entities.get(id).add(queue.pollFirst()));
    }

    @Override
    public void stop(UUID id) {}

    @Override
    public void teleport(UUID id, Vec3 center) {
        if (entities.containsKey(id)) entities.put(id, center);
    }

    @Override
    public void teleport(UUID id, Vec3 center, Vec3 look) {
        teleport(id, center);
        if (look != null && !look.isZero()) looking.put(id, look.normalize());
    }

    /** Make spawning vulnerable clones fail (like a platform that refuses one), to test what survives it. */
    public boolean failVulnerableClones;

    // ---- clones: exist and move like entities, but queries and sweeps ignore them (like the real ones) ----
    public final java.util.Set<UUID> clones = new java.util.HashSet<>();

    /** Which way each entity was last turned (spawnClone's facing, MovementControl.face). */
    public final Map<UUID, Vec3> facing = new HashMap<>();

    @Override
    public Optional<UUID> spawnClone(UUID of, String world, Vec3 center, Vec3 facingDir) {
        UUID id = UUID.randomUUID();
        entities.put(id, center);
        clones.add(id);
        if (facingDir != null) facing.put(id, facingDir.normalize());
        return Optional.of(id);
    }

    /** Health left of vulnerable clones (design HP); the test damage effect kills them at 0. */
    public final Map<UUID, Double> health = new HashMap<>();
    /** Glowing vulnerable clones. */
    public final java.util.Set<UUID> glowing = new java.util.HashSet<>();

    @Override
    public Optional<UUID> spawnClone(UUID of, String world, Vec3 center, Vec3 facingDir,
                                     me.mephisto.ability_engine.engine.platform.CloneSpawner.Options options) {
        if (!options.vulnerable()) return spawnClone(of, world, center, facingDir);
        if (failVulnerableClones) throw new IllegalStateException("clone refused (test)");
        UUID id = UUID.randomUUID(); // a real target: not in "clones", so abilities find it
        entities.put(id, center);
        health.put(id, options.health());
        if (options.glowing()) glowing.add(id);
        if (options.teamOf() != null) teamOf(options.teamOf()).ifPresent(t -> team(id, t));
        if (facingDir != null) facing.put(id, facingDir.normalize());
        return Optional.of(id);
    }

    /** A vulnerable clone takes damage: dies at 0. */
    public void hurt(UUID id, double amount) {
        Double left = health.get(id);
        if (left == null) return;
        left -= amount;
        health.put(id, left);
        if (left <= 0) kill(id);
    }

    @Override
    public void face(UUID id, Vec3 direction) { facing.put(id, direction.normalize()); }

    @Override
    public void despawn(UUID clone) {
        entities.remove(clone);
        clones.remove(clone);
    }
    public void look(UUID id, Vec3 direction) { looking.put(id, direction.normalize()); }

    private final Map<UUID, Vec3> moving = new HashMap<>();

    /** Walking this way (horizontal), for dashes along the movement direction. Null = standing still. */
    public void walk(UUID id, Vec3 direction) {
        if (direction == null) moving.remove(id);
        else moving.put(id, new Vec3(direction.x(), 0, direction.z()).normalize());
    }

    @Override
    public Optional<Vec3> movementOf(UUID entity) { return Optional.ofNullable(moving.get(entity)); }
    public void floor(double y) { floorY = y; }

    /** Health as a share of max (0..1), set by tests (unset = unknown). */
    public final Map<UUID, Double> healthFraction = new HashMap<>();

    @Override
    public java.util.OptionalDouble healthFraction(UUID entity) {
        Double f = healthFraction.get(entity);
        return f == null ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(f);
    }

    @Override
    public Optional<Aim> aimOf(UUID entity) {
        Vec3 pos = at(entity);
        if (pos == null) return Optional.empty();
        return Optional.of(new Aim(WORLD, pos, looking.getOrDefault(entity, new Vec3(1, 0, 0))));
    }

    @Override
    public Optional<PointTarget> positionOf(Target target) {
        if (target instanceof PointTarget p) return Optional.of(p);
        Vec3 pos = at(((EntityTarget) target).id());
        return pos == null ? Optional.empty() : Optional.of(new PointTarget(WORLD, pos));
    }

    @Override
    public List<EntitySnapshot> livingEntitiesNear(PointTarget center, double radius) {
        return entities.entrySet().stream()
                .filter(e -> !clones.contains(e.getKey()))
                .filter(e -> e.getValue().distance(center.position()) <= radius + 1)
                .map(e -> new EntitySnapshot(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public Optional<SweepHit> sweep(String world, Vec3 from, Vec3 to, double radius, Predicate<UUID> passThrough) {
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSquared();
        if (len2 < 1e-12) return Optional.empty();

        double bestT = Double.MAX_VALUE;
        SweepHit best = null;

        for (var e : entities.entrySet()) {
            if (passThrough.test(e.getKey()) || clones.contains(e.getKey())) continue;
            double t = Math.max(0, Math.min(1, e.getValue().subtract(from).dot(seg) / len2));
            Vec3 closest = from.add(seg.multiply(t));
            if (closest.distance(e.getValue()) <= ENTITY_RADIUS + radius && t < bestT) {
                bestT = t;
                best = new SweepHit(new EntityTarget(e.getKey()), closest, null);
            }
        }
        for (Box b : boxes) { // solid columns block rays too (slab test: x, z within the box, y below its top)
            double[] lo = {b.minX(), -1e9, b.minZ()}, hi = {b.maxX(), b.top(), b.maxZ()};
            double[] o = {from.x(), from.y(), from.z()}, d = {seg.x(), seg.y(), seg.z()};
            double tIn = 0, tOut = 1;
            int axis = -1;
            boolean miss = false;
            for (int k = 0; k < 3 && !miss; k++) {
                if (Math.abs(d[k]) < 1e-12) {
                    if (o[k] < lo[k] || o[k] > hi[k]) miss = true;
                    continue;
                }
                double t1 = (lo[k] - o[k]) / d[k], t2 = (hi[k] - o[k]) / d[k];
                double near = Math.min(t1, t2), far = Math.max(t1, t2);
                if (near > tIn) { tIn = near; axis = k; }
                tOut = Math.min(tOut, far);
                if (tIn > tOut) miss = true;
            }
            if (miss || axis < 0 || tIn >= bestT) continue;
            Vec3 p = from.add(seg.multiply(tIn));
            double[] n = {0, 0, 0};
            n[axis] = d[axis] > 0 ? -1 : 1;
            bestT = tIn;
            best = new SweepHit(new PointTarget(WORLD, p), p, new Vec3(n[0], n[1], n[2]));
        }
        if (floorY != null && from.y() >= floorY && to.y() < floorY) {
            double t = (from.y() - floorY) / (from.y() - to.y());
            if (t < bestT) {
                Vec3 p = from.add(seg.multiply(t));
                best = new SweepHit(new PointTarget(WORLD, p), p, Vec3.UP);
            }
        }
        return Optional.ofNullable(best);
    }

    @Override public boolean isLoaded(String world, Vec3 position) { return true; }

    /** Solid columns (solid from far below up to {@code top}), e.g. a cliff or a pillar to stand on. */
    private record Box(double minX, double maxX, double minZ, double maxZ, double top) {
        boolean contains(double x, double z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }

    private final java.util.List<Box> boxes = new java.util.ArrayList<>();
    private final Map<UUID, String> teams = new HashMap<>();

    public void box(double minX, double maxX, double minZ, double maxZ, double top) {
        boxes.add(new Box(minX, maxX, minZ, maxZ, top));
    }

    public void team(UUID entity, String team) {
        if (team == null) teams.remove(entity);
        else teams.put(entity, team);
    }

    @Override
    public Optional<String> teamOf(UUID entity) { return Optional.ofNullable(teams.get(entity)); }

    @Override
    public Optional<Vec3> groundBelow(String world, Vec3 from, double maxDrop) {
        Double best = null;
        for (Box b : boxes) {
            if (!b.contains(from.x(), from.z())) continue;
            if (from.y() < b.top() - 1e-9) return Optional.empty(); // started inside it
            if (from.y() - b.top() <= maxDrop && (best == null || b.top() > best)) best = b.top();
        }
        if (best == null && floorY != null && from.y() >= floorY && from.y() - floorY <= maxDrop) best = floorY;
        return best == null ? Optional.empty() : Optional.of(new Vec3(from.x(), best, from.z()));
    }
    @Override public boolean isAlive(UUID entity) { return entities.containsKey(entity); }
}
