package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.construct.ConstructSystem;
import me.mephisto.ability_engine.engine.construct.Strike;
import me.mephisto.ability_engine.engine.team.Teams;
import me.mephisto.ability_engine.engine.barrier.BarrierSystem;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ProjectileRenderer;
import me.mephisto.ability_engine.engine.platform.SweepHit;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Simulates every projectile with ONE repeating task (the old code started a task per
 * projectile and never cancelled it). Physics and collision are engine-side; the platform
 * only renders. The ticker stops itself when nothing is in flight.
 */
public final class ProjectileSystem {

    /** Pushes a bounced projectile off the surface so it doesn't re-hit the same face. */
    private static final double BOUNCE_NUDGE = 0.05;

    private final WorldQuery world;
    private final ConstructSystem constructs;
    private final Teams teams;
    private final BarrierSystem barriers;
    private final ProjectileRenderer renderer;
    private final TaskScheduler scheduler;
    private final EngineLog log;
    private final List<Projectile> active = new ArrayList<>();
    /** Each caster's most recent projectile per ability, for "steer my latest bolt". */
    private final Map<UUID, Map<String, Projectile>> latest = new HashMap<>();
    private TaskHandle ticker;

    public ProjectileSystem(WorldQuery world, ConstructSystem constructs, Teams teams, BarrierSystem barriers,
                            ProjectileRenderer renderer, TaskScheduler scheduler, EngineLog log) {
        this.world = world;
        this.constructs = constructs;
        this.teams = teams;
        this.barriers = barriers;
        this.renderer = renderer;
        this.scheduler = scheduler;
        this.log = log;
    }

    /** Launch a projectile. {@code resumer} is resumed with hit_entity / hit_block / expired. */
    public ProjectileHandle launch(ProjectileSpec spec, String worldName, Vec3 position, Vec3 velocity, Resumer resumer) {
        return launch(spec, worldName, position, velocity, resumer, null);
    }

    /**
     * @param sweepFrom where the first collision check starts (the caster's eye), so a target standing
     *                  between the eye and the muzzle is still hit at point blank. Null = the spawn position.
     */
    public ProjectileHandle launch(ProjectileSpec spec, String worldName, Vec3 position, Vec3 velocity, Resumer resumer,
                                   Vec3 sweepFrom) {
        Projectile p = new Projectile(spec, worldName, position, velocity, resumer, renderer.spawn(worldName, position, spec));
        p.sweepFrom = sweepFrom;
        active.add(p);
        var instance = resumer.context().instance();
        latest.computeIfAbsent(instance.caster(), k -> new HashMap<>()).put(instance.ability().id(), p);
        if (ticker == null) ticker = scheduler.every(1, 1, this::tick);
        return p;
    }

    public int activeCount() { return active.size(); }

    /** The caster's most recent projectile from this ability, if it's still flying. */
    public Optional<ProjectileHandle> latest(UUID caster, String abilityId) {
        Map<String, Projectile> mine = latest.get(caster);
        Projectile p = mine == null ? null : mine.get(abilityId);
        return p != null && !p.done ? Optional.of(p) : Optional.empty();
    }

    /** Remove every projectile without resuming anything (plugin disable). */
    public void shutdown() {
        active.forEach(p -> p.visual.remove());
        active.clear();
        latest.clear();
        if (ticker != null) ticker.cancel();
        ticker = null;
    }

    private void tick() {
        // Snapshot: a hit can resume a graph that launches more projectiles mid-loop.
        for (Projectile p : List.copyOf(active)) {
            if (!p.done) step(p);
        }
        active.removeIf(p -> p.done);
        latest.values().forEach(m -> m.values().removeIf(p -> p.done));
        latest.values().removeIf(Map::isEmpty);
        if (active.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void step(Projectile p) {
        ExecutionContext ctx = p.resumer.context();
        if (!ctx.instance().isActive()) { // cast was cancelled: vanish quietly
            p.done = true;
            p.visual.remove();
            return;
        }
        if (++p.ticksLived > p.spec.lifetimeTicks()
                || (p.spec.range() > 0 && p.unguidedDistance >= p.spec.range())) {
            finish(p, Ports.EXPIRED, null);
            return;
        }

        for (MotionModifier m : p.motion) p.velocity = m.apply(p.position, p.velocity, ctx);
        Vec3 next = p.position.add(p.velocity);

        if (!world.isLoaded(p.world, next)) {
            finish(p, Ports.EXPIRED, null);
            return;
        }

        Vec3 from = p.sweepFrom != null ? p.sweepFrom : p.position;
        p.sweepFrom = null;
        var hit = world.sweep(p.world, from, next, p.spec.size() / 2, teams.passThroughFor(ctx.caster()));

        // Constructs and barriers are engine objects the world doesn't know about: check them too, nearest wins.
        double worldDist = hit.map(h -> h.position().distance(p.position)).orElse(Double.MAX_VALUE);
        var barrier = barriers.cross(p.world, from, next, p.spec.size() / 2, ctx.caster());
        if (barrier.isPresent() && barrier.get().distance() <= worldDist) {
            // Absorbed by an enemy's frontal barrier: no hit logic, no explosion.
            moveTo(p, barrier.get().position());
            barriers.blocked(p.world, barrier.get().position());
            p.done = true;
            p.visual.remove();
            p.resumer.abandon();
            return;
        }
        var construct = constructs.sweep(p.world, p.position, next, p.spec.size() / 2);
        if (construct.isPresent() && construct.get().distance() <= worldDist) {
            Object phase = ctx.blackboard().raw("phase");
            Strike strike = new Strike(ctx.caster(), ctx.instance().ability().id(), phase == null ? "none" : phase.toString());
            if (constructs.strike(construct.get().construct(), strike)) {
                // Absorbed into the geometry: the projectile's own hit logic doesn't run.
                moveTo(p, construct.get().position());
                p.done = true;
                p.visual.remove();
                p.resumer.abandon();
                return;
            }
        }

        if (hit.isEmpty()) {
            moveTo(p, next);
            return;
        }

        SweepHit h = hit.get();
        if (h.target() instanceof EntityTarget) {
            moveTo(p, h.position());
            finish(p, Ports.HIT_ENTITY, h.target());
        } else if (p.bouncesLeft > 0 && h.normal() != null && p.spec.bounce(p.velocity, h.normal()) != null) {
            p.bouncesLeft--;
            p.velocity = p.spec.bounce(p.velocity, h.normal());
            moveTo(p, h.position().add(h.normal().multiply(BOUNCE_NUDGE)));
        } else { // out of bounces, or too slow to bounce: it lands
            moveTo(p, h.position());
            finish(p, Ports.HIT_BLOCK, h.target());
        }
    }

    private void moveTo(Projectile p, Vec3 pos) {
        p.lastPosition = p.position;
        double moved = pos.distance(p.position);
        p.position = pos;
        p.visual.moveTo(pos);
        if (!p.isGuided()) p.unguidedDistance += moved;
    }

    private void finish(Projectile p, String port, Target hit) {
        p.done = true;
        p.visual.remove();
        if (hit != null) {
            p.resumer.context().put(Keys.HIT, hit);
            // Where the hit came from (the projectile's approach), so frontal blocks judge direction correctly.
            p.resumer.context().blackboard().putRaw(Keys.HIT_FROM.name(), new PointTarget(p.world, p.lastPosition));
        }
        log.debug(() -> "projectile -> " + port + (hit != null ? " " + hit : ""));
        p.resumer.resume(port);
    }
}
