package me.mephisto.ability_engine.engine.state;

import me.mephisto.ability_engine.engine.platform.GameClock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldowns in server ticks. Reads the clock itself, so callers can't pass the wrong "now".
 * <p>Abilities with charges (2+): one timeline per ability, "when are ALL charges back". Each use pushes
 * it out by one cooldown; the charges available are how many whole cooldowns are left before it. So
 * charges come back one at a time, and it only counts as on cooldown with none left.
 */
public final class CooldownManager {

    /** An ability's charges and the time each one takes to come back. */
    public record Charges(int max, int cooldownTicks) {
        public static final Charges SINGLE = new Charges(1, 0);
    }

    private final GameClock clock;
    private final java.util.function.Function<String, Charges> chargesOf;
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

    public CooldownManager(GameClock clock) {
        this(clock, id -> Charges.SINGLE);
    }

    /** @param chargesOf an ability's charges (from its definition) */
    public CooldownManager(GameClock clock, java.util.function.Function<String, Charges> chargesOf) {
        this.clock = clock;
        this.chargesOf = chargesOf;
    }

    public boolean isReady(UUID caster, String abilityId) {
        return remainingTicks(caster, abilityId) == 0;
    }

    /** Ticks until it can be used again (with charges: until one is back, 0 while any is left). */
    public long remainingTicks(UUID caster, String abilityId) {
        long left = rechargeTicks(caster, abilityId);
        Charges c = chargesOf.apply(abilityId);
        if (c.max() > 1) left -= (long) (c.max() - 1) * c.cooldownTicks();
        return Math.max(0, left);
    }

    /** Ticks until everything is back (all charges); same as {@link #remainingTicks} without charges. */
    public long rechargeTicks(UUID caster, String abilityId) {
        long ready = readyAt.getOrDefault(caster, Map.of()).getOrDefault(abilityId, 0L);
        return Math.max(0, ready - clock.now());
    }

    /** Charges available right now (1/0 for abilities without charges). */
    public int charges(UUID caster, String abilityId) {
        Charges c = chargesOf.apply(abilityId);
        if (c.max() <= 1 || c.cooldownTicks() <= 0) return isReady(caster, abilityId) ? 1 : 0;
        long left = rechargeTicks(caster, abilityId);
        int missing = (int) Math.ceil(left / (double) c.cooldownTicks());
        return Math.max(0, c.max() - missing);
    }

    /** Was trigger(). With charges: spends one (the next comes back {@code cooldownTicks} after the last). */
    public void start(UUID caster, String abilityId, int cooldownTicks) {
        if (cooldownTicks <= 0) return;
        Map<String, Long> mine = readyAt.computeIfAbsent(caster, k -> new HashMap<>());
        long now = clock.now();
        if (chargesOf.apply(abilityId).max() > 1) {
            mine.put(abilityId, Math.max(now, mine.getOrDefault(abilityId, 0L)) + cooldownTicks);
        } else {
            mine.put(abilityId, now + cooldownTicks);
        }
    }

    /** Take {@code ticks} off a running cooldown (never below ready). Nothing happens if it isn't running. */
    public void reduce(UUID caster, String abilityId, int ticks) {
        Map<String, Long> mine = readyAt.get(caster);
        Long ready = mine == null ? null : mine.get(abilityId);
        if (ready == null || ticks <= 0) return;
        long reduced = ready - ticks;
        if (reduced <= clock.now()) mine.remove(abilityId);
        else mine.put(abilityId, reduced);
    }

    public void clear(UUID caster, String abilityId) {
        Map<String, Long> mine = readyAt.get(caster);
        if (mine != null) mine.remove(abilityId);
    }

    public void clearAll(UUID caster) { readyAt.remove(caster); }
}
