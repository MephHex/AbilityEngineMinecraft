package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ultimate with recasts lasts its whole cast: its charge is spent (or its cooldown starts) only when the cast is over,
 * not at the first press nor at a recast. And a status's on-hit with {@code sp_of: source} uses its giver's spell power.
 */
class UltimateRecastTest {

    private TestEngine t;
    private UUID hero;

    /** "volley": F, then F again twice (each window 2s); the second recast ends it. 10s cooldown (charge off). */
    private void setup(boolean charge) {
        t = new TestEngine();
        t.load(map(
                "abilities", map(
                        "volley", map("cooldown", 200, "nodes", map(
                                "first", map("type", "print", "message", "first", "next", "wait1"),
                                "wait1", map("type", "await_recast", "window", 40, "on", map("recast", "wait2")),
                                "wait2", map("type", "await_recast", "window", 40, "on", map("recast", "last")),
                                "last", map("type", "print", "message", "last"))),
                        "jab", map("nodes", map("n", map("type", "print", "message", "jab")))),
                "characters", map("hero", map("slots", map("primary", "jab", "ultimate", "volley")))));
        if (charge) t.engine.ultCharge().configure(true, 5, 2, 0);
        hero = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(hero, "hero");
        if (charge) t.engine.ultCharge().set(hero, 100);
    }

    private boolean pressF() { return t.engine.loadouts().activate(hero, Slots.ULTIMATE).success(); }

    private boolean running() { return t.engine.instances().isRunning(hero, "volley"); }

    @Test
    void theChargeStaysUntilTheLastRecast() {
        setup(true);
        assertTrue(pressF());
        assertEquals(100, t.engine.ultCharge().of(hero), 1e-9, "a recast to come: not spent yet");
        assertTrue(pressF(), "F again: the first recast");
        assertTrue(running());
        assertEquals(100, t.engine.ultCharge().of(hero), 1e-9, "another recast to come");
        assertTrue(pressF(), "the second recast: it's over");
        assertFalse(running());
        assertEquals(0, t.engine.ultCharge().of(hero), 1e-9, "now it's used up");
    }

    @Test
    void unusedRecastsSpendItWhenTheWindowRunsOut() {
        setup(true);
        assertTrue(pressF());
        t.time.advance(20);
        assertEquals(100, t.engine.ultCharge().of(hero), 1e-9, "the window still open");
        assertTrue(pressF(), "the first recast");
        assertEquals(1, t.engine.instances().count(), "a recast, never a second cast");
        t.time.advance(45); // the second window runs out: the cast is over
        assertFalse(running());
        assertEquals(0, t.engine.ultCharge().of(hero), 1e-9);
    }

    @Test
    void withCooldownsTheCooldownStartsWhenTheCastEnds() {
        setup(false);
        assertTrue(pressF());
        assertTrue(pressF(), "a recast");
        assertEquals(0, t.engine.cooldowns().remainingTicks(hero, "volley"), "no cooldown at a recast");
        assertTrue(pressF());
        assertFalse(running());
        assertTrue(t.engine.cooldowns().remainingTicks(hero, "volley") > 0, "the cast is over: now the cooldown");
    }

    @Test
    void aStatusOnHitCanScaleWithItsGiversSpellPower() {
        t = new TestEngine();
        Map<String, Object> hit = map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "radius", "radius", 4), "effects", list(map("id", "damage", "amount", 1)))));
        t.load(map(
                "abilities", map("swing", hit),
                "statuses", map(
                        "tome", map("spell_power", 50),
                        "blessing", map("on_hit", list(map("id", "damage", "amount", 10, "sp", 0.5, "sp_of", "source"))),
                        "own_blessing", map("on_hit", list(map("id", "damage", "amount", 10, "sp", 0.5)))),
                "characters", map("ally", map("slots", map("primary", "swing")))));
        UUID fae = t.spawn(20, 1, 0), ally = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(ally, "ally");
        t.engine.statuses().apply(fae, "tome", 0, fae); // she has 50 spell power, the ally none
        t.engine.statuses().apply(ally, "blessing", 0, fae);
        UUID dummy = t.spawn(2, 1, 0);
        assertTrue(t.engine.loadouts().activate(ally, Slots.PRIMARY).success());
        assertEquals(1 + 10 + 25, t.damage(dummy), 1e-9, "+10 and half of HER 50 spell power");

        t.engine.statuses().remove(ally, "blessing");
        t.engine.statuses().apply(ally, "own_blessing", 0, fae);
        UUID other = t.spawn(-2, 1, 0);
        t.time.advance(40);
        assertTrue(t.engine.loadouts().activate(ally, Slots.PRIMARY).success());
        assertEquals(1 + 10, t.damage(other), 1e-9, "without sp_of: the holder's own (none)");
    }
}
