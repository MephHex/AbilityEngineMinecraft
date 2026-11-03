package me.mephisto.ability_engine.engine.graph;

import me.mephisto.ability_engine.engine.AbilityEngine;

import java.util.UUID;

/**
 * Time as it runs for one entity since this started: each tick counts as their time scale right then (0.5: half a
 * tick; see {@link AbilityEngine#timeScale}). Read it every tick while it matters, so a slow that lands midway counts
 * from then on. While their time runs normally it's exactly the ticks passed.
 */
public final class LocalTime {

    private final AbilityEngine engine;
    private final UUID who;
    private final long startedAt;
    private long at;
    private double elapsed;

    public LocalTime(AbilityEngine engine, UUID who) {
        this.engine = engine;
        this.who = who;
        this.startedAt = engine.clock().now();
        this.at = startedAt;
    }

    /** Ticks passed for them so far. */
    public double elapsed() {
        long now = engine.clock().now();
        if (now > at) {
            elapsed += (now - at) * engine.timeScale(who);
            at = now;
        }
        return elapsed;
    }

    /** Whether their time has run differently from the clock's so far. */
    public boolean bent() { return Math.abs(elapsed() - (engine.clock().now() - startedAt)) > 1e-9; }

    /** When a bar that fills over real ticks would have had to start to be as far along now. */
    public long barStart() { return engine.clock().now() - Math.round(elapsed()); }
}
