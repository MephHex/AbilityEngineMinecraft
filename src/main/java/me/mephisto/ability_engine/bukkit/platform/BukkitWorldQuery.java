package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.SweepHit;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.target.Target;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class BukkitWorldQuery implements WorldQuery {

    /** Only rays at least this long are traced in debug: hitscans, not every projectile tick. */
    private static final double TRACE_MIN_LENGTH = 4.0;

    private EngineLog log; // set once the engine exists; null = no tracing
    private final MovementTracker movement = new MovementTracker();

    public void setLog(EngineLog log) { this.log = log; }

    /** Register this as a listener: it records which way players walk (for movement-direction dashes). */
    public MovementTracker movementTracker() { return movement; }

    @Override
    public Optional<Vec3> movementOf(UUID entity) { return movement.directionOf(entity); }

    @Override
    public Optional<Aim> aimOf(UUID entity) {
        if (!(Bukkit.getEntity(entity) instanceof LivingEntity living)) return Optional.empty();
        Location eye = living.getEyeLocation();
        return Optional.of(new Aim(eye.getWorld().getName(), Convert.vec(eye), Convert.vec(eye.getDirection())));
    }

    @Override
    public Optional<PointTarget> positionOf(Target target) {
        if (target instanceof PointTarget p) return Optional.of(p);
        Entity e = Bukkit.getEntity(((EntityTarget) target).id());
        if (e == null || !e.isValid()) return Optional.empty();
        return Optional.of(new PointTarget(e.getWorld().getName(), Convert.vec(e.getBoundingBox().getCenter())));
    }

    @Override
    public List<EntitySnapshot> livingEntitiesNear(PointTarget center, double radius) {
        Location loc = Convert.location(center.world(), center.position());
        if (loc == null) return List.of();
        return loc.getWorld().getNearbyEntities(loc, radius, radius, radius).stream()
                .filter(e -> whyNotTargetable(e) == null)
                .map(e -> new EntitySnapshot(e.getUniqueId(), Convert.vec(e.getBoundingBox().getCenter())))
                .toList();
    }

    /**
     * First block or living entity along the segment. Done in two explicit steps instead of
     * World#rayTrace so every candidate can be traced:
     *  1. block ray -> limits how far entities can be hit (no hitting through walls)
     *  2. every nearby entity's hitbox, fattened by {@code radius}, tested against the ray
     */
    @Override
    public Optional<SweepHit> sweep(String world, Vec3 from, Vec3 to, double radius, Predicate<UUID> passThrough) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        if (distance < 1e-6) return Optional.empty();
        Location start = Convert.location(world, from);
        if (start == null) return Optional.empty();

        World w = start.getWorld();
        Vector origin = start.toVector();
        Vector dir = Convert.bukkit(delta.normalize());
        boolean trace = log != null && log.isDebug() && distance >= TRACE_MIN_LENGTH;
        List<String> notes = trace ? new ArrayList<>() : null;

        RayTraceResult block = w.rayTraceBlocks(start, dir, distance, FluidCollisionMode.NEVER, true);
        double limit = block == null ? distance : block.getHitPosition().distance(origin);

        Vector end = origin.clone().add(dir.clone().multiply(limit));
        BoundingBox searchArea = BoundingBox.of(origin, end).expand(radius + 2.0);

        Entity best = null;
        Vector bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : w.getNearbyEntities(searchArea)) {
            if (passThrough.test(e.getUniqueId())) continue; // caster and allies (or everything, blocks-only)
            String rejected = whyNotTargetable(e);
            if (rejected != null) {
                if (trace) notes.add(e.getType() + ": " + rejected);
                continue;
            }
            BoundingBox box = e.getBoundingBox().expand(radius);
            Vector hitPos;
            if (box.contains(origin)) {
                hitPos = origin.clone(); // point blank: ray starts inside the hitbox
            } else {
                RayTraceResult r = box.rayTrace(origin, dir, limit);
                if (r == null) {
                    if (trace) notes.add(e.getType() + ": ray passes it");
                    continue;
                }
                hitPos = r.getHitPosition();
            }
            double d = hitPos.distance(origin);
            if (trace) notes.add(e.getType() + String.format(": HIT at %.1f", d));
            if (d < bestDist) {
                bestDist = d;
                best = e;
                bestPos = hitPos;
            }
        }

        if (trace) {
            String blockInfo = block == null ? "none" : String.format("%s at %.1f", block.getHitBlock() == null ? "?" : block.getHitBlock().getType(), limit);
            Entity chosen = best;
            log.debug(() -> String.format("sweep %.1f blocks, block=%s, entity=%s, candidates=%s",
                    distance, blockInfo, chosen == null ? "none" : chosen.getType(), notes));
        }

        if (best != null) {
            return Optional.of(new SweepHit(new EntityTarget(best.getUniqueId()), Convert.vec(bestPos), null));
        }
        if (block != null) {
            Vec3 pos = Convert.vec(block.getHitPosition());
            Vec3 normal = block.getHitBlockFace() != null ? Convert.vec(block.getHitBlockFace().getDirection()) : null;
            return Optional.of(new SweepHit(new PointTarget(world, pos), pos, normal));
        }
        return Optional.empty();
    }

    @Override
    public boolean isLoaded(String world, Vec3 position) {
        World w = Bukkit.getWorld(world);
        // Don't use location.getChunk().isLoaded(): getChunk() LOADS the chunk.
        return w != null && w.isChunkLoaded((int) Math.floor(position.x()) >> 4, (int) Math.floor(position.z()) >> 4);
    }

    /** One block ray straight down: the first solid top surface (real height for slabs/stairs). */
    @Override
    public Optional<Vec3> groundBelow(String world, Vec3 from, double maxDrop) {
        World w = Bukkit.getWorld(world);
        if (w == null || !isLoaded(world, from)) return Optional.empty();
        Block startBlock = w.getBlockAt((int) Math.floor(from.x()), (int) Math.floor(from.y()), (int) Math.floor(from.z()));
        if (!startBlock.isPassable() && startBlock.getBoundingBox().getMaxY() > from.y() + 0.01) {
            return Optional.empty(); // started inside a solid block
        }
        RayTraceResult down = w.rayTraceBlocks(Convert.location(world, from), new Vector(0, -1, 0), maxDrop,
                FluidCollisionMode.NEVER, true); // passable blocks (grass, flowers) are ignored
        if (down == null) return Optional.empty(); // nothing below within maxDrop: void
        return Optional.of(new Vec3(from.x(), down.getHitPosition().getY(), from.z()));
    }

    /** Vanilla /team on the main scoreboard: players by name, other entities by UUID. */
    @Override
    public Optional<String> teamOf(UUID id) {
        Entity e = Bukkit.getEntity(id);
        if (e == null || Bukkit.getScoreboardManager() == null) return Optional.empty();
        String entry = e instanceof Player p ? p.getName() : id.toString();
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entry);
        return team == null ? Optional.empty() : Optional.of(team.getName());
    }

    @Override
    public boolean isAlive(UUID entity) {
        Entity e = Bukkit.getEntity(entity);
        return e != null && e.isValid() && !(e instanceof LivingEntity l && l.isDead());
    }

    /** Null if the entity can be targeted, otherwise the reason (shown in debug traces). */
    private static String whyNotTargetable(Entity e) {
        if (!(e instanceof LivingEntity living)) return "not a living entity";
        if (VisualEntities.isVisual(e)) return "ability visual";
        if (living.isDead()) return "dead";
        if (!living.isValid()) return "not valid";
        if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) return "spectator";
        return null;
    }
}
