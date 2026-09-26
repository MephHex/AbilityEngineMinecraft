package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileTest {

    private static Map<String, Object> hitDamage(double amount) {
        return map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                "effects", list(map("id", "damage", "amount", amount)));
    }

    @Test
    void hitsEntityThenCleansUpEverything() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(10, 1, 0);
        t.load(abilities("shot", map("nodes", map(
                "fire", map("type", "projectile", "speed", 1.0, "size", 0.5, "on", map("hit_entity", "dmg")),
                "dmg", hitDamage(5)))));

        t.engine.activator().activate(caster, "shot");
        assertEquals(1, t.render.alive);
        t.time.advance(20);

        assertEquals(5.0, t.damage(mob), 1e-9);
        assertEquals(0, t.render.alive, "visual removed");
        assertEquals(0, t.engine.projectiles().activeCount());
        assertEquals(0, t.engine.instances().count());
        assertEquals(0, t.time.pendingTasks(), "ticker stops when nothing is in flight (old code leaked a task per shot)");
    }

    @Test
    void fastSmallProjectileDoesNotTunnelThroughEntities() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(5.3, 1, 0);
        t.load(abilities("shot", map("nodes", map(
                "fire", map("type", "projectile", "speed", 3.0, "size", 0.1, "on", map("hit_entity", "dmg")),
                "dmg", hitDamage(1)))));

        t.engine.activator().activate(caster, "shot");
        t.time.advance(10);
        assertEquals(1.0, t.damage(mob), 1e-9);
    }

    private int ticksUntilBlockHit(int bounces) {
        TestEngine t = new TestEngine();
        t.world.floor(0);
        UUID caster = t.spawn(0, 3, 0);
        t.world.look(caster, new Vec3(1, -1, 0));
        t.load(abilities("ball", map("nodes", map(
                "fire", map("type", "projectile", "speed", 0.5, "bounces", bounces, "restitution", 0.9, "friction", 0.0,
                        "motion", list(map("type", "gravity", "amount", 0.05)),
                        "on", map("hit_block", "cue")),
                "cue", map("type", "play_cue", "cue", "impact", "at", "hit")))));

        t.engine.activator().activate(caster, "ball");
        for (int tick = 1; tick <= 200; tick++) {
            t.time.advance(1);
            if (!t.render.cues.isEmpty()) {
                assertEquals(1, t.render.cues.size());
                return tick;
            }
        }
        throw new AssertionError("never hit the floor");
    }

    @Test
    void bouncesBeforeTheFinalBlockHit() {
        int direct = ticksUntilBlockHit(0);
        int bounced = ticksUntilBlockHit(2);
        assertTrue(bounced > direct, "direct=" + direct + " bounced=" + bounced);
    }

    @Test
    void eachPelletIsItsOwnBranch() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(6, 1, 0);
        t.load(abilities("shotgun", map("nodes", map(
                "fire", map("type", "projectile", "count", 5, "speed", 2.0, "size", 0.3,
                        "on", map("spawned", "bang", "hit_entity", "dmg")),
                "bang", map("type", "play_cue", "cue", "cast"),
                "dmg", hitDamage(2)))));

        t.engine.activator().activate(caster, "shotgun");
        assertEquals(list("cast"), t.render.cues, "spawned fires once, immediately");
        t.time.advance(10);
        assertEquals(10.0, t.damage(mob), 1e-9);
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void cancellingTheCastRemovesProjectilesInFlight() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("shot", map("nodes", map("fire", map("type", "projectile", "speed", 0.5)))));

        t.engine.activator().activate(caster, "shot");
        t.time.advance(2);
        t.engine.resetEntity(caster, "died");
        t.time.advance(1);

        assertEquals(0, t.render.alive);
        assertEquals(0, t.engine.projectiles().activeCount());
    }

    @Test
    void homingCurvesOntoAnOffAxisTarget() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(10, 1, 5); // not in front of the crosshair
        t.load(abilities("orb", map("nodes", map(
                "lock", map("type", "acquire_target", "query", map("type", "radius", "radius", 20),
                        "store", "lock", "on", map("hit", "fire")),
                "fire", map("type", "projectile", "speed", 0.8, "lifetime", 100,
                        "motion", list(map("type", "homing", "key", "lock", "turn", 0.3)),
                        "on", map("hit_entity", "dmg")),
                "dmg", hitDamage(6)))));

        t.engine.activator().activate(caster, "orb");
        t.time.advance(60);
        assertEquals(6.0, t.damage(mob), 1e-9);
    }

    private static final ProjectileSpec BOUNCY = ProjectileSpec.builder().restitution(0.35).friction(0.4).minBounceSpeed(0.05).build();

    @Test
    void steepImpactThudsInPlace() {
        Vec3 out = BOUNCY.bounce(new Vec3(0.36, -1.35, 0), Vec3.UP); // thrown hard at your feet
        assertEquals(0.4725, out.y(), 1e-9);                            // 1.35 * 0.35 back up
        assertEquals(0.0, out.x(), 1e-9);                               // forward speed killed by the hard hit
    }

    @Test
    void shallowSkimKeepsMostForwardSpeed() {
        Vec3 out = BOUNCY.bounce(new Vec3(1.3, -0.3, 0), Vec3.UP);
        assertEquals(0.105, out.y(), 1e-9);
        assertTrue(out.x() > 1.1, "keeps rolling forward, got " + out.x());
    }

    @Test
    void tooSlowToBounceLands() {
        assertEquals(null, BOUNCY.bounce(new Vec3(1.0, -0.1, 0), Vec3.UP)); // 0.035 rebound < 0.05
    }

    @Test
    void worksOnWallsToo() {
        Vec3 out = BOUNCY.bounce(new Vec3(1.0, 0, 0.2), new Vec3(-1, 0, 0)); // head-on into a wall facing -x
        assertEquals(-0.35, out.x(), 1e-9);
        assertEquals(0.0, out.z(), 1e-9);
    }

    @Test
    void oldBounceDampingKeyGivesAClearError() {
        TestEngine t = new TestEngine();
        var report = new me.mephisto.ability_engine.engine.data.AbilityLoader(t.engine).load(abilities("b", map("nodes",
                map("fire", map("type", "projectile", "bounce_damping", 0.6)))), "abilities.yml");
        assertTrue(report.errors().get(0).contains("bounce_damping: was replaced by restitution"), report.errors().toString());
    }
}
