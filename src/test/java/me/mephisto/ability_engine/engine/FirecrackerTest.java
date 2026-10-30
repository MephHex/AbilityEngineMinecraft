package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Firecracker (fixtures/firecracker.yml): 190 HP, base damage 40. She stands at the origin on a floor, looking +x,
 * team blue; enemies are red (no sheet: 200 HP, no armor).
 */
class FirecrackerTest {

    private static final double BASE = 40;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "firecracker");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private boolean use(String slot) { return t.engine.loadouts().activate(p, slot).success(); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    // ---- passive: Double Tap ----

    @Test
    void everyShotIsTwoHalfShotsAndBothApplyOnHits() throws IOException {
        setup();
        UUID e = foe(6, 0);
        t.engine.statuses().apply(p, "empowered", 200, p); // on-hit: +20 per hit
        assertTrue(use(Slots.PRIMARY));
        t.time.advance(20);
        assertEquals(2, t.render.spawned, "two shots");
        assertEquals(2 * (BASE * 0.5 + 20), t.damage(e), 1e-6, "50% each, and the on-hit on both");
    }

    @Test
    void aKillMakesHerAttacksFaster() throws IOException {
        setup();
        t.engine.notifyKill(p, foe(3, 0), true);
        assertTrue(has(p, "firecracker_rush"));
        assertEquals(1.5, t.engine.stats().attackSpeedMultiplier(p), 1e-9, "+50% attack speed");
        t.time.advance(61);
        assertFalse(has(p, "firecracker_rush"), "for 3s");
    }

    // ---- 1: Rocket ----

    @Test
    void theRocketBurnsAroundItAndBlindsWhoeverWatchedItGoOff() throws IOException {
        setup();
        UUID hit = foe(8, 0);
        UUID watcher = foe(8, 6);
        t.world.look(watcher, new Vec3(0, 0, -1));      // looking at the blast
        UUID away = foe(8, -6);
        t.world.look(away, new Vec3(0, 0, -1));         // looking the other way
        UUID walled = foe(8, 11);
        t.world.look(walled, new Vec3(0, 0, -1));       // looking at it, behind a wall
        t.world.box(6, 10, 8, 9, 10);
        assertTrue(use(Slots.ABILITY_1));
        for (int i = 0; i < 20 && t.damage(hit) == 0; i++) t.time.advance(1);
        assertEquals(BASE * 0.9, t.damage(hit), 1e-6, "90% base damage (the burn ticks after)");
        assertTrue(has(hit, "burn"), "burning");
        assertTrue(has(watcher, "firecracker_flashed"), "watched it: flashbanged");
        assertTrue(t.engine.tags().has(watcher, "state.blinded"));
        assertEquals(0, t.damage(watcher), 1e-9, "too far for the blast itself");
        assertFalse(has(away, "firecracker_flashed"), "looking away");
        assertFalse(has(walled, "firecracker_flashed"), "a wall in between");
        t.time.advance(41);
        assertFalse(has(watcher, "firecracker_flashed"), "2s");
    }

    // ---- 2: Scatter Fire ----

    @Test
    void scatterFireRocketsFlyThroughEnemies() throws IOException {
        setup();
        UUID near = foe(3, 0);
        UUID behind = foe(5, 0);
        assertTrue(use(Slots.ABILITY_2));
        assertEquals(3, t.render.spawned, "three rockets");
        t.time.advance(45);
        assertTrue(t.damage(near) >= BASE * 0.45 - 1e-6, "the first one in line");
        assertTrue(t.damage(behind) >= BASE * 0.45 - 1e-6, "and through to the one behind");
    }

    // ---- 3: Firework Bomb ----

    @Test
    void theBombGoesOffKnockingBackAndSlowingEnemies() throws IOException {
        setup();
        UUID e = foe(7, 0);
        t.world.look(p, new Vec3(7, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(20);
        assertEquals(0, t.damage(e), 1e-9, "the fuse is still burning");
        t.time.advance(40);
        assertEquals(BASE * 0.6, t.damage(e), 1e-6, "60% base damage");
        assertTrue(has(e, "firecracker_slowed"));
        assertTrue(t.knockbackVec.get(e).y() > 0.5, "knocked up");
        assertFalse(has(p, "firecracker_soaring"), "she was out of it");
    }

    @Test
    void pressing3AgainSetsTheBombOffEarly() throws IOException {
        setup();
        UUID e = foe(7, 0);
        t.world.look(p, new Vec3(7, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(20);                     // thrown and lying there, the fuse burning (1.5s)
        assertEquals(0, t.damage(e), 1e-9);
        assertTrue(use(Slots.ABILITY_3), "3 again");
        t.time.advance(2);
        assertEquals(BASE * 0.6, t.damage(e), 1e-6, "it went off right away");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "firecracker_ab3") > 0, "on cooldown since the throw");
        t.time.advance(40);
        assertEquals(BASE * 0.6, t.damage(e), 1e-6, "and only once");
    }

    @Test
    void caughtInHerOwnBombSheRocketJumpsUnharmed() throws IOException {
        setup();
        t.world.look(p, new Vec3(2, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(60);
        assertTrue(t.knockbackVec.containsKey(p) && t.knockbackVec.get(p).y() > 0.5, "blown up into the air");
        assertTrue(has(p, "firecracker_soaring"), "and the landing won't hurt");
        assertEquals(0, t.damage(p), 1e-9, "no damage");
        assertFalse(has(p, "firecracker_slowed"), "no slow");
    }

    // ---- ULTIMATE: Grand Finale ----

    @Test
    void theFinaleTurnsHerShotsIntoBigFireworks() throws IOException {
        setup();
        UUID e = foe(6, 0);
        assertTrue(use(Slots.ULTIMATE));
        assertTrue(t.engine.instances().timer(p).isPresent(), "a boss bar counts it down");
        assertTrue(use(Slots.PRIMARY));
        t.time.advance(20);
        assertEquals(2 * BASE, t.damage(e), 1e-6, "two big fireworks, 100% each");
        assertTrue(t.knockbackVec.containsKey(e), "knocked back");

        t.time.advance(110);
        assertTrue(t.engine.instances().timer(p).isEmpty(), "6s: over");
        double before = t.damage(e);
        assertTrue(use(Slots.PRIMARY));
        t.time.advance(20);
        assertEquals(BASE, t.damage(e) - before, 1e-6, "back to Double Tap: 50% x2");
    }
}
