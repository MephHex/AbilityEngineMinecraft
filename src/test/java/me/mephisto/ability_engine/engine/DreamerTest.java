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

/** The Dreamer (fixture snapshot). She stands at x=0 facing +x; everyone faces +x unless turned. */
class DreamerTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "dreamer");
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void untilDone() {
        for (int i = 0; i < 40 && t.engine.instances().count() > 0; i++) t.time.advance(1);
    }

    // ---- Passive: backstab ------------------------------------------------------------------

    @Test
    void hittingTheirBackCrits() throws IOException {
        setup();
        UUID target = enemyAt(2, 0);                 // faces +x: away from her
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertEquals(60, t.damage(target), 1e-9, "40 x 1.5");
        assertTrue(t.render.cues.contains("crit"));
    }

    @Test
    void hittingTheirFrontDoesnt() throws IOException {
        setup();
        UUID target = enemyAt(2, 0);
        t.world.look(target, new Vec3(-1, 0, 0));    // facing her
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertEquals(40, t.damage(target), 1e-9);
        assertFalse(t.render.cues.contains("crit"));
    }

    // ---- 1: Dreamwalk -------------------------------------------------------------------------

    @Test
    void dreamwalkHidesAndMakesHerUntargetable() throws IOException {
        setup();
        UUID shooter = enemyAt(8, 0);
        t.world.look(shooter, new Vec3(-1, 0, 0));
        UUID teammate = t.spawn(-2, 1, 0);             // right behind her, in the line of fire
        t.world.team(teammate, "blue");
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        for (String tag : new String[]{Tags.HIDDEN, Tags.UNTARGETABLE, Tags.HASTED}) {
            assertTrue(t.engine.tags().has(p, tag), tag);
        }
        t.engine.activator().activate(shooter, "test_blast");    // a hitscan
        assertEquals(0, t.damage(p), 1e-9);
        assertTrue(t.damage(teammate) > 0, "the ray went straight through her");
        double afterRay = t.damage(teammate);
        t.engine.activator().activate(shooter, "arcane_bolt");   // a projectile
        t.time.advance(20);
        assertEquals(0, t.damage(p), 1e-9);
        assertTrue(t.damage(teammate) > afterRay, "the bolt flew through her too");
    }

    @Test
    void dealingDamageWakesHerAndTheNextHitBlindsOnce() throws IOException {
        setup();
        UUID target = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(10);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertFalse(t.engine.tags().has(p, Tags.HIDDEN), "woke up");
        assertTrue(t.engine.tags().has(target, Tags.BLINDED), "blinded");

        t.time.advance(45);                            // blindness over
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertFalse(t.engine.tags().has(target, Tags.BLINDED), "only the first hit blinds");
    }

    @Test
    void dreamwalkEndsAfterFiveSeconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(100);
        assertFalse(t.engine.tags().has(p, Tags.UNTARGETABLE));
    }

    // ---- 2: Crescent Rush ------------------------------------------------------------------------

    @Test
    void rushDamagesEveryoneAlongThePath() throws IOException {
        setup();
        UUID onPath = enemyAt(4, 0);                   // faces +x, the way she's dashing
        UUID aside = enemyAt(4, 4);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertTrue(pos(p).x() > 7, "dashed through");
        assertEquals(55, t.damage(onPath), 1e-9, "she ends up in front of them: no crit");
        assertEquals(0, t.damage(aside), 1e-9);
    }

    @Test
    void rushingThroughSomeoneFacingYouEndsBehindThem() throws IOException {
        setup();
        UUID target = enemyAt(4, 0);
        t.world.look(target, new Vec3(-1, 0, 0));      // watching her come
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertEquals(55 * 1.5, t.damage(target), 1e-9, "backstab from where she landed");
    }

    @Test
    void rushGoesWhereSheLooksEvenUp() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, 1, 0));
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertTrue(pos(p).y() > 5, "y=" + pos(p).y());
    }

    @Test
    void playerKillsRefreshIt() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertTrue(t.engine.cooldowns().remainingTicks(p, "crescent_rush") > 0);
        t.engine.notifyKill(p, UUID.randomUUID(), false);
        assertTrue(t.engine.cooldowns().remainingTicks(p, "crescent_rush") > 0, "a mob kill doesn't count");
        t.engine.notifyKill(p, UUID.randomUUID(), true);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "crescent_rush"), "a player kill does");
    }

    // ---- 3: Dream Echo -----------------------------------------------------------------------------

    @Test
    void theEchoStaysAndRecastSwaps() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertEquals(1, t.world.clones.size(), "an echo where she stood");
        UUID echo = t.world.clones.iterator().next();
        assertEquals(0, pos(echo).x(), 1e-9);

        t.world.move(p, new Vec3(6, 1, 3));
        t.engine.loadouts().activate(p, Slots.ABILITY_3);   // recast
        assertEquals(new Vec3(0, 1, 0), pos(p), "she's where the echo was");
        assertEquals(new Vec3(6, 1, 3), pos(echo), "and it's where she was");
        assertEquals(360, t.engine.cooldowns().remainingTicks(p, "dream_echo"), "cooldown after the swap");
    }

    @Test
    void theEchoRepeatsCrescentRushTowardHerCrosshair() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);  // echo at (0,1,0)
        t.world.move(p, new Vec3(0, 1, 10));
        UUID onHerPath = enemyAt(4, 10);
        UUID onEchoPath = enemyAt(4, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertEquals(55, t.damage(onHerPath), 1e-9);
        assertEquals(55, t.damage(onEchoPath), 1e-9, "the echo's rush hit too");
        UUID echo = t.world.clones.iterator().next();
        assertTrue(pos(echo).x() > 7, "the echo dashed");
    }

    @Test
    void theEchoIsntHitByAnything() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        UUID echo = t.world.clones.iterator().next();
        t.world.move(p, new Vec3(-5, 1, 0));               // she steps back; the echo is in front of her
        t.engine.loadouts().activate(p, Slots.ABILITY_2); // her rush passes through it
        untilDone();
        assertEquals(0, t.damage(echo), 1e-9);
    }

    @Test
    void theEchoFadesAfterTenSeconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(200);
        assertTrue(t.world.clones.isEmpty());
        assertTrue(t.engine.summons().find(p, "dream_echo").isEmpty());
    }
}
