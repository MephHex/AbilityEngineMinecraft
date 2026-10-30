package me.mephisto.ability_engine.engine.floor;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Floors: discs of ground that belong to someone (the Amethyst's crystal floor). Only a look on the platform: no real
 * blocks. While an enemy of the owner stands on one, its {@code enemyStatus} is kept on them (a slow); while the owner
 * stands on one of their own, its {@code ownerStatus} (faster, and a tag that says so). Each is put on again every
 * {@link #PERIOD} ticks for a little longer than that, so it drops off by itself soon after they step off.
 * <p>Several at once are fine, and touching or overlapping ones count as one floor: "on the floor" is being on any of
 * them. A new one with the same owner and name replaces the old (one Geode Bloom floor at a time).
 * <p>Gliding (a character's {@code glide:}): standing on one of their own floors, the owner dashes a few blocks the way
 * they press (Bukkit: SHIFT + a movement key), never off their floors: it stops at the edge. With {@code teleport} it's a
 * blink there instead (the Fae Trickster's Trick Room), which also stops in front of walls.
 * <p>A floor can be a square instead of a disc (Trick Room).
 */
public final class Floors {

    /** What a floor's owner_status gives its owner standing on it for their glide to work there. */
    public static final String ON_FLOOR = "state.on_floor";
    /** How often who's standing on what is looked at. */
    static final int PERIOD = 2;
    /** How long each status put on lasts: a little longer than the period, so a step (or a hop) doesn't flicker it. */
    static final int HOLD = PERIOD + 4;
    /** An entity's middle (a player's: ~0.9 above their feet) this far below the disc still counts as on it... */
    static final double BELOW = 0.1;
    /** ...and this far above (their feet about a block up: a step, a small hop). */
    static final double ABOVE = 2.0;
    /** How far a body's middle is above its feet (a player's), for a floor's reach up and down. */
    static final double MIDDLE = 0.9;
    /** How finely a glide's path is checked for the floor's edge (blocks). */
    private static final double STEP = 0.1;
    /** A glide that can't go at least this far (right at the edge) doesn't start. */
    private static final double MIN_GLIDE = 0.3;
    /** A glide this close to where it should end is there. */
    private static final double ARRIVED = 0.05;

    /**
     * One disc of floor.
     *
     * @param name        a new one with the same owner and name replaces this one (null = never replaced)
     * @param center      the middle of the disc, on the ground
     * @param enemyStatus kept on the owner's enemies standing on it (null = none)
     * @param ownerStatus kept on the owner standing on it (null = none)
     * @param drawn       the platform draws it (false: it looks like nothing of its own, e.g. real blocks show it)
     * @param sight       only where its centre can see counts as on it: it doesn't reach behind a wall
     * @param square      a square, {@code radius} blocks from its centre to each side (9x9 = 4.5), lined up with the
     *                    world's axes, instead of a disc
     * @param up          someone's feet up to this many blocks above it still count as on it (NaN = the default: about a
     *                    block, a step or a hop)
     * @param down        ...and this many below it (NaN = the default: a hair)
     * @param lit         drawn lit up: the whole of it glowing, not just its look (e.g. a room while Topsy-Turvy holds it)
     * @param over        laid over the owner's floor of this name (null = none): when that one is replaced (moved), this
     *                    one moves along with it, keeping its own time
     * @param sticky      its enemy status stays on an enemy once they've stood on it (stepping off doesn't end it), and
     *                    comes off everyone it gave it to when the floor goes
     * @param enemyDelay  an enemy has to stand on it this many ticks in a row before its enemy status goes on them (0:
     *                    at once); off it, it lasts its usual moment longer ({@link #HOLD})
     */
    public record Patch(UUID owner, String name, String world, Vec3 center, double radius, String enemyStatus,
                        String ownerStatus, boolean drawn, boolean sight, boolean square, double up, double down,
                        boolean lit, String over, boolean sticky, int enemyDelay) {

        /** No delay. */
        public Patch(UUID owner, String name, String world, Vec3 center, double radius, String enemyStatus,
                     String ownerStatus, boolean drawn, boolean sight, boolean square, double up, double down,
                     boolean lit, String over, boolean sticky) {
            this(owner, name, world, center, radius, enemyStatus, ownerStatus, drawn, sight, square, up, down, lit, over,
                    sticky, 0);
        }

        /** Not sticky. */
        public Patch(UUID owner, String name, String world, Vec3 center, double radius, String enemyStatus,
                     String ownerStatus, boolean drawn, boolean sight, boolean square, double up, double down,
                     boolean lit, String over) {
            this(owner, name, world, center, radius, enemyStatus, ownerStatus, drawn, sight, square, up, down, lit, over, false, 0);
        }

        /** Not lit, not laid over another. */
        public Patch(UUID owner, String name, String world, Vec3 center, double radius, String enemyStatus,
                     String ownerStatus, boolean drawn, boolean sight, boolean square, double up, double down) {
            this(owner, name, world, center, radius, enemyStatus, ownerStatus, drawn, sight, square, up, down, false, null);
        }

        /** The same floor, laid where {@code under} is now (its spot, size, shape and reach). */
        Patch movedOnto(Patch under) {
            return new Patch(owner, name, under.world(), under.center(), under.radius(), enemyStatus, ownerStatus, drawn,
                    sight, under.square(), under.up(), under.down(), lit, over, sticky, enemyDelay);
        }

        /** The default reach up and down. */
        public Patch(UUID owner, String name, String world, Vec3 center, double radius, String enemyStatus,
                     String ownerStatus, boolean drawn, boolean sight, boolean square) {
            this(owner, name, world, center, radius, enemyStatus, ownerStatus, drawn, sight, square, Double.NaN, Double.NaN);
        }

        /** Drawn, reaching behind walls. */
        public Patch(UUID owner, String name, String world, Vec3 center, double radius, String enemyStatus,
                     String ownerStatus) {
            this(owner, name, world, center, radius, enemyStatus, ownerStatus, true, false);
        }

        /** A disc. */
        public Patch(UUID owner, String name, String world, Vec3 center, double radius, String enemyStatus,
                     String ownerStatus, boolean drawn, boolean sight) {
            this(owner, name, world, center, radius, enemyStatus, ownerStatus, drawn, sight, false);
        }

        /** Is someone whose middle is at {@code middle} standing on it? */
        public boolean contains(String inWorld, Vec3 middle) { return contains(inWorld, middle, 0); }

        /**
         * Is {@code middle} on it, at least {@code inset} blocks in from its edge (e.g. a whole body's width inside: their
         * feet on one of its own blocks, not straddling the edge)?
         */
        public boolean contains(String inWorld, Vec3 middle, double inset) {
            if (!world.equals(inWorld)) return false;
            double dy = middle.y() - center.y();
            double lowest = Double.isNaN(down) ? -BELOW : MIDDLE - down;  // (their middle: about this high over their feet)
            double highest = Double.isNaN(up) ? ABOVE : MIDDLE + up;
            if (dy < lowest || dy > highest) return false;
            double dx = middle.x() - center.x(), dz = middle.z() - center.z();
            double r = radius - inset;
            if (r < 0) return false;
            if (square) return Math.abs(dx) <= r + 1e-9 && Math.abs(dz) <= r + 1e-9;
            return dx * dx + dz * dz <= r * r + 1e-9;
        }
    }

    /** Told when someone blinks on a floor (a teleporting glide), e.g. so their clone does the same. */
    public interface BlinkListener {
        /** {@code entity} blinked along {@code direction} (flat, unit), asking for {@code distance} blocks. */
        void blinked(UUID entity, Vec3 direction, double distance);
    }

    /** Told when floors come and go (e.g. to draw them). */
    public interface Listener {
        void added(Patch patch);

        void removed(Patch patch);
    }

    private final AbilityEngine engine;
    private final List<Patch> active = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BlinkListener> blinkListeners = new ArrayList<>();
    private final Map<UUID, Long> lastGlide = new HashMap<>();
    private final Map<UUID, TaskHandle> gliding = new HashMap<>();
    private TaskHandle ticker;

    public Floors(AbilityEngine engine) {
        this.engine = engine;
    }

    public void addListener(Listener listener) { listeners.add(listener); }

    public void addBlinkListener(BlinkListener listener) { blinkListeners.add(listener); }

    /** A sticky floor's enemies it's put its status on (to take it off them when the floor goes). */
    private final Map<Patch, Set<UUID>> stuck = new HashMap<>();
    /** A floor with an enemy delay: since when (tick) each enemy has stood on it without stepping off. */
    private final Map<Patch, Map<UUID, Long>> standing = new HashMap<>();
    /** How long a sticky floor's status is put on for: until the floor goes (it takes it off then). */
    private static final int STICK = 20 * 60 * 60;

    /** A floor laid over another that moved with it: the old one -> where it is now (so taking the old one away still works). */
    private final Map<Patch, Patch> movedTo = new HashMap<>();

    /**
     * Put a floor down; returns how to take it away (idempotent). Replaces the owner's floor of the same name, and their
     * floors laid over that one move onto the new one.
     */
    public Runnable add(Patch patch) {
        List<Patch> replaced = new ArrayList<>();
        if (patch.name() != null) {
            for (Patch old : List.copyOf(active)) {
                if (!old.owner().equals(patch.owner()) || !patch.name().equals(old.name())) continue;
                remove(old);
                replaced.add(old);
            }
        }
        put(patch);
        for (Patch old : replaced) carryOver(old, patch); // (after: what's laid over it comes back on top of it)
        return () -> remove(patch);
    }

    private void put(Patch patch) {
        active.add(patch);
        for (Listener l : List.copyOf(listeners)) l.added(patch);
        if (ticker == null) ticker = engine.scheduler().every(1, PERIOD, this::tick);
        refresh(patch, new HashSet<>()); // (on it already: it counts from now)
    }

    /** The owner's floors laid over {@code from} move onto {@code to} (e.g. a lit room following the room). */
    private void carryOver(Patch from, Patch to) {
        if (from.name() == null) return;
        for (Patch o : List.copyOf(active)) {
            if (!o.owner().equals(from.owner()) || !from.name().equals(o.over())) continue;
            Patch moved = o.movedOnto(to);
            Set<UUID> had = stuck.remove(o); // (moving it isn't it going: what it stuck on stays on)
            Map<UUID, Long> stood = standing.remove(o); // (nor a step off it for whoever stands on it)
            remove(o);
            if (had != null) stuck.put(moved, had);
            if (stood != null) standing.put(moved, stood);
            movedTo.put(o, moved);
            put(moved);
        }
    }

    private void remove(Patch patch) {
        while (movedTo.containsKey(patch)) patch = movedTo.remove(patch); // (it moved: take away where it is now)
        if (!active.remove(patch)) return;
        standing.remove(patch);
        Set<UUID> had = stuck.remove(patch); // sticky: off everyone it stuck on
        if (had != null && patch.enemyStatus() != null) {
            for (UUID id : had) engine.statuses().remove(id, patch.enemyStatus());
        }
        for (Listener l : List.copyOf(listeners)) l.removed(patch);
        if (active.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** Every floor down right now. */
    public List<Patch> active() { return List.copyOf(active); }

    /** {@code owner}'s floors right now. */
    public List<Patch> of(UUID owner) {
        return active.stream().filter(p -> p.owner().equals(owner)).toList();
    }

    /** Is {@code entity} standing on one of their own floors right now? */
    public boolean onOwnFloor(UUID entity) {
        Optional<PointTarget> at = engine.world().positionOf(new EntityTarget(entity));
        return at.isPresent() && inside(of(entity), at.get().world(), at.get().position());
    }

    private boolean inside(List<Patch> patches, String world, Vec3 middle) {
        for (Patch p : patches) if (on(p, world, middle)) return true;
        return false;
    }

    /** How high above a floor's ground its sight runs: walls lower than this (a 1-block one) don't hide anything. */
    static final double SIGHT_HEIGHT = 1.5;

    /**
     * Is someone whose middle is at {@code middle} standing on {@code p}? With sight, only if nothing solid stands between
     * its centre and them at head height (1.5 above its ground, level): a wall 2 blocks tall hides them, a 1-block one
     * doesn't.
     */
    private boolean on(Patch p, String world, Vec3 middle) { return on(p, world, middle, 0); }

    private boolean on(Patch p, String world, Vec3 middle, double inset) {
        if (!p.contains(world, middle, inset)) return false;
        if (!p.sight()) return true;
        Vec3 from = p.center().add(0, SIGHT_HEIGHT, 0);
        Vec3 to = new Vec3(middle.x(), from.y(), middle.z());
        return engine.world().sweep(world, from, to, 0, id -> true).isEmpty();
    }

    /** Who stands where: the statuses go on (again) for a little while. */
    private void tick() {
        Set<String> done = new HashSet<>(); // entity|status: once a tick each
        for (Patch patch : List.copyOf(active)) refresh(patch, done);
    }

    /**
     * {@code patch}'s statuses on whoever stands on it now (the owner's, its owner's enemies'). Also the moment it's put
     * down, so standing on it counts right away (e.g. pressing a room's key again at once), not only from its first tick.
     */
    private void refresh(Patch patch, Set<String> done) {
        UUID owner = patch.owner();
        if (!engine.world().isAlive(owner)) return;
        String mine = owner + "|" + patch.ownerStatus();
        if (patch.ownerStatus() != null && !done.contains(mine)) {
            var at = engine.world().positionOf(new EntityTarget(owner));
            if (at.isPresent() && on(patch, at.get().world(), at.get().position())) {
                done.add(mine);
                engine.statuses().apply(owner, patch.ownerStatus(), HOLD, owner);
            }
        }
        if (patch.enemyStatus() == null) return;
        Map<UUID, Long> stood = patch.enemyDelay() > 0 ? standing.computeIfAbsent(patch, k -> new HashMap<>()) : null;
        Set<UUID> onIt = new HashSet<>();
        long now = engine.clock().now();
        for (EntitySnapshot e : engine.world().livingEntitiesNear(new PointTarget(patch.world(), patch.center()),
                patch.radius() + Math.max(ABOVE, Double.isNaN(patch.up()) ? 0 : patch.up() + MIDDLE))) {
            if (e.id().equals(owner) || !engine.teams().enemies(owner, e.id())) continue;
            if (!on(patch, patch.world(), e.center())) continue;
            onIt.add(e.id());
            if (stood != null && now - stood.computeIfAbsent(e.id(), k -> now) < patch.enemyDelay()) continue; // not long enough yet
            if (!done.add(e.id() + "|" + patch.enemyStatus())) continue;
            if (patch.sticky()) { // once is enough: it stays on them until the floor goes
                if (stuck.computeIfAbsent(patch, k -> new HashSet<>()).add(e.id())
                        || !engine.statuses().has(e.id(), patch.enemyStatus())) {
                    engine.statuses().apply(e.id(), patch.enemyStatus(), STICK, owner);
                }
                continue;
            }
            engine.statuses().apply(e.id(), patch.enemyStatus(), HOLD, owner);
        }
        if (stood != null) stood.keySet().retainAll(onIt); // stepped off: their time on it starts over
    }

    /**
     * Glide {@code entity} along {@code direction} (flat; any length): their character's glide, if they have one, are
     * standing on their own floor, can move, and haven't glided in the last {@code gap} ticks. It goes up to
     * {@code distance} blocks, stopping at the floor's edge. True if it started.
     */
    public boolean glide(UUID entity, Vec3 direction) {
        CharacterDef.Glide g = engine.loadouts().characterOf(entity).map(CharacterDef::glide).orElse(null);
        if (g == null || gliding.containsKey(entity)) return false;
        Vec3 dir = new Vec3(direction.x(), 0, direction.z());
        if (dir.lengthSquared() < 1e-9) return false;
        dir = dir.normalize();
        var tags = engine.tags();
        long now = engine.clock().now();
        Long last = lastGlide.get(entity);
        if (engine.mirrors().puppeting(entity)) { // their keys work the puppet: it blinks, on their floors, they stay
            if (tags.has(entity, Tags.BLOCK_MOVE) || tags.has(entity, Tags.STUNNED)) return false;
            if (last != null && now - last < g.gap()) return false;
            boolean any = false;
            var mirrors = engine.mirrors();
            for (UUID clone : mirrors.clonesOf(entity)) {
                if (tags.has(clone, Tags.DASHING)) continue;
                if (blink(clone, entity, mirrors.reflect(clone, dir), g.distance(), g.cue()) > 0) any = true;
            }
            if (any) lastGlide.put(entity, now);
            return any;
        }
        if (tags.has(entity, Tags.BLOCK_MOVE) || tags.has(entity, Tags.BLOCK_WALK) || tags.has(entity, Tags.STUNNED)) return false;
        if (!tags.has(entity, ON_FLOOR)) return false; // (their floor's owner_status gives it: a floor without it, no glide)
        if (last != null && now - last < g.gap()) return false;
        Optional<PointTarget> at = engine.world().positionOf(new EntityTarget(entity));
        if (at.isEmpty()) return false;
        String world = at.get().world();
        Vec3 start = at.get().position();
        List<Patch> mine = of(entity);
        if (!inside(mine, world, start)) return false;
        if (g.teleport()) {
            if (blink(entity, entity, dir, g.distance(), g.cue()) <= 0) return false;
            lastGlide.put(entity, now);
            for (BlinkListener l : List.copyOf(blinkListeners)) l.blinked(entity, dir, g.distance());
            return true;
        }
        double reach = floorReach(mine, world, start, dir, g.distance()); // how far it can go without leaving the floor
        if (reach < MIN_GLIDE) return false;
        lastGlide.put(entity, now);
        if (g.cue() != null) engine.cuesFor(entity).play(g.cue(), world, start);
        new Glide(entity, g, world, start, dir, reach).push();
        return true;
    }

    /** How far from {@code start} along {@code dir} (at most {@code distance}) stays on {@code patches}. */
    private double floorReach(List<Patch> patches, String world, Vec3 start, Vec3 dir, double distance) {
        return floorReach(patches, world, start, dir, distance, 0);
    }

    /** ...staying at least {@code inset} in from their edges. */
    private double floorReach(List<Patch> patches, String world, Vec3 start, Vec3 dir, double distance, double inset) {
        double reach = 0;
        while (reach + STEP <= distance + 1e-9 && inside(patches, world, start.add(dir.multiply(reach + STEP)), inset)) {
            reach += STEP;
        }
        return reach;
    }

    private boolean inside(List<Patch> patches, String world, Vec3 middle, double inset) {
        for (Patch p : patches) if (on(p, world, middle, inset)) return true;
        return false;
    }

    /** A body's half width, and the gap kept from a wall a blink stops at. */
    private static final double BODY = 0.3, WALL_GAP = 0.05;
    /** Heights (from the middle) a blink is checked at against walls: just over the feet, the middle, just under the head. */
    private static final double[] HEIGHTS = {-0.8, 0, 0.8};
    /** ...and across: both sides of the body and its centre, so it can't slip past a corner or an off-centre block. */
    private static final double[] ACROSS = {-BODY, 0, BODY};

    /**
     * Blink {@code entity} up to {@code distance} blocks along {@code dir} (flat, unit) from where it stands, on
     * {@code floorOwner}'s floors: never off them (it stops with its whole body still inside, standing on the floor's own
     * blocks, not on the edge) and never into a block (it stops in front of the first wall). Its look and momentum stay. {@code cue} plays where it left and where it arrived (null = none).
     * Returns how far it went: 0 = not at all (it isn't on those floors, or can't get anywhere).
     */
    public double blink(UUID entity, UUID floorOwner, Vec3 dir, double distance, String cue) {
        Optional<PointTarget> at = engine.world().positionOf(new EntityTarget(entity));
        if (at.isEmpty()) return 0;
        String world = at.get().world();
        Vec3 start = at.get().position();
        List<Patch> floors = of(floorOwner);
        if (!inside(floors, world, start)) return 0;
        // (its whole body ends up inside: a body's width in from the edge, on one of the floor's own blocks)
        double reach = Math.min(floorReach(floors, world, start, dir, distance, BODY + WALL_GAP), clearReach(world, start, dir, distance));
        if (reach < MIN_GLIDE) return 0;
        Vec3 to = start.add(dir.multiply(reach));
        engine.movement().blink(entity, to);
        if (cue != null) {
            var cues = engine.cuesFor(floorOwner);
            cues.play(cue, world, start);
            cues.play(cue, world, to);
        }
        return reach;
    }

    /**
     * How far a body can go from {@code start} along {@code dir} before any of it would meet a block: rays from its feet
     * to its head and from side to side (see HEIGHTS, ACROSS). Never through a wall, however thin, nor past a corner.
     */
    private double clearReach(String world, Vec3 start, Vec3 dir, double distance) {
        double reach = distance;
        Vec3 side = new Vec3(-dir.z(), 0, dir.x());
        for (double dy : HEIGHTS) {
            for (double dx : ACROSS) {
                Vec3 from = start.add(side.multiply(dx)).add(0, dy, 0);
                Vec3 to = from.add(dir.multiply(distance + BODY));
                var hit = engine.world().sweep(world, from, to, 0, id -> true); // (blocks only)
                if (hit.isPresent()) {
                    double along = hit.get().position().subtract(from).dot(dir);
                    reach = Math.min(reach, along - BODY - WALL_GAP);
                }
            }
        }
        return Math.max(0, reach);
    }

    /** Is {@code entity} gliding right now? */
    public boolean isGliding(UUID entity) { return gliding.containsKey(entity); }

    /** One glide: pushed along every tick until it's gone far enough, would leave the floor, or they can't move. */
    private final class Glide {
        private final UUID entity;
        private final CharacterDef.Glide def;
        private final String world;
        private final Vec3 start;
        private final Vec3 dir;
        private final double reach;
        private final int maxTicks;
        private int ticks;

        Glide(UUID entity, CharacterDef.Glide def, String world, Vec3 start, Vec3 dir, double reach) {
            this.entity = entity;
            this.def = def;
            this.world = world;
            this.start = start;
            this.dir = dir;
            this.reach = reach;
            this.maxTicks = (int) Math.ceil(reach / def.speed()) + 4; // (a little slack: friction, a bump)
        }

        /** The first push now, then one a tick. */
        void push() {
            if (step()) gliding.put(entity, engine.scheduler().every(1, 1, this::tick));
        }

        private void tick() {
            if (!step()) end();
        }

        private void end() {
            TaskHandle task = gliding.remove(entity);
            if (task != null) task.cancel();
        }

        /** One tick's push; false when it's over (and they've been stopped). */
        private boolean step() {
            Optional<PointTarget> at = engine.world().positionOf(new EntityTarget(entity));
            var tags = engine.tags();
            if (at.isEmpty() || !at.get().world().equals(world) || ticks++ >= maxTicks
                    || tags.has(entity, Tags.BLOCK_MOVE) || tags.has(entity, Tags.STUNNED)) {
                if (at.isPresent()) engine.movement().stop(entity);
                return false;
            }
            Vec3 now = at.get().position();
            double gone = Math.hypot(now.x() - start.x(), now.z() - start.z());
            double left = reach - gone;
            double push = Math.min(def.speed(), left);
            if (left <= ARRIVED || !inside(of(entity), world, now.add(dir.multiply(push)))) {
                engine.movement().stop(entity);
                return false;
            }
            engine.movement().setVelocity(entity, dir.multiply(push));
            if (def.trail() != null) engine.cuesFor(entity).play(def.trail(), world, now);
            return true;
        }
    }

    /** Stop everything (plugin disable). */
    public void shutdown() {
        movedTo.clear();
        for (Patch p : List.copyOf(active)) remove(p);
        stuck.clear();
        standing.clear();
        gliding.values().forEach(TaskHandle::cancel);
        gliding.clear();
    }
}
