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

    // Regression (in-game): aiming at someone points you slightly DOWN, and the dash used to end
    // when its path met the ground, a few blocks short of them.
    @Test
    void aimingSlightlyDownGlidesAlongTheFloorAndStillHits() throws IOException {
        setup();
        t.world.floor(0);
        t.world.look(p, new Vec3(1, -0.3, 0).normalize());  // the path meets the floor at x ~ 3.3
        UUID target = enemyAt(6, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertEquals(55, t.damage(target), 1e-9);
        assertTrue(pos(p).x() > 7, "kept going along the floor, x=" + pos(p).x());
        assertTrue(pos(p).y() > 0, "above the floor");
    }

    // Regression (in-game): with ping, where the server thinks you are trails behind. Hits land as you
    // pass each enemy, not by looking back along the path afterwards.
    @Test
    void lagDoesntLoseTheHits() throws IOException {
        setup();
        t.world.lag(p, 3);
        UUID near = enemyAt(3, 0);
        UUID far = enemyAt(8, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 40; i++) t.time.advance(1);
        assertEquals(55, t.damage(near), 1e-9);
        assertEquals(55, t.damage(far), 1e-9);
    }

    @Test
    void eachEnemyIsHitOnce() throws IOException {
        setup();
        UUID target = enemyAt(4, 0.5);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertEquals(55, t.damage(target), 1e-9);
    }

    // ---- 3: Dream Echo -----------------------------------------------------------------------------

    /** Aim at the floor ~4 blocks ahead and confirm (left click). Returns the echo. */
    private UUID placeEcho() {
        t.world.floor(0);
        t.world.look(p, new Vec3(1, -0.25, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting(), "aim preview first");
        assertTrue(t.engine.targeting().confirm(p).success(), "placed");
        t.world.look(p, new Vec3(1, 0, 0));
        assertEquals(1, t.world.clones.size());
        return t.world.clones.iterator().next();
    }

    private UUID summonEchoAt(double x, double z) {
        return t.engine.summons().summonClone(p, "dream_echo", "world", new Vec3(x, 1, z), 200).orElseThrow();
    }

    @Test
    void theEchoIsPlacedWhereSheAims() throws IOException {
        setup();
        UUID echo = placeEcho();
        assertEquals(4, pos(echo).x(), 0.3, "on the spot she aimed at");
        assertEquals(0.9, pos(echo).y(), 1e-6, "standing on the floor");
    }

    @Test
    void recastSwapsPlaces() throws IOException {
        setup();
        UUID echo = placeEcho();
        Vec3 echoWas = pos(echo);
        t.world.move(p, new Vec3(-3, 1, 3));
        t.engine.loadouts().activate(p, Slots.ABILITY_3);  // recast
        assertEquals(echoWas, pos(p), "she's where the echo was");
        assertEquals(new Vec3(-3, 1, 3), pos(echo), "and it's where she was");
        assertEquals(360, t.engine.cooldowns().remainingTicks(p, "dream_echo"), "cooldown after the swap");
    }

    @Test
    void theEchoSpawnsFacingHer() throws IOException {
        setup();
        UUID echo = placeEcho();                         // ~4 blocks ahead of her (+x)
        Vec3 facing = t.world.facing.get(echo);
        assertTrue(facing != null && facing.x() < -0.99, "looks back at her: " + facing);
        assertEquals(0, facing.y(), 1e-9, "level");
    }

    @Test
    void theEchoTurnsTowardItsDashBeforeDashing() throws IOException {
        setup();
        UUID echo = summonEchoAt(4, 6);                 // off to her side
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        Vec3 facing = t.world.facing.get(echo);          // right away, before it has moved
        assertTrue(facing != null && facing.x() > 0.9 && facing.z() < 0, "toward her cursor (+x, back toward her line): " + facing);
        assertFalse(t.world.facing.containsKey(p), "her own view is never turned");
    }

    @Test
    void theEchoDashesTowardWhereSheAimsSoTheyConverge() throws IOException {
        setup();
        UUID echo = summonEchoAt(4, 6);                 // off to her side
        // She looks along +x: her cursor is ~40 blocks down that line. The echo heads for that point.
        UUID onEchoPath = enemyAt(8.9, 5.2);             // ~5 blocks along the echo's line to the cursor
        UUID onHerPath = enemyAt(4, 0.9);                // in her dash, but not under her crosshair
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertEquals(55, t.damage(onHerPath), 1e-9);
        assertEquals(55, t.damage(onEchoPath), 1e-9, "the echo hit someone on its way to her cursor");
        assertTrue(pos(echo).z() < 5 && pos(echo).x() > 10, "moved toward her line: " + pos(echo));
    }

    @Test
    void crosshairOnAnEnemyBothOfThemCrossThroughIt() throws IOException {
        setup();
        summonEchoAt(4, 6);
        UUID target = enemyAt(6, 0);                     // right under her crosshair
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertEquals(110, t.damage(target), 1e-9, "her rush and the echo's, converging on it");
    }

    @Test
    void theEchoStopsAtHerCursorIfItsCloser() throws IOException {
        setup();
        t.world.box(9, 10, -20, 20, 10);                 // a wall 9 blocks ahead: her cursor lands on it
        UUID echo = summonEchoAt(6, 3);                  // ~4.2 blocks from that point
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertTrue(pos(echo).x() <= 9.5, "didn't overshoot the cursor point: " + pos(echo));
    }

    @Test
    void theEchoIsntHitByAnything() throws IOException {
        setup();
        UUID echo = summonEchoAt(3, 0);                   // right in her path
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        untilDone();
        assertEquals(0, t.damage(echo), 1e-9);
    }

    @Test
    void theEchoFadesAfterTenSeconds() throws IOException {
        setup();
        placeEcho();
        t.time.advance(200);
        assertTrue(t.world.clones.isEmpty());
        assertTrue(t.engine.summons().find(p, "dream_echo").isEmpty());
    }

    // ---- Ultimate: Dream Tempest ------------------------------------------------------------------

    @Test
    void dreamTempestLiftsYouOutOfReachLookingDown() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertEquals(new Vec3(0, 13, 0), pos(p), "12 blocks above where you stood");
        assertTrue(t.world.aimOf(p).orElseThrow().direction().y() < -0.9, "looking down at the spot");
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE));
        assertTrue(t.engine.tags().has(p, Tags.HIDDEN));
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "can't move");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "can't use abilities");
        assertEquals("dreamer_ult1", t.engine.instances().timer(p).orElseThrow().ability().id(), "boss bar");
    }

    @Test
    void dreamTempestSlashesTheCircleForThreeSecondsThenPutsYouBack() throws IOException {
        setup();
        UUID inside = enemyAt(3, 2);
        UUID outside = enemyAt(8, 0);
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.time.advance(30);
        double half = t.damage(inside);
        assertTrue(half > 0 && half < 150, "slashing over time: " + half);
        t.time.advance(31);
        assertEquals(150, t.damage(inside), 1e-9, "10 every 0.2s for 3s");
        assertEquals(0, t.damage(outside), 1e-9, "only within 5 blocks");
        assertEquals(new Vec3(0, 1, 0), pos(p), "back where you were");
        assertFalse(t.engine.tags().has(p, Tags.UNTARGETABLE));
        assertTrue(t.engine.instances().timer(p).isEmpty());
        t.time.advance(20);
        assertEquals(150, t.damage(inside), 1e-9, "stopped");
    }

    @Test
    void cutShortYouStillComeBack() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.time.advance(10);
        t.engine.instances().cancelAll(p, "character_change");
        assertEquals(new Vec3(0, 1, 0), pos(p));
    }
}
