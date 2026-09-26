package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutTest {

    private static Map<String, Object> selfDamage(double amount) {
        return map("cooldown", 20, "nodes", map("hit", map("type", "apply_effects", "targets", map("type", "self"),
                "effects", list(map("id", "damage", "amount", amount)))));
    }

    private static Map<String, Object> file() {
        return map(
                "abilities", map("small", selfDamage(1), "big", selfDamage(10)),
                "characters", map("tester", map("name", "Tester", "weapon", "IRON_SWORD",
                        "slots", map("ability_1", "small", "ultimate", "big"))));
    }

    @Test
    void slotFiresTheAbilityInThatSlot() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(file());
        t.engine.loadouts().assign(p, "tester");

        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertEquals(10.0, t.damage(p), 1e-9);
        assertEquals("empty_slot:ability_2", t.engine.loadouts().activate(p, Slots.ABILITY_2).reason());
    }

    @Test
    void noCharacterMeansNoSlotCasting() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(file());

        assertFalse(t.engine.loadouts().has(p));
        assertEquals("no_character", t.engine.loadouts().activate(p, Slots.ABILITY_1).reason());
    }

    @Test
    void slotsShareTheNormalCooldownAndBlockRules() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(file());
        t.engine.loadouts().assign(p, "tester");

        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).reason().startsWith("on_cooldown"));
        t.engine.statuses().apply(p, "stun", null);
        assertEquals("blocked:block.ability", t.engine.loadouts().activate(p, Slots.ULTIMATE).reason());
    }

    @Test
    void badKitsAreReportedWithPaths() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map(
                "abilities", map("small", selfDamage(1)),
                "characters", map(
                        "typo_slot", map("slots", map("abilty_1", "small")),
                        "missing_ability", map("slots", map("ultimate", "nope")))), "abilities.yml");

        assertEquals(0, report.characters());
        assertEquals(2, report.errors().size());
        assertTrue(report.errors().get(0).startsWith("abilities.yml.characters.typo_slot.slots.abilty_1: unknown slot"), report.errors().get(0));
        assertTrue(report.errors().get(1).contains("unknown ability 'nope'"), report.errors().get(1));
    }

    @Test
    void characterRemovedByReloadStopsCountingAsAssigned() {
        TestEngine t = new TestEngine();
        UUID p = t.spawn(0, 1, 0);
        t.load(file());
        t.engine.loadouts().assign(p, "tester");

        t.engine.characters().clear(); // what a reload does before re-reading the file
        assertFalse(t.engine.loadouts().has(p));
    }

    @Test
    void displayInfoIsLoaded() {
        TestEngine t = new TestEngine();
        t.load(map("abilities", map("shot", map(
                "display", map("name", "Arc Shot", "icon", "PRISMARINE_SHARD", "description", list("line 1", "line 2")),
                "nodes", map("p", map("type", "print", "message", "x"))))));

        var display = t.engine.abilities().find("shot").orElseThrow().display();
        assertEquals("Arc Shot", display.name());
        assertEquals("PRISMARINE_SHARD", display.icon());
        assertEquals(list("line 1", "line 2"), display.description());
    }

    @Test
    void archmageKitUsesAllFiveSlots() throws java.io.IOException {
        TestEngine t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        UUID p = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(p, "archmage");
        assertEquals("arcane_bolt", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow());
        assertEquals("arcane_missile", t.engine.loadouts().abilityIn(p, Slots.ABILITY_1).orElseThrow());
        assertEquals("foldstep", t.engine.loadouts().abilityIn(p, Slots.ABILITY_2).orElseThrow());
        assertEquals("unstable_binding", t.engine.loadouts().abilityIn(p, Slots.ABILITY_3).orElseThrow());
        assertEquals("empty_slot:ultimate", t.engine.loadouts().activate(p, Slots.ULTIMATE).reason());
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "LMB fires the bolt");
    }
}
