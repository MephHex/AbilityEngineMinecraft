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

    public void apply(UUID target, StatusDef def, int durationTicks, UUID source) {
        for (Guard g : List.copyOf(guards)) {
            if (g.blocks(target, def, source)) {
                log.debug(() -> "status " + def.id() + " on " + target + " blocked");
                return;
            }
        }
        applyInternal(target, def, durationTicks, source);
        for (ApplyListener l : List.copyOf(applyListeners)) l.applied(target, def, durationTicks, source);
    }

    private void applyInternal(UUID target, StatusDef def, int durationTicks, UUID source) {
        long now = clock.now();
        long newExpiry = durationTicks <= 0 ? Long.MAX_VALUE : now + durationTicks;
        Map<String, ActiveStatus> mine = active.computeIfAbsent(target, k -> new HashMap<>());
        ActiveStatus existing = mine.get(def.id());

        if (existing == null) {
            ActiveStatus s = new ActiveStatus(def, source, newExpiry);
            mine.put(def.id(), s);
            tags.grantAll(target, def.grantedTags());
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

        switch (def.stacking()) {
            case REFRESH -> existing.expiresAt = Math.max(existing.expiresAt, newExpiry);
            case EXTEND -> existing.expiresAt = existing.isInfinite() || newExpiry == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : existing.expiresAt + durationTicks;
            case STACK -> {
                existing.stacks = Math.min(def.maxStacks(), existing.stacks + 1);
                existing.expiresAt = newExpiry;
            }
        }
        schedule(target, existing);
    }

    private void schedule(UUID target, ActiveStatus s) {
        if (s.expiryTask != null) s.expiryTask.cancel();
        s.expiryTask = null;
        if (s.isInfinite()) return;
        long delay = Math.max(1, s.expiresAt - clock.now());
        s.expiryTask = scheduler.after(delay, () -> {
            Map<String, ActiveStatus> mine = active.get(target);
            if (mine != null && mine.get(s.def.id()) == s) remove(target, s.def.id());
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
        tags.revokeAll(target, s.def.grantedTags());
        log.debug(() -> "status -" + statusId + " on " + target);
    }

    /** Take {@code count} stacks off a status; it ends when none are left. */
    public void removeStacks(UUID target, String statusId, int count) {
        Map<String, ActiveStatus> mine = active.get(target);
        ActiveStatus s = mine == null ? null : mine.get(statusId);
        if (s == null) return;
        if (s.stacks <= count) remove(target, statusId);
        else s.stacks -= count;
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

    public Optional<Gauge> gauge(UUID target, String statusId) {
        return find(target, statusId).map(s -> {
            if (s.isInfinite()) return new Gauge(s.stacks(), 1);
            double full = Math.max(1, s.def().defaultDurationTicks());
            double left = Math.max(0, s.expiresAt() - clock.now());
            return new Gauge(s.stacks(), Math.min(1, left / full));
        });
    }

    public List<ActiveStatus> on(UUID target) {
        return List.copyOf(active.getOrDefault(target, Map.of()).values());
    }
}
