package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Archmage's passive from the shipped abilities.yml: hold RMB (secondary) to steer the latest
 * Arcane Bolt. Holding right click re-sends the input about every 4 ticks; the test does the same.
 */
class ArcaneFocusTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0); // looking +x, open space
        t.engine.loadouts().assign(p, "archmage");
    }

    private double focus() { return t.engine.resources().value(p, "focus"); }

    private boolean guiding() { return t.engine.tags().has(p, "state.guiding"); }

    /** Hold RMB for {@code ticks}: first press, then a repeat every 4 ticks. */
    private void hold(int ticks) {
        t.engine.loadouts().activate(p, Slots.SECONDARY, true);
        for (int i = 0; i < ticks; i += 4) {
            t.time.advance(4);
            t.engine.loadouts().activate(p, Slots.SECONDARY, false);
        }
    }

    @Test
    void focusStartsFull() throws IOException {
        setup();
        assertEquals(100.0, focus(), 1e-9);
    }

    @Test
    void unguidedBoltExpiresAfter30Blocks() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        t.time.advance(14);
        assertEquals(1, t.engine.projectiles().activeCount());
        t.time.advance(3);
        assertEquals(0, t.engine.projectiles().activeCount(), "speed 2: 30 blocks in ~15 ticks");
    }

    @Test
    void rangePausesWhileGuidedAndResumesOnRelease() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        t.time.advance(2);                          // ~4.5 blocks unguided so far
        hold(20);                                   // ~40 more blocks, guided
        assertEquals(1, t.engine.projectiles().activeCount(), "alive far past 30 blocks while guided");
        assertTrue(focus() < 70, "focus was drained: " + focus());

        t.time.advance(12);                         // released: the hold ends, the bolt flies on
        assertFalse(guiding());
        assertEquals(1, t.engine.projectiles().activeCount(), "resumes with its ~25 remaining blocks");
        t.time.advance(20);
        assertEquals(0, t.engine.projectiles().activeCount(), "...then expires");
    }

    @Test
    void guidingSlowsAndBlocksFiring() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        hold(20);
        assertTrue(guiding());
        assertTrue(t.engine.tags().has(p, Tags.SLOWED));
        assertEquals("blocked:state.guiding", t.engine.loadouts().activate(p, Slots.PRIMARY).reason());

        t.time.advance(12);
        assertFalse(t.engine.tags().has(p, Tags.SLOWED), "slow ends with the hold");
    }

    @Test
    void steeringTurnsTheBoltTowardTheCrosshair() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        t.world.look(p, new Vec3(1, 0, 1));         // crosshair off to the side
        hold(8);
        Vec3 pos = t.engine.projectiles().latest(p, "arcane_bolt").orElseThrow().position();
        assertTrue(pos.z() > 2, "curved toward the crosshair: z=" + pos.z());
    }

    @Test
    void noBoltInFlightMeansNothingHappensAndNothingIsSpent() throws IOException {
        setup();
        hold(12);
        assertEquals(100.0, focus(), 1e-9);
        assertFalse(guiding());
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void runningOutOfFocusEndsGuidance() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        hold(64);                                   // 2 Focus per tick: empty after 50 ticks
        assertFalse(guiding(), "guidance ended when Focus ran out");
        assertTrue(focus() < 2, "focus: " + focus());
    }

    @Test
    void stunInterruptsGuidance() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        hold(4);
        t.engine.statuses().apply(p, "stun", 20, null);
        assertFalse(guiding());
    }

    /** What /ae castas <dummy> arcane_focus hold 20 sends: a first press, then a "still held" every 4 ticks. */
    @Test
    void aDummyCanHoldTooWithoutACharacter() throws IOException {
        setup();
        UUID dummy = t.spawn(0, 1, 5);                       // no character, no Focus pool...
        t.engine.resources().define(dummy, t.engine.loadouts().characterOf(p).orElseThrow().resources().get("focus"));
        t.engine.activator().activate(dummy, "arcane_bolt");
        assertTrue(t.engine.activator().activate(dummy, "arcane_focus").success());
        for (int i = 0; i < 20; i += 4) {
            t.time.advance(4);
            t.engine.activator().activate(dummy, "arcane_focus", false);
        }
        assertTrue(t.engine.tags().has(dummy, "state.guiding"), "still guiding while the pings continue");
        t.time.advance(12);
        assertFalse(t.engine.tags().has(dummy, "state.guiding"), "released when they stop");
    }
}
