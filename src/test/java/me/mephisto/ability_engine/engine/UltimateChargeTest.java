package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.ability.activation.AbilityActivator;
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

/**
 * Ultimates charge up (5% an ability that lands, 2% a basic attack, 0.5% a second) instead of cooling down. The "hero"
 * at the origin looking +x, team blue: LMB jab (cone), 1 zap (everyone within 6), F boom (everyone within 6).
 */
class UltimateChargeTest {

    private TestEngine t;
    private UUID hero;

    private static Map<String, Object> hitAll(String target) {
        Map<String, Object> query = target.equals("cone") ? map("type", "cone", "range", 4, "angle", 90)
                : map("type", "radius", "radius", 6);
        return map("nodes", map("hit", map("type", "apply_effects", "targets", query,
                "effects", list(map("id", "damage", "amount", 10)))));
    }

    private void setup() {
        t = new TestEngine();
        t.load(map(
                "abilities", map("jab", hitAll("cone"), "zap", hitAll("radius"), "boom", hitAll("radius"),
                        "other_zap", hitAll("radius")),
                "statuses", map("dream_essence", map("ult_charge_rate", 1.5)),
                "characters", map(
                        "hero", map("slots", map("primary", "jab", "ability_1", "zap", "ultimate", "boom")),
                        "other", map("slots", map("primary", "jab", "ability_1", "other_zap", "ultimate", "boom")))));
        t.engine.ultCharge().configure(true, 5, 2, 0.5);
        hero = t.spawn(0, 1, 0);
        t.world.team(hero, "blue");
        t.engine.loadouts().assign(hero, "hero");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private double charge() { return t.engine.ultCharge().of(hero); }

    @Test
    void itStartsEmptyAndTheUltimateWaitsForAFullCharge() {
        setup();
        foe(3, 0);
        assertEquals(0, charge(), 1e-9);
        var r = t.engine.loadouts().activate(hero, Slots.ULTIMATE);
        assertFalse(r.success());
        assertTrue(r.reason().startsWith(AbilityActivator.ULT_CHARGING), r.reason());
        assertEquals(0, t.engine.instances().count(), "nothing cast");
    }

    @Test
    void anAbilityThatLandsGivesFiveABasicAttackTwoOncePerCast() {
        setup();
        foe(3, 0);
        foe(3, 1); // two hit by each cast: still once
        assertTrue(t.engine.loadouts().activate(hero, Slots.ABILITY_1).success());
        assertEquals(5, charge(), 1e-9);
        assertTrue(t.engine.loadouts().activate(hero, Slots.PRIMARY).success());
        assertEquals(7, charge(), 1e-9);
    }

    @Test
    void missesAndAlliesDontCharge() {
        setup();
        UUID friend = t.spawn(3, 1, 0);
        t.world.team(friend, "blue");
        assertTrue(t.engine.loadouts().activate(hero, Slots.ABILITY_1).success()); // only an ally in reach
        assertEquals(0, charge(), 1e-9);
    }

    @Test
    void itChargesBySelfEverySecond() {
        setup();
        t.time.advance(20 * 4);
        assertEquals(2, charge(), 1e-9, "0.5% a second, 4s");
    }

    @Test
    void ultChargeRateMakesItFaster() {
        setup();
        foe(3, 0);
        t.engine.statuses().apply(hero, "dream_essence", 0, hero);
        assertTrue(t.engine.loadouts().activate(hero, Slots.ABILITY_1).success());
        assertEquals(7.5, charge(), 1e-9, "5 x 1.5");
    }

    @Test
    void fullItCastsSpendsItAllAndHasNoCooldownItsHitsDontRecharge() {
        setup();
        foe(3, 0);
        t.engine.ultCharge().set(hero, 100);
        assertTrue(t.engine.loadouts().activate(hero, Slots.ULTIMATE).success());
        assertEquals(0, charge(), 1e-9, "spent, and its own hit didn't charge it");
        assertEquals(0, t.engine.cooldowns().remainingTicks(hero, "boom"), "no cooldown: the charge is what it waits for");
        assertFalse(t.engine.loadouts().activate(hero, Slots.ULTIMATE).success(), "empty again");
    }

    @Test
    void aNewCharacterStartsOver() {
        setup();
        t.engine.ultCharge().set(hero, 80);
        t.engine.loadouts().assign(hero, "other");
        assertEquals(0, charge(), 1e-9);
        t.engine.ultCharge().set(hero, 40);
        t.engine.loadouts().assign(hero, "other"); // the same one again
        assertEquals(40, charge(), 1e-9);
    }

    @Test
    void offUltimatesKeepTheirCooldowns() {
        setup();
        t.engine.ultCharge().configure(false, 5, 2, 0.5);
        t.load(map("abilities", map("boom", map("cooldown", 100, "nodes", map("n", map("type", "print", "message", "boom"))))));
        assertTrue(t.engine.loadouts().activate(hero, Slots.ULTIMATE).success(), "no charge needed");
        assertEquals(100, t.engine.cooldowns().remainingTicks(hero, "boom"));
    }
}
