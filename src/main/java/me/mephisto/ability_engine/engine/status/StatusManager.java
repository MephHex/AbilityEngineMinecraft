package me.mephisto.ability_engine.engine.status;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.effect.EffectConfig;
import me.mephisto.ability_engine.engine.platform.GameClock;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.tag.TagManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies, stacks and expires statuses. The single source of truth for duration —
 * replaces CrowdControlManager and the private removal timer that lived inside StunEffect.
 */
public final class StatusManager {

    private final GameClock clock;
    private final TaskScheduler scheduler;
    private final TagManager tags;
    private final StatusRegistry registry;
    private final EngineLog log;
    private final Map<UUID, Map<String, ActiveStatus>> active = new HashMap<>();
    private EffectApplier effectApplier = (source, target, effects) -> {};

    /** Runs a status's tick effects (the engine wires this up; statuses don't know about effects). */
    @FunctionalInterface
    public interface EffectApplier {
        void apply(UUID source, UUID target, java.util.List<EffectConfig> effects);
    }

    public void setEffectApplier(EffectApplier applier) { this.effectApplier = applier; }

    /** Starts a status's looping cue on its holder (the engine wires this up). */
    @FunctionalInterface
    public interface CueStarter {
        me.mephisto.ability_engine.engine.platform.CueHandle start(UUID holder, String cue);
    }

    private CueStarter cueStarter = (holder, cue) -> me.mephisto.ability_engine.engine.platform.CueHandle.NONE;

    public void setCueStarter(CueStarter starter) { this.cueStarter = starter; }

    /** Who dies when a kill_on_end status runs out (the engine: it checks what saves them first). */
    private java.util.function.Consumer<UUID> killer = id -> {};

    public void setKiller(java.util.function.Consumer<UUID> killer) { this.killer = killer; }

    public StatusManager(GameClock clock, TaskScheduler scheduler, TagManager tags, StatusRegistry registry, EngineLog log) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.tags = tags;
        this.registry = registry;
        this.log = log;
    }

    /** Apply with the definition's default duration. */
    public void apply(UUID target, String statusId, UUID source) {
        StatusDef def = registry.require(statusId);
        apply(target, def, def.defaultDurationTicks(), source);
    }

    public void apply(UUID target, String statusId, int durationTicks, UUID source) {
        apply(target, registry.require(statusId), durationTicks, source);
    }

    /** Told after every status application (new or stacked/refreshed). */
    @FunctionalInterface
    public interface ApplyListener {
        void applied(UUID target, StatusDef def, int durationTicks, UUID source);
    }

    private final List<ApplyListener> applyListeners = new ArrayList<>();

    public void addApplyListener(ApplyListener listener) { applyListeners.add(listener); }

    /** Can refuse a status before it lands (e.g. debuff immunity). */
    @FunctionalInterface
    public interface Guard {
        /** True: {@code def} doesn't land on {@code target}. */
        boolean blocks(UUID target, StatusDef def, UUID source);
    }

    private final List<Guard> guards = new ArrayList<>();

    public void addGuard(Guard guard) { guards.add(guard); }

    /** A debuff: not a buff, and put on you by someone else (or by nobody: the world). */
    public static boolean isDebuff(UUID target, StatusDef def, UUID source) {
        return !def.positive() && !target.equals(source);
    }

    /** The crowd-control tags (with a slower move or attack speed, a status's crowd-control parts). */
    private static final java.util.Set<String> CROWD_CONTROL_TAGS = java.util.Set.of(
            me.mephisto.ability_engine.engine.tag.Tags.STUNNED, me.mephisto.ability_engine.engine.tag.Tags.SILENCED,
            me.mephisto.ability_engine.engine.tag.Tags.DISARMED, me.mephisto.ability_engine.engine.tag.Tags.ROOTED,
            me.mephisto.ability_engine.engine.tag.Tags.SLOWED, me.mephisto.ability_engine.engine.tag.Tags.FROZEN,
            me.mephisto.ability_engine.engine.tag.Tags.PARALYZED, me.mephisto.ability_engine.engine.tag.Tags.BLOCK_ABILITY,
            me.mephisto.ability_engine.engine.tag.Tags.BLOCK_MOVE, me.mephisto.ability_engine.engine.tag.Tags.BLOCK_WALK);

    /**
     * Has crowd-control parts: it stops or hinders them (stunned, rooted, silenced, disarmed, frozen, paralyzed, held in
     * place, slowed: a crowd-control tag, or a slower move or attack speed). Those parts are what state.unstoppable
     * switches off; anything else about it (damage over time, other tags) it doesn't.
     */
    public static boolean isCrowdControl(StatusDef def) {
        return def.moveSpeed() < 1 || def.attackSpeed() < 1
                || def.grantedTags().stream().anyMatch(CROWD_CONTROL_TAGS::contains);
    }

    /**
     * Nothing but crowd control (a stun, a root, a plain slow): it doesn't land on the unstoppable at all, so it can't
     * kick in once they aren't any more. A slowing poison isn't: it lands, and only its slow is switched off.
     */
    public static boolean isPureCrowdControl(StatusDef def) {
        return isCrowdControl(def) && CROWD_CONTROL_TAGS.containsAll(def.grantedTags())
                && def.tickEffects().isEmpty() && def.onHit().isEmpty()
                && def.damageDealt() == 1 && def.damageTaken() == 1 && def.farDamage() == null
                && def.moveSpeed() <= 1 && def.attackSpeed() <= 1 && def.jumpBoost() == 0 && def.healingTaken() == 1;
    }

    /** They're unstoppable and this is a debuff on them: its crowd-control parts don't apply right now. */
    public boolean crowdControlSuppressed(UUID holder, ActiveStatus s) {
        return tags.has(holder, me.mephisto.ability_engine.engine.tag.Tags.UNSTOPPABLE) && isDebuff(holder, s.def, s.source);
    }

    /** The tags {@code s} should give its holder right now: its own, without the crowd-control ones while suppressed. */
    private java.util.Set<String> tagsFor(UUID holder, ActiveStatus s) {
        if (!crowdControlSuppressed(holder, s)) return s.def.grantedTags();
        return s.def.grantedTags().stream().filter(t -> !CROWD_CONTROL_TAGS.contains(t))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Give {@code s}'s holder exactly the tags it should give them now (some came or went with unstoppable). */
    private void resyncTags(UUID holder, ActiveStatus s) {
        if (active.getOrDefault(holder, Map.of()).get(s.def.id()) != s) return; // gone meanwhile
        java.util.Set<String> want = tagsFor(holder, s);
        java.util.Set<String> had = s.granted;
        s.granted = want;
        for (String t : had) if (!want.contains(t)) tags.revoke(holder, t);
        for (String t : want) if (!had.contains(t)) tags.grant(holder, t);
    }

    /**
     * They just became unstoppable: crowd control on them lets go. Pure crowd control (a stun, a root) ends; anything
     * else (a slowing poison) stays, without its crowd-control tags meanwhile (its slow doesn't count either, see
     * {@link #crowdControlSuppressed}).
     */
    public void suppressCrowdControl(UUID holder) {
        for (ActiveStatus s : on(holder)) {
            if (!isDebuff(holder, s.def, s.source) || !isCrowdControl(s.def)) continue;
            if (isPureCrowdControl(s.def)) remove(holder, s.def.id());
            else resyncTags(holder, s);
        }
    }

    /** No longer unstoppable: what's still on them hinders them again (a poison's slow, for the time it has left). */
    public void restoreCrowdControl(UUID holder) {
        for (ActiveStatus s : on(holder)) resyncTags(holder, s);
    }

    public void apply(UUID target, StatusDef def, int durationTicks, UUID source) {
        for (Guard g : List.copyOf(guards)) {
            if (g.blocks(target, def, source)) {
                log.debug(() -> "status " + def.id() + " on " + target + " blocked");
                return;
            }
        }
        String requires = def.links().requires();
        if (requires != null && !has(target, requires)) return; // it only lasts while that does: nothing to hang on
        if (def.singleTarget() && source != null) { // one target at a time: a new one wipes it off the old ones
            for (var e : List.copyOf(active.entrySet())) {
                if (e.getKey().equals(target)) continue;
                ActiveStatus other = e.getValue().get(def.id());
                if (other != null && source.equals(other.source())) remove(e.getKey(), def.id());
            }
        }
        int before = find(target, def.id()).map(ActiveStatus::stacks).orElse(0);
        applyInternal(target, def, durationTicks, source);
        for (ApplyListener l : List.copyOf(applyListeners)) l.applied(target, def, durationTicks, source);
        String atMax = def.links().atMax();
        if (atMax != null && before < def.maxStacks()
                && find(target, def.id()).map(s -> s.stacks() >= def.maxStacks()).orElse(false)) {
            apply(target, atMax, target); // full: it sets that off (their own)
        }
    }

    private void applyInternal(UUID target, StatusDef def, int durationTicks, UUID source) {
        long now = clock.now();
        long newExpiry = durationTicks <= 0 ? Long.MAX_VALUE : now + durationTicks;
        Map<String, ActiveStatus> mine = active.computeIfAbsent(target, k -> new HashMap<>());
        ActiveStatus existing = mine.get(def.id());

        if (existing == null) {
            ActiveStatus s = new ActiveStatus(def, source, newExpiry);
            s.span = Math.max(0, durationTicks);
            mine.put(def.id(), s);
            s.granted = tagsFor(target, s); // unstoppable: without its crowd-control tags
            tags.grantAll(target, s.granted);
            if (def.links().cue() != null) s.cue = cueStarter.start(target, def.links().cueFor(s.stacks));
            if (def.tickEvery() > 0 && !def.tickEffects().isEmpty()) {
                // Refreshing doesn't restart the rhythm: ticks keep their pace until the status ends.
                // Scheduled BEFORE the expiry, so a tick landing on the last tick still happens
                // (a 40-tick burn ticking every 10 ticks 4 times, not 3).
                s.tickTask = scheduler.every(def.tickEvery(), def.tickEvery(),
                        () -> effectApplier.apply(s.source, target, def.tickEffects()));
            }
            schedule(target, s);
            log.debug(() -> "status +" + def.id() + " on " + target + " for " + durationTicks + "t");
            return;
        }

        long expiredAt = existing.expiresAt;
        switch (def.stacking()) {
            case REFRESH -> existing.expiresAt = Math.max(existing.expiresAt, newExpiry);
            case EXTEND -> existing.expiresAt = existing.isInfinite() || newExpiry == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : existing.expiresAt + durationTicks;
            case STACK -> {
                int was = existing.stacks;
                existing.stacks = Math.min(def.maxStacks(), existing.stacks + 1);
                existing.expiresAt = newExpiry;
                if (existing.stacks != was) restartCue(target, existing);
            }
        }
        if (existing.expiresAt != expiredAt && !existing.isInfinite()) existing.span = existing.expiresAt - now; // a new time
        schedule(target, existing);
    }

    private void schedule(UUID target, ActiveStatus s) {
        if (s.expiryTask != null) s.expiryTask.cancel();
        s.expiryTask = null;
        if (s.isInfinite()) return;
        long delay = Math.max(1, s.expiresAt - clock.now());
        s.expiryTask = scheduler.after(delay, () -> {
            Map<String, ActiveStatus> mine = active.get(target);
            if (mine == null || mine.get(s.def.id()) != s) return;
            if (s.def.decayEvery() > 0 && s.stacks > 1) { // decay: one stack at a time
                s.stacks--;
                restartCue(target, s);
                s.expiresAt = clock.now() + s.def.decayEvery();
                schedule(target, s);
                return;
            }
            // Its time is up and it kills: while it's still on them, so it can't save them from itself (a character's
            // on_lethal for this very status), but anything else still can (decaying health -> a spectral form).
            if (s.def.life().killOnEnd()) killer.accept(target);
            remove(target, s.def.id());
            String then = s.def.links().then();
            if (then != null && registry.find(then).isPresent()) apply(target, then, s.source); // its time is up: what comes next
        });
    }

    public void remove(UUID target, String statusId) {
        Map<String, ActiveStatus> mine = active.get(target);
        if (mine == null) return;
        ActiveStatus s = mine.remove(statusId);
        if (mine.isEmpty()) active.remove(target);
        if (s == null) return;
        if (s.expiryTask != null) s.expiryTask.cancel();
        if (s.tickTask != null) s.tickTask.cancel();
        if (s.cue != null) s.cue.stop();
        tags.revokeAll(target, s.granted);
        log.debug(() -> "status -" + statusId + " on " + target);
        for (ActiveStatus tied : on(target)) { // statuses that only last while this one did
            if (statusId.equals(tied.def.links().requires())) remove(target, tied.def.id());
        }
    }

    /** Take {@code count} stacks off a status; it ends when none are left. */
    public void removeStacks(UUID target, String statusId, int count) {
        Map<String, ActiveStatus> mine = active.get(target);
        ActiveStatus s = mine == null ? null : mine.get(statusId);
        if (s == null) return;
        if (s.stacks <= count) remove(target, statusId);
        else {
            s.stacks -= count;
            restartCue(target, s);
        }
    }

    /** A cue_per_stack status's stacks changed: its looping cue becomes the one for the new count. */
    private void restartCue(UUID target, ActiveStatus s) {
        if (!s.def.links().cuePerStack() || s.def.links().cue() == null) return;
        if (s.cue != null) s.cue.stop();
        s.cue = cueStarter.start(target, s.def.links().cueFor(s.stacks));
    }

    public void clear(UUID target) {
        Map<String, ActiveStatus> mine = active.get(target);
        if (mine == null) return;
        for (String id : new ArrayList<>(mine.keySet())) remove(target, id);
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(active.keySet())) clear(id);
    }

    public boolean has(UUID target, String statusId) { return find(target, statusId).isPresent(); }

    public Optional<ActiveStatus> find(UUID target, String statusId) {
        return Optional.ofNullable(active.getOrDefault(target, Map.of()).get(statusId));
    }

    public long remainingTicks(UUID target, String statusId) {
        return find(target, statusId).map(s -> s.isInfinite() ? Long.MAX_VALUE : Math.max(0, s.expiresAt - clock.now())).orElse(0L);
    }

    /**
     * A status as a gauge: its stacks, and how much of its duration is left (0..1, measured against its
     * default duration; 1 for infinite). Empty if the target doesn't have it.
     */
    public record Gauge(int stacks, double fraction) {}

    /** A gauge by stacks instead: filled to its stacks out of its max_stacks (a status that builds up). */
    public Optional<Gauge> stackGauge(UUID target, String statusId) {
        return find(target, statusId).map(s -> new Gauge(s.stacks(), Math.min(1, (double) s.stacks() / s.def().maxStacks())));
    }

    public Optional<Gauge> gauge(UUID target, String statusId) {
        return find(target, statusId).map(s -> {
            if (s.isInfinite()) return new Gauge(s.stacks(), 1);
            // against the time it was given (e.g. an ultimate's longer one), else its default duration
            double full = Math.max(1, s.span > 0 ? s.span : s.def().defaultDurationTicks());
            double left = Math.max(0, s.expiresAt() - clock.now());
            return new Gauge(s.stacks(), Math.min(1, left / full));
        });
    }

    public List<ActiveStatus> on(UUID target) {
        return List.copyOf(active.getOrDefault(target, Map.of()).values());
    }
}
