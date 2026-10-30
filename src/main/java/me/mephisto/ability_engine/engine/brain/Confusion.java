package me.mephisto.ability_engine.engine.brain;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.tag.TagListener;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Confused ({@code state.confused}, Confuse Ray): every tick whoever has it is turned to face the one they go after
 * (within {@link #RANGE}, the nearest of the first kind there is: their teammates, then the neutral with no team (camps,
 * bosses), then their enemies, and only then whoever confused them), runs at them
 * (their own walking is held meanwhile, see the platform's run), and uses their primary on them as often as it's ready
 * (as if they pressed it; their status's friendly fire lets it hit a teammate). A crossbow (a quiver) is loaded for them
 * once its draw time has passed, then fired. A brain-driven enemy is driven by this instead of its brain. Nobody in range
 * at all: they're stunned meanwhile (the stun's tags). Stunned otherwise: nothing; rooted: they turn and attack.
 */
public final class Confusion implements TagListener {

    /** How far they look for a teammate to go after (blocks). */
    static final double RANGE = 24;
    /** They stop running this close to them (blocks): in reach of a swing. */
    static final double STOP = 2.0;
    /** A crossbow's draw with no Quick Charge (ticks), and what each Quick Charge level takes off (vanilla). */
    private static final int DRAW = 25, DRAW_PER_LEVEL = 5;
    /** Their view is turned again only when it's off by more than this (degrees): no needless camera nudges. */
    private static final double TURN_FROM = 3;

    private final AbilityEngine engine;
    private final Set<UUID> confused = new LinkedHashSet<>();
    /** A crossbow being drawn for them: since when (tick). */
    private final Map<UUID, Long> drawing = new HashMap<>();
    private TaskHandle ticker;

    public Confusion(AbilityEngine engine) {
        this.engine = engine;
    }

    @Override
    public void onTagAdded(UUID entity, String tag) {
        if (!Tags.CONFUSED.equals(tag)) return;
        confused.add(entity);
        if (ticker == null) ticker = engine.scheduler().every(1, 1, this::tick);
    }

    @Override
    public void onTagRemoved(UUID entity, String tag) {
        if (!Tags.CONFUSED.equals(tag)) return;
        confused.remove(entity);
        drawing.remove(entity);
        undaze(entity);
        engine.movement().stop(entity);
    }

    /** Is this entity confused right now (its brain stands aside)? */
    public boolean drives(UUID entity) { return confused.contains(entity); }

    private void tick() {
        if (confused.isEmpty()) {
            if (ticker != null) ticker.cancel();
            ticker = null;
            return;
        }
        for (UUID id : List.copyOf(confused)) {
            if (!engine.world().isAlive(id)) { // (dying cleared their tags: nothing of ours to take back)
                confused.remove(id);
                drawing.remove(id);
                dazed.remove(id);
                continue;
            }
            if (!engine.tags().has(id, Tags.CONFUSED)) {
                confused.remove(id);
                drawing.remove(id);
                undaze(id);
                continue;
            }
            UUID source = sourceOf(id).orElse(null);
            UUID mate = closest(id, Pick.TEAMMATE, source);            // their own teammates first...
            if (mate == null) mate = closest(id, Pick.NEUTRAL, source);  // ...then the neutral (camps, bosses: no team)...
            if (mate == null) mate = closest(id, Pick.ENEMY, source);    // ...then their enemies...
            if (mate == null && inReach(id, source)) mate = source;       // ...and only then whoever confused them
            if (mate == null) { // nobody in range at all: as good as stunned
                daze(id);
                continue;
            }
            undaze(id);
            if (engine.tags().has(id, Tags.STUNNED)) continue;
            var me = engine.world().aimOf(id);
            var them = engine.world().positionOf(new EntityTarget(mate));
            var at = engine.world().positionOf(new EntityTarget(id));
            if (me.isEmpty() || them.isEmpty() || at.isEmpty()) continue;
            Vec3 look = them.get().position().subtract(me.get().eye());
            if (!look.isZero() && angle(me.get().direction(), look) > TURN_FROM) engine.movement().face(id, look);
            Vec3 flat = new Vec3(look.x(), 0, look.z());
            double away = new Vec3(them.get().position().x() - at.get().position().x(), 0,
                    them.get().position().z() - at.get().position().z()).length();
            if (away > STOP && !flat.isZero() && !engine.tags().has(id, Tags.BLOCK_MOVE)) {
                engine.movement().run(id, flat.normalize());
            }
            attack(id);
        }
    }

    /** Their primary, as if they pressed it (its cooldown and crowd control decide). A crossbow is drawn for them first. */
    private void attack(UUID id) {
        var quivers = engine.quivers();
        if (quivers.has(id) && !quivers.isLoaded(id) && !quivers.rapidFire(id)) {
            long now = engine.clock().now();
            long since = drawing.computeIfAbsent(id, k -> now);
            double draw = Math.max(0, DRAW - DRAW_PER_LEVEL * quivers.reloadSpeed(id))
                    / Math.max(0.1, engine.stats().attackSpeedMultiplier(id));
            if (now - since < draw || !quivers.tryLoad(id)) return; // still drawing (or it can't be loaded now)
            drawing.remove(id);
        }
        if (engine.loadouts().activate(id, Slots.PRIMARY, true).success()) engine.movement().swing(id);
    }

    /** The ones held stunned for having nobody in range (the tags we gave them, to take back). */
    private final Set<UUID> dazed = new LinkedHashSet<>();
    private static final List<String> DAZE = List.of(Tags.STUNNED, Tags.BLOCK_MOVE, Tags.BLOCK_ABILITY);

    /** Nobody in range: they're stunned meanwhile (the same tags as a stun: its look, its label, no moving or acting). */
    private void daze(UUID id) {
        if (!dazed.add(id)) return;
        for (String tag : DAZE) engine.tags().grant(id, tag);
        drawing.remove(id);
        engine.movement().stop(id);
    }

    private void undaze(UUID id) {
        if (!dazed.remove(id)) return;
        for (String tag : DAZE) engine.tags().revoke(id, tag);
    }

    /** Who they go after, in order: their teammates, the neutral (no team), their enemies (not whoever confused them). */
    private enum Pick { TEAMMATE, NEUTRAL, ENEMY }

    /** Who confused them (the source of their confusing status), if anyone. */
    public java.util.Optional<UUID> sourceOf(UUID id) {
        for (var s : engine.statuses().on(id)) {
            if (s.def().grantedTags().contains(Tags.CONFUSED) && s.source() != null && !s.source().equals(id)) {
                return java.util.Optional.of(s.source());
            }
        }
        return java.util.Optional.empty();
    }

    /** Whoever confused them is there to go after: alive, within reach, not hidden or untargetable. */
    private boolean inReach(UUID id, UUID source) {
        if (source == null || !engine.world().isAlive(source) || engine.tags().has(source, Tags.UNTARGETABLE)
                || engine.tags().has(source, Tags.HIDDEN)) return false;
        var a = engine.world().positionOf(new EntityTarget(id));
        var b = engine.world().positionOf(new EntityTarget(source));
        return a.isPresent() && b.isPresent() && a.get().world().equals(b.get().world())
                && a.get().position().distance(b.get().position()) <= RANGE;
    }

    /**
     * The nearest one within reach (not themselves, not whoever confused them, alive, not hidden or untargetable) of
     * that kind: on their own team, on no team (neutral: camps, bosses), or on another team. Null if there's none.
     */
    private UUID closest(UUID id, Pick pick, UUID source) {
        var team = engine.teams().teamOf(id);
        var at = engine.world().positionOf(new EntityTarget(id));
        if (at.isEmpty() || (pick == Pick.TEAMMATE && team.isEmpty())) return null;
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntitySnapshot e : engine.world().livingEntitiesNear(at.get(), RANGE)) {
            if (e.id().equals(id) || e.id().equals(source) || !engine.world().isAlive(e.id())) continue;
            var theirs = engine.teams().teamOf(e.id());
            Pick kind = theirs.isEmpty() ? Pick.NEUTRAL
                    : team.isPresent() && team.get().equals(theirs.get()) ? Pick.TEAMMATE : Pick.ENEMY;
            if (kind != pick) continue;
            if (engine.tags().has(e.id(), Tags.UNTARGETABLE) || engine.tags().has(e.id(), Tags.HIDDEN)) continue;
            double d = e.center().distance(at.get().position());
            if (d < bestDist) {
                bestDist = d;
                best = e.id();
            }
        }
        return best;
    }

    private static double angle(Vec3 a, Vec3 b) {
        double la = a.length(), lb = b.length();
        if (la < 1e-9 || lb < 1e-9) return 180;
        double cos = Math.max(-1, Math.min(1, a.dot(b) / (la * lb)));
        return Math.toDegrees(Math.acos(cos));
    }

    /** Plugin disable. */
    public void shutdown() {
        for (UUID id : List.copyOf(dazed)) undaze(id);
        confused.clear();
        drawing.clear();
        if (ticker != null) ticker.cancel();
        ticker = null;
    }
}
