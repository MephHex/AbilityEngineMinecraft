package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.state.ResourceDef;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceTest {

    private static final ResourceDef FOCUS = new ResourceDef("focus", 100, 12, 20, 9, null);

    @Test
    void definedPoolStartsFullAndRegensAfterTheDelay() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        var res = t.engine.resources();
        res.define(p, FOCUS);
        assertEquals(100.0, res.value(p, "focus"), 1e-9);

        res.consume(p, "focus", 50);
        t.time.advance(20);
        assertEquals(50.0, res.value(p, "focus"), 1e-9, "no regen during the 1s delay");
        t.time.advance(20);
        assertEquals(62.0, res.value(p, "focus"), 1e-9, "then 12 per second");
        t.time.advance(400);
        assertEquals(100.0, res.value(p, "focus"), 1e-9, "capped at max");
    }

    @Test
    void spendingRestartsTheDelay() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        var res = t.engine.resources();
        res.define(p, FOCUS);
        res.consume(p, "focus", 60);
        t.time.advance(15);
        res.consume(p, "focus", 10);
        t.time.advance(15);
        assertEquals(30.0, res.value(p, "focus"), 1e-9, "the second spend pushed regen back");
    }

    @Test
    void plainPoolsStillWorkLikeBefore() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        var res = t.engine.resources();
        res.set(p, "mana", 5);
        res.consume(p, "mana", 2);
        t.time.advance(100);
        assertEquals(3, res.get(p, "mana"), "no max, no regen");
        assertTrue(res.fraction(p, "mana").isEmpty());
        assertFalse(res.has(p, "mana", 4));
        assertTrue(res.has(p, "mana", 0), "free is always affordable");
    }
}
