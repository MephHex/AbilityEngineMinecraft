package me.mephisto.ability_engine.engine.brain;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Brains: what drives a non-player playing a character with a {@code brain:} (a PvE enemy, a boss, a test dummy). Players
 * are never driven, whatever they play. Every tick it faces its target (and walks after it, with {@code chase}); every
 * {@code think_every} ticks it picks a target and tries its rules.
 * <ul>
 *   <li><b>Target:</b> the nearest enemy within {@code range} (in sight, with {@code sight}); it keeps the one it has while
 *       that one stays valid (alive, in range and sight, targetable and not hidden). Clones count: they can be fooled.</li>
 *   <li><b>Rules:</b> tried in order; the first whose conditions all hold and that casts (its cooldown, costs and crowd
 *       control allowing) wins, one cast per decision. A rule casts a slot (as if its key were pressed) or an ability by
 *       id. Aim previews are skipped (it fires at what it faces). {@code every} keeps a rule from firing again too soon,
 *       {@code once} lets it fire only once (an enrage), {@code recast} presses it again that many ticks later.</li>
 * </ul>
 * Dying, or a new character, starts it over.
 */
public final class BrainManager {

    private final AbilityEngine engine;
    private final Map<UUID, Mind> minds = new HashMap<>();
    private TaskHandle ticker;

    /** One driven entity's state: its target, and when each of its rules last fired. */
    private static final class Mind {
        final String character;
        UUID target;
        long nextThought;
        final Map<Integer, Long> lastCast = new HashMap<>();
        final java.util.Set<Integer> spent = new java.util.HashSet<>();

        Mind(String character) { this.character = character; }
    }

    public BrainManager(AbilityEngine engine) {
        this.engine = engine;
        engine.loadouts().onAssign(this::assigned);
    }

    private void assigned(UUID entity) {
        minds.remove(entity);
        var c = engine.loadouts().baseCharacterOf(entity);
        if (c.isEmpty() || c.get().brain() == null || engine.world().isPlayer(entity)) return;
        minds.put(entity, new Mind(c.get().id()));
        if (!engine.targeting().isQuickCast(entity)) engine.targeting().toggleQuickCast(entity); // no aim previews
        if (ticker == null) ticker = engine.scheduler().every(1, 1, this::tick);
    }

    /** Is this entity driven by a brain right now? */
    public boolean drives(UUID entity) { return minds.containsKey(entity); }

    /** Who it's after right now, if anyone. */
    public Optional<UUID> targetOf(UUID entity) {
        return Optional.ofNullable(minds.get(entity)).map(m -> m.target);
    }

    /** Its rules start over (it died, came back): nothing spent, no target. */
    public void reset(UUID entity) {
        Mind m = minds.get(entity);
        if (m == null) return;
        m.target = null;
        m.lastCast.clear();
        m.spent.clear();
    }

    private void tick() {
        long now = engine.clock().now();
        for (var e : List.copyOf(minds.entrySet())) {
            UUID self = e.getKey();
            Mind mind = e.getValue();
            var character = engine.loadouts().baseCharacterOf(self);
            if (character.isEmpty() || !character.get().id().equals(mind.character) || character.get().brain() == null) {
                minds.remove(self); // a new character (or none): not this brain's any more
                continue;
            }
            if (!engine.world().isAlive(self)) continue;
            CharacterDef.Brain brain = character.get().brain();
            boolean thinking = now >= mind.nextThought;
            if (thinking) {
                mind.nextThought = now + brain.thinkEvery();
                mind.target = pickTarget(self, brain, mind.target);
            }
            if (engine.confusion().drives(self)) continue; // confused: it goes after its own teammates instead
            if (mind.target == null) continue;
            follow(self, brain, mind.target);
            if (thinking) think(self, brain, mind, now);
        }
        if (minds.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** The one it was after, if still valid; else the nearest valid enemy; else nobody. */
    private UUID pickTarget(UUID self, CharacterDef.Brain brain, UUID current) {
        var at = engine.world().positionOf(new EntityTarget(self));
        if (at.isEmpty()) return null;
        if (current != null && valid(self, brain, current, at.get().position())) return current;
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntitySnapshot e : engine.world().livingEntitiesNear(at.get(), brain.range())) {
            double d = e.center().distance(at.get().position());
            if (d < bestDist && valid(self, brain, e.id(), at.get().position())) {
                best = e.id();
                bestDist = d;
            }
        }
        return best;
    }

    private boolean valid(UUID self, CharacterDef.Brain brain, UUID other, Vec3 from) {
        if (other.equals(self) || !engine.world().isAlive(other) || !engine.teams().enemies(self, other)) return false;
        var tags = engine.tags();
        if (tags.has(other, Tags.UNTARGETABLE) || (tags.has(other, Tags.HIDDEN) && !tags.has(other, Tags.PUPPETEER))) return false;
        var pos = engine.world().positionOf(new EntityTarget(other));
        if (pos.isEmpty() || pos.get().position().distance(from) > brain.range()) return false;
        if (!brain.sight()) return true;
        var eye = engine.world().aimOf(self).map(a -> a.eye()).orElse(from);
        return engine.world().sweep(pos.get().world(), eye, pos.get().position(), 0, id -> true).isEmpty(); // (blocks only)
    }

    /** Facing it, and walking after it (chase) until close enough. */
    private void follow(UUID self, CharacterDef.Brain brain, UUID target) {
        var me = engine.world().aimOf(self);
        var them = engine.world().positionOf(new EntityTarget(target));
        if (me.isEmpty() || them.isEmpty()) return;
        Vec3 to = them.get().position().subtract(me.get().eye());
        if (!to.isZero()) engine.movement().face(self, to);
        if (brain.chase() == null || engine.tags().has(self, Tags.BLOCK_MOVE) || engine.tags().has(self, Tags.STUNNED)
                || engine.tags().has(self, Tags.BLOCK_WALK) || engine.tags().has(self, Tags.SWAPPING)) return;
        Vec3 flat = new Vec3(to.x(), 0, to.z());
        if (engine.tags().has(self, Tags.INVERTED)) { // controls inverted (Topsy-Turvy): it walks away from them instead
            if (!flat.isZero()) engine.movement().drive(self, flat.normalize().multiply(-brain.chase().speed()), false);
            return;
        }
        if (flat.length() <= brain.chase().stopAt()) return;
        engine.movement().drive(self, flat.normalize().multiply(brain.chase().speed()), false);
    }

    /** Its rules, in order: the first that holds and casts. */
    private void think(UUID self, CharacterDef.Brain brain, Mind mind, long now) {
        for (int i = 0; i < brain.rules().size(); i++) {
            var rule = brain.rules().get(i);
            if (rule.once() && mind.spent.contains(i)) continue;
            Long last = mind.lastCast.get(i);
            if (last != null && now - last < rule.every()) continue;
            if (!holds(self, mind.target, rule.when())) continue;
            if (!cast(self, rule.cast())) continue;
            mind.lastCast.put(i, now);
            if (rule.once()) mind.spent.add(i);
            if (rule.recast() > 0) {
                String what = rule.cast();
                engine.scheduler().after(rule.recast(), () -> {
                    if (engine.world().isAlive(self)) cast(self, what);
                });
            }
            return;
        }
    }

    private boolean cast(UUID self, String what) {
        if (Slots.ALL.contains(what)) return engine.loadouts().activate(self, what, true).success();
        return engine.activator().activate(self, what, true).success();
    }

    private boolean holds(UUID self, UUID target, List<CharacterDef.Brain.Condition> when) {
        for (var c : when) {
            boolean ok = switch (c.kind()) {
                case "target_within" -> distance(self, target) <= c.value();
                case "target_beyond" -> distance(self, target) >= c.value();
                case "health_below" -> share(self) < c.value();
                case "health_above" -> share(self) > c.value();
                case "target_health_below" -> share(target) < c.value();
                case "chance" -> engine.random().nextDouble() < c.value();
                case "has_tag" -> engine.tags().has(self, c.tag());
                case "lacks_tag" -> !engine.tags().has(self, c.tag());
                default -> false;
            };
            if (!ok) return false;
        }
        return true;
    }

    private double distance(UUID a, UUID b) {
        var pa = engine.world().positionOf(new EntityTarget(a));
        var pb = engine.world().positionOf(new EntityTarget(b));
        return pa.isEmpty() || pb.isEmpty() ? Double.MAX_VALUE : pa.get().position().distance(pb.get().position());
    }

    /** Health as a share of max (unknown: full). */
    private double share(UUID entity) {
        var f = engine.world().healthFraction(entity);
        return f.isPresent() ? f.getAsDouble() : 1;
    }

    /** Plugin disable. */
    public void shutdown() {
        minds.clear();
        if (ticker != null) ticker.cancel();
        ticker = null;
    }
}
