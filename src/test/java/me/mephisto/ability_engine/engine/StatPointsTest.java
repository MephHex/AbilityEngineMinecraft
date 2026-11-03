package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.stats.StatSheets;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characters' own spell resistance (from their armor), cooldown reduction as points (like armor: never 100%), and flat
 * stats from items (armor, max HP, base damage on top of the character's own).
 */
class StatPointsTest {

    private TestEngine t;
    private UUID p;

    private void setup(double armor) {
        t = new TestEngine();
        t.load(map(
                "abilities", map("jab", map("cooldown", 20, "nodes", map("n", map("type", "print", "message", "jab"))),
                        "spell", map("cooldown", 200, "nodes", map("n", map("type", "print", "message", "spell")))),
                "statuses", map(
                        "haste", map("additive", true, "stacking", "stack", "max_stacks", 30, "ability_haste", 25),
                        "plate", map("additive", true, "stacking", "stack", "max_stacks", 9, "armor_flat", 10,
                                "max_health_flat", 50, "base_damage_flat", 6),
                        "big", map("additive", true, "stacking", "stack", "max_stacks", 9, "armor", 1.5, "max_health", 1.2)),
                "characters", map("hero", map("stats", map("health", 200, "armor", armor, "base_damage", 40),
                        "slots", map("primary", "jab", "ability_1", "spell")))));
        p = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(p, "hero");
    }

    @Test
    void ownSpellResistanceComesFromArmor() {
        assertEquals(0, StatSheets.baseSpellResistance(0), 1e-9);
        assertEquals(8, StatSheets.baseSpellResistance(8), 1e-9, "below 10: as much as the armor");
        assertEquals(10, StatSheets.baseSpellResistance(15), 1e-9, "10 to 20: less 5");
        assertEquals(15, StatSheets.baseSpellResistance(20), 1e-9);
        assertEquals(30, StatSheets.baseSpellResistance(40), 1e-9, "above 20: less 10");
        setup(25);
        assertEquals(0, t.engine.stats().spellResistance(p), 1e-9, "off until configured");
        t.engine.stats().setResistanceFromArmor(true);
        assertEquals(15, t.engine.stats().spellResistance(p), 1e-9, "25 armor: 15 of their own");
    }

    @Test
    void cooldownReductionIsPointsLikeArmor() {
        setup(0);
        assertEquals(200, t.engine.stats().cooldownTicks(p, "spell", 200));
        t.engine.statuses().apply(p, "haste", 0, p); // 25 points
        assertEquals(160, t.engine.stats().cooldownTicks(p, "spell", 200), "x100/125");
        for (int i = 0; i < 29; i++) t.engine.statuses().apply(p, "haste", 0, p); // 30 copies: 750 points
        assertEquals(Math.round(200 * 100.0 / 850), t.engine.stats().cooldownTicks(p, "spell", 200), "lots: still not 0");
        assertEquals(20, t.engine.stats().cooldownTicks(p, "jab", 20), "basic attacks don't care");
    }

    @Test
    void flatStatsAddToTheCharactersOwnBeforePercents() {
        setup(20);
        t.engine.statuses().apply(p, "plate", 0, p);
        assertEquals(30, t.engine.stats().armor(p), 1e-9, "20 + 10");
        assertEquals(250, t.engine.stats().maxHealth(p), 1e-9, "200 + 50");
        assertEquals(46, t.engine.stats().baseDamage(p), 1e-9, "40 + 6");
        assertEquals(6, t.engine.stats().bonusDamage(p), 1e-9, "the 6 is bonus damage");
        t.engine.statuses().apply(p, "big", 0, p);
        assertEquals(45, t.engine.stats().armor(p), 1e-9, "(20 + 10) x1.5");
        assertEquals(300, t.engine.stats().maxHealth(p), 1e-9, "(200 + 50) x1.2");
    }
}
