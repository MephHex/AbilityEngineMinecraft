package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.platform.GameClock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** When each entity was last in combat: dealt or took damage (every hit goes through DamageModifiers). */
public final class CombatTracker {

    private final GameClock clock;
    private final Map<UUID, Long> lastCombat = new HashMap<>();

    public CombatTracker(GameClock clock) {
        this.clock = clock;
    }

    /** A hit: both sides are in combat now. */
    public void hit(UUID attacker, UUID victim) {
        long now = clock.now();
        if (attacker != null) lastCombat.put(attacker, now);
        if (victim != null) lastCombat.put(victim, now);
    }

    /** Server tick of the last combat, or Long.MIN_VALUE if never. */
    public long lastCombat(UUID entity) { return lastCombat.getOrDefault(entity, Long.MIN_VALUE); }

    public void forget(UUID entity) { lastCombat.remove(entity); }
}
