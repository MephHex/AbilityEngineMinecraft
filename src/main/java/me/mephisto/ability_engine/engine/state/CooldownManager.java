package me.mephisto.ability_engine.engine.state;

import me.mephisto.ability_engine.engine.platform.GameClock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cooldowns in server ticks. Reads the clock itself, so callers can't pass the wrong "now". */
public final class CooldownManager {

    private final GameClock clock;
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

    public CooldownManager(GameClock clock) {
        this.clock = clock;
    }

    public boolean isReady(UUID caster, String abilityId) {
        return remainingTicks(caster, abilityId) == 0;
    }

    public long remainingTicks(UUID caster, String abilityId) {
        long ready = readyAt.getOrDefault(caster, Map.of()).getOrDefault(abilityId, 0L);
        return Math.max(0, ready - clock.now());
    }

    /** Was trigger(). */
    public void start(UUID caster, String abilityId, int cooldownTicks) {
        if (cooldownTicks <= 0) return;
        readyAt.computeIfAbsent(caster, k -> new HashMap<>()).put(abilityId, clock.now() + cooldownTicks);
    }

    public void clear(UUID caster, String abilityId) {
        Map<String, Long> mine = readyAt.get(caster);
        if (mine != null) mine.remove(abilityId);
    }

    public void clearAll(UUID caster) { readyAt.remove(caster); }
}
