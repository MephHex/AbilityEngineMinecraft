package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Lightkeeper, from the shipped lightkeeper.yml (base damage 30, no spell power). He stands at x=0 on a floor at
 * y=0, facing +x, on team blue; enemies are on team red.
 */
class LightkeeperTest {

    private static final double BASE = 30;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        var report = ShippedContent.realReport(t.engine);
        assertTrue(report.isClean(), () -> String.join("\n", report.errors()));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.world.players.add(p);
        t.world.look(p, new Vec3(1, 0, 0));
        t.engine.loadouts().assign(p, "lightkeeper");
        t.time.advance(1);
    }

    private UUID at(double x, double z, String team) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, team);
        return e;
    }

    @Test
    void nothingBlindsHimOrDarkensHisSight() throws IOException {
        setup();
        UUID other = at(5, 5, "red");
        t.engine.statuses().apply(p, "blinded", 60, other);
        t.engine.statuses().apply(p, "darkness", 60, other);
        t.engine.statuses().apply(p, "veil_shroud", 60, other);
        assertFalse(t.engine.tags().has(p, Tags.BLINDED));
        assertFalse(t.engine.tags().has(p, "state.darkness"));
        t.engine.statuses().apply(other, "blinded", 60, p);
        assertTrue(t.engine.tags().has(other, Tags.BLINDED), "anyone else: blinded");
    }

    @Test
    void waylightHealsAlliesAndBurnsEnemiesOnTheWay() throws IOException {
        setup();
        UUID friend = at(4, 0, "blue"), foe = at(8, 0, "red"), beyond = at(12, 0, "red");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        t.time.advance(30);
        assertEquals(40, t.healed.getOrDefault(friend, 0.0), 1e-6, "an ally it passed: healed");
        assertEquals(0, t.damage(friend), 1e-9);
        assertEquals(BASE * 0.8, t.damage(foe), 1e-6, "an enemy: hurt");
        assertTrue(t.engine.tags().has(foe, Tags.GLOWING), "and revealed");
        assertEquals(BASE * 0.8, t.damage(beyond), 1e-6, "through everyone");
    }

    @Test
    void lightGuardSheltersAlliesAndOnlyIlluminateWorksMeanwhile() throws IOException {
        setup();
        UUID inside = at(3, 0, "blue"), outside = at(9, 0, "blue");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        t.time.advance(2);
        assertTrue(t.engine.statuses().has(inside, "lk_sheltered"));
        assertTrue(t.engine.statuses().has(p, "lk_sheltered"), "him too");
        assertFalse(t.engine.statuses().has(outside, "lk_sheltered"));
        assertTrue(t.engine.tags().has(p, Tags.SLOWED), "he walks slower");
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "no attacks");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success(), "3 works");
    }

    @Test
    void theXpBarEmptiesOverItsTime() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(1);
        double early = t.engine.instances().castProgress(p).orElseThrow();
        t.time.advance(80);
        double half = t.engine.instances().castProgress(p).orElseThrow();
        assertTrue(early > 0.95, "full at first: " + early);
        assertEquals(0.5, half, 0.05, "half way: half full");
    }

    @Test
    void pressingTwoAgainPutsTheLightOutAndOnlyThenTheCooldownStarts() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(20);
        assertTrue(t.engine.instances().isRunning(p, "lk_ab2"));
        t.engine.loadouts().activate(p, Slots.ABILITY_2);          // again: it goes out
        t.time.advance(1);
        assertFalse(t.engine.instances().isRunning(p, "lk_ab2"));
        assertFalse(t.engine.tags().has(p, Tags.SLOWED));
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "attacks again");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "on cooldown now");
    }

    @Test
    void anAllysHitInTheLightBurnsWithHisSpellPower() throws IOException {
        setup();
        UUID ally = at(1, 2, "blue"), foe = at(6, 2, "red");
        t.world.players.add(ally);
        t.world.look(ally, new Vec3(1, 0, 0));
        t.engine.loadouts().assign(ally, "lightkeeper");
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(2);
        assertTrue(t.engine.loadouts().activate(ally, Slots.PRIMARY).success());
        t.time.advance(10);
        var burn = t.engine.statuses().find(foe, "lk_sunfire");
        assertTrue(burn.isPresent(), "burning");
        assertEquals(p, burn.get().source(), "his: it scales with his spell power");
        assertTrue(t.engine.tags().has(foe, Tags.BURNING));
    }

    @Test
    void illuminateShieldsAndHastensAlliesAndRevealsEnemies() throws IOException {
        setup();
        UUID friend = at(4, 0, "blue"), foe = at(0, 5, "red"), far = at(12, 0, "red");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        t.time.advance(1);
        assertEquals(50, t.shields.getOrDefault(friend, 0.0), 1e-6);
        assertEquals(50, t.shields.getOrDefault(p, 0.0), 1e-6, "him too");
        assertTrue(t.engine.statuses().has(friend, "lk_quickened"));
        assertTrue(t.engine.tags().has(foe, Tags.GLOWING));
        assertFalse(t.engine.tags().has(far, Tags.GLOWING), "6 blocks");
    }

    @Test
    void theBeaconHealsAndCleansesAndHoldsHim() throws IOException {
        setup();
        UUID friend = at(3, 0, "blue"), foe = at(20, 0, "red");
        t.engine.statuses().apply(friend, "blinded", 200, foe);
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.time.advance(2);
        assertTrue(t.engine.tags().has(p, Tags.ROOTED), "rooted while he channels");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_3).success(), "nothing else");
        assertFalse(t.engine.tags().has(friend, Tags.BLINDED), "cleansed");
        t.time.advance(110);
        assertEquals(25 * 10, t.healed.getOrDefault(friend, 0.0), 1e-6, "10 pulses");
        assertFalse(t.engine.tags().has(p, Tags.ROOTED), "over");
    }
}
