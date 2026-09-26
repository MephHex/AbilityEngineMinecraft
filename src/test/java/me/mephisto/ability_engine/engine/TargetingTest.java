package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetingTest {

    /** "blink" is aimed (preview); "zap" isn't. Both hurt the caster so casts are observable. */
    private static Map<String, Object> file() {
        Object hurt = map("type", "apply_effects", "targets", map("type", "self"),
                "effects", list(map("id", "damage", "amount", 1)));
        return map("abilities", map(
                "blink", map("cooldown", 100,
                        "targeting", map("shape", "circle", "range", 10, "radius", 1, "timeout", 40),
                        "nodes", map("hurt", hurt)),
                "zap", map("cooldown", 20, "nodes", map("hurt", hurt))));
    }

    private static TestEngine setup() {
        TestEngine t = new TestEngine();
        t.load(file());
        return t;
    }

    @Test
    void pressOpensAPreviewAndSpendsNothing() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);

        ActivationResult r = t.engine.activator().activate(p, "blink");
        assertTrue(r.openedTargeting());
        assertTrue(t.engine.targeting().isTargeting(p));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "blink"), "no cooldown until confirm");
        assertEquals(0.0, t.damage(p), 1e-9, "not cast yet");
        t.time.advance(3);
        assertTrue(t.render.previewDraws >= 3, "preview redrawn every tick");
    }

    @Test
    void confirmCastsWithAimAndStartsCooldown() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");

        assertTrue(t.engine.targeting().confirm(p).success());
        assertEquals(1.0, t.damage(p), 1e-9);
        assertEquals(100, t.engine.cooldowns().remainingTicks(p, "blink"));
        assertFalse(t.engine.targeting().isTargeting(p));
        assertEquals(list("confirmed"), t.render.previewEnds);
    }

    /** Regression: holding Q auto-repeats it; a repeat must never confirm (it teleported instantly). */
    @Test
    void pressingTheSameKeyAgainDoesNothing() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");
        for (int i = 0; i < 5; i++) {
            t.time.advance(2);
            assertTrue(t.engine.activator().activate(p, "blink").openedTargeting()); // key repeat, fresh or not
        }
        assertTrue(t.engine.targeting().isTargeting(p), "still aiming");
        assertEquals(0.0, t.damage(p), 1e-9, "not cast");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "blink"));
    }

    @Test
    void heldInputNeverConfirms() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");
        t.engine.activator().activate(p, "blink", false); // auto-repeat of the press that opened it
        assertTrue(t.engine.targeting().isTargeting(p));
        assertEquals(0.0, t.damage(p), 1e-9);
    }

    @Test
    void cancelSpendsNothing() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");
        t.engine.targeting().cancel(p, "cancelled");

        assertFalse(t.engine.targeting().isTargeting(p));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "blink"));
        assertTrue(t.engine.activator().activate(p, "blink").openedTargeting(), "can aim again right away");
    }

    @Test
    void anotherAbilitySwitchesAndCastsIt() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");
        assertTrue(t.engine.activator().activate(p, "zap").success());
        assertFalse(t.engine.targeting().isTargeting(p));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "blink"));
        assertEquals(list("switched"), t.render.previewEnds);
    }

    @Test
    void noTimeLimitByDefault() {
        TestEngine t = new TestEngine();
        t.load(map("abilities", map("aimed", map(
                "targeting", map("shape", "circle", "range", 10),
                "nodes", map("p", map("type", "print", "message", "x"))))));
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "aimed");
        t.time.advance(20 * 60);
        assertTrue(t.engine.targeting().isTargeting(p), "still aiming after a minute");
    }

    @Test
    void ageTicksIsWhatTheInputGraceUses() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        assertEquals(-1, t.engine.targeting().ageTicks(p));
        t.engine.activator().activate(p, "blink");
        assertEquals(0, t.engine.targeting().ageTicks(p), "the opening press and its echoes land at age 0-2");
        t.time.advance(3);
        assertEquals(3, t.engine.targeting().ageTicks(p));
    }

    @Test
    void optionalTimeoutStillWorks() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");
        t.time.advance(40);
        assertFalse(t.engine.targeting().isTargeting(p));
        assertEquals(list("timeout"), t.render.previewEnds);
        assertEquals(0, t.time.pendingTasks(), "ticker stops when nobody aims");
    }

    @Test
    void stunWhileAimingCancels() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");
        t.engine.statuses().apply(p, "stun", 20, null);
        t.time.advance(1);
        assertFalse(t.engine.targeting().isTargeting(p));
        assertEquals(list("blocked"), t.render.previewEnds);
    }

    @Test
    void quickCastWithGroundRuleRefusesTheSky() {
        TestEngine t = new TestEngine();
        t.load(map("abilities", map("hop", map("cooldown", 50,
                "targeting", map("shape", "circle", "range", 10, "ground", true),
                "nodes", map("p", map("type", "print", "message", "x"))))));
        UUID p = t.spawn(0, 1, 0); // no floor anywhere
        t.engine.targeting().toggleQuickCast(p);
        assertEquals("no_ground", t.engine.activator().activate(p, "hop").reason());
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "hop"));
    }

    @Test
    void quickCastSkipsThePreview() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.targeting().toggleQuickCast(p);
        assertTrue(t.engine.activator().activate(p, "blink").success());
        assertFalse(t.engine.targeting().isTargeting(p));
        assertEquals(1.0, t.damage(p), 1e-9);
    }

    @Test
    void aimIsAlsoFilledForUnaimedAbilities() {
        TestEngine t = new TestEngine();
        t.world.floor(0);
        UUID p = t.spawn(0, 2, 0);
        t.world.look(p, new Vec3(1, -1, 0));
        t.load(map("abilities", map("ping", map("nodes", map("cue",
                map("type", "play_cue", "cue", "here", "at", "aim"))))));
        t.engine.activator().activate(p, "ping");
        assertEquals(list("here"), t.render.cues, "aim was set, so the cue found a position");
    }

    @Test
    void confirmRechecksCooldownLikeStuff() {
        TestEngine t = setup();
        UUID p = t.spawn(0, 1, 0);
        t.engine.activator().activate(p, "blink");
        t.engine.cooldowns().start(p, "blink", 50); // something put it on cooldown while aiming
        assertTrue(t.engine.targeting().confirm(p).reason().startsWith("on_cooldown"));
        assertEquals(0.0, t.damage(p), 1e-9);
    }

}
