package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.platform.Animator;
import me.mephisto.ability_engine.engine.platform.CueHandle;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Model Engine support, engine side: a character's model, and animate / stop_animation in a cast. */
class AnimationTest {

    /** Records what's played and stopped: "play <entity> <name>", "stop <entity> <name>". */
    static final class Recorder implements Animator {
        final List<String> log = new ArrayList<>();
        final List<Settings> settings = new ArrayList<>();

        @Override
        public CueHandle play(UUID entity, String animation, Settings settings) {
            this.settings.add(settings);
            log.add("play " + entity + " " + animation);
            return () -> log.add("stop " + entity + " " + animation);
        }

        @Override
        public void stop(UUID entity, String animation) { log.add("stop " + entity + " " + animation); }
    }

    private TestEngine t;
    private final Recorder anim = new Recorder();

    private UUID setup(Object model) {
        t = new TestEngine();
        t.engine.setAnimator(anim);
        var knight = new java.util.LinkedHashMap<String, Object>(map("slots", map(
                "ability_1", "lunge", "ability_2", "brace", "ultimate", "helper_caller")));
        if (model != null) knight.put("model", model);
        t.load(map(
                "abilities", map(
                        "lunge", map("nodes", map(
                                "a", map("type", "animate", "part", "swing", "speed", 1.5, "lerp_in", 4, "lerp_out", 2,
                                        "next", "b"),
                                "b", map("type", "animate", "animation", "knight_shout"))),
                        "brace", map("nodes", map(
                                "a", map("type", "animate", "part", "hold", "until_end", true, "next", "b"),
                                "b", map("type", "delay", "ticks", 20, "next", "c"),
                                "c", map("type", "stop_animation", "animation", "knight_shout"))),
                        "helper_caller", map("nodes", map("a", map("type", "cast", "ability", "knight_helper", "free", true))),
                        "knight_helper", map("nodes", map("a", map("type", "animate", "part", "flourish")))),
                "characters", map("knight", knight)));
        UUID id = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(id, "knight");
        return id;
    }

    @Test
    void partsAreNamedAfterTheCharacterAndSlot() {
        UUID k = setup(null);
        assertTrue(t.engine.loadouts().activate(k, Slots.ABILITY_1).success());
        assertEquals(List.of("play " + k + " knight_ability1_swing", "play " + k + " knight_shout"), anim.log);
    }

    @Test
    void settingsReachTheAnimator() {
        UUID k = setup(null);
        t.engine.loadouts().activate(k, Slots.ABILITY_1);
        assertEquals(List.of(new Animator.Settings(1.5, 4, 2, true), Animator.Settings.DEFAULT), anim.settings);
    }

    @Test
    void theModelsPrefixNamesTheParts() {
        UUID k = setup(map("id", "knight_model", "hide_player", true, "prefix", "sirknight"));
        CharacterDef.Model model = t.engine.loadouts().characterOf(k).orElseThrow().model();
        assertEquals(new CharacterDef.Model("knight_model", true, "sirknight"), model);
        t.engine.loadouts().activate(k, Slots.ABILITY_1);
        assertEquals("play " + k + " sirknight_ability1_swing", anim.log.get(0));
    }

    @Test
    void aModelCanBeJustItsId() {
        UUID k = setup("knight_model");
        assertEquals(new CharacterDef.Model("knight_model", false, null), t.engine.loadouts().characterOf(k).orElseThrow().model());
        assertEquals("knight", t.engine.loadouts().characterOf(k).orElseThrow().animationPrefix(), "no prefix: the character's id");
    }

    @Test
    void aModelIsShownOnlyForItsAnimationsUnlessAlways() {
        UUID k = setup(map("id", "knight_model"));
        assertTrue(!t.engine.loadouts().characterOf(k).orElseThrow().model().always());
        UUID w = setup(map("id", "knight_model", "show", "always", "hide_player", true));
        assertEquals(new CharacterDef.Model("knight_model", true, null, true), t.engine.loadouts().characterOf(w).orElseThrow().model());
    }

    @Test
    void theHeldItemIsInTheRightHandUnlessTurnedOff() {
        UUID k = setup(map("id", "knight_model"));
        assertEquals(CharacterDef.Hold.DEFAULT, t.engine.loadouts().characterOf(k).orElseThrow().model().hold());
        UUID tuned = setup(map("id", "knight_model", "hold_offset", java.util.List.of(0, -9.5, 1),
                "hold_rotation", java.util.List.of(-80, 180, 0)));
        assertEquals(new CharacterDef.Hold(0, -9.5, 1, -80, 180, 0), t.engine.loadouts().characterOf(tuned).orElseThrow().model().hold());
        UUID none = setup(map("id", "knight_model", "hold", false));
        assertNull(t.engine.loadouts().characterOf(none).orElseThrow().model().hold());
    }

    @Test
    void animationsBlendInAndOutUnlessToldOtherwise() {
        UUID k = setup(map("id", "knight_model"));
        assertEquals(CharacterDef.Model.DEFAULT_BLEND, t.engine.loadouts().characterOf(k).orElseThrow().model().blend());
        UUID sharp = setup(map("id", "knight_model", "blend", 0));
        assertEquals(0, t.engine.loadouts().characterOf(sharp).orElseThrow().model().blend());
    }

    @Test
    void scaleIsAutomaticUnlessGiven() {
        UUID k = setup(map("id", "knight_model"));
        assertEquals(0, t.engine.loadouts().characterOf(k).orElseThrow().model().scale());
        UUID big = setup(map("id", "knight_model", "scale", 1.5));
        assertEquals(1.5, t.engine.loadouts().characterOf(big).orElseThrow().model().scale());
    }

    @Test
    void aBadHoldOffsetIsReported() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map("characters", map("knight", map(
                "slots", map(), "model", map("id", "m", "hold_offset", java.util.List.of(1, 2))))), "src/test");
        assertTrue(String.join(" ", report.errors()).contains("hold_offset"), String.join(" ", report.errors()));
    }

    @Test
    void anUnknownShowIsReported() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map("characters", map("knight", map(
                "slots", map(), "model", map("id", "m", "show", "sometimes")))), "src/test");
        assertTrue(String.join(" ", report.errors()).contains("show"), String.join(" ", report.errors()));
    }

    @Test
    void noModelNoField() {
        UUID k = setup(null);
        assertNull(t.engine.loadouts().characterOf(k).orElseThrow().model());
    }

    @Test
    void untilEndStopsItWhenTheCastEnds() {
        UUID k = setup(null);
        t.engine.loadouts().activate(k, Slots.ABILITY_2);
        assertEquals(List.of("play " + k + " knight_ability2_hold"), anim.log);
        t.time.advance(25);
        assertEquals(List.of("play " + k + " knight_ability2_hold", "stop " + k + " knight_shout",
                "stop " + k + " knight_ability2_hold"), anim.log, "stop_animation, then the cast ends: its hold stops");
    }

    @Test
    void aHelperWithoutASlotUsesItsAbilityId() {
        UUID k = setup(null);
        assertTrue(t.engine.loadouts().activate(k, Slots.ULTIMATE).success());
        assertEquals(List.of("play " + k + " knight_knighthelper_flourish"), anim.log);
    }

    @Test
    void withoutAModelPluginNothingBreaks() {
        UUID k = setup(null);
        t.engine.setAnimator(null); // Animator.NONE
        assertTrue(t.engine.loadouts().activate(k, Slots.ABILITY_1).success());
        t.engine.loadouts().activate(k, Slots.ABILITY_2);
        t.time.advance(25);
        assertTrue(anim.log.isEmpty());
    }

    @Test
    void badAnimateNodesAreReported() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map("abilities", map(
                "both", map("nodes", map("a", map("type", "animate", "part", "x", "animation", "y"))),
                "neither", map("nodes", map("a", map("type", "animate"))),
                "slow", map("nodes", map("a", map("type", "animate", "part", "x", "speed", 0))))), "src/test");
        String errors = String.join("\n", report.errors());
        assertEquals(3, report.errors().size(), errors);
        assertTrue(errors.contains("one of them"), errors);
        assertTrue(errors.contains("speed"), errors);
    }

    @Test
    void theShippedArcanistSwingsOnEveryShot() throws java.io.IOException {
        TestEngine t = new TestEngine();
        var report = me.mephisto.ability_engine.engine.testkit.ShippedContent.realReport(t.engine);
        assertTrue(report.isClean(), () -> String.join(System.lineSeparator(), report.errors()));
        Recorder anim = new Recorder();
        t.engine.setAnimator(anim);
        t.world.floor(0);
        UUID p = t.spawn(0, 1, 0);
        t.world.players.add(p);
        t.world.look(p, new me.mephisto.ability_engine.engine.math.Vec3(1, 0, 0));
        t.engine.loadouts().assign(p, "arcanist");
        CharacterDef.Model model = t.engine.loadouts().characterOf(p).orElseThrow().model();
        assertEquals("arcanist", model.id());
        assertTrue(model.always() && model.hidePlayer(), "he is the model: worn always over his hidden body");
        assertEquals("arcanist_walk", model.behavior().pick(java.util.Set.of("moving"), tag -> false, 1).animation());
        assertEquals("arcanist_run", model.behavior().pick(java.util.Set.of("moving", "sprinting"), tag -> false, 1).animation());
        assertEquals("arcanist_idle", model.behavior().pick(java.util.Set.of("on_ground"), tag -> false, 1).animation());
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        assertEquals(List.of("play " + p + " animationTest"), anim.log);
        assertEquals(16, anim.settings.get(0).fit(), "1.25 shots a second: 16 ticks between them");
    }
}
