package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.ModelBehavior;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player as their model: its states (picked by what they do) and on: events, as loaded and picked. */
class ModelBehaviorTest {

    private static final Predicate<String> NO_TAGS = tag -> false;

    private static CharacterDef.Model load(Object model) {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map("characters", map("knight", map(
                "slots", map(), "model", model))), "src/test");
        assertTrue(report.errors().isEmpty(), String.join(" ", report.errors()));
        UUID k = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(k, "knight");
        return t.engine.loadouts().characterOf(k).orElseThrow().model();
    }

    private static String error(Object model) {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map("characters", map("knight", map(
                "slots", map(), "model", model))), "src/test");
        return String.join(" ", report.errors());
    }

    private static final Object KNIGHT = map("id", "knight_model",
            "states", List.of(
                    map("animation", "knight_stunned", "when", "state.stunned"),
                    map("part", "swim", "when", "swimming"),
                    map("part", "sneak_walk", "when", List.of("sneaking", "moving")),
                    map("part", "sneak", "when", "sneaking"),
                    map("part", "run", "when", "sprinting", "scale_with", "movement", "made_for", 5.6),
                    map("part", "walk", "when", List.of("moving", "not animating"), "scale_with", "movement"),
                    map("part", "hurt_idle", "health_below", 0.3),
                    map("part", "idle")),
            "on", map("jump", "knight_jump", "hurt", "knight_hurt"));

    @Test
    void statesAndEventsLoad() {
        CharacterDef.Model m = load(KNIGHT);
        List<ModelBehavior.State> states = m.behavior().states();
        assertEquals(8, states.size());
        assertEquals("knight_swim", states.get(1).animation(), "part: is <character>_<part>");
        assertEquals(List.of(ModelBehavior.condition("sneaking"), ModelBehavior.condition("moving")), states.get(2).when());
        assertEquals(new ModelBehavior.Condition("animating", true), states.get(5).when().get(1));
        assertEquals(Map.of("jump", "knight_jump", "hurt", "knight_hurt"), m.behavior().on());
    }

    @Test
    void statesMakeTheModelWornAlwaysOverAHiddenBody() {
        CharacterDef.Model m = load(KNIGHT);
        assertTrue(m.always());
        assertTrue(m.hidePlayer());
        CharacterDef.Model plain = load(map("id", "knight_model"));
        assertFalse(plain.always());
        assertFalse(plain.hidePlayer());
        assertTrue(plain.behavior().isEmpty());
        CharacterDef.Model shown = load(map("id", "knight_model", "hide_player", false, "on", map("swing", "knight_swing")));
        assertTrue(shown.always());
        assertFalse(shown.hidePlayer(), "hide_player: false still shows their body");
    }

    @Test
    void theFirstStateThatHoldsPlays() {
        ModelBehavior b = load(KNIGHT).behavior();
        assertEquals("knight_idle", b.pick(Set.of("on_ground"), NO_TAGS, 1).animation());
        assertEquals("knight_walk", b.pick(Set.of("moving"), NO_TAGS, 1).animation());
        assertEquals("knight_run", b.pick(Set.of("moving", "sprinting"), NO_TAGS, 1).animation());
        assertEquals("knight_sneak_walk", b.pick(Set.of("moving", "sneaking"), NO_TAGS, 1).animation());
        assertEquals("knight_sneak", b.pick(Set.of("sneaking"), NO_TAGS, 1).animation());
        assertEquals("knight_stunned", b.pick(Set.of("moving"), "state.stunned"::equals, 1).animation(), "tags count");
    }

    @Test
    void notAndHealthConditions() {
        ModelBehavior b = load(KNIGHT).behavior();
        assertEquals("knight_idle", b.pick(Set.of("moving", "animating"), NO_TAGS, 1).animation(),
                "not animating: no walk while an ability animates");
        assertEquals("knight_hurt_idle", b.pick(Set.of(), NO_TAGS, 0.2).animation());
        assertEquals("knight_idle", b.pick(Set.of(), NO_TAGS, 0.5).animation());
    }

    @Test
    void noFallbackNoState() {
        ModelBehavior b = load(map("id", "m", "states", List.of(map("animation", "walk", "when", "moving")))).behavior();
        assertNull(b.pick(Set.of(), NO_TAGS, 1));
    }

    @Test
    void movementScalesTheSpeed() {
        List<ModelBehavior.State> states = load(KNIGHT).behavior().states();
        ModelBehavior.State walk = states.get(5), run = states.get(4), idle = states.get(7);
        assertEquals(1, walk.speedAt(ModelBehavior.WALK_SPEED), 1e-9);
        assertEquals(0.5, walk.speedAt(ModelBehavior.WALK_SPEED / 2), 1e-9, "slowed: walks slower");
        assertEquals(1, run.speedAt(5.6), 1e-9, "made_for: its own pace");
        assertEquals(0.25, walk.speedAt(0), 1e-9, "never below a quarter");
        assertEquals(1, idle.speedAt(10), 1e-9, "not scaled");
    }

    @Test
    void ownAnimationsAreKnown() {
        assertTrue(load(KNIGHT).behavior().animations().containsAll(List.of("knight_idle", "knight_jump", "knight_stunned")));
    }

    @Test
    void mistakesAreReported() {
        assertTrue(error(map("id", "m", "states", List.of(map("animation", "a", "when", "dancing")))).contains("dancing"));
        assertTrue(error(map("id", "m", "on", map("sneeze", "a"))).contains("sneeze"));
        assertTrue(error(map("id", "m", "show", "animating", "states", List.of(map("animation", "a")))).contains("show"));
        assertTrue(error(map("id", "m", "states", List.of(map("when", "moving")))).contains("animation"));
        assertTrue(error(map("id", "m", "states", List.of(map("animation", "a", "health_below", 2)))).contains("health_below"));
        assertTrue(error(map("id", "m", "states", List.of(map("animation", "a", "scale_with", "mood")))).contains("scale_with"));
        assertTrue(error(map("id", "m", "states", map("idle", "a"))).contains("states"));
    }

    @Test
    void thePrefixNamesParts() {
        assertEquals("kn_walk", load(map("id", "m", "prefix", "kn", "states", List.of(map("part", "walk"))))
                .behavior().states().get(0).animation());
    }
}
