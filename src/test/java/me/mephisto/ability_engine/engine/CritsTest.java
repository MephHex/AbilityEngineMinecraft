package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.Crits;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CritsTest {

    @Test
    void theConstantIsWellBelowTheStatedChance() {
        // the known Dota values: 25% -> C ~ 8.5%, 50% -> ~30.2%
        assertEquals(0.0847, Crits.constantFor(0.25), 0.001);
        assertEquals(0.3021, Crits.constantFor(0.50), 0.001);
        assertEquals(0, Crits.constantFor(0));
        assertEquals(1, Crits.constantFor(1));
    }

    @Test
    void overManyAttacksTheShareThatCritsIsTheChance() {
        for (double chance : new double[]{0.05, 0.2, 0.25, 0.4, 0.6}) {
            Crits crits = new Crits(new Random(42));
            UUID attacker = UUID.randomUUID();
            int hits = 200_000, crit = 0;
            for (int i = 0; i < hits; i++) if (crits.roll(attacker, i, chance)) crit++;
            assertEquals(chance, (double) crit / hits, 0.01, "at " + chance);
        }
    }

    @Test
    void neverMoreThanOneOverCAttacksWithoutACrit() {
        Crits crits = new Crits(new Random(7));
        UUID attacker = UUID.randomUUID();
        int longest = 0, run = 0;
        for (int i = 0; i < 100_000; i++) {
            if (crits.roll(attacker, i, 0.25)) run = 0;
            else longest = Math.max(longest, ++run);
        }
        assertTrue(longest < 12, "25%: a crit within 12 attacks, was " + longest);
    }

    @Test
    void aCastRollsOnceForEveryoneItHits() {
        Crits crits = new Crits(new Random(1));
        UUID attacker = UUID.randomUUID();
        long cast = 0;
        while (!crits.roll(attacker, cast, 0.3)) cast++;
        assertTrue(crits.roll(attacker, cast, 0.3)); // the same swing's second target
        assertTrue(crits.critted(attacker, cast));
        assertFalse(crits.critted(attacker, cast + 1));
    }

    @Test
    void noChanceNoCrits() {
        Crits crits = new Crits(new Random(3));
        UUID attacker = UUID.randomUUID();
        for (int i = 0; i < 1000; i++) assertFalse(crits.roll(attacker, i, 0));
        for (int i = 1000; i < 1010; i++) assertTrue(crits.roll(attacker, i, 1));
    }
}
