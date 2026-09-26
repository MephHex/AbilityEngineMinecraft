package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the XP-bar HUD reads: engine.instances().castProgress(player). */
class CastBarTest {

    private static Object windUp(boolean bar) {
        return map("nodes", map(
                "wait", map("type", "delay", "ticks", 10, "cast_bar", bar, "next", "after"),
                "after", map("type", "delay", "ticks", 10)));   // keeps the cast alive, no bar
    }

    @Test
    void delayWithCastBarFillsThenDisappears() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(abilities("wind", windUp(true)));

        t.engine.activator().activate(p, "wind");
        assertEquals(0.0, t.engine.instances().castProgress(p).orElseThrow(), 1e-9);
        t.time.advance(5);
        assertEquals(0.5, t.engine.instances().castProgress(p).orElseThrow(), 1e-9);
        t.time.advance(5);
        assertEquals(Optional.empty(), t.engine.instances().castProgress(p), "bar gone once the wait is over");
        assertEquals(1, t.engine.instances().count(), "even though the cast continues");
    }

    @Test
    void plainDelayShowsNoBar() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(abilities("wind", windUp(false)));
        t.engine.activator().activate(p, "wind");
        t.time.advance(3);
        assertEquals(Optional.empty(), t.engine.instances().castProgress(p));
    }

    @Test
    void channelsShowABarForTheirDuration() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(abilities("beam", map("mode", map("type", "channel", "period", 4, "duration", 20),
                "nodes", map("p", map("type", "print", "message", "x")))));

        t.engine.activator().activate(p, "beam");
        t.time.advance(10);
        assertEquals(0.5, t.engine.instances().castProgress(p).orElseThrow(), 1e-9);
        t.time.advance(10);
        assertEquals(Optional.empty(), t.engine.instances().castProgress(p));
    }

    @Test
    void interruptionClearsTheBarImmediately() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(abilities("wind", map("interrupted_by", list("state.stunned"),
                "nodes", map("wait", map("type", "delay", "ticks", 20, "cast_bar", true)))));

        t.engine.activator().activate(p, "wind");
        t.time.advance(5);
        assertTrue(t.engine.instances().castProgress(p).isPresent());
        t.engine.statuses().apply(p, "stun", 10, null);
        assertEquals(Optional.empty(), t.engine.instances().castProgress(p));
    }
}
