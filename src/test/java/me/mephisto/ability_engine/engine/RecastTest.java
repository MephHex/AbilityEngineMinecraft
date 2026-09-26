package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.projectile.Accelerate;
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

/** tidal_orb from the shipped abilities.yml: slow on hit, recast = steer + accelerate + stun + AoE. */
class RecastTest {

    private static TestEngine shipped() throws IOException {
        TestEngine t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        return t;
    }

    @Test
    void withoutRecastTheOrbSlows() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(6, 1, 0);

        assertTrue(t.engine.activator().activate(caster, "tidal_orb").success());
        t.time.advance(40);

        assertTrue(t.engine.tags().has(mob, Tags.SLOWED));
        assertFalse(t.engine.tags().has(mob, Tags.STUNNED));
        assertEquals(0.0, t.damage(mob), 1e-9);
        assertEquals(0, t.engine.instances().count(), "recast window closes when the orb is gone");
        assertEquals(0, t.render.alive);
    }

    @Test
    void recastSteersToCrosshairThenStunsAndExplodes() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);          // looking +x
        UUID mob = t.spawn(8, 1, 8);             // off to the side: only reachable by steering
        UUID bystander = t.spawn(9, 1, 8.5);     // within 3.5 of the impact
        UUID far = t.spawn(20, 1, 20);

        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(3);
        t.world.look(caster, new Vec3(8, 0, 8)); // put the crosshair on the mob
        ActivationResult recast = t.engine.activator().activate(caster, "tidal_orb");
        assertTrue(recast.success(), "second press is a recast, not 'on cooldown': " + recast.reason());
        t.time.advance(15); // steered + accelerating: lands within ~10 ticks; stun lasts 30

        assertTrue(t.engine.tags().has(mob, Tags.STUNNED), "main target stunned");
        assertFalse(t.engine.tags().has(mob, Tags.SLOWED), "empowered hit doesn't slow");
        assertEquals(60.0, t.damage(mob), 1e-9);
        assertEquals(60.0, t.damage(bystander), 1e-9, "AoE hits nearby");
        assertFalse(t.engine.tags().has(bystander, Tags.STUNNED), "only the main target is stunned");
        assertEquals(0.0, t.damage(far), 1e-9);
        assertEquals(0.0, t.damage(caster), 1e-9);
        assertEquals(0, t.engine.instances().count());

        t.time.advance(30);
        assertFalse(t.engine.tags().has(mob, Tags.STUNNED), "stun wears off");
    }

    @Test
    void holdingTheButtonNeverRecasts() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(6, 1, 0);

        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(4);
        ActivationResult held = t.engine.activator().activate(caster, "tidal_orb", false); // auto-repeat
        assertEquals("held", held.reason(), "ignored: no recast, and no second cast either");
        assertEquals(1, t.engine.instances().count());
        t.time.advance(40);

        assertTrue(t.engine.tags().has(mob, Tags.SLOWED), "orb kept flying straight, un-empowered");
        assertFalse(t.engine.tags().has(mob, Tags.STUNNED));
    }

    @Test
    void pressAfterTheOrbLandedIsANormalCooldownMessage() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        t.spawn(3, 1, 0);

        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(20); // orb has hit
        assertTrue(t.engine.activator().activate(caster, "tidal_orb").reason().startsWith("on_cooldown"));
    }

    @Test
    void cannotRecastWhileStunned() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);

        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(2);
        t.engine.statuses().apply(caster, "stun", null);
        assertEquals("blocked:" + Tags.BLOCK_ABILITY, t.engine.activator().activate(caster, "tidal_orb").reason());
    }

    @Test
    void accelerateIsCappedAndKeepsDirection() {
        Accelerate a = new Accelerate(0.5, 2.0);
        Vec3 v = new Vec3(0, 0, 1);
        v = a.apply(Vec3.ZERO, v, null);
        assertEquals(1.5, v.length(), 1e-9);
        v = a.apply(Vec3.ZERO, a.apply(Vec3.ZERO, v, null), null);
        assertEquals(2.0, v.length(), 1e-9);
        assertEquals(1.0, v.normalize().z(), 1e-9);
    }

    // ---- cooldown starts when the recast window closes ---------------------------------------------

    private static final int COOLDOWN = 200; // tidal_orb

    @Test
    void noCooldownWhileTheWindowIsOpen() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(10);
        assertEquals(0, t.engine.cooldowns().remainingTicks(caster, "tidal_orb"));
        assertTrue(t.engine.instances().awaitingRecast(caster, "tidal_orb"), "this is what makes the icon glint");
    }

    @Test
    void recastStartsTheCooldown() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(10);
        t.engine.activator().activate(caster, "tidal_orb"); // recast
        assertEquals(COOLDOWN, t.engine.cooldowns().remainingTicks(caster, "tidal_orb"));
        assertFalse(t.engine.instances().awaitingRecast(caster, "tidal_orb"));
    }

    @Test
    void theWindowTimingOutStartsTheCooldown() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);          // open space: the orb flies its whole lifetime
        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(79);
        assertEquals(0, t.engine.cooldowns().remainingTicks(caster, "tidal_orb"));
        t.time.advance(1);                        // window: 80 ticks
        assertEquals(COOLDOWN, t.engine.cooldowns().remainingTicks(caster, "tidal_orb"));
    }

    @Test
    void theWindowClosingEarlyStartsTheCooldown() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        t.spawn(3, 1, 0);                         // the orb hits this quickly
        t.engine.activator().activate(caster, "tidal_orb");
        t.time.advance(15);
        assertTrue(t.engine.cooldowns().remainingTicks(caster, "tidal_orb") > COOLDOWN - 15);
    }

    @Test
    void aStunDuringTheWindowStillStartsTheCooldown() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        t.load(me.mephisto.ability_engine.engine.testkit.Yml.abilities("orb_interruptible", me.mephisto.ability_engine.engine.testkit.Yml.map(
                "cooldown", 100, "interrupted_by", me.mephisto.ability_engine.engine.testkit.Yml.list("state.stunned"),
                "nodes", me.mephisto.ability_engine.engine.testkit.Yml.map(
                        "wait", me.mephisto.ability_engine.engine.testkit.Yml.map("type", "await_recast", "window", 80)))));
        t.engine.activator().activate(caster, "orb_interruptible");
        t.time.advance(5);
        t.engine.statuses().apply(caster, "stun", 10, null);
        assertEquals(100, t.engine.cooldowns().remainingTicks(caster, "orb_interruptible"), "no free cast");
    }

    @Test
    void cooldownStartsCastKeepsTheOldBehaviour() throws IOException {
        TestEngine t = shipped();
        UUID caster = t.spawn(0, 1, 0);
        t.load(me.mephisto.ability_engine.engine.testkit.Yml.abilities("old_style", me.mephisto.ability_engine.engine.testkit.Yml.map(
                "cooldown", 100, "cooldown_starts", "cast",
                "nodes", me.mephisto.ability_engine.engine.testkit.Yml.map(
                        "wait", me.mephisto.ability_engine.engine.testkit.Yml.map("type", "await_recast", "window", 80)))));
        t.engine.activator().activate(caster, "old_style");
        assertEquals(100, t.engine.cooldowns().remainingTicks(caster, "old_style"));
    }
}
