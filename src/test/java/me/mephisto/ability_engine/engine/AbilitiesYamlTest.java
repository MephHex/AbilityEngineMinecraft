package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import me.mephisto.ability_engine.engine.testkit.Yml;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped abilities.yml must always load cleanly. SnakeYAML comes with paper-api on the test classpath. */
class AbilitiesYamlTest {

    /** The REAL content (whatever it's tuned to) must always load without errors. */
    @Test
    void shippedContentLoadsWithoutErrors() throws IOException {
        TestEngine t = new TestEngine();
        LoadReport report = ShippedContent.realReport(t.engine);
        assertTrue(report.isClean(), String.join("\n", report.errors()));
        assertTrue(report.abilities() > 0 && report.characters() > 0);
    }

    @Test
    void theFrozenFixturesLoadToo() throws IOException {
        TestEngine t = new TestEngine();
        LoadReport report = ShippedContent.loadInto(t.engine);
        assertTrue(report.isClean(), String.join("\n", report.errors()));
        assertEquals(124, report.abilities());
        assertEquals(95, report.statuses());
        assertEquals(19, report.characters());
        assertEquals(5, report.infusions());
    }

    private static TestEngine loadShipped() throws IOException {
        TestEngine t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        return t;
    }

    @Test
    void filesCanUseEachOthersContentInAnyOrder() throws IOException {
        TestEngine t = loadShipped();
        // duelist.yml's abilities use stun from shared.yml; archmage.yml's abilities use statuses from shared.yml
        assertEquals("parasol_guard", t.engine.characters().find("duelist").orElseThrow().abilityIn("ability_2"));
    }

    @Test
    void theSameIdInTwoFilesIsReportedWithBothNames() {
        TestEngine t = new TestEngine();
        Map<String, Object> a = Yml.abilities("zap", Yml.map("nodes", Yml.map("p", Yml.map("type", "print", "message", "a"))));
        Map<String, Object> b = Yml.abilities("zap", Yml.map("nodes", Yml.map("p", Yml.map("type", "print", "message", "b"))));
        LoadReport report = new AbilityLoader(t.engine).load(List.of(
                new AbilityLoader.Source("content/one.yml", a), new AbilityLoader.Source("content/two.yml", b)));
        assertEquals(1, report.abilities(), "the first one wins");
        assertEquals(List.of("content/two.yml: ability 'zap' is already defined in content/one.yml (skipped)"), report.errors());
    }

    @Test
    void aCharacterCanUseAnAbilityFromALaterFile() {
        TestEngine t = new TestEngine();
        Map<String, Object> kit = Yml.map("characters", Yml.map("k", Yml.map("slots", Yml.map("primary", "zap"))));
        Map<String, Object> ability = Yml.abilities("zap", Yml.map("nodes", Yml.map("p", Yml.map("type", "print", "message", "x"))));
        LoadReport report = new AbilityLoader(t.engine).load(List.of(
                new AbilityLoader.Source("a_kits.yml", kit), new AbilityLoader.Source("b_abilities.yml", ability)));
        assertTrue(report.isClean(), report.errors().toString());
    }

    /** Regression: SnakeYAML reads an unquoted "on:" key as boolean true, which silently dropped every edge. */
    @Test
    void onPortsFromRealYamlAreWired() throws IOException {
        TestEngine t = loadShipped();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(5, 1, 0);

        assertTrue(t.engine.activator().activate(caster, "stun_hit").success());
        assertTrue(t.engine.tags().has(mob, Tags.STUNNED), "stun_hit: aim.hit must lead to the stun node");

        assertTrue(t.engine.activator().activate(caster, "test_blast").success());
        assertEquals(40.0, t.damage(mob), 1e-9);
    }

    @Test
    void badDataNamesTheExactPath() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(Yml.abilities(
                "bad", Yml.map("nodes", Yml.map("n", Yml.map("type", "apply_effects",
                        "targets", Yml.map("type", "self"),
                        "effects", Yml.list(Yml.map("id", "damage")))))),
                "abilities.yml");
        assertEquals(1, report.errors().size());
        assertEquals("abilities.yml.abilities.bad.nodes.n.effects[0].amount: give amount: <flat>, "
                + "base: <x base damage> and/or max_hp: <x target's max HP>", report.errors().get(0));
    }
}
