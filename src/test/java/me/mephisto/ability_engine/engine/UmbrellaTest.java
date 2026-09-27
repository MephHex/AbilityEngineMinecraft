package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Umbrella kit (fixtures/duelist.yml): Royal Lunge charges, Piercing Thrust, Parasol Drift. */
class UmbrellaTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "duelist");
    }

    private UUID enemy(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    @Test
    void royalLungeHasTwoChargesThatComeBackOneAtATime() throws IOException {
        setup();
        assertEquals(2, t.engine.cooldowns().charges(p, "royal_lunge"));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        t.time.advance(20);
        assertEquals(1, t.engine.cooldowns().charges(p, "royal_lunge"));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "royal_lunge"), "one left: not on cooldown");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "the second charge");
        t.time.advance(20);
        assertEquals(0, t.engine.cooldowns().charges(p, "royal_lunge"));
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "none left");
        assertEquals(120, t.engine.cooldowns().remainingTicks(p, "royal_lunge"), 1, "first charge 8s after the first use");

        t.time.advance(121);
        assertEquals(1, t.engine.cooldowns().charges(p, "royal_lunge"), "one back");
        t.time.advance(20);
        assertEquals(1, t.engine.cooldowns().charges(p, "royal_lunge"), "the other one takes another 8s");
        t.time.advance(140);
        assertEquals(2, t.engine.cooldowns().charges(p, "royal_lunge"), "both back");
    }

    @Test
    void piercingThrustWindsUpThenHitsEveryoneInALine() throws IOException {
        setup();
        UUID near = enemy(2, 0);
        UUID far = enemy(6.5, 0);
        UUID outOfReach = enemy(9, 0);
        UUID aside = enemy(3, 3);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        t.time.advance(5);
        assertEquals(0, t.damage(near), 1e-9, "still winding up (0.3s)");
        assertTrue(t.engine.instances().castProgress(p).isPresent(), "cast bar");
        t.time.advance(2);
        assertEquals(55, t.damage(near), 1e-9);
        assertEquals(55, t.damage(far), 1e-9, "7 blocks of reach");
        assertEquals(0, t.damage(outOfReach), 1e-9);
        assertEquals(0, t.damage(aside), 1e-9);
        assertTrue(t.engine.tags().has(near, Tags.SLOWED) && t.engine.tags().has(far, Tags.SLOWED));
    }

    @Test
    void aStunDuringTheWindUpCancelsTheThrust() throws IOException {
        setup();
        UUID near = enemy(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(3);
        t.engine.statuses().apply(p, "stun", null);
        t.time.advance(10);
        assertEquals(0, t.damage(near), 1e-9);
    }

    @Test
    void theParasolDriftIsATrait() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().characterOf(p).orElseThrow().has(CharacterDef.SNEAK_SLOW_FALL));
    }
}
