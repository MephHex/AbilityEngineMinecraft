package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * additive: true statuses (items) add their bonuses up, one per stack (two +20% = +40%), instead of multiplying; the
 * rest still multiply, with that total.
 */
class AdditiveStatsTest {

    private TestEngine t;
    private UUID me;

    private void setup() {
        t = new TestEngine();
        t.load(map("statuses", map(
                "fang", map("additive", true, "stacking", "stack", "max_stacks", 9, "damage_dealt", 1.2, "cooldown_reduction", 0.95),
                "vamp", map("additive", true, "damage_dealt", 1.2),
                "strength", map("damage_dealt", 1.5),
                "fast", map("additive", true, "stacking", "stack", "max_stacks", 30, "cooldown_reduction", 0.9))));
        me = t.spawn(0, 1, 0);
    }

    @Test
    void additiveBonusesAddUpPerStackAndMultiplyWithTheRest() {
        setup();
        t.engine.statuses().apply(me, "fang", 0, me);
        assertEquals(1.2, t.engine.stats().damageDealtMultiplier(me), 1e-9);
        t.engine.statuses().apply(me, "fang", 0, me);              // a second copy: a second stack
        assertEquals(1.4, t.engine.stats().damageDealtMultiplier(me), 1e-9, "+20% +20%, not x1.44");
        t.engine.statuses().apply(me, "vamp", 0, me);
        assertEquals(1.6, t.engine.stats().damageDealtMultiplier(me), 1e-9, "another item adds too");
        t.engine.statuses().apply(me, "strength", 100, me);
        assertEquals(1.6 * 1.5, t.engine.stats().damageDealtMultiplier(me), 1e-9, "a plain buff multiplies the total");
        assertEquals(0.9, t.engine.stats().cooldownMultiplier(me), 1e-9, "two 5% = 10% cooldown reduction");
    }

    @Test
    void twoCopiesOfAnItemStackItsStatsButNotItsEffects() {
        t = new TestEngine();
        t.load(map("statuses", map(
                "nullmail", map("additive", true, "stacking", "stack", "max_stacks", 9, "armor", 1.2,
                        "ability_damage_taken", 0.8, "ability_lifesteal", 0.2, "ult_charge_rate", 1.25,
                        "healing_taken", 1.2, "ally_healing_dealt", 1.4, "crit_damage", 0.35,
                        "spell_power", 30, "crit_chance", 0.2, "spell_resistance", 20))));
        me = t.spawn(0, 1, 0);
        t.engine.statuses().apply(me, "nullmail", 0, me);
        t.engine.statuses().apply(me, "nullmail", 0, me); // bought twice
        var stats = t.engine.stats();
        assertEquals(60, stats.spellPower(me), 1e-9, "stats: per copy (points)");
        assertEquals(0.4, stats.critChance(me), 1e-9);
        assertEquals(40, stats.spellResistance(me), 1e-9);
        assertEquals(0.8, stats.damageTakenMultiplier(me, true), 1e-9, "effects: once");
        assertEquals(0.2, stats.abilityLifesteal(me), 1e-9);
        assertEquals(1.25, stats.ultChargeMultiplier(me), 1e-9);
        assertEquals(1.2, stats.healingMultiplier(me), 1e-9);
        assertEquals(1.4, stats.allyHealingMultiplier(me), 1e-9);
        assertEquals(0.35, stats.critDamageBonus(me), 1e-9);
    }

    @Test
    void spellResistanceWorksLikeArmorOnAbilities() {
        t = new TestEngine();
        t.load(map("statuses", map("ward", map("spell_resistance", 25))));
        me = t.spawn(0, 1, 0);
        t.engine.statuses().apply(me, "ward", 0, me);
        assertEquals(80, t.engine.stats().afterSpellResistance(me, 100), 1e-9, "25 points: 20% less");
        assertEquals(0.2, t.engine.stats().spellResistanceReduction(me), 1e-9);
    }

    @Test
    void anAdditiveTotalNeverGoesBelowTenPercent() {
        setup();
        for (int i = 0; i < 20; i++) t.engine.statuses().apply(me, "fast", 0, me); // 20 x 10%
        assertEquals(0.1, t.engine.stats().cooldownMultiplier(me), 1e-9, "90% at most");
    }
}
