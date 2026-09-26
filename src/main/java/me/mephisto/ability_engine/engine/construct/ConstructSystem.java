package me.mephisto.ability_engine.engine.construct;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ConstructRenderer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.team.Teams;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Placed constructs ("geometry"): a position, a hitbox, a fragile window and a fuse. The branch
 * that placed one waits until it ends, then continues out of:
 * <ul>
 *   <li>{@code fuse}   — the timer ran out</li>
 *   <li>{@code struck} — the owner's projectile hit it; writes {@code struck_by} (ability id) and
 *       {@code struck_phase} (that shot's "phase", e.g. "empowered") to the waiting branch</li>
 *   <li>{@code broken} — an enemy hit it while it was still fragile; also writes {@code struck_by}</li>
 * </ul>
 * One ticker for all constructs, stopped when none exist. Allies of the owner (Teams) never trigger or break it.
 */
public final class ConstructSystem {

    public static final String KEY_STRUCK_BY = "struck_by";
    public static final String KEY_STRUCK_PHASE = "struck_phase";

    private static final AtomicLong IDS = new AtomicLong();

    private final ConstructRenderer renderer;
    private final Teams teams;
    private final TaskScheduler scheduler;
    private final EngineLog log;
    private final List<Construct> active = new ArrayList<>();
    private TaskHandle ticker;

    public ConstructSystem(ConstructRenderer renderer, TaskScheduler scheduler, Teams teams, EngineLog log) {
        this.renderer = renderer;
        this.teams = teams;
        this.scheduler = scheduler;
        this.log = log;
    }

    public record Hit(ConstructHandle construct, Vec3 position, double distance) {}

    // ---- placing ------------------------------------------------------------------------------

    public ConstructHandle place(UUID owner, String world, Vec3 position, double size, int fragileTicks, int fuseTicks,
                                 String visual, Resumer resumer) {
        Construct c = new Construct(IDS.incrementAndGet(), owner, world, position, size, fragileTicks, fuseTicks, resumer);
        c.visual = renderer.spawn(c, visual);
        active.add(c);
        if (ticker == null) ticker = scheduler.every(1, 1, this::tick);
        return c;
    }

    /** Constructs still standing (ended ones are dropped from the list on the next tick). */
    public int activeCount() { return (int) active.stream().filter(c -> !c.done).count(); }

    public List<ConstructHandle> all() { return active.stream().filter(c -> !c.done).map(c -> (ConstructHandle) c).toList(); }

    // ---- being hit ----------------------------------------------------------------------------

    /**
     * Something hit a construct. Returns true if the hit was absorbed (a projectile should stop):
     * <ul>
     *   <li>owner's projectile → it's struck (shatters early)</li>
     *   <li>enemy anything while fragile → it breaks (fizzles)</li>
     *   <li>owner's melee, allies (anything), or enemies after the fragile window → ignored, passes through</li>
     * </ul>
     */
    public boolean strike(ConstructHandle handle, Strike strike) {
        if (!(handle instanceof Construct c) || c.done) return false;
        boolean owner = strike.attacker().equals(c.owner);
        if (owner && !strike.isMelee()) {
            finish(c, Ports.STRUCK, strike);
            return true;
        }
        if (owner || teams.allies(strike.attacker(), c.owner)) return false; // allies can't trigger or break it
        if (c.isFragile()) {
            finish(c, Ports.BROKEN, strike);
            return true;
        }
        return false;
    }

    /** First construct a moving sphere of radius {@code radius} touches between two points. */
    public Optional<Hit> sweep(String world, Vec3 from, Vec3 to, double radius) {
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSquared();
        Hit best = null;
        for (Construct c : active) {
            if (c.done || !c.world.equals(world)) continue;
            double t = len2 < 1e-12 ? 0 : Math.max(0, Math.min(1, c.position.subtract(from).dot(seg) / len2));
            Vec3 closest = from.add(seg.multiply(t));
            if (closest.distance(c.position) <= c.size + radius) {
                double d = closest.distance(from);
                if (best == null || d < best.distance()) best = new Hit(c, closest, d);
            }
        }
        return Optional.ofNullable(best);
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    private void tick() {
        for (Construct c : List.copyOf(active)) {
            if (c.done) continue;
            if (!c.resumer.context().instance().isActive()) { // cast cancelled (death, quit): vanish
                c.done = true;
                c.visual.remove();
                continue;
            }
            c.age++;
            if (c.age >= c.fuseTicks) {
                finish(c, Ports.FUSE, null);
            } else {
                c.visual.update(c.progress(), c.isFragile());
            }
        }
        active.removeIf(c -> c.done);
        if (active.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void finish(Construct c, String port, Strike strike) {
        c.done = true;
        c.visual.remove();
        var board = c.resumer.context().blackboard();
        board.putRaw(KEY_STRUCK_BY, strike == null ? "none" : strike.abilityId());
        board.putRaw(KEY_STRUCK_PHASE, strike == null ? "none" : strike.phase());
        log.debug(() -> "construct #" + c.id + " -> " + port + (strike != null ? " by " + strike : ""));
        c.resumer.resume(port);
    }

    /** Remove everything without resuming (plugin disable). */
    public void shutdown() {
        active.forEach(c -> {
            c.done = true;
            c.visual.remove();
        });
        active.clear();
        if (ticker != null) ticker.cancel();
        ticker = null;
    }

    // ---- the construct itself -------------------------------------------------------------------

    private static final class Construct implements ConstructHandle {
        final long id;
        final UUID owner;
        final String world;
        final Vec3 position;
        final double size;
        final int fragileTicks;
        final int fuseTicks;
        final Resumer resumer;
        ConstructVisual visual;
        int age;
        boolean done;

        Construct(long id, UUID owner, String world, Vec3 position, double size, int fragileTicks, int fuseTicks, Resumer resumer) {
            this.id = id;
            this.owner = owner;
            this.world = world;
            this.position = position;
            this.size = size;
            this.fragileTicks = fragileTicks;
            this.fuseTicks = Math.max(1, fuseTicks);
            this.resumer = resumer;
        }

        @Override public long id() { return id; }
        @Override public UUID owner() { return owner; }
        @Override public String world() { return world; }
        @Override public Vec3 position() { return position; }
        @Override public double size() { return size; }
        @Override public boolean isAlive() { return !done; }
        @Override public boolean isFragile() { return !done && age < fragileTicks; }
        @Override public double progress() { return Math.min(1, age / (double) fuseTicks); }
    }
}
