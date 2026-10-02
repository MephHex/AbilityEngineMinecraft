package me.mephisto.ability_engine.engine.construct;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ConstructRenderer;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
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
 *   <li>{@code triggered} — a TRAP ({@link Options#triggerRadius}): an enemy came within range once it
 *       was armed; writes that enemy as "hit"</li>
 * </ul>
 * Traps are usually not {@link Options#solid}: projectiles and punches pass through them.
 * One ticker for all constructs, stopped when none exist. Allies of the owner (Teams) never trigger or break it.
 */
public final class ConstructSystem {

    public static final String KEY_STRUCK_BY = "struck_by";
    public static final String KEY_STRUCK_PHASE = "struck_phase";

    private static final AtomicLong IDS = new AtomicLong();

    private final ConstructRenderer renderer;
    private final Teams teams;
    private final TaskScheduler scheduler;
    private final WorldQuery world;
    private final EngineLog log;
    private final List<Construct> active = new ArrayList<>();
    private TaskHandle ticker;

    public ConstructSystem(ConstructRenderer renderer, TaskScheduler scheduler, Teams teams, WorldQuery world, EngineLog log) {
        this.renderer = renderer;
        this.teams = teams;
        this.scheduler = scheduler;
        this.world = world;
        this.log = log;
    }

    public record Hit(ConstructHandle construct, Vec3 position, double distance) {}

    /** Who can set a trap off. The owner can't, except with EVERYONE (friend, foe and the owner). */
    public enum TriggeredBy { ENEMIES, ALLIES, ALL, EVERYONE }

    /**
     * @param solid         projectiles and punches hit it (false: they pass through)
     * @param triggerRadius a trap: an enemy within this many blocks sets it off (0 = not a trap)
     * @param armTicks      a trap can't be set off for this long after it's placed
     * @param limit         at most this many from the same owner and ability at once: placing one more
     *                      ends the oldest (its "fuse"). 0 = no limit
     * @param triggeredBy   who sets the trap off: enemies (default), the owner's allies, both (all: the owner
     *                      never), or both and the owner (everyone)
     * @param hidden        only the owner and their allies see it (the platform hides it from everyone else)
     * @param idleCue       played at it every {@code idleEvery} ticks while it stands (null = none), e.g. a glow
     * @param allyTriggerRadius allies (when they can set it off) have to come this close instead (0 = the same as
     *                      everyone), e.g. a fruit enemies set off from afar that allies pick up
     */
    public record Options(boolean solid, double triggerRadius, int armTicks, int limit, TriggeredBy triggeredBy,
                          boolean hidden, String idleCue, int idleEvery, double allyTriggerRadius) {
        public static final Options DEFAULT = new Options(true, 0, 0, 0);

        public Options {
            if (triggeredBy == null) triggeredBy = TriggeredBy.ENEMIES;
            idleEvery = Math.max(1, idleEvery);
        }

        public Options(boolean solid, double triggerRadius, int armTicks, int limit, TriggeredBy triggeredBy,
                       boolean hidden, String idleCue, int idleEvery) {
            this(solid, triggerRadius, armTicks, limit, triggeredBy, hidden, idleCue, idleEvery, 0);
        }

        public Options(boolean solid, double triggerRadius, int armTicks, int limit) {
            this(solid, triggerRadius, armTicks, limit, TriggeredBy.ENEMIES, false, null, 1);
        }
    }

    /** Cues as seen by an owner's audience (a duel in a veil: only the two). Set by the engine. */
    private java.util.function.Function<UUID, me.mephisto.ability_engine.engine.platform.CuePlayer> cues = id -> null;

    public void setCues(java.util.function.Function<UUID, me.mephisto.ability_engine.engine.platform.CuePlayer> cues) {
        this.cues = cues;
    }

    /** A trap is sprung by an entity whose centre is at most this far above or below it. */
    private static final double TRIGGER_HEIGHT = 2.0;

    // ---- placing ------------------------------------------------------------------------------

    public ConstructHandle place(UUID owner, String world, Vec3 position, double size, int fragileTicks, int fuseTicks,
                                 String visual, Resumer resumer) {
        return place(owner, world, position, size, fragileTicks, fuseTicks, visual, resumer, Options.DEFAULT);
    }

    public ConstructHandle place(UUID owner, String world, Vec3 position, double size, int fragileTicks, int fuseTicks,
                                 String visual, Resumer resumer, Options options) {
        if (options.limit() > 0) makeRoom(owner, abilityOf(resumer), options.limit());
        Construct c = new Construct(IDS.incrementAndGet(), owner, world, position, size, fragileTicks, fuseTicks, resumer,
                options);
        c.visual = renderer.spawn(c, visual);
        active.add(c);
        if (ticker == null) ticker = scheduler.every(1, 1, this::tick);
        return c;
    }

    private static String abilityOf(Resumer resumer) { return resumer.context().instance().ability().id(); }

    /** Over the limit with one more: the oldest ones end, as if their fuse ran out. */
    private void makeRoom(UUID owner, String ability, int limit) {
        List<Construct> mine = active.stream()
                .filter(c -> !c.done && c.owner.equals(owner) && abilityOf(c.resumer).equals(ability))
                .toList(); // placement order: oldest first
        for (int i = 0; i <= mine.size() - limit; i++) finish(mine.get(i), Ports.FUSE, null);
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
        if (!(handle instanceof Construct c) || c.done || !c.options.solid()) return false;
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

    /**
     * Set off this owner's constructs from this ability within {@code radius} of a point (their centre), as if
     * someone had walked into them: they exit "triggered", armed or not. E.g. a seed's burst setting off traps
     * near it. Returns how many went off.
     */
    public int setOff(UUID owner, String ability, String world, Vec3 at, double radius) {
        List<Construct> near = active.stream()
                .filter(c -> !c.done && c.owner.equals(owner) && c.world.equals(world) && abilityOf(c.resumer).equals(ability))
                .filter(c -> c.position.distance(at) <= radius)
                .toList(); // first: going off runs their graphs, which may place more
        for (Construct c : near) if (!c.done) finish(c, Ports.TRIGGERED, null);
        return near.size();
    }

    /** First construct a moving sphere of radius {@code radius} touches between two points. */
    public Optional<Hit> sweep(String world, Vec3 from, Vec3 to, double radius) {
        return sweep(world, from, to, radius, c -> c.options.solid());
    }

    /** Like {@link #sweep}, but only this owner's constructs from this ability, solid or not. */
    public Optional<Hit> sweepOwn(String world, Vec3 from, Vec3 to, double radius, UUID owner, String ability) {
        return sweep(world, from, to, radius, c -> c.owner.equals(owner) && abilityOf(c.resumer).equals(ability));
    }

    private Optional<Hit> sweep(String world, Vec3 from, Vec3 to, double radius, java.util.function.Predicate<Construct> which) {
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSquared();
        Hit best = null;
        for (Construct c : active) {
            if (c.done || !which.test(c) || !c.world.equals(world)) continue;
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
            Optional<UUID> victim = c.armed() ? intruder(c) : Optional.empty();
            if (victim.isPresent()) {
                c.resumer.context().put(Keys.HIT, new EntityTarget(victim.get()));
                finish(c, Ports.TRIGGERED, null);
            } else if (c.age >= c.fuseTicks) {
                finish(c, Ports.FUSE, null);
            } else {
                c.visual.update(c.progress(), c.isFragile());
                if (c.options.idleCue() != null && c.age % c.options.idleEvery() == 0) {
                    var player = cues.apply(c.owner);
                    if (player != null) player.play(c.options.idleCue(), c.world, c.position);
                }
            }
        }
        active.removeIf(c -> c.done);
        if (active.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /**
     * A trap's victim: the nearest one in range of those who may set it off (by default its owner's enemies;
     * {@code triggered_by} can make it allies, or both). The owner never sets it off, except with "everyone".
     */
    private Optional<UUID> intruder(Construct c) {
        double r = c.options.triggerRadius();
        if (r <= 0) return Optional.empty();
        double allyR = c.options.allyTriggerRadius() > 0 ? c.options.allyTriggerRadius() : r;
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntitySnapshot e : world.livingEntitiesNear(new PointTarget(c.world, c.position), Math.max(r, allyR) + TRIGGER_HEIGHT)) {
            boolean owner = e.id().equals(c.owner) && c.options.triggeredBy() != TriggeredBy.EVERYONE;
            if (owner || !world.isAlive(e.id()) || !setsOff(c, e.id())) continue;
            Vec3 d = e.center().subtract(c.position);
            double flat = Math.sqrt(d.x() * d.x() + d.z() * d.z());
            double reach = teams.allies(c.owner, e.id()) || e.id().equals(c.owner) ? allyR : r;
            if (flat > reach || Math.abs(d.y()) > TRIGGER_HEIGHT || flat >= bestDist) continue;
            best = e.id();
            bestDist = flat;
        }
        return Optional.ofNullable(best);
    }

    private boolean setsOff(Construct c, UUID who) {
        boolean ally = teams.allies(c.owner, who);
        return switch (c.options.triggeredBy()) {
            case ENEMIES -> !ally;
            case ALLIES -> ally;
            case ALL, EVERYONE -> true;
        };
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
        final Options options;
        ConstructVisual visual;
        int age;
        boolean done;

        Construct(long id, UUID owner, String world, Vec3 position, double size, int fragileTicks, int fuseTicks, Resumer resumer,
                  Options options) {
            this.options = options;
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
        @Override public boolean solid() { return options.solid(); }
        @Override public boolean armed() { return !done && age >= options.armTicks(); }
        @Override public boolean hidden() { return options.hidden(); }
    }
}
