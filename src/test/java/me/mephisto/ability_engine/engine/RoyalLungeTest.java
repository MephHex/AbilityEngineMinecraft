package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
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

/** royal_lunge from the shipped abilities.yml. Caster at x=0 looking +x. */
class RoyalLungeTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);
    }

    private double x() { return t.world.positionOf(new EntityTarget(p)).orElseThrow().position().x(); }

    @Test
    void stopsAtTheFirstEnemyDamagesAndStuns() throws IOException {
        setup();
        UUID first = t.spawn(6, 1, 0);
        UUID behind = t.spawn(8, 1, 0);
        assertTrue(t.engine.activator().activate(p, "royal_lunge").success());
        assertTrue(t.engine.tags().has(p, "state.dashing"));
        for (int i = 0; i < 15 && t.damage(first) == 0; i++) t.time.advance(1);

        assertEquals(50, t.damage(first), 1e-9);
        assertTrue(t.engine.tags().has(first, Tags.STUNNED), "brief stun");
        t.time.advance(10);
        assertFalse(t.engine.tags().has(first, Tags.STUNNED), "only 0.5s");
        assertEquals(0, t.damage(behind), 1e-9, "stopped at the first one");
        assertTrue(x() < 6, "stopped in front of them, x=" + x());
        assertFalse(t.engine.tags().has(p, "state.dashing"), "dash over");
    }

    @Test
    void aMissEndsAtMaxRange() throws IOException {
        setup();
        t.engine.activator().activate(p, "royal_lunge");
        t.time.advance(20);
        assertTrue(x() >= 10 && x() < 11.5, "about 10 blocks: x=" + x());
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void passesThroughAllies() throws IOException {
        setup();
        UUID ally = t.spawn(3, 1, 0);
        UUID enemy = t.spawn(7, 1, 0);
        t.world.team(p, "red");
        t.world.team(ally, "red");
        t.engine.activator().activate(p, "royal_lunge");
        t.time.advance(15);
        assertEquals(0, t.damage(ally), 1e-9);
        assertEquals(50, t.damage(enemy), 1e-9);
    }

    @Test
    void noOtherAbilitiesMidLunge() throws IOException {
        setup();
        t.engine.activator().activate(p, "royal_lunge");
        assertEquals("blocked:" + Tags.BLOCK_ABILITY, t.engine.activator().activate(p, "test_blast").reason());
    }

    @Test
    void aStunMidLungeStopsIt() throws IOException {
        setup();
        UUID enemy = t.spawn(9, 1, 0);
        t.engine.activator().activate(p, "royal_lunge");
        t.time.advance(2);
        t.engine.statuses().apply(p, "stun", 20, null);
        double stoppedAt = x();
        t.time.advance(10);
        assertEquals(stoppedAt, x(), 1e-9, "no more pushing");
        assertEquals(0, t.damage(enemy), 1e-9);
    }

    @Test
    void followsTheAimUpAndDown() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, 1, 0));
        t.engine.activator().activate(p, "royal_lunge");
        t.time.advance(20);
        double y = t.world.positionOf(new EntityTarget(p)).orElseThrow().position().y();
        assertTrue(y > 6, "trident-style: went up along the aim, y=" + y);
    }

    @Test
    void spinsExactlyWhileLunging() throws IOException {
        setup();
        UUID enemy = t.spawn(6, 1, 0);
        t.engine.activator().activate(p, "royal_lunge");
        assertEquals(java.util.List.of("riptide"), t.render.loops, "spinning from the first tick");
        for (int i = 0; i < 15 && t.damage(enemy) == 0; i++) t.time.advance(1);
        assertTrue(t.render.loops.isEmpty(), "stopped the moment the lunge hit");
    }

    @Test
    void theSpinStopsWhenTheLungeIsInterrupted() throws IOException {
        setup();
        t.engine.activator().activate(p, "royal_lunge");
        t.time.advance(2);
        t.engine.statuses().apply(p, "stun", 20, null);
        assertTrue(t.render.loops.isEmpty());
    }
}
