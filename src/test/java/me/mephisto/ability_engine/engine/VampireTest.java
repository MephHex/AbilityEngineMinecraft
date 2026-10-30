package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Vampire (fixtures/vampire.yml): 210 HP, base damage 36. She stands at the origin on a floor, looking +x, team blue;
 * enemies are red (no sheet: 200 HP, no armor).
 */
class VampireTest {

    private static final double BASE = 36;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "vampire");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private boolean use(String slot) { return t.engine.loadouts().activate(p, slot).success(); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private Vec3 where(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    // ---- passive: Blood Feast ----

    @Test
    void aKillHealsHerAndMakesHerFaster() throws IOException {
        setup();
        UUID victim = foe(3, 0);
        t.engine.notifyKill(p, victim, true);
        assertEquals(210 * 0.15, t.healed.getOrDefault(p, 0.0), 1e-6, "15% of her max HP");
        assertTrue(has(p, "vampire_feast"));
        assertEquals(1.3, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "30% faster");
        t.time.advance(61);
        assertFalse(has(p, "vampire_feast"), "for 3s");
    }

    // ---- 1: Sanguine Tether ----

    @Test
    void theTetherDrinksFromTheThreeNearestAndWhoeverHoldsOnBleeds() throws IOException {
        setup();
        UUID a = foe(2, 0), b = foe(0, 3), c = foe(-4, 0);
        UUID fourth = foe(5, 2);
        UUID far = foe(12, 0);
        assertTrue(use(Slots.ABILITY_1));
        assertTrue(t.engine.tags().has(p, Tags.DISARMED), "no basic attacks while channeling");
        assertFalse(use(Slots.PRIMARY));
        t.time.advance(52);
        for (UUID e : new UUID[] {a, b, c}) {
            assertEquals(4 * BASE * 0.15, t.damage(e), 1e-6, "15% every 0.5s");
            assertTrue(has(e, "vampire_bleed"), "held on: bleeding");
        }
        assertEquals(0, t.damage(fourth), 1e-9, "3 at most: the nearest");
        assertEquals(0, t.damage(far), 1e-9, "out of reach");
        assertEquals(3 * 4 * 10, t.healed.getOrDefault(p, 0.0), 1e-6, "each drop of blood heals her 10");
        assertFalse(t.engine.tags().has(p, Tags.DISARMED), "the channel is over");
    }

    @Test
    void breakingFreeMeansNoBleedAndTheDrinkingStops() throws IOException {
        setup();
        UUID runner = foe(3, 0);
        UUID stayer = foe(-3, 0);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(15);
        t.world.move(runner, new Vec3(15, 1, 0)); // more than 10 blocks away
        t.time.advance(5);
        double drunk = t.damage(runner);
        t.time.advance(40);
        assertEquals(drunk, t.damage(runner), 1e-9, "broken: no more drinking");
        assertFalse(has(runner, "vampire_bleed"), "broke free: no bleed");
        assertTrue(has(stayer, "vampire_bleed"), "the other one held on");
    }

    @Test
    void pressingItAgainStopsTheChannel() throws IOException {
        setup();
        UUID e = foe(3, 0);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(20);
        assertTrue(use(Slots.ABILITY_1), "1 again: stop");
        t.time.advance(40);
        assertFalse(has(e, "vampire_bleed"));
        assertFalse(t.engine.tags().has(p, Tags.DISARMED));
    }

    @Test
    void withNobodyAroundItsFree() throws IOException {
        setup();
        foe(20, 0);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(2);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "vampire_ab1"), "no cooldown");
    }

    // ---- 2: Bat Form ----

    @Test
    void asABatSheFliesHiddenFor1s() throws IOException {
        setup();
        assertTrue(use(Slots.ABILITY_2));
        assertTrue(t.engine.tags().has(p, Tags.FLYING));
        assertTrue(t.engine.tags().has(p, Tags.HIDDEN), "only the bat is seen");
        t.time.advance(21);
        assertFalse(t.engine.tags().has(p, Tags.FLYING));
        assertFalse(t.engine.tags().has(p, Tags.HIDDEN));
    }

    // ---- 3: Charm ----

    @Test
    void aCharmedEnemyIsHelplessAndWalksToHer() throws IOException {
        setup();
        UUID e = foe(8, 0);
        assertTrue(use(Slots.ABILITY_3));
        t.time.advance(10);
        assertTrue(has(e, "vampire_charmed"), "the charm landed");
        assertEquals(BASE * 0.4, t.damage(e), 1e-6, "40% base damage");
        assertTrue(t.engine.tags().has(e, Tags.BLOCK_ABILITY), "no spells");
        assertTrue(t.engine.tags().has(e, Tags.DISARMED), "no basic attacks");
        assertTrue(t.engine.tags().has(e, Tags.BLOCK_MOVE), "no walking of their own");
        double before = where(e).x();
        t.time.advance(20);
        double walked = before - where(e).x();
        assertTrue(walked > 1.2 && walked < 2.0, "walking to her, slowly: " + walked);
        t.time.advance(30);
        assertFalse(has(e, "vampire_charmed"), "1.75s");
        double after = where(e).x();
        t.time.advance(20);
        assertEquals(after, where(e).x(), 1e-9, "then they're let go");
    }

    // ---- ULTIMATE: Crimson Rain ----

    @Test
    void sheRainsBloodFromTheSkyThenComesDown() throws IOException {
        setup();
        UUID e = foe(6, 0);
        t.world.look(p, new Vec3(6, -4, 0).normalize());
        assertTrue(use(Slots.ULTIMATE));
        assertFalse(t.engine.tags().has(p, Tags.ANCHORED), "rising first: hanging would stop the rise");
        t.time.advance(40);
        assertTrue(t.engine.tags().has(p, Tags.ANCHORED), "hanging in the air at the top");
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_WALK), "not locked: she can drift a little");
        assertFalse(use(Slots.ABILITY_2), "no abilities meanwhile...");
        assertFalse(use(Slots.PRIMARY), "...and no basic attacks: only the rain");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.ABILITY_2).isPresent(), "their icons show a barrier");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.PRIMARY).isPresent(), "the weapon too");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.ULTIMATE).isEmpty(), "F still works: it stops the rain");
        assertTrue(t.damage(e) > 0, "the missiles hit");
        assertTrue(t.engine.tags().has(p, Tags.SAFE_FALL), "the drop back down won't hurt");
        assertTrue(use(Slots.ULTIMATE), "F again: stop");
        assertFalse(t.engine.tags().has(p, Tags.ANCHORED), "she comes back down");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.ABILITY_2).isEmpty(), "usable again: no barrier");
        double dealt = t.damage(e);
        t.time.advance(40);
        assertEquals(dealt, t.damage(e), 1e-9, "no more missiles");
    }
}
