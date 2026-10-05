package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** cooldown_reduction on statuses (items): abilities come back sooner; primary / secondary fire don't. */
class CooldownReductionTest {

    private TestEngine t;

    /** "mage": primary jab (20), secondary poke (40), ability 1 zap (100), ability 2 dash (100, 2 charges). */
    private UUID setup() {
        t = new TestEngine();
        t.load(map(
                "abilities", map(
                        "jab", map("cooldown", 20, "nodes", map("n", map("type", "print", "message", "jab"))),
                        "poke", map("cooldown", 40, "nodes", map("n", map("type", "print", "message", "poke"))),
                        "zap", map("cooldown", 100, "nodes", map("n", map("type", "print", "message", "zap"))),
                        "dash", map("cooldown", 100, "charges", 2, "nodes", map("n", map("type", "print", "message", "dash")))),
                "statuses", map(
                        "haste_ring", map("cooldown_reduction", 0.9),
                        "focus", map("cooldown_reduction", 0.95, "stacking", "stack", "max_stacks", 3)),
                "characters", map("mage", map("slots", map("primary", "jab", "secondary", "poke",
                        "ability_1", "zap", "ability_2", "dash")))));
        UUID id = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(id, "mage");
        return id;
    }

    @Test
    void abilitiesComeBackSoonerFireDoesnt() {
        UUID mage = setup();
        t.engine.statuses().apply(mage, "haste_ring", 0, null);
        assertEquals(0.9, t.engine.stats().cooldownMultiplier(mage), 1e-9);
        assertTrue(t.engine.loadouts().activate(mage, Slots.ABILITY_1).success());
        assertEquals(90, t.engine.cooldowns().remainingTicks(mage, "zap"), "10% off 100");
        assertTrue(t.engine.loadouts().activate(mage, Slots.PRIMARY).success());
        assertEquals(20, t.engine.cooldowns().remainingTicks(mage, "jab"), "primary fire: attack speed's, not this");
        assertTrue(t.engine.loadouts().activate(mage, Slots.SECONDARY).success());
        assertEquals(40, t.engine.cooldowns().remainingTicks(mage, "poke"), "secondary fire neither");
    }

    @Test
    void sourcesAndStacksMultiply() {
        UUID mage = setup();
        t.engine.statuses().apply(mage, "haste_ring", 0, null);
        t.engine.statuses().apply(mage, "focus", 100, null);
        t.engine.statuses().apply(mage, "focus", 100, null);
        assertEquals(0.9 * 0.95 * 0.95, t.engine.stats().cooldownMultiplier(mage), 1e-9);
    }

    @Test
    void chargesComeBackOnTheReducedTime() {
        UUID mage = setup();
        t.engine.statuses().apply(mage, "haste_ring", 0, null);
        assertTrue(t.engine.loadouts().activate(mage, Slots.ABILITY_2).success());
        assertTrue(t.engine.loadouts().activate(mage, Slots.ABILITY_2).success());
        assertEquals(0, t.engine.cooldowns().charges(mage, "dash"));
        assertEquals(90, t.engine.cooldowns().remainingTicks(mage, "dash"), "the first charge: 90, not 100");
    }

    @Test
    void itMustBeBelowOneAndAboveZero() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map(
                "statuses", map("too_much", map("cooldown_reduction", 1.1), "none", map("cooldown_reduction", 0))), "src/test");
        String errors = String.join("\n", report.errors());
        assertEquals(2, errors.lines().filter(l -> l.contains("cooldown_reduction")).count(), errors);
        assertTrue(errors.contains("below 1"), errors);
    }
}
