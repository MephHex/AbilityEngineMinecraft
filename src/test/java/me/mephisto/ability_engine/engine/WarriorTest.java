package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Warrior, from the shipped warrior.yml. He stands at x=0 on a floor at y=0, facing +x; enemies are on team red. */
class WarriorTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        var report = ShippedContent.realReport(t.engine);
        assertTrue(report.isClean(), () -> String.join("\n", report.errors()));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.world.players.add(p);
        t.world.look(p, new Vec3(1, 0, 0));
        t.engine.loadouts().assign(p, "warrior");
        t.time.advance(1);
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    @Test
    void ironResolveWeakensEverySlow() throws IOException {
        setup();
        UUID other = enemyAt(5, 5);
        t.engine.statuses().apply(p, "berserker_hamstring", 40, other);     // -40%
        t.engine.statuses().apply(other, "berserker_hamstring", 40, p);
        assertEquals(0.6, t.engine.stats().moveSpeedMultiplier(other), 1e-9, "anyone else: 40% slower");
        assertEquals(0.76, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "him: 40% weaker, 24% slower");
    }

    @Test
    void ironResolveLeavesHasteAlone() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_2);                  // Iron Will: +20%
        assertEquals(1.2, t.engine.stats().moveSpeedMultiplier(p), 1e-9);
        t.engine.statuses().apply(p, "berserker_hamstring", 40, enemyAt(5, 5));
        assertEquals(1.2 * 0.76, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "the slow shrinks, the haste doesn't");
    }

    @Test
    void ironWillHardensHim() throws IOException {
        setup();
        double armor = t.engine.stats().armor(p), resist = t.engine.stats().spellResistance(p);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        assertEquals(armor + 25, t.engine.stats().armor(p), 1e-9);
        assertEquals(resist + 25, t.engine.stats().spellResistance(p), 1e-9);
        t.time.advance(101);
        assertEquals(armor, t.engine.stats().armor(p), 1e-9, "5s");
    }

    @Test
    void whirlwindCutsEveryHalfSecondForThreeSeconds() throws IOException {
        setup();
        UUID near = enemyAt(2, 0), far = enemyAt(5, 0);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        t.time.advance(1);
        double one = t.damage(near);
        assertTrue(one > 0, "a cut at once");
        t.time.advance(80);
        assertEquals(one * 6, t.damage(near), 1e-6, "6 cuts in all");
        assertEquals(0, t.damage(far), 1e-9, "3 blocks only");
    }

    // ---- Unstoppable Charge ----

    @Test
    void theChargeSpeedsUpAndRunsIntoAWall() throws IOException {
        setup();
        t.world.box(45, 47, -5, 5, 4);                 // a wall 45 blocks ahead
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.time.advance(1);
        double early = pos(p).x();
        t.time.advance(1);
        double firstStep = pos(p).x() - early;
        t.time.advance(60);                            // past full speed
        double x = pos(p).x();
        t.time.advance(1);
        double fullStep = pos(p).x() - x;
        assertTrue(fullStep > firstStep * 3, "faster and faster: " + firstStep + " -> " + fullStep);
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_ABILITY), "nothing else meanwhile");
        for (int i = 0; i < 40 && t.engine.instances().isRunning(p, "warrior_ult"); i++) t.time.advance(1);
        assertFalse(t.engine.instances().isRunning(p, "warrior_ult"), "the wall stopped him");
        assertTrue(pos(p).x() < 45 && pos(p).x() > 40, "in front of it: " + pos(p));
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_ABILITY));
    }

    @Test
    void theChargeKnocksUpWhoeverItRunsIntoAndPinsTheFirstAtFullSpeed() throws IOException {
        setup();
        UUID early = enemyAt(3, 0);                    // reached before full speed: knocked up
        UUID late = enemyAt(40, 0);                    // reached at full speed: pinned
        t.world.box(55, 57, -5, 5, 4);                 // and crushed into this wall
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        for (int i = 0; i < 25 && t.damage(early) == 0; i++) t.time.advance(1);
        assertTrue(t.damage(early) > 0, "a little damage");
        assertTrue(pos(early).y() > 1.5, "knocked up: " + pos(early));
        double bump = t.damage(early);
        for (int i = 0; i < 120 && t.damage(late) == 0; i++) t.time.advance(1);
        assertTrue(t.engine.statuses().has(late, "warrior_pinned"), "pinned");
        t.time.advance(2);
        assertTrue(pos(late).x() > pos(p).x(), "carried in front of him");
        for (int i = 0; i < 40 && t.engine.instances().isRunning(p, "warrior_ult"); i++) t.time.advance(1);
        assertTrue(t.engine.tags().has(late, Tags.STUNNED), "crushed into the wall: stunned");
        assertTrue(t.damage(late) > bump * 5, "and hurt: " + t.damage(late));
        assertFalse(t.engine.statuses().has(late, "warrior_pinned"), "let go");
        assertEquals(bump, t.damage(early), 1e-6, "knocked up once, not again");
    }

    @Test
    void runningIntoTheWallBlastsEveryoneAround() throws IOException {
        setup();
        t.world.box(8, 10, -5, 5, 4);
        UUID beside = enemyAt(6.5, 2.5);               // beside the spot he stops at, not in his way
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        for (int i = 0; i < 80 && t.engine.instances().isRunning(p, "warrior_ult"); i++) t.time.advance(1);
        assertFalse(t.engine.instances().isRunning(p, "warrior_ult"));
        assertTrue(t.damage(beside) > 0, "the blast");
    }

    @Test
    void theChargeTurnsTowardWhereHeLooks() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.time.advance(5);
        t.world.look(p, new Vec3(0, 0, 1));            // now he looks +z
        t.time.advance(40);
        Vec3 at = pos(p);
        assertTrue(at.z() > 3, "it turned his way: " + at);
    }
}
