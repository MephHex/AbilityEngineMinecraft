package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.platform.Animator;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * time_scale (a time slow): channels, charge-ups, repeats and shots run on the holder's own time, and animations play at
 * attack speed / fitted to a duration.
 */
class TimeScaleTest {

    private TestEngine t;
    private final AnimationTest.Recorder anim = new AnimationTest.Recorder();

    private static Object done(String cue) { return map("type", "play_cue", "cue", cue); }

    private UUID setup() {
        t = new TestEngine();
        t.engine.setAnimator(anim);
        t.load(map(
                "statuses", map(
                        "slowed_time", map("time_scale", 0.5),
                        "quick_time", map("time_scale", 2.0),
                        "frenzy", map("attack_speed", 1.5)),
                "abilities", map(
                        "jab", map("cooldown", 20, "nodes", map(
                                "a", map("type", "animate", "part", "swing", "fit", "attack", "scale_with", "attack_speed"))),
                        "channel", map("nodes", map(
                                "a", map("type", "animate", "part", "channel", "fit", 20, "next", "b"),
                                "b", map("type", "delay", "ticks", 20, "cast_bar", true, "next", "c"),
                                "c", done("channel_done"))),
                        "windup", map("nodes", map(
                                "a", map("type", "charge", "ticks", 20, "min", 15, "on", map("out", "b", "early", "c")),
                                "b", done("charged"),
                                "c", done("too_early"))),
                        "spin", map("nodes", map(
                                "a", map("type", "repeat", "times", 3, "every", 10, "on", map("each", "b", "out", "c")),
                                "b", done("spin"),
                                "c", done("spun"))),
                        "shot", map("nodes", map(
                                "a", map("type", "projectile", "speed", 0.1, "size", 0.2, "lifetime", 10,
                                        "on", map("expired", "b")),
                                "b", done("shot_gone")))),
                "characters", map("knight", map("slots", map("primary", "jab", "ability_1", "channel",
                        "ability_2", "windup", "ability_3", "spin", "ultimate", "shot")))));
        UUID id = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(id, "knight");
        return id;
    }

    private int count(String cue) { return Collections.frequency(t.render.cues, cue); }

    @Test
    void aChannelTakesTwiceAsLongAtHalfTime() {
        UUID k = setup();
        t.engine.statuses().apply(k, "slowed_time", 0, null);
        assertEquals(0.5, t.engine.timeScale(k), 1e-9);
        t.engine.loadouts().activate(k, Slots.ABILITY_1);
        t.time.advance(25);
        assertEquals(0, count("channel_done"), "20 ticks of his time: 40 real ones");
        t.time.advance(16);
        assertEquals(1, count("channel_done"));
    }

    @Test
    void aSlowLandingMidwayStretchesTheRest() {
        UUID k = setup();
        t.engine.loadouts().activate(k, Slots.ABILITY_1);
        t.time.advance(10);
        t.engine.statuses().apply(k, "slowed_time", 0, null); // half done; the other 10 take 20
        t.time.advance(15);
        assertEquals(0, count("channel_done"));
        t.time.advance(6);
        assertEquals(1, count("channel_done"));
    }

    @Test
    void fasterTimeEndsItSooner() {
        UUID k = setup();
        t.engine.statuses().apply(k, "quick_time", 0, null);
        t.engine.loadouts().activate(k, Slots.ABILITY_1);
        t.time.advance(11);
        assertEquals(1, count("channel_done"), "20 of his ticks in 10");
    }

    @Test
    void normalTimeIsUnchanged() {
        UUID k = setup();
        t.engine.loadouts().activate(k, Slots.ABILITY_1);
        t.time.advance(19);
        assertEquals(0, count("channel_done"));
        t.time.advance(1);
        assertEquals(1, count("channel_done"));
    }

    @Test
    void aChargeFillsOnHisOwnTime() {
        UUID k = setup();
        t.engine.loadouts().activate(k, Slots.ABILITY_2);
        t.time.advance(16);
        t.engine.instances().release(k);
        assertEquals(1, count("charged"), "16 ticks held: past min 15");

        t.engine.statuses().apply(k, "slowed_time", 0, null);
        t.engine.loadouts().activate(k, Slots.ABILITY_2);
        t.time.advance(16);
        t.engine.instances().release(k);
        assertEquals(1, count("too_early"), "16 real ticks at half time: 8 of his, short of min 15");
    }

    @Test
    void aFullChargeFiresLaterWhenSlowed() {
        UUID k = setup();
        t.engine.statuses().apply(k, "slowed_time", 0, null);
        t.engine.loadouts().activate(k, Slots.ABILITY_2);
        t.time.advance(30);
        assertEquals(0, count("charged"), "full after 40 real ticks");
        t.time.advance(11);
        assertEquals(1, count("charged"));
    }

    @Test
    void repeatsComeSlower() {
        UUID k = setup();
        t.engine.statuses().apply(k, "slowed_time", 0, null);
        t.engine.loadouts().activate(k, Slots.ABILITY_3);
        assertEquals(1, count("spin"));
        t.time.advance(15);
        assertEquals(1, count("spin"), "every 10 of his ticks: 20 real ones");
        t.time.advance(6);
        assertEquals(2, count("spin"));
    }

    @Test
    void aSlowedShootersShotLivesLonger() {
        UUID k = setup();
        t.engine.statuses().apply(k, "slowed_time", 0, null);
        t.engine.loadouts().activate(k, Slots.ULTIMATE);
        t.time.advance(15);
        assertEquals(0, count("shot_gone"), "10 ticks of life at half time: 20");
        t.time.advance(10);
        assertEquals(1, count("shot_gone"));
    }

    @Test
    void attackAnimationsFitTheAttackAndItsSpeed() {
        UUID k = setup();
        t.engine.statuses().apply(k, "frenzy", 0, null);
        t.engine.loadouts().activate(k, Slots.PRIMARY);
        // fit: attack = 20 / 1.5 = 13 ticks; scale_with: attack_speed = x1.5 on top
        assertEquals(new Animator.Settings(1.5, 0, 0, true, 13), anim.settings.get(0));
    }

    @Test
    void aChannelAnimationFitsItsTicks() {
        UUID k = setup();
        t.engine.loadouts().activate(k, Slots.ABILITY_1);
        assertEquals(new Animator.Settings(1, 0, 0, true, 20), anim.settings.get(0));
    }

    @Test
    void slowFieldsSlowEnemiesInsideButNotTheOwner() {
        t = new TestEngine();
        t.load(map("abilities", map("field", map("nodes", map(
                "a", map("type", "slow_field", "at", "caster", "length", 10, "width", 10, "slow", 0.5, "time_scale", 0.4,
                        "next", "b"),
                "b", map("type", "delay", "ticks", 100))))));
        UUID owner = t.spawn(0, 1, 0);
        UUID enemy = t.spawn(2, 1, 0);
        UUID far = t.spawn(40, 1, 0);
        t.engine.activator().activate(owner, "field");
        assertEquals(0.4, t.engine.timeScale(enemy), 1e-9);
        assertEquals(1, t.engine.timeScale(owner), 1e-9);
        assertEquals(1, t.engine.timeScale(far), 1e-9);
    }

    @Test
    void badValuesAreReported() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map(
                "statuses", map("stopped", map("time_scale", 0)),
                "abilities", map(
                        "a", map("nodes", map("x", map("type", "animate", "part", "p", "fit", 0))),
                        "b", map("nodes", map("x", map("type", "animate", "part", "p", "scale_with", "luck"))))), "src/test");
        String errors = String.join("\n", report.errors());
        assertEquals(3, report.errors().size(), errors);
        assertTrue(errors.contains("time_scale") && errors.contains("fit") && errors.contains("scale_with"), errors);
    }
}
