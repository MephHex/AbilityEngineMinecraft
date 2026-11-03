package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
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
 * Mitosis (fixtures/mitosis.yml): 240 HP, 20 armor, base damage 38. She stands at the origin on a floor, looking +x
 * (her left is -z), team blue, at full health; enemies are red (no sheet: 200 HP, no armor).
 */
class MitosisTest {

    private static final double BASE = 38;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.players.add(p);
        t.world.team(p, "blue");
        t.world.look(p, new Vec3(1, 0, 0));
        t.world.healthFraction.put(p, 1.0);
        t.engine.loadouts().assign(p, "mitosis");
        t.time.advance(4);
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private boolean use(String slot) { return t.engine.loadouts().activate(p, slot).success(); }

    private UUID bud() {
        assertTrue(use(Slots.SECONDARY));
        return t.engine.bodies().cloneOf(p).orElseThrow();
    }

    private Vec3 where(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private double share(UUID id) { return t.world.healthFraction.get(id); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    // ---- passive: Mitosis ----

    @Test
    void buddingSplitsHerHealthAndPutsTheCloneOnHerLeft() throws IOException {
        setup();
        t.world.healthFraction.put(p, 0.7); // 168 of 240
        UUID clone = bud();
        assertEquals(84, t.world.health.get(clone), 1e-9, "half her health, rounded down");
        assertEquals(0.35, share(p), 1e-9, "she keeps the rest");
        assertEquals(240, t.engine.stats().maxHealth(clone), 1e-9, "her max HP");
        assertEquals(20, t.engine.stats().armor(clone), 1e-9, "her armor");
        assertEquals(BASE, t.engine.stats().baseDamage(clone), 1e-9, "her damage");
        Vec3 at = where(clone);
        assertEquals(0, at.x(), 1e-9);
        assertEquals(-1.5, at.z(), 1e-9, "1.5 blocks to her left");
        Vec3 look = t.world.facing.get(clone);
        assertTrue(look.x() > 0.99 && look.z() > 0.01, "it looks at what she looks at (24 blocks ahead): their lines of sight cross " + look);
        assertTrue(t.world.teamOf(clone).filter("blue"::equals).isPresent(), "on her team");
        assertTrue(has(p, "mitosis_fuse_wait"), "the fuse countdown");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "mitosis_bud"), "no cooldown while it lives");
        t.time.advance(10);
        assertFalse(use(Slots.SECONDARY) && t.engine.bodies().cloneOf(p).isEmpty(), "RMB again early: nothing");
        assertTrue(t.engine.bodies().cloneOf(p).isPresent(), "one clone at a time, still there");
    }

    @Test
    void theCloneCastsWhatSheCastsFromWhereItStandsAndItsHitsAreHers() throws IOException {
        setup();
        t.engine.ultCharge().configure(true, 5, 2, 0);
        UUID clone = bud();
        UUID enemy = foe(3, -1.5); // in front of the clone, not of her
        t.time.advance(20);
        assertTrue(use(Slots.PRIMARY));
        assertEquals(BASE, t.damage(enemy), 1e-9, "the clone's Stretch: 100% of her base damage");
        assertEquals(2, t.engine.ultCharge().of(p), 1e-9, "its hit charges her ultimate");
        assertEquals(p, t.engine.bodies().credited(clone));

        t.engine.statuses().apply(clone, "stun", 0, null); // crowd control on it holds only it
        t.time.advance(20);
        assertTrue(use(Slots.PRIMARY));
        assertEquals(BASE, t.damage(enemy), 1e-9, "stunned, it doesn't copy the cast");
    }

    @Test
    void theCloneWalksItsOwnHeadingAndLooksAtWhatSheLooksAt() throws IOException {
        setup();
        UUID clone = bud();
        t.world.look(p, new Vec3(0, 0, 1)); // she turns 90 degrees right (to +z)...
        t.time.advance(1);
        Vec3 look = t.world.facing.get(clone);
        assertTrue(look.z() > 0.99, "...it looks at her new spot, 24 blocks out that way: " + look);

        t.world.face(clone, new Vec3(-1, 0, 0)); // something turned its head: its heading stays
        t.world.input(p, new Vec3(0, 0, 0.2), false); // she walks forward (+z)
        t.time.advance(1);
        Vec3 before = where(clone);
        t.time.advance(1);
        Vec3 step = where(clone).subtract(before);
        assertEquals(0, step.x(), 1e-6);
        assertTrue(step.z() > 0.1, "its heading turned with her: it walks forward its way (+z): " + step);
    }

    @Test
    void lookingAtTheCloneItLooksBackAtHer() throws IOException {
        setup();
        UUID clone = bud(); // 1.5 blocks to her left (-z)
        t.world.look(p, new Vec3(0, 0, -1)); // she looks straight at it...
        t.time.advance(1);
        Vec3 look = t.world.facing.get(clone);
        assertTrue(look.z() > 0.99, "...it looks straight back at her (+z), their eyes meet: " + look);
        t.world.look(p, new Vec3(1, 0, 0)); // she looks away, ahead
        t.time.advance(1);
        assertTrue(t.world.facing.get(clone).x() > 0.99, "it looks where she looks again: " + t.world.facing.get(clone));
    }

    @Test
    void itsHeadingDriftsApartFromHersAndStaysThatWay() throws IOException {
        setup();
        UUID clone = bud();
        t.engine.bodies().swap(p); // (a swap: each starts over from the way it faces now)
        t.world.look(p, new Vec3(1, 0, 0));
        t.time.advance(1);
        t.world.input(p, new Vec3(0.2, 0, 0), false); // she walks forward (+x)
        Vec3 before = where(clone);
        t.time.advance(1);
        Vec3 step = where(clone).subtract(before);
        assertTrue(step.x() > 0.1, "it walks the way it's headed: " + step);
    }

    @Test
    void fusingNeedsTenSecondsAndTwoBlocksThenItsHealthAndEffectsAreHers() throws IOException {
        setup();
        t.world.healthFraction.put(p, 0.8); // 192: 96 each
        UUID clone = bud();
        t.engine.statuses().apply(clone, "slow", 400, null); // a debuff on the clone
        t.time.advance(100);
        use(Slots.SECONDARY);
        assertTrue(t.engine.bodies().cloneOf(p).isPresent(), "not before 10s");
        t.time.advance(110);
        t.world.move(clone, new Vec3(0, 1, -5));
        use(Slots.SECONDARY);
        assertTrue(t.engine.bodies().cloneOf(p).isPresent(), "not from 5 blocks away");
        t.world.move(clone, new Vec3(0, 1, -1.5));
        t.time.advance(2);
        assertTrue(use(Slots.SECONDARY));
        t.time.advance(2);
        assertTrue(t.engine.bodies().cloneOf(p).isEmpty(), "fused");
        assertEquals(0.8, share(p), 1e-9, "its health is hers again");
        assertTrue(has(p, "slow"), "its debuff moved over to her");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "mitosis_bud") > 280, "the passive's 15s cooldown");
    }

    @Test
    void swappingTradesPlacesButEveryBodyKeepsItsOwnHealthAndEffects() throws IOException {
        setup();
        UUID clone = bud(); // 120 each: 0.5
        t.world.healthFraction.put(clone, 0.3);
        t.engine.statuses().apply(p, "stun", 60, null); // she's stunned: she can still swap
        Vec3 mine = where(p), its = where(clone);
        assertTrue(use(Slots.SNEAK_SECONDARY));
        assertEquals(its, where(p), "she's where it was");
        assertEquals(mine, where(clone), "it's where she was");
        assertEquals(0.3, share(p), 1e-9, "she takes over its health");
        assertEquals(0.5, share(clone), 1e-9);
        assertFalse(has(p, "stun"), "the stun stays with the body she left");
        assertTrue(has(clone, "stun"));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "mitosis_swap") > 90, "5s cooldown");
        assertFalse(use(Slots.SNEAK_SECONDARY), "on cooldown");
    }

    @Test
    void aKillingBlowMovesHerIntoTheClone() throws IOException {
        setup();
        UUID clone = bud();
        t.world.healthFraction.put(clone, 0.4);
        t.engine.statuses().apply(p, "slow", 100, null);       // on the dying body: gone with it
        t.engine.statuses().apply(clone, "silence", 100, null); // on the clone: hers now
        Vec3 its = where(clone);
        var saved = t.engine.preventDeath(p, null);
        assertTrue(saved.isPresent(), "no death");
        assertEquals(0.4, saved.getAsDouble(), 1e-9, "the clone's health");
        assertEquals(its, where(p), "she's where the clone was");
        assertFalse(has(p, "slow"));
        assertTrue(has(p, "silence"));
        t.time.advance(2);
        assertTrue(t.engine.bodies().cloneOf(p).isEmpty(), "the clone is gone");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "mitosis_bud") > 280, "the passive's cooldown");
        assertTrue(t.engine.preventDeath(p, null).isEmpty(), "no clone: the next one kills her");
    }

    @Test
    void theCloneDyingPutsThePassiveOnCooldown() throws IOException {
        setup();
        UUID clone = bud();
        t.world.hurt(clone, 500);
        t.time.advance(2);
        assertTrue(t.engine.bodies().cloneOf(p).isEmpty());
        assertTrue(t.engine.cooldowns().remainingTicks(p, "mitosis_bud") > 280);
    }

    // ---- 1: Sticky Fingers ----

    @Test
    void aSecondArmOnAnotherEnemySlamsThemTogether() throws IOException {
        setup();
        UUID first = foe(4, 0);
        UUID second = foe(4, 3);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(5);
        assertEquals(BASE * 0.6, t.damage(first), 1e-9, "the arm: 60% base damage");
        assertTrue(has(first, "mitosis_sticky_slow"));
        t.time.advance(10); // (no clone: no waiting for its arm)
        t.world.look(p, new Vec3(4, 0, 3).normalize());
        assertTrue(use(Slots.ABILITY_1), "the second arm");
        t.time.advance(30);
        assertEquals(BASE * 1.6, t.damage(first), 1e-6, "slammed: 100% base damage more");
        assertEquals(BASE * 1.0, t.damage(second), 1e-6);
        assertTrue(where(first).distance(where(second)) < 2, "pulled into each other");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "mitosis_ab1") > 0, "spent: the cooldown starts");
    }

    @Test
    void bothArmsGoAtTheEnemySheLooksAtAndOnTheSameOneNoPull() throws IOException {
        setup();
        bud();
        UUID mine = foe(4, 0); // in her sights: the clone aims there too, so their arms meet on it
        t.time.advance(10);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(40);
        assertEquals(BASE * 1.2, t.damage(mine), 1e-6, "both arms' damage...");
        assertFalse(t.engine.links().find(p, "mitosis_sticky").isPresent(), "...no pull, and both second arms used up");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "mitosis_ab1") > 0);
    }

    @Test
    void herArmAndTheClonesLandingTogetherOnTwoEnemiesSlamThemAtOnce() throws IOException {
        setup();
        bud();
        UUID mine = foe(6, 0);      // in her sights
        UUID theirs = foe(2, -1.0); // in the way of the clone's arm (on its line to her spot)
        t.time.advance(10);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(40);
        assertEquals(BASE * 1.6, t.damage(mine), 1e-6, "her arm and then the slam");
        assertEquals(BASE * 1.6, t.damage(theirs), 1e-6, "its arm and then the slam");
        assertTrue(where(mine).distance(where(theirs)) < 2, "slammed together, no second arm needed");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "mitosis_ab1") > 0, "both second arms used up");
    }

    // ---- ultimate: Bubble Trouble ----

    @Test
    void theBubbleCarriesEnemiesToHerSpotAndTheClonesGoesThereToo() throws IOException {
        setup();
        UUID clone = bud();
        UUID near = foe(3, 0);
        UUID corner = foe(-3.6, 3.6); // near a corner of the full square: 5.1 blocks away, still on it
        UUID outside = foe(-4.6, 0);  // past its edge
        t.time.advance(10);
        t.world.look(p, new Vec3(1, -0.1, 0).normalize()); // the ground about 10 blocks ahead
        assertTrue(use(Slots.ULTIMATE));
        t.time.advance(20);
        assertFalse(t.engine.statuses().on(near).stream().anyMatch(s -> s.def().id().startsWith("mitosis_puddle_slow")),
                "3 blocks out: the growing square hasn't reached them yet (1.6 blocks)");
        t.time.advance(15);
        assertTrue(t.engine.statuses().on(near).stream().anyMatch(s -> s.def().id().startsWith("mitosis_puddle_slow_4")),
                "now it has (3.2 blocks): slowed 57%");
        t.time.advance(10);
        assertTrue(has(near, "root"), "rooted at full channel");
        assertTrue(has(corner, "root"), "the square's corner counts");
        assertFalse(has(outside, "root"), "past its edge doesn't");
        assertTrue(t.engine.targeting().isTargeting(p), "picking the spot");
        assertTrue(t.engine.targeting().keyConfirms(p), "F confirms too");
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(3);
        assertTrue(has(near, "mitosis_swallowed"), "swallowed");
        t.time.advance(25);
        assertFalse(has(near, "mitosis_swallowed"), "spat out");
        assertTrue(where(p).distance(where(clone)) < 0.5, "both bubbles landed on the same spot");
        assertTrue(t.damage(near) >= BASE * 1.2 - 1e-6, "the landing: 120% base damage (" + t.damage(near) + ")");
        assertTrue(where(p).x() > 5, "she leapt ahead, where she looked: " + where(p));
    }

    @Test
    void fAgainDuringThePuddleCancelsItForHalfTheCharge() throws IOException {
        setup();
        t.engine.ultCharge().configure(true, 5, 2, 0);
        t.engine.ultCharge().set(p, 100);
        UUID near = foe(3, 0);
        assertTrue(use(Slots.ULTIMATE));
        t.time.advance(10);
        assertTrue(use(Slots.ULTIMATE), "F again: cancel");
        t.time.advance(40);
        assertFalse(t.engine.instances().isRunning(p, "mitosis_ult"));
        assertEquals(50, t.engine.ultCharge().of(p), 1e-9, "half the charge back");
        assertFalse(has(near, "root"), "no root");
    }
}
