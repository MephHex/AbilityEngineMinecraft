package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import me.mephisto.ability_engine.engine.testkit.Yml;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** foldstep from the shipped abilities.yml: 0.5s rooted channel, then teleport to the crosshair. */
class FoldstepTest {

    private static TestEngine shipped() throws IOException {
        TestEngine t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        return t;
    }

    @Test
    void rootedAndLockedForExactlyTheChannelThenTeleports() throws IOException {
        TestEngine t = shipped();
        t.world.floor(0);
        UUID caster = t.spawn(0, 1, 0);
        t.world.look(caster, new Vec3(1, -0.1, 0)); // crosshair on the floor ahead

        assertTrue(t.engine.activator().activate(caster, "foldstep").openedTargeting());
        assertTrue(t.engine.targeting().confirm(caster).success());
        assertTrue(t.engine.tags().has(caster, Tags.BLOCK_MOVE), "rooted");
        assertEquals("blocked:" + Tags.BLOCK_ABILITY, t.engine.activator().activate(caster, "test_blast").reason(),
                "no other abilities during the channel");

        t.time.advance(9);
        assertTrue(t.teleports.isEmpty(), "not before 0.5s");
        t.time.advance(1);
        assertEquals(1, t.teleports.size());
        assertEquals(0.0, t.teleports.get(0).y(), 1e-6, "landed on the floor under the crosshair");
        assertFalse(t.engine.tags().has(caster, Tags.BLOCK_MOVE), "free again");
        assertFalse(t.engine.tags().has(caster, Tags.BLOCK_ABILITY));
    }

    @Test
    void aimingAtTheSkyLandsOnTheGroundBeneathIt() throws IOException {
        TestEngine t = shipped();
        t.world.floor(0);
        UUID caster = t.spawn(0, 1.6, 0);
        t.world.look(caster, new Vec3(1, 0.5, 0)); // up into the air
        t.engine.activator().activate(caster, "foldstep");
        t.time.advance(1);
        assertEquals(0, t.render.invalidDraws, "preview is valid: it sits on the ground below");

        assertTrue(t.engine.targeting().confirm(caster).success());
        t.time.advance(10);
        Vec3 landed = t.teleports.get(0);
        assertEquals(0.0, landed.y(), 1e-6, "on the floor, not in the air");
        assertTrue(landed.x() > 9, "straight below the crosshair point, ~10.7 blocks out: " + landed.x());
    }

    @Test
    void aimingOffACliffLandsOnTheCliffEdge() throws IOException {
        TestEngine t = shipped();
        t.world.floor(0);
        t.world.box(-20, 5, -20, 20, 30);          // cliff top at y=30, edge at x=5, 30-block drop beyond
        UUID caster = t.spawn(0, 31.62, 0);        // standing on it (eye height), looking out level
        t.engine.activator().activate(caster, "foldstep");
        t.time.advance(1);
        assertEquals(0, t.render.invalidDraws, "preview shows the edge, not red");
        assertTrue(t.engine.targeting().confirm(caster).success());
        t.time.advance(10);

        Vec3 landed = t.teleports.get(0);
        assertEquals(30.0, landed.y(), 1e-6, "stays on top: the bottom is deeper than max_drop");
        assertTrue(landed.x() > 4.5 && landed.x() <= 5.0, "right at the edge: x=" + landed.x());
    }

    @Test
    void aShortDropIsFine() throws IOException {
        TestEngine t = shipped();
        t.world.floor(0);
        t.world.box(-20, 5, -20, 20, 4);           // a 4-block ledge: within max_drop 6
        UUID caster = t.spawn(0, 5.62, 0);
        t.engine.activator().activate(caster, "foldstep");
        assertTrue(t.engine.targeting().confirm(caster).success());
        t.time.advance(10);
        assertEquals(0.0, t.teleports.get(0).y(), 1e-6, "jumped down off the ledge");
    }

    @Test
    void thePreviewRayIgnoresEntities() throws IOException {
        TestEngine t = shipped();
        t.world.floor(0);
        UUID caster = t.spawn(0, 1, 0);
        t.world.look(caster, new Vec3(1, -0.2, 0)); // floor under the crosshair at x=5
        t.spawn(2.5, 0.5, 0);                      // a mob standing right in the ray's path
        t.engine.activator().activate(caster, "foldstep");
        assertTrue(t.engine.targeting().confirm(caster).success());
        t.time.advance(10);
        assertTrue(t.teleports.get(0).x() > 4, "went past the mob to the floor: x=" + t.teleports.get(0).x());
    }

    @Test
    void noGroundAtAllIsRefusedAndSpendsNothing() throws IOException {
        TestEngine t = shipped();         // no floor: the void
        UUID caster = t.spawn(0, 1, 0);
        t.engine.activator().activate(caster, "foldstep");
        t.time.advance(1);

        assertTrue(t.render.invalidDraws > 0, "preview drawn red");
        assertEquals("no_ground", t.engine.targeting().confirm(caster).reason());
        assertTrue(t.engine.targeting().isTargeting(caster), "still aiming");
        assertEquals(0, t.engine.cooldowns().remainingTicks(caster, "foldstep"));
    }

    @Test
    void stunDuringTheChannelCancelsTheTeleport() throws IOException {
        TestEngine t = shipped();
        t.world.floor(0);
        UUID caster = t.spawn(0, 1, 0);
        t.world.look(caster, new Vec3(1, -0.2, 0));
        t.engine.activator().activate(caster, "foldstep");
        assertTrue(t.engine.targeting().confirm(caster).success());
        t.time.advance(5);
        t.engine.statuses().apply(caster, "stun", 20, null);
        t.time.advance(10);

        assertTrue(t.teleports.isEmpty());
        assertEquals(0, t.engine.instances().count());
        t.time.advance(20);
        assertFalse(t.engine.tags().has(caster, Tags.BLOCK_MOVE), "root from the cancelled cast was released");
    }

    @Test
    void ownTagsDontInterruptTheCast() {
        // interrupted_by containing a tag the cast grants itself must not cancel it on start
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(Yml.abilities("c", Yml.map(
                "active_tags", Yml.list("block.ability"),
                "interrupted_by", Yml.list("block.ability"),
                "nodes", Yml.map("w", Yml.map("type", "delay", "ticks", 10)))));
        t.engine.activator().activate(caster, "c");
        assertEquals(1, t.engine.instances().count());
    }
}
