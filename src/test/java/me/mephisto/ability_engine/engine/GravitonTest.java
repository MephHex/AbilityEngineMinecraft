package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Graviton (fixture: base damage 30). He stands at the origin facing +x; enemies are on team red, he's on blue.
 */
class GravitonTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "graviton");
    }

    private UUID foe(double x, double z) {
        UUID id = t.spawn(x, 1, z);
        t.world.team(id, "red");
        return id;
    }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private double shield() { return t.shields.getOrDefault(p, 0.0); }

    /** Put him in a fight (as if hit) and let any shield tick still due pass. */
    private void fight() {
        t.engine.combat().hit(foe(30, 30), p);
        t.time.advance(20);
    }

    // ---- passive: Event Horizon ----

    @Test
    void outOfCombatAShieldBuildsUpAndStopsInAFight() throws IOException {
        setup();
        t.time.advance(40);
        assertTrue(shield() >= 30, "+10 every 0.5s out of combat: " + shield());
        fight();
        double during = shield();
        t.time.advance(40);
        assertEquals(during, shield(), 1e-9, "in a fight: no more");
        t.time.advance(60);                     // 4s after the hit
        assertTrue(shield() > during, "out of combat again");
    }

    // ---- primary: Graviton Orb ----

    @Test
    void theOrbBurstsOnAnEnemyAndHitsThoseAroundThem() throws IOException {
        setup();
        UUID a = foe(6, 0);
        UUID b = foe(6, 2);
        use(Slots.PRIMARY);
        t.time.advance(15);
        assertEquals(18, t.damage(a), 1e-9, "60% of 30");
        assertEquals(18, t.damage(b), 1e-9, "2 blocks away: in the burst");
    }

    @Test
    void theOrbBurstsWhenItFadesToo() throws IOException {
        setup();
        use(Slots.PRIMARY);
        t.time.advance(30);
        assertEquals(0, t.engine.projectiles().activeCount());
        assertTrue(t.render.cues.contains("graviton_orb_burst"));
    }

    // ---- 1: Gravity Grasp ----

    @Test
    void graspedEnemiesAreSlowedAndDraggedIntoAnotherOneBothAreSlammed() throws IOException {
        setup();
        UUID a = foe(8, 0);
        use(Slots.ABILITY_1);
        t.time.advance(8);
        assertEquals(15, t.damage(a), 1e-9, "50% of 30");
        assertTrue(t.engine.statuses().has(a, "graviton_grasped"));
        assertEquals(0.3, t.engine.stats().moveSpeedMultiplier(a), 1e-9, "-70%");

        // He looks along +z: they're dragged from (8, 0) toward his crosshair, 40 blocks out at (0, 40)
        t.world.look(p, new Vec3(0, 0, 1));
        Vec3 toward = new Vec3(0, 1, 40).subtract(new Vec3(8, 1, 0)).normalize();
        Vec3 onTheWay = new Vec3(8, 1, 0).add(toward.multiply(4));
        UUID b = foe(onTheWay.x(), onTheWay.z());
        use(Slots.ABILITY_1);                   // again: drag
        t.time.advance(15);
        assertEquals(36, t.damage(b), 1e-9, "slammed into: 120%");
        assertEquals(15 + 36, t.damage(a), 1e-9, "the dragged one too");
        assertTrue(t.engine.tags().has(a, Tags.STUNNED));
        assertTrue(t.engine.tags().has(b, Tags.STUNNED));
        assertFalse(t.engine.statuses().has(a, "graviton_grasped"), "let go");
    }

    @Test
    void draggedIntoNobodyTheyreJustLetGo() throws IOException {
        setup();
        UUID a = foe(8, 0);
        use(Slots.ABILITY_1);
        t.time.advance(8);
        t.world.look(p, new Vec3(0, 0, 1));
        use(Slots.ABILITY_1);
        t.time.advance(20);
        assertEquals(15, t.damage(a), 1e-9);
        assertFalse(t.engine.tags().has(a, Tags.STUNNED));
        assertFalse(t.engine.statuses().has(a, "graviton_grasped"));
    }

    // ---- 2: Kinetic Sink ----

    @Test
    void theSinkSwallowsShotsFromTheFrontEachIsShield() throws IOException {
        setup();
        UUID enemy = foe(10, 0);
        t.world.look(enemy, new Vec3(-1, 0, 0));
        fight();
        double before = shield();
        use(Slots.ABILITY_2);
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "no basic attacks while channeling");
        t.engine.activator().activate(enemy, "arcane_bolt");
        t.time.advance(10);
        assertEquals(0, t.damage(p), 1e-9, "swallowed");
        assertEquals(before + 25, shield(), 1e-9, "+25 shield");
        t.time.advance(50);
        t.engine.activator().activate(enemy, "arcane_bolt");
        t.time.advance(10);
        assertEquals(24, t.damage(p), 1e-9, "after 2.5s it's down (30, less his armor 25)");
    }

    // ---- 3: Stasis Field ----

    /** A field along x from 3 to 13 (centred on (8, 0, 0)), 6 wide. */
    private void field() {
        t.world.floor(0);
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting(), "aim preview first");
        assertTrue(t.engine.targeting().confirm(p).success(), "placed");
        t.world.look(p, new Vec3(1, 0, 0));
        assertEquals(1, t.engine.slowFields().active().size());
    }

    @Test
    void enemyShotsCrawlThroughTheField() throws IOException {
        setup();
        UUID ally = t.spawn(-5, 1, 2);          // (past him: z = 2, not through him)
        t.world.team(ally, "blue");
        UUID enemy = foe(20, 2);
        t.world.look(enemy, new Vec3(-1, 0, 0));
        field();
        t.engine.activator().activate(enemy, "arcane_bolt");
        t.time.advance(25);                     // at full speed it'd be there in ~13 ticks
        assertEquals(0, t.damage(ally), 1e-9, "still crawling through");
        assertEquals(1, t.engine.projectiles().activeCount());
        t.time.advance(60);
        assertEquals(30, t.damage(ally), 1e-9, "it gets there in the end (it ages slower too)");
    }

    @Test
    void hisSidesShotsPassAtFullSpeed() throws IOException {
        setup();
        UUID ally = t.spawn(-5, 1, 0);
        t.world.team(ally, "blue");
        UUID enemy = foe(20, 0);
        field();
        t.engine.activator().activate(ally, "arcane_bolt"); // faces +x: through the field at the enemy
        t.time.advance(15);
        assertEquals(30, t.damage(enemy), 1e-9);
    }

    @Test
    void theFieldIsGoneAfterFiveSeconds() throws IOException {
        setup();
        field();
        t.time.advance(101);
        assertEquals(0, t.engine.slowFields().active().size());
    }

    // ---- ULTIMATE: Gravitic Collapse ----

    @Test
    void enemiesStillOverTheCircleAreSmashedDownThoseWhoDriftOutArent() throws IOException {
        setup();
        t.world.floor(0);
        UUID a = foe(6, 0);
        UUID b = foe(8, 3);
        UUID away = foe(10, 0);
        t.world.look(p, new Vec3(6, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.world.look(p, new Vec3(1, 0, 0));
        t.time.advance(30);
        assertTrue(t.engine.tags().has(a, Tags.LIFTED));
        assertTrue(t.engine.tags().has(away, Tags.LIFTED));
        assertTrue(t.engine.tags().has(p, Tags.ANCHORED), "he hangs in the air");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "he can still act");

        t.world.move(away, new Vec3(20, 1, 0));    // drifts out of the circle
        t.time.advance(60);
        assertEquals(90, t.damage(a), 1e-9, "300% of 30");
        assertEquals(90, t.damage(b), 1e-9);
        assertTrue(t.engine.tags().has(a, Tags.STUNNED));
        assertTrue(t.engine.tags().has(a, Tags.SAFE_FALL), "the slam is the damage, not the fall");
        assertEquals(0, t.damage(away), 1e-9, "drifted out: no slam");
        assertFalse(t.engine.tags().has(away, Tags.STUNNED));
        assertFalse(t.engine.tags().has(away, Tags.LIFTED));
        assertTrue(t.engine.tags().has(away, Tags.SAFE_FALL), "falls back down unharmed");
        assertFalse(t.engine.tags().has(p, Tags.ANCHORED), "he comes back down");
    }
}
