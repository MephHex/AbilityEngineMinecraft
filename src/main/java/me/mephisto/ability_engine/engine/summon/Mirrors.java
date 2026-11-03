package me.mephisto.ability_engine.engine.summon;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.floor.Floors;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.MoveInput;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirroring clones (summon_clone with {@code mirror: true}, the Fae Trickster's Changeling): a clone that copies its
 * owner's every move from where it stands, as a mirror image: reflected across the upright plane halfway between them
 * where it was placed (it faces them). Walking toward it, it walks toward them, so they meet and cross; a step to the
 * side (along the mirror) is the same step to that side, and jumps are copied as they are.
 * <p>Placed right on its owner (the Changeling), it's a half-turned copy instead: everything turned round 180 degrees
 * about where it appeared (a mirror there would keep them stacked whenever they walk along it). They walk ahead, it walks
 * ahead its own way (the other way); either of them moving, they split, and walking toward each other they cross.
 * <ul>
 *   <li>Only what the owner PUTS IN is copied: every tick the walk their keys ask for (at their speed: sprinting, sneaking,
 *       slows) and their jumps, mirrored, drive the clone through its own physics (walls stop it, it falls off edges,
 *       knockback on IT still knocks it back). Knockback on them, pulls, drags, teleports and leaps aren't copied: being
 *       moved isn't moving. Rooted or stunned, their held keys count as nothing (they don't move, so neither does it).
 *       Without input to read (not a player), it falls back to copying their change of position, up to a step a tick.</li>
 *   <li>It looks where they look, mirrored, and crouches while they hold sneak.</li>
 *   <li>Their arm swings (basic attacks) are its arm swings ({@link #swung}); they hurt no one.</li>
 *   <li>A blink on their floors (Trick Room) is its blink too: mirrored, the same distance from where it stands, on the
 *       same floors, so it stops at their edge, and only if it's on one of them.</li>
 *   <li>While the clone dashes by itself (a ghost of the owner's dash, mirrored too: see {@link #reflect}) it isn't moved
 *       or turned.</li>
 *   <li>A jump in the owner's position bigger than a step (a teleport, a swap) isn't copied.</li>
 * </ul>
 */
public final class Mirrors implements Floors.BlinkListener {

    /**
     * What an owner's clones do with their moves (the Changeling's recast cycles them).
     * <ul>
     *   <li>COPY: as above.</li>
     *   <li>STILL: it stops copying movement and turning: it stays where it is, looking where it looked (it still swings
     *       and fakes their slashes).</li>
     *   <li>PUPPET: the owner can't walk or jump ({@code state.puppeteer}; they can still look, swing and cast) and their
     *       keys (walking, jumping, sneaking, Trick Room teleports) move only the clone. They're hidden from everyone else
     *       meanwhile ({@code state.hidden}): on Bukkit a frozen decoy of them stands in their place, so they can turn to
     *       steer without anyone seeing them turn.</li>
     * </ul>
     */
    public enum Mode { COPY, STILL, PUPPET }

    /** A change of position this big in one tick (blocks, sideways) is a teleport, not a step: not copied. */
    static final double MAX_STEP = 1.6;

    private final AbilityEngine engine;
    private final Map<UUID, UUID> ownerOf = new LinkedHashMap<>(); // clone -> owner
    private final Map<UUID, Vec3> mirror = new HashMap<>();         // clone -> the mirror's normal (flat, unit; ZERO = turned)
    private final java.util.Set<UUID> crouched = new java.util.HashSet<>(); // clones crouching right now
    private final Map<UUID, Vec3> last = new HashMap<>();           // owner -> where they were last tick
    private final Map<UUID, Mode> modes = new HashMap<>();          // owner -> their clones' mode (missing = COPY)
    private TaskHandle ticker;
    /** Clones that copy their owner as they are, not mirrored (Mitosis): see {@link #addFollower}. */
    private final java.util.Set<UUID> followers = new java.util.HashSet<>();
    /** Owner -> the way they looked last tick (yaw, pitch in degrees), for followers' turns. */
    private final Map<UUID, double[]> lastLook = new HashMap<>();
    /** Follower -> the way it walks "forward" (yaw, degrees): turned by its owner's turns, never put back in step. */
    private final Map<UUID, Double> heading = new HashMap<>();
    /** How far out a follower's owner's crosshair is looked for (blocks): the spot both of them look at. */
    static final double FOCUS_RANGE = 24;
    /** Closer than this to its eyes, the spot is no help (it'd stare at its feet): it looks along its heading. */
    private static final double FOCUS_MIN = 2;

    public Mirrors(AbilityEngine engine) {
        this.engine = engine;
    }

    /** {@code clone} (a summon of {@code owner}'s) copies their moves from now on, until it's gone. */
    public void add(UUID owner, UUID clone) {
        ownerOf.put(clone, owner);
        mirror.put(clone, normal(owner, clone));
        engine.world().positionOf(new EntityTarget(owner)).ifPresent(p -> last.put(owner, p.position()));
        if (ticker == null) ticker = engine.scheduler().every(1, 1, this::tick);
    }

    /**
     * {@code clone} copies {@code owner} as they are, from its own position (Mitosis), not as a mirror image:
     * <ul>
     *   <li>it has a heading of its own, turned by their turns (they turn 30 degrees right: so does its heading, from
     *       wherever it was), and their walk relative to where they face is its walk relative to its heading (they walk
     *       forward: it walks forward its way), at their speed (its own slows and haste counted instead of theirs). Never
     *       put back in step with them: walls and drops drift them apart;</li>
     *   <li>it LOOKS at what they look at: the spot their crosshair is on (a block or someone, up to 24 blocks; nothing
     *       there: that far along their look), so their lines of sight cross there and both bodies' casts go at it. When
     *       they look at IT, it looks back at them: their eyes meet.</li>
     * </ul>
     * Crowd control on it holds only it (stunned: it doesn't move or turn; rooted: it only turns).
     */
    public void addFollower(UUID owner, UUID clone) {
        add(owner, clone);
        followers.add(clone);
        engine.world().aimOf(owner).ifPresent(a -> lastLook.put(owner, yawPitch(a.direction())));
        engine.world().aimOf(clone).ifPresent(a -> heading.put(clone, yawPitch(a.direction())[0]));
        faceFocus(owner, clone);
    }

    /**
     * Turn {@code clone} (a follower) to look at the spot {@code owner} looks at right now (see {@link #addFollower}),
     * e.g. just before it copies a cast, so it aims at what they aim at. False if there's nothing to look at.
     */
    public boolean faceFocus(UUID owner, UUID clone) {
        var world = engine.world();
        var theirs = world.aimOf(owner);
        var its = world.aimOf(clone);
        if (theirs.isEmpty() || its.isEmpty() || !theirs.get().world().equals(its.get().world())) return false;
        Vec3 eye = theirs.get().eye(), dir = theirs.get().direction();
        Vec3 far = eye.add(dir.multiply(FOCUS_RANGE));
        String w = theirs.get().world();
        Vec3 spot = world.sweep(w, eye, far, 0,
                        id -> id.equals(owner) || id.equals(clone) || engine.teams().allies(owner, id))
                .map(h -> h.position()).orElse(far);
        // They look at IT: it looks back at them (their eyes meet), unless something stands in between
        if (lookedAt(w, eye, dir, spot, clone)) {
            Vec3 back = eye.subtract(its.get().eye());
            if (back.isZero()) return false;
            engine.movement().face(clone, back.normalize());
            return true;
        }
        Vec3 toward = spot.subtract(its.get().eye());
        if (toward.length() < FOCUS_MIN) return false;
        engine.movement().face(clone, toward.normalize());
        return true;
    }

    /** How close to their line of sight the follower's middle has to be (blocks) for them to be looking at it. */
    private static final double LOOKED_AT = 0.7;

    /**
     * Is the owner, looking from {@code eye} along {@code dir}, looking at {@code clone}: its middle within LOOKED_AT of
     * their line of sight, in front of them and nearer than whatever their sight stops at ({@code spot}).
     */
    private boolean lookedAt(String w, Vec3 eye, Vec3 dir, Vec3 spot, UUID clone) {
        var at = engine.world().positionOf(new EntityTarget(clone));
        if (at.isEmpty() || !at.get().world().equals(w)) return false;
        Vec3 c = at.get().position();
        double along = c.subtract(eye).dot(dir);
        if (along <= 0 || along > spot.subtract(eye).length() + 0.5) return false;
        return c.subtract(eye.add(dir.multiply(along))).length() <= LOOKED_AT;
    }

    /**
     * {@code owner} was moved or turned by something (a swap): nothing to copy from that, start over from here. Their
     * followers' headings start over from where they face now.
     */
    public void resync(UUID owner) {
        engine.world().positionOf(new EntityTarget(owner)).ifPresent(p -> last.put(owner, p.position()));
        engine.world().aimOf(owner).ifPresent(a -> lastLook.put(owner, yawPitch(a.direction())));
        for (UUID clone : clonesOf(owner)) {
            if (followers.contains(clone)) engine.world().aimOf(clone).ifPresent(a -> heading.put(clone, yawPitch(a.direction())[0]));
        }
    }

    /** Minecraft's yaw and pitch (degrees) of a look. */
    static double[] yawPitch(Vec3 d) {
        Vec3 n = d.normalize();
        return new double[]{Math.toDegrees(Math.atan2(-n.x(), n.z())), Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, n.y()))))};
    }

    /** The look along a yaw and pitch (degrees). */
    static Vec3 look(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    /** {@code v} turned about the upright axis by {@code degrees} (Minecraft yaw: +90 turns south into west). */
    static Vec3 turn(Vec3 v, double degrees) {
        double r = Math.toRadians(degrees), c = Math.cos(r), sn = Math.sin(r);
        return new Vec3(v.x() * c - v.z() * sn, v.y(), v.x() * sn + v.z() * c);
    }

    private static double wrap(double degrees) {
        double d = degrees % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /** A clone this close to its owner (sideways) when placed is on top of them: a half-turned copy, not a mirror. */
    static final double ON_TOP = 0.3;

    /**
     * The mirror between {@code owner} and {@code clone}: the flat way from one to the other (it stays put once placed).
     * Right on top of them: ZERO (a half-turned copy, see the class comment).
     */
    private Vec3 normal(UUID owner, UUID clone) {
        var a = engine.world().positionOf(new EntityTarget(owner));
        var b = engine.world().positionOf(new EntityTarget(clone));
        if (a.isPresent() && b.isPresent()) {
            Vec3 d = b.get().position().subtract(a.get().position());
            Vec3 flat = new Vec3(d.x(), 0, d.z());
            if (flat.length() > ON_TOP) return flat.normalize();
        }
        return Vec3.ZERO;
    }

    /**
     * {@code v} as {@code clone} does it: reflected in its mirror (the part toward / away from its owner turned round, the
     * rest kept), or for a half-turned copy turned round (sideways reversed, up and down kept). Anything that isn't a
     * mirroring clone does it as it is.
     */
    public Vec3 reflect(UUID clone, Vec3 v) {
        Vec3 n = mirror.get(clone);
        if (n == null || v == null) return v;
        if (n.isZero()) return new Vec3(-v.x(), v.y(), -v.z());
        return v.subtract(n.multiply(2 * v.dot(n)));
    }

    /** The clones copying {@code owner} right now. */
    public List<UUID> clonesOf(UUID owner) {
        List<UUID> out = new ArrayList<>();
        ownerOf.forEach((clone, o) -> {
            if (o.equals(owner) && live(clone)) out.add(clone);
        });
        return out;
    }

    private boolean live(UUID clone) {
        return engine.summons().isSummon(clone) && engine.world().isAlive(clone);
    }

    private void tick() {
        ownerOf.keySet().removeIf(clone -> !live(clone));
        mirror.keySet().retainAll(ownerOf.keySet());
        followers.retainAll(ownerOf.keySet());
        heading.keySet().retainAll(followers);
        crouched.retainAll(ownerOf.keySet());
        Map<UUID, List<UUID>> byOwner = new LinkedHashMap<>();
        ownerOf.forEach((clone, owner) -> byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(clone));
        last.keySet().retainAll(byOwner.keySet());
        lastLook.keySet().retainAll(byOwner.keySet());
        for (UUID owner : List.copyOf(modes.keySet())) if (!byOwner.containsKey(owner)) forget(owner); // clone gone
        if (byOwner.isEmpty()) {
            ticker.cancel();
            ticker = null;
            return;
        }
        byOwner.forEach(this::follow);
    }

    /** One tick of {@code owner}'s moves, onto each of their clones. */
    private void follow(UUID owner, List<UUID> clones) {
        var at = engine.world().positionOf(new EntityTarget(owner));
        if (at.isEmpty() || !engine.world().isAlive(owner)) return;
        Vec3 now = at.get().position();
        Vec3 before = last.put(owner, now);
        Vec3 step = before == null ? Vec3.ZERO : now.subtract(before);
        boolean teleported = Math.hypot(step.x(), step.z()) > MAX_STEP;
        Mode mode = mode(owner);
        boolean puppet = mode == Mode.PUPPET;
        var keys = engine.world().inputOf(owner);
        // rooted or stunned: their movement keys move nothing (but a held SHIFT still crouches them). A puppeteer is held
        // in place on purpose: their keys still count, and a held jump key jumps the puppet (they can't jump themselves).
        var input = keys.map(in -> !held(owner) ? new MoveInput(Vec3.ZERO, false, in.sneak(), false)
                : puppet ? new MoveInput(in.move(), in.jumpHeld(), in.sneak(), in.jumpHeld()) : in);
        var look = engine.world().aimOf(owner).map(a -> a.direction()).orElse(null);
        double[] nowLook = look == null || look.isZero() ? null : yawPitch(look);
        double[] lookBefore = nowLook == null ? null : lastLook.put(owner, nowLook);
        for (UUID clone : clones) {
            if (engine.tags().has(clone, Tags.DASHING)) continue; // its own (ghost) dash moves it meanwhile
            if (followers.contains(clone)) {
                follow(owner, clone, input.orElse(null), nowLook, lookBefore);
                continue;
            }
            if (input.isPresent()) { // what they put in, nothing else
                MoveInput in = input.get();
                if (mode == Mode.STILL) in = new MoveInput(Vec3.ZERO, false, in.sneak(), false); // it stays put
                if (in.sneak() != crouched.contains(clone)) { // crouching with them (SHIFT)
                    if (in.sneak()) crouched.add(clone);
                    else crouched.remove(clone);
                    engine.movement().crouch(clone, in.sneak());
                }
                if (!in.move().isZero() || in.jump()) engine.movement().drive(clone, reflect(clone, in.move()), in.jump());
            } else if (mode == Mode.COPY && !teleported && !step.isZero()) engine.movement().stride(clone, reflect(clone, step));
            if (mode != Mode.STILL && look != null && !look.isZero()) engine.movement().face(clone, reflect(clone, look));
        }
    }

    /** One tick of a follower (see {@link #addFollower}): their walk and turns, its way. */
    private void follow(UUID owner, UUID clone, MoveInput in, double[] nowLook, double[] lookBefore) {
        var tags = engine.tags();
        if (tags.has(clone, Tags.STUNNED)) return; // stunned: it does nothing at all
        var cloneAim = engine.world().aimOf(clone).map(a -> a.direction()).orElse(null);
        double[] cloneLook = cloneAim == null || cloneAim.isZero() ? null : yawPitch(cloneAim);
        if (cloneLook != null && !heading.containsKey(clone)) heading.put(clone, cloneLook[0]);
        if (nowLook != null && lookBefore != null && heading.containsKey(clone)) { // their turn is its heading's turn
            heading.put(clone, heading.get(clone) + wrap(nowLook[0] - lookBefore[0]));
        }
        Double walkYaw = heading.get(clone);
        if (in != null && walkYaw != null && nowLook != null) {
            boolean held = tags.has(clone, Tags.BLOCK_MOVE) || tags.has(clone, Tags.BLOCK_WALK);
            if (in.sneak() != crouched.contains(clone)) { // crouching with them (SHIFT)
                if (in.sneak()) crouched.add(clone);
                else crouched.remove(clone);
                engine.movement().crouch(clone, in.sneak());
            }
            if (!held && (!in.move().isZero() || in.jump())) {
                // their walk relative to their facing, turned onto its heading; its own speed instead of theirs
                Vec3 move = turn(in.move(), walkYaw - nowLook[0]);
                double theirs = engine.stats().moveSpeedMultiplier(owner);
                if (theirs > 1e-6) move = move.multiply(engine.stats().moveSpeedMultiplier(clone) / theirs);
                engine.movement().drive(clone, move, in.jump());
            }
        }
        // it looks at what they look at (their lines of sight cross there); nothing useful to look at: along its heading
        if (!faceFocus(owner, clone) && walkYaw != null && nowLook != null) engine.movement().face(clone, look(walkYaw, nowLook[1]));
    }

    /** Can {@code owner}'s keys move anything right now? Not rooted, stunned or held in place. */
    private boolean held(UUID owner) {
        var tags = engine.tags();
        return !tags.has(owner, Tags.BLOCK_MOVE) && !tags.has(owner, Tags.STUNNED) && !tags.has(owner, Tags.BLOCK_WALK);
    }

    /** {@code owner}'s clones' mode right now. */
    public Mode mode(UUID owner) { return modes.getOrDefault(owner, Mode.COPY); }

    /** Are {@code owner}'s keys working a puppet right now (PUPPET, with a clone out)? */
    public boolean puppeting(UUID owner) { return mode(owner) == Mode.PUPPET && !clonesOf(owner).isEmpty(); }

    /** The next mode for {@code owner}'s clones: copy -> still -> puppet -> copy. Returns it. */
    public Mode cycle(UUID owner) {
        Mode next = Mode.values()[(mode(owner).ordinal() + 1) % Mode.values().length];
        setMode(owner, next);
        return next;
    }

    /** Puppeteering holds them where they stand; anything else lets them go. */
    public void setMode(UUID owner, Mode mode) {
        Mode was = mode(owner);
        if (was == mode) return;
        // puppeteering: they can't walk, and they're hidden from everyone else (a decoy stands in, see Bukkit's PuppetHold)
        if (was == Mode.PUPPET) {
            engine.tags().revoke(owner, Tags.PUPPETEER);
            engine.tags().revoke(owner, Tags.HIDDEN);
        }
        if (mode == Mode.PUPPET) {
            engine.tags().grant(owner, Tags.PUPPETEER);
            engine.tags().grant(owner, Tags.HIDDEN);
        }
        if (mode == Mode.COPY) modes.remove(owner);
        else modes.put(owner, mode);
        // switching to copying (or from puppeteering): where they are now is the start, nothing to catch up on
        engine.world().positionOf(new EntityTarget(owner)).ifPresent(p -> last.put(owner, p.position()));
    }

    /** Their clones are gone (or they died): back to copying, and not held in place any more. */
    public void forget(UUID owner) { setMode(owner, Mode.COPY); }

    private final Map<UUID, Long> lastSwing = new HashMap<>();

    /**
     * {@code owner} swung their arm (a basic attack, or the platform saw their arm swing): so do their clones. The two
     * reports of one swing (a cast and its arm swing) come within a tick of each other: that's one swing.
     */
    public void swung(UUID owner) {
        long now = engine.clock().now();
        Long last = lastSwing.get(owner);
        if (last != null && now - last <= 1) return;
        lastSwing.put(owner, now);
        for (UUID clone : clonesOf(owner)) engine.movement().swing(clone);
    }

    @Override
    public void blinked(UUID owner, Vec3 direction, double distance) {
        // Where they are now is where they blinked to: that's no step to copy.
        engine.world().positionOf(new EntityTarget(owner)).ifPresent(p -> last.put(owner, p.position()));
        if (mode(owner) != Mode.COPY) return; // (still: it stays; a puppet blinks by itself, see Floors.glide)
        String cue = engine.loadouts().characterOf(owner).map(CharacterDef::glide).map(CharacterDef.Glide::cue).orElse(null);
        for (UUID clone : clonesOf(owner)) {
            if (engine.tags().has(clone, Tags.DASHING)) continue;
            engine.floors().blink(clone, owner, reflect(clone, direction), distance, cue);
        }
    }

    /** Stop everything (plugin disable). */
    public void shutdown() {
        for (UUID owner : List.copyOf(modes.keySet())) forget(owner);
        ownerOf.clear();
        mirror.clear();
        followers.clear();
        lastLook.clear();
        heading.clear();
        crouched.clear();
        last.clear();
        if (ticker != null) ticker.cancel();
        ticker = null;
    }
}
