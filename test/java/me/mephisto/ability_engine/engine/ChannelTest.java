package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelTest {

    private static Object channel(int period, int duration, String ammo) {
        Object mode = ammo == null
                ? map("type", "channel", "period", period, "duration", duration)
                : map("type", "channel", "period", period, "duration", duration, "ammo", ammo);
        return map("mode", mode, "nodes", map(
                "pulse", map("type", "apply_effects", "targets", map("type", "self"),
                        "effects", list(map("id", "damage", "amount", 1)))));
    }

    @Test
    void pulsesEveryPeriodForTheDuration() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("beam", channel(4, 20, null)));

        t.engine.activator().activate(caster, "beam");
        assertTrue(t.engine.tags().has(caster, Tags.CHANNELING));
        t.time.advance(40);

        assertEquals(5.0, t.damage(caster), 1e-9); // t = 0, 4, 8, 12, 16
        assertEquals(0, t.engine.instances().count());
        assertFalse(t.engine.tags().has(caster, Tags.CHANNELING));
        assertEquals(0, t.time.pendingTasks());
    }

    /** Regression: the old ChannelPhase compared elapsed MILLISECONDS against world-tick stun expiry,
     *  so after being stunned once, every later channel was cancelled on its first pulse. */
    @Test
    void channelWorksAfterAnEarlierStunHasExpired() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("beam", channel(4, 20, null)));

        t.engine.statuses().apply(caster, "stun", 40, null);
        t.time.advance(41);
        t.engine.activator().activate(caster, "beam");
        t.time.advance(40);
        assertEquals(5.0, t.damage(caster), 1e-9);
    }

    @Test
    void stunInterruptsARunningChannelImmediately() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("beam", channel(4, 40, null)));

        t.engine.activator().activate(caster, "beam");
        t.time.advance(4); // second pulse
        t.engine.statuses().apply(caster, "stun", 10, null);
        t.time.advance(40);

        assertEquals(2.0, t.damage(caster), 1e-9);
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void channelEndsWhenAmmoRunsOut() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("beam", channel(1, 100, "fuel")));
        t.engine.resources().set(caster, "fuel", 3);

        t.engine.activator().activate(caster, "beam");
        t.time.advance(10);
        assertEquals(3.0, t.damage(caster), 1e-9);
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void cannotStartTheSameChannelTwice() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("beam", map("blocked_by", list(), "mode", map("type", "channel", "period", 4, "duration", 40),
                "nodes", map("p", map("type", "print", "message", "x")))));

        assertTrue(t.engine.activator().activate(caster, "beam").success());
        assertEquals("already_active", t.engine.activator().activate(caster, "beam").reason());
    }
}
