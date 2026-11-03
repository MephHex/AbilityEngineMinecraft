package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** start_cue's ticks: a looping cue (a vanilla pose) that stops before a long cast does. */
class StartCueTicksTest {

    private TestEngine t;

    private UUID caster(Object ticks) {
        t = new TestEngine();
        var cue = new java.util.LinkedHashMap<String, Object>(map("type", "start_cue", "cue", "pose_bow", "next", "wait"));
        if (ticks != null) cue.put("ticks", ticks);
        t.load(map(
                "abilities", map("shot", map("nodes", map(
                        "pose", cue,
                        "wait", map("type", "delay", "ticks", 40)))),
                "characters", map("archer", map("slots", map("primary", "shot")))));
        UUID id = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(id, "archer");
        return id;
    }

    @Test
    void ticksStopItBeforeTheCastEnds() {
        UUID a = caster(10);
        assertTrue(t.engine.loadouts().activate(a, Slots.PRIMARY).success());
        assertEquals(List.of("pose_bow"), t.render.loops);
        t.time.advance(9);
        assertEquals(List.of("pose_bow"), t.render.loops, "still on");
        t.time.advance(2);
        assertEquals(List.of(), t.render.loops, "off after 10 ticks, the cast still going");
        t.time.advance(40);
        assertEquals(List.of(), t.render.loops, "the cast's end doesn't stop it twice");
    }

    @Test
    void withoutTicksItLastsTheCast() {
        UUID a = caster(null);
        t.engine.loadouts().activate(a, Slots.PRIMARY);
        t.time.advance(30);
        assertEquals(List.of("pose_bow"), t.render.loops);
        t.time.advance(15);
        assertEquals(List.of(), t.render.loops);
    }

    @Test
    void aCastEndingFirstStopsItThen() {
        UUID a = caster(100);
        t.engine.loadouts().activate(a, Slots.PRIMARY);
        t.time.advance(45);
        assertEquals(List.of(), t.render.loops);
    }

    @Test
    void badTicksAreReported() {
        TestEngine e = new TestEngine();
        LoadReport report = new AbilityLoader(e.engine).load(map("abilities", map("shot", map("nodes", map(
                "pose", map("type", "start_cue", "cue", "pose_bow", "ticks", 0))))), "src/test");
        assertTrue(String.join(" ", report.errors()).contains("ticks"), String.join(" ", report.errors()));
    }
}
