package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.construct.ConstructHandle;
import me.mephisto.ability_engine.engine.construct.Strike;
import me.mephisto.ability_engine.engine.effect.Knockback;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * unstable_binding from the shipped abilities.yml.
 * Layout: caster at x=0 looking +x; the geometry floats at about (4.7, 1, 0);
 * "near" is ~1 block from it, "edge" 3 blocks, "far" 6 blocks (outside the 3.5 blast).
 */
class UnstableBindingTest {

    private TestEngine t;
    private UUID caster;
    private UUID near;
    private UUID edge;
    private UUID far;

    private void setup() throws IOException {
        t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        caster = t.spawn(0, 1, 0);
        near = t.spawn(5.2, 1, 0.8);
        edge = t.spawn(4.7, 1, 3.0);
        far = t.spawn(4.7, 1, 6.0);

        // aim at the floor 5 blocks ahead, confirm with "left click"
        t.world.look(caster, new Vec3(1, -0.2, 0));
        assertTrue(t.engine.activator().activate(caster, "unstable_binding").openedTargeting());
        assertTrue(t.engine.targeting().confirm(caster).success());
        t.world.look(caster, new Vec3(1, 0, 0));
        assertEquals(1, t.engine.constructs().activeCount());
    }

    private ConstructHandle binding() { return t.engine.constructs().all().get(0); }

    private void advanceUntilDamaged(UUID who) {
        for (int i = 0; i < 60 && t.damage(who) == 0; i++) t.time.advance(1);
    }

    @Test
    void fuseShattersOnTimeWithDistanceScaledKnockback() throws IOException {
        setup();
        t.time.advance(59);
        assertEquals(0.0, t.damage(near), 1e-9, "nothing before 3s");
        t.time.advance(1);

        assertEquals(30.0, t.damage(near), 1e-9);
        assertEquals(30.0, t.damage(edge), 1e-9);
        assertEquals(0.0, t.damage(far), 1e-9, "outside the blast");
        assertEquals(0.0, t.damage(caster), 1e-9);
        assertTrue(t.knockback.get(near) > t.knockback.get(edge), "stronger near the centre");
        assertFalse(t.engine.tags().has(near, Tags.STUNNED), "a plain shatter doesn't stun");
        assertTrue(t.render.cues.contains("shatter"));
        assertEquals(0, t.render.constructsAlive, "visual removed");
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void ownBoltShattersItEarlyAndIsAbsorbed() throws IOException {
        setup();
        t.time.advance(2);
        t.engine.activator().activate(caster, "arcane_bolt");
        advanceUntilDamaged(near);

        assertEquals(30.0, t.damage(near), 1e-9, "shatter damage only: the bolt itself was absorbed");
        assertFalse(t.engine.tags().has(near, Tags.STUNNED));
        assertEquals(0, t.engine.constructs().activeCount());
    }

    @Test
    void recastMissileStunsAndRevealsEveryoneInRange() throws IOException {
        setup();
        t.time.advance(2);
        t.engine.activator().activate(caster, "arcane_missile");
        t.time.advance(2);
        assertTrue(t.engine.activator().activate(caster, "arcane_missile").success(), "recast");
        advanceUntilDamaged(near);

        assertTrue(t.render.cues.contains("shatter_stun"));
        for (UUID hit : new UUID[]{near, edge}) {
            assertTrue(t.engine.tags().has(hit, Tags.STUNNED), "stunned");
            assertTrue(t.engine.tags().has(hit, Tags.GLOWING), "glowing");
            assertEquals(30.0, t.damage(hit), 1e-9);
        }
        assertFalse(t.engine.tags().has(far, Tags.STUNNED));
    }

    @Test
    void unrecastMissileGivesAPlainShatter() throws IOException {
        setup();
        t.engine.activator().activate(caster, "arcane_missile");
        advanceUntilDamaged(near);

        assertTrue(t.render.cues.contains("shatter"));
        assertFalse(t.engine.tags().has(near, Tags.STUNNED));
        assertEquals(30.0, t.damage(near), 1e-9);
    }

    @Test
    void enemyProjectileInsideTheWindowBreaksItHarmlessly() throws IOException {
        setup();
        UUID enemy = t.spawn(10, 1, 0);
        t.world.look(enemy, new Vec3(-1, 0, 0));
        t.engine.activator().activate(enemy, "arcane_bolt");
        t.time.advance(5);

        assertEquals(0, t.engine.constructs().activeCount(), "broken");
        assertTrue(t.render.cues.contains("fizzle"));
        assertEquals(0.0, t.damage(near), 1e-9, "no shatter");
        assertEquals(0.0, t.damage(caster), 1e-9, "the bolt was stopped by the geometry");
        t.time.advance(60);
        assertEquals(0.0, t.damage(near), 1e-9, "and it doesn't go off later");
    }

    @Test
    void afterTheWindowEnemyFirePassesThrough() throws IOException {
        setup();
        t.time.advance(31); // fragile window (30) is over
        UUID enemy = t.spawn(10, 1, 0);
        t.world.look(enemy, new Vec3(-1, 0, 0));
        t.engine.activator().activate(enemy, "arcane_bolt");
        t.time.advance(6);

        assertEquals(1, t.engine.constructs().activeCount(), "still standing");
        assertEquals(30.0, t.damage(caster), 1e-9, "the bolt flew through and hit the caster");
    }

    @Test
    void meleeRules() throws IOException {
        setup();
        UUID enemy = t.spawn(4.7, 1, 1.5);
        assertFalse(t.engine.constructs().strike(binding(), Strike.melee(caster)), "own melee does nothing");
        assertTrue(t.engine.constructs().strike(binding(), Strike.melee(enemy)), "enemy punch breaks it while fragile");
        assertEquals(0, t.engine.constructs().activeCount());
        assertTrue(t.render.cues.contains("fizzle"));
    }

    @Test
    void enemyMeleeAfterTheWindowDoesNothing() throws IOException {
        setup();
        t.time.advance(31);
        UUID enemy = t.spawn(4.7, 1, 1.5);
        assertFalse(t.engine.constructs().strike(binding(), Strike.melee(enemy)));
        assertEquals(1, t.engine.constructs().activeCount());
    }

    @Test
    void casterDyingRemovesIt() throws IOException {
        setup();
        t.time.advance(5);
        t.engine.resetEntity(caster, "death");
        t.time.advance(1);
        assertEquals(0, t.engine.constructs().activeCount());
        assertEquals(0, t.render.constructsAlive);
        t.time.advance(80);
        assertEquals(0.0, t.damage(near), 1e-9, "no ghost explosion");
    }

    @Test
    void knockbackFallsOffWithDistance() {
        Vec3 c = new Vec3(0, 0, 0);
        double atCenter = flat(Knockback.impulse(c, new Vec3(0.01, 0, 0), 4, 1.4, 0.4, 0.3));
        double atHalf = flat(Knockback.impulse(c, new Vec3(2, 0, 0), 4, 1.4, 0.4, 0.3));
        double atEdge = flat(Knockback.impulse(c, new Vec3(4, 0, 0), 4, 1.4, 0.4, 0.3));
        assertEquals(1.4, atCenter, 0.01);
        assertEquals(0.9, atHalf, 1e-9);
        assertEquals(0.4, atEdge, 1e-9);
        assertEquals(0.3, Knockback.impulse(c, new Vec3(2, 0, 0), 4, 1.4, 0.4, 0.3).y(), 1e-9, "lift");
    }

    private static double flat(Vec3 v) { return new Vec3(v.x(), 0, v.z()).length(); }

    @Test
    void cantBePlacedDownACliffEither() throws IOException {
        t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        t.world.floor(-300);                       // a 300-block drop...
        t.world.box(-20, 5, -20, 20, 0);           // ...past a cliff edge at x=5
        caster = t.spawn(0, 1.62, 0);
        t.world.look(caster, new Vec3(1, -0.3, 0)); // aiming out over the edge
        t.engine.activator().activate(caster, "unstable_binding");
        assertTrue(t.engine.targeting().confirm(caster).success());

        Vec3 at = binding().position();
        assertEquals(1.0, at.y(), 1e-6, "on top of the cliff (ground 0 + 1 float), not 300 below");
        assertTrue(at.x() <= 5.0, "at or before the edge: x=" + at.x());
    }
}
