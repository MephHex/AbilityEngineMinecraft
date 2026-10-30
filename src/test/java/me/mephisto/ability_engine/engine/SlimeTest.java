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
 * The Slime (fixtures/slime.yml): 300 HP, base damage 38, x1.2 size. She stands at the origin on a floor, looking +x,
 * team blue, at full health; enemies are red (no sheet: 200 HP, no armor).
 */
class SlimeTest {

    private static final double BASE = 38;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.world.healthFraction.put(p, 1.0);
        t.engine.loadouts().assign(p, "slime");
        t.time.advance(4);
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private boolean use(String slot) { return t.engine.loadouts().activate(p, slot).success(); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    /** Her health is now that share: seen, and any slime it drops has landed. */
    private void health(double share) {
        t.world.healthFraction.put(p, share);
        t.time.advance(30);
    }

    private int slimes() { return t.engine.constructs().activeCount(); }

    private Vec3 where(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private me.mephisto.ability_engine.engine.loadout.CharacterDef.Stats stats() {
        return t.engine.loadouts().characterOf(p).orElseThrow().stats();
    }

    // ---- passive: Gelatinous ----

    @Test
    void every20PercentLostDropsASlimeAndShrinksHer() throws IOException {
        setup();
        assertTrue(has(p, "slime_size_5"));
        assertEquals(1.2, stats().scale(), 1e-9, "full size at full health");
        assertEquals(0, slimes());

        health(0.75);
        assertEquals(1, slimes(), "below 80%: a slime");
        assertTrue(has(p, "slime_size_4"));
        assertFalse(has(p, "slime_size_5"), "one band at a time");
        assertEquals(1.1, stats().scale(), 1e-9, "smaller");
        assertEquals(1.0, stats().moveSpeed(), 1e-9, "faster");

        health(0.35);
        assertEquals(3, slimes(), "a big hit through two bands: two slimes");
        assertEquals(0.9, stats().scale(), 1e-9);
        assertEquals(1.12, stats().moveSpeed(), 1e-9);

        health(0.1);
        assertEquals(0.75, stats().scale(), 1e-9, "smallest below 20%");
        assertEquals(1.25, stats().moveSpeed(), 1e-9, "and fastest");

        health(0.9);
        assertEquals(4, slimes(), "healing drops nothing");
        assertTrue(has(p, "slime_size_5"));
        assertEquals(1.2, stats().scale(), 1e-9, "grown back");

        health(0.7);
        assertEquals(5, slimes(), "lost again: another one");
    }

    @Test
    void sheEatsHerSlimesEnemiesSquashThemAlliesLeaveThemBe() throws IOException {
        setup();
        health(0.75);
        Vec3 blob = t.engine.constructs().all().get(0).position();
        assertTrue(Math.hypot(blob.x(), blob.z()) > 1.0, "it pops out away from her: " + blob);

        UUID friend = t.spawn(blob.x(), 1, blob.z());
        t.world.team(friend, "blue");
        t.time.advance(5);
        assertEquals(1, slimes(), "an ally doesn't pick it up");
        t.world.move(friend, new Vec3(-20, 1, 0));

        t.world.move(p, new Vec3(blob.x(), 1, blob.z()));
        t.time.advance(2);
        assertEquals(0, slimes(), "eaten");
        assertEquals(300 * 0.15, t.healed.getOrDefault(p, 0.0), 1e-6, "15% of her max HP back");

        t.world.move(p, new Vec3(0, 1, 0));
        health(0.55);
        blob = t.engine.constructs().all().get(0).position();
        UUID e = foe(blob.x(), blob.z());
        t.time.advance(2);
        assertEquals(0, slimes(), "squashed");
        assertEquals(300 * 0.15, t.healed.getOrDefault(p, 0.0), 1e-6, "nothing for her");
        assertEquals(0, t.healed.getOrDefault(e, 0.0), 1e-9);
    }

    // ---- 1: Ooze Burst ----

    @Test
    void oozeBurstCostsHer10PercentDropsASlimeAndSlimesEnemies() throws IOException {
        setup();
        UUID e = foe(3, 0);
        UUID far = foe(9, 0);
        assertTrue(use(Slots.ABILITY_1));
        assertEquals(300 * 0.1, t.damage(p), 1e-6, "10% of her max HP");
        assertEquals(200 * 0.08, t.damage(e), 1e-6, "8% of their max HP");
        assertTrue(has(e, "slime_slimed"));
        assertEquals(0.6, t.engine.stats().moveSpeedMultiplier(e), 1e-9, "slowed 40%");
        assertEquals(0, t.damage(far), 1e-9);
        t.time.advance(30);
        assertEquals(1, slimes(), "a slime dropped off her");
    }

    @Test
    void oozeBurstDoesNothingBelow15Percent() throws IOException {
        setup();
        t.world.healthFraction.put(p, 0.12);
        t.time.advance(4);
        UUID e = foe(3, 0);
        assertTrue(use(Slots.ABILITY_1));
        assertEquals(0, t.damage(p), 1e-9);
        assertEquals(0, t.damage(e), 1e-9);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "slime_ab1"), "free");
    }

    // ---- 2: Slime Ball ----

    @Test
    void flungIntoAWallTheyStickToIt() throws IOException {
        setup();
        t.world.box(10, 11, -5, 5, 10); // a wall ahead
        UUID e = foe(5, 0);
        assertTrue(use(Slots.ABILITY_2));
        t.time.advance(30);
        assertEquals(BASE * 0.9, t.damage(e), 1e-6, "90% base damage");
        assertTrue(has(e, "slime_stuck"), "stuck to the wall");
        assertTrue(t.engine.tags().has(e, Tags.BLOCK_MOVE), "rooted");
        assertTrue(t.engine.tags().has(e, Tags.ANCHORED), "held up there, even mid-air");
        t.time.advance(31);
        assertFalse(has(e, "slime_stuck"), "1.5s");
    }

    @Test
    void withNoWallTheyJustFlyOff() throws IOException {
        setup();
        UUID e = foe(5, 0);
        assertTrue(use(Slots.ABILITY_2));
        t.time.advance(30);
        assertTrue(t.damage(e) > 0);
        assertFalse(has(e, "slime_stuck"));
        assertTrue(where(e).x() > 11, "flung away: " + where(e));
    }

    // ---- 3: Belly Flop ----

    @Test
    void sheFlopsOntoTheSpotStunningThoseUnder() throws IOException {
        setup();
        UUID e = foe(8, 0);
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        for (int i = 0; i < 40 && t.damage(e) == 0; i++) t.time.advance(1); // up, then down onto it
        assertEquals(BASE, t.damage(e), 1e-6, "100% base damage");
        assertTrue(t.engine.tags().has(e, Tags.STUNNED), "stunned");
        assertTrue(t.knockbackVec.get(e).y() > 0.5, "knocked up");
        assertTrue(Math.abs(where(p).x() - 8) < 1.5, "she came down on the spot: " + where(p));
        t.time.advance(13);
        assertFalse(t.engine.tags().has(e, Tags.STUNNED), "briefly");
    }

    // ---- ULTIMATE: Bounce Storm ----

    @Test
    void theStormBouncesFiveTimesThenEnds() throws IOException {
        setup();
        foe(2, 0);
        assertTrue(use(Slots.ULTIMATE));
        assertFalse(use(Slots.ABILITY_1), "no abilities meanwhile");
        assertFalse(use(Slots.PRIMARY), "no basic attacks");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.ABILITY_1).isPresent(), "their icons show a barrier");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.PRIMARY).isPresent(), "the weapon too");
        assertEquals(1.0, t.engine.instances().timer(p).orElseThrow().left(), 1e-9, "the boss bar: 5 bounces left");
        for (int i = 0; i < 100 && t.render.cues.stream().noneMatch("slime_slam"::equals); i++) t.time.advance(1);
        assertEquals(0.8, t.engine.instances().timer(p).orElseThrow().left(), 1e-9, "one down: 4 left");
        t.time.advance(300);
        assertEquals(5, t.render.cues.stream().filter("slime_slam"::equals).count(), "5 landings");
        assertTrue(t.engine.instances().of(p).stream().noneMatch(i -> i.ability().id().equals("slime_ult")), "over");
        assertFalse(has(p, "slime_bounces"), "the count is cleared");
        assertFalse(t.engine.tags().has(p, Tags.SAFE_FALL));
        assertTrue(t.engine.instances().timer(p).isEmpty(), "the boss bar's gone");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.ABILITY_1).isEmpty(), "usable again: no barrier");
    }

    @Test
    void theBouncesGoWhereSheLooksNotWhereSheWalks() throws IOException {
        setup();
        t.world.look(p, new Vec3(0, 0, 1));    // looking +z...
        t.world.walk(p, new Vec3(1, 0, 0));    // ...walking +x
        assertTrue(use(Slots.ULTIMATE));
        for (int i = 0; i < 100 && t.render.cues.stream().noneMatch("slime_slam"::equals); i++) t.time.advance(1);
        Vec3 at = where(p);
        assertTrue(at.z() > 2, "the first bounce went the way she looks: " + at);
        assertTrue(Math.abs(at.x()) < 0.5, "not the way she walks: " + at);
    }
}
