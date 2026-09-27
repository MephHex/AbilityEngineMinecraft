package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.quiver.Bolt;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.*;

/** The Hunter kit (fixtures/hunter.yml): quiver, infusions, Venom Step, Volatile Flask, Overdrive. */
class HunterTest {

    private static final Bolt PLAIN = Bolt.PLAIN;
    private static final Bolt POISON = new Bolt(List.of("poison"));

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(p, "hunter");
    }

    private List<Bolt> queue() { return t.engine.quivers().queue(p); }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    /** LMB, then let the bolt fly. */
    private void shoot() {
        assertTrue(t.engine.loadouts().activate(p, "primary").success());
        t.time.advance(10);
    }

    // ---- quiver ------------------------------------------------------------------------------

    @Test
    void startsWithThreePlainBoltsAndNothingLoaded() throws IOException {
        setup();
        assertEquals(List.of(PLAIN, PLAIN, PLAIN), queue());
        assertFalse(t.engine.quivers().isLoaded(p));
    }

    @Test
    void loadingTakesTheLeftmostBoltShiftsTheRestAndAddsAPlainOneOnTheRight() throws IOException {
        setup();
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().infuse(p, "slowness", 2);
        Bolt both = new Bolt(List.of("poison", "slowness"));
        Bolt slow = new Bolt(List.of("slowness"));
        assertEquals(List.of(both, slow, PLAIN), queue(), "one bolt can carry several infusions");

        assertTrue(t.engine.quivers().tryLoad(p));
        assertEquals(both, t.engine.quivers().loaded(p).orElseThrow());
        assertEquals(List.of(slow, PLAIN, PLAIN), queue());
        assertFalse(t.engine.quivers().tryLoad(p), "already loaded");
        assertEquals(List.of(slow, PLAIN, PLAIN), queue(), "a refused load moves nothing");
    }

    @Test
    void infusionsNeverTouchTheLoadedBolt() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        t.engine.quivers().infuse(p, "poison", 2);
        assertEquals(PLAIN, t.engine.quivers().loaded(p).orElseThrow());
        assertEquals(List.of(POISON, POISON, PLAIN), queue());
    }

    @Test
    void cantReloadWhileStunned() throws IOException {
        setup();
        t.engine.statuses().apply(p, "stun", 20, null);
        assertFalse(t.engine.quivers().canLoad(p));
        assertFalse(t.engine.quivers().tryLoad(p));
        t.time.advance(21);
        assertTrue(t.engine.quivers().tryLoad(p));
    }

    @Test
    void aNewCharacterGetsAFreshQuiver() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        t.engine.quivers().infuse(p, "poison", 3);
        t.engine.loadouts().assign(p, "hunter");
        assertFalse(t.engine.quivers().isLoaded(p));
        assertEquals(List.of(PLAIN, PLAIN, PLAIN), queue());
        t.engine.loadouts().clear(p);
        assertEquals(List.of(), queue(), "no character, no quiver");
        assertFalse(t.engine.quivers().tryLoad(p));
    }

    // ---- primary + passive ---------------------------------------------------------------------

    @Test
    void shootingWithNothingLoadedIsADryClick() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        shoot();
        assertEquals(0, t.damage(target), 1e-9);
        assertEquals(0, t.render.spawned, "no bolt flew");
        assertTrue(t.render.cues.contains("dry_fire"));
    }

    @Test
    void aLoadedBoltHitsAndEmptiesTheCrossbow() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        t.engine.quivers().tryLoad(p);
        shoot();
        assertEquals(45, t.damage(target), 1e-9);
        assertFalse(t.engine.quivers().isLoaded(p));
    }

    @Test
    void infusedBoltsApplyTheirInfusions() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().infuse(p, "slowness", 1);
        t.engine.quivers().tryLoad(p);
        shoot();
        assertTrue(t.engine.statuses().has(target, "poisoned"));
        assertTrue(t.engine.tags().has(target, Tags.SLOWED), "slowness infusion");
        double onImpact = t.damage(target);
        t.time.advance(80);
        assertEquals(40, t.damage(target) - onImpact, 1e-9, "poison: 40 over 4s");
    }

    @Test
    void plainBoltsCarryNoInfusions() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().tryLoad(p); // the poisoned one
        shoot();
        t.engine.quivers().tryLoad(p); // a plain one
        t.engine.statuses().clear(target);
        t.time.advance(10); // primary cooldown
        shoot();
        assertFalse(t.engine.statuses().has(target, "poisoned"));
    }

    @Test
    void aFiredBoltIsTintedByItsInfusions() throws IOException {
        setup();
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().infuse(p, "slowness", 2);
        for (int i = 0; i < 3; i++) {
            t.engine.quivers().tryLoad(p);
            shoot();
        }
        // poison + slowness mixed (#4E9331 and #5A6C81 averaged), then slowness alone, then a plain bolt
        assertEquals(java.util.Arrays.asList("#547F59", "#5A6C81", null), t.render.tints);
    }

    @Test
    void hitsRaiseQuickChargeTo2Then3Then4ThenItFades() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        assertEquals(0, t.engine.quivers().reloadSpeed(p));
        int[] expected = {2, 3, 4, 4}; // the 4th hit: still 4 (3 stacks max)
        for (int i = 1; i <= 4; i++) {
            t.engine.quivers().tryLoad(p);
            shoot();
            assertEquals(expected[i - 1], t.engine.quivers().reloadSpeed(p), "after hit " + i);
        }
        t.time.advance(101);
        assertEquals(0, t.engine.quivers().reloadSpeed(p), "5s without a hit: back to normal");
        assertTrue(t.damage(target) > 0);
    }

    @Test
    void theRhythmBarShowsTheLevelAndDrainsWithTheTimeLeft() throws IOException {
        setup();
        var statusBar = t.engine.loadouts().characterOf(p).orElseThrow().statusBar();
        assertEquals("hunters_rhythm", statusBar.status());
        assertTrue(statusBar.reloadSpeedLevel(), "the number shows the Quick Charge level");
        String bar = statusBar.status();
        assertTrue(t.engine.statuses().gauge(p, bar).isEmpty(), "nothing before the first hit");

        t.spawn(10, 1, 0);
        t.engine.quivers().tryLoad(p);
        shoot(); // hits, then 10 ticks pass
        var g = t.engine.statuses().gauge(p, bar).orElseThrow();
        assertEquals(1, g.stacks(), "level 1");
        assertTrue(g.fraction() > 0.85 && g.fraction() <= 1, "nearly full: " + g.fraction());

        t.time.advance(40);
        assertEquals(0.5, t.engine.statuses().gauge(p, bar).orElseThrow().fraction(), 0.1, "drains over 5s");

        t.engine.quivers().tryLoad(p);
        shoot();
        g = t.engine.statuses().gauge(p, bar).orElseThrow();
        assertEquals(2, g.stacks(), "level 2");
        assertTrue(g.fraction() > 0.85, "refilled by the hit: " + g.fraction());

        t.time.advance(101);
        assertTrue(t.engine.statuses().gauge(p, bar).isEmpty(), "ran out: bar empty again");
    }

    @Test
    void missesDontCountForTheRhythm() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        shoot();
        assertEquals(0, t.engine.quivers().reloadSpeed(p));
    }

    // ---- Venom Step ------------------------------------------------------------------------------

    @Test
    void venomStepDashesWhereYouWalkNotWhereYouLook() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, 0, 0));
        t.world.walk(p, new Vec3(0, 0, 1)); // strafing sideways
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(10);
        Vec3 at = pos(p);
        assertTrue(at.z() >= 5.5, "dashed sideways: " + at);
        assertEquals(0, at.x(), 1e-6, "not forward");
    }

    @Test
    void venomStepGoesTheFullDistanceWithPing() throws IOException {
        setup();
        t.world.lag(p, 2); // ~100ms round trip: pushes show up in the position 2 ticks late
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(20);
        assertTrue(pos(p).x() >= 5.5, "didn't give up waiting for the first movement: x=" + pos(p).x());
    }

    @Test
    void venomStepSurvivesAMovementPacketArrivingLate() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(1);
        t.world.lag(p, 1); // mid-dash, one tick's position update comes late
        t.time.advance(20);
        assertTrue(pos(p).x() >= 5.5, "a late packet isn't a wall: x=" + pos(p).x());
    }

    @Test
    void cantVenomStepWhileRootedAndNothingIsSpent() throws IOException {
        setup();
        t.engine.statuses().apply(p, "root", 40, null);
        var result = t.engine.loadouts().activate(p, "ability_1");
        assertFalse(result.success());
        assertEquals("blocked:" + Tags.BLOCK_MOVE, result.reason());
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "venom_step"), "no cooldown spent");
        assertEquals(List.of(PLAIN, PLAIN, PLAIN), queue(), "no poison either");
        t.time.advance(10);
        assertEquals(0, pos(p).x(), 1e-9, "didn't move");

        t.time.advance(31); // root over
        assertTrue(t.engine.loadouts().activate(p, "ability_1").success());
    }

    @Test
    void aRootMidDashStopsIt() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(1);
        double stoppedAt = pos(p).x();
        t.engine.statuses().apply(p, "root", 40, null);
        t.time.advance(10);
        assertEquals(stoppedAt, pos(p).x(), 1e-9, "no more pushing once rooted");
        assertTrue(stoppedAt < 5.5);
    }

    @Test
    void theOtherKitsDashesAndBlinksRespectRootsToo() throws IOException {
        setup();
        UUID other = t.spawn(0, 1, 20);
        t.engine.statuses().apply(other, "root", 40, null);
        for (String ability : List.of("royal_lunge", "lifeline_step", "foldstep")) {
            assertEquals("blocked:" + Tags.BLOCK_MOVE, t.engine.activator().activate(other, ability).reason(), ability);
        }
        assertFalse(t.engine.abilities().find("infused_bolt").orElseThrow().movement(), "shooting isn't movement");
    }

    @Test
    void venomStepGoesForwardWhenStandingStill() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, 1, 0)); // looking up: still a ground dash
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(10);
        Vec3 at = pos(p);
        assertTrue(at.x() >= 5.5, "forward: " + at);
        assertEquals(1, at.y(), 1e-6, "flat");
    }

    @Test
    void venomStepReloadsThenPoisonsTheNextThreeQueuedBolts() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ability_1");
        assertEquals(PLAIN, t.engine.quivers().loaded(p).orElseThrow(), "reloaded with the bolt that was next");
        assertEquals(List.of(POISON, POISON, POISON), queue());
    }

    @Test
    void venomStepWhenAlreadyLoadedStillPoisons() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        t.engine.loadouts().activate(p, "ability_1");
        assertEquals(PLAIN, t.engine.quivers().loaded(p).orElseThrow());
        assertEquals(List.of(POISON, POISON, POISON), queue(), "nothing shifted");
    }

    @Test
    void venomStepOnlyTurnsYouInvisibleDuringOverdrive() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ability_1");
        assertFalse(t.engine.tags().has(p, Tags.INVISIBLE));
        t.time.advance(200);

        t.engine.loadouts().activate(p, "ultimate");
        t.engine.loadouts().activate(p, "ability_1");
        assertTrue(t.engine.tags().has(p, Tags.INVISIBLE));
        t.time.advance(51);
        assertFalse(t.engine.tags().has(p, Tags.INVISIBLE), "2.5s");
    }

    // ---- Volatile Flask --------------------------------------------------------------------------

    @Test
    void theFlaskBouncesOnceThenBurstsDamagingAndSlowing() throws IOException {
        setup();
        t.world.floor(0);
        // A lob at ~17 degrees: first touches the ground near x=21, bounces, and lands near x=29.
        UUID atTheBounce = t.spawn(21, 0.5, 2.5);
        UUID atTheLanding = t.spawn(29.5, 1, 1.5);
        t.world.look(p, new Vec3(1, 0.3, 0));
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(40); // bursts after ~25 ticks; the 3s slow is still on
        assertEquals(0, t.damage(atTheBounce), 1e-9, "the first bounce doesn't burst");
        assertEquals(50, t.damage(atTheLanding), 1e-9);
        assertTrue(t.engine.tags().has(atTheLanding, Tags.SLOWED));
    }

    @Test
    void theFlaskBurstsOnTheFirstEnemyItHits() throws IOException {
        setup();
        UUID enemy = t.spawn(6, 1, 0);
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(20);
        assertEquals(50, t.damage(enemy), 1e-9);
    }

    @Test
    void burstingItOnYourselfBuffsYouAndInfusesSlownessIntoTheNextBolt() throws IOException {
        setup();
        t.world.floor(0);
        t.world.look(p, new Vec3(0.05, -1, 0)); // straight at your feet
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(40);
        assertEquals(0, t.damage(p), 1e-9, "your own flask doesn't hurt you");
        assertTrue(t.engine.tags().has(p, Tags.HASTED), "speed");
        t.time.advance(60);
        assertTrue(t.healed.getOrDefault(p, 0.0) > 0, "a little regen");
        assertEquals(new Bolt(List.of("slowness")), queue().get(0));
        assertEquals(PLAIN, queue().get(1), "only the next one");
    }

    @Test
    void aFlaskFarFromYouDoesntBuffYou() throws IOException {
        setup();
        t.world.floor(0);
        t.world.look(p, new Vec3(1, 0.3, 0));
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(100);
        assertFalse(t.engine.tags().has(p, Tags.HASTED));
        assertEquals(List.of(PLAIN, PLAIN, PLAIN), queue());
    }

    // ---- Overdrive ---------------------------------------------------------------------------------

    @Test
    void overdriveMaxesReloadSpeedAndSpeedsYouUpFor8Seconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ultimate");
        assertEquals(4, t.engine.quivers().reloadSpeed(p));
        assertTrue(t.engine.tags().has(p, Tags.HASTED));
        t.time.advance(161);
        assertEquals(0, t.engine.quivers().reloadSpeed(p));
        assertFalse(t.engine.tags().has(p, Tags.HASTED));
    }

    // ---- basic attack: cooldown cut ---------------------------------------------------------------

    @Test
    void boltHitsTakeASecondOffVenomStep() throws IOException {
        setup();
        t.spawn(10, 1, 0);
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(10); // dash over
        long before = t.engine.cooldowns().remainingTicks(p, "venom_step");
        t.engine.quivers().tryLoad(p);
        shoot();
        long after = t.engine.cooldowns().remainingTicks(p, "venom_step");
        assertEquals(before - 10 - 20, after, 1, "10 ticks passed, the hit took 20 off");
    }

    // ---- Hunter's Snare ------------------------------------------------------------------------------

    /** Toss a trap straight ahead onto a floor and let it settle. Returns it. */
    private me.mephisto.ability_engine.engine.construct.ConstructHandle throwSnare() {
        t.world.floor(0);
        t.world.look(p, new Vec3(1, 0, 0));
        int before = t.engine.constructs().activeCount();
        t.engine.cooldowns().clear(p, "hunters_snare");
        assertTrue(t.engine.loadouts().activate(p, "ability_3").success());
        t.time.advance(40);
        assertEquals(before + 1, t.engine.constructs().activeCount(), "landed and set");
        var all = t.engine.constructs().all();
        return all.get(all.size() - 1);
    }

    @Test
    void theSnareIsTossedAShortWayAndLiesOnTheGround() throws IOException {
        setup();
        var trap = throwSnare();
        assertTrue(trap.position().x() > 3 && trap.position().x() < 12, "a short toss: x=" + trap.position().x());
        assertEquals(0.0, trap.position().y(), 1e-6, "lying on the floor");
        assertTrue(trap.armed(), "armed where it stopped");
    }

    @Test
    void theSnareRootsRevealsAndMarksTheFirstEnemyToStepOnIt() throws IOException {
        setup();
        var trap = throwSnare();
        UUID enemy = t.spawn(30, 1, 0);
        t.world.move(enemy, trap.position().add(0.5, 0.9, 0.5));
        t.time.advance(1);
        assertTrue(t.engine.tags().has(enemy, Tags.ROOTED));
        assertTrue(t.engine.tags().has(enemy, Tags.GLOWING));
        assertTrue(t.engine.statuses().has(enemy, "hunters_mark"));
        assertTrue(t.render.cues.contains("trap_spring"));
        assertEquals(0, t.engine.constructs().activeCount(), "sprung: gone");
    }

    @Test
    void theSnareIgnoresAllies() throws IOException {
        setup();
        var trap = throwSnare();
        UUID ally = t.spawn(30, 1, 0);
        t.world.team(p, "blue");
        t.world.team(ally, "blue");
        t.world.move(ally, trap.position().add(0, 0.9, 0));
        t.time.advance(5);
        assertFalse(t.engine.tags().has(ally, Tags.ROOTED));
        assertEquals(1, t.engine.constructs().activeCount(), "still waiting");
    }

    @Test
    void aDirectHitWithTheThrowSnaresAtOnce() throws IOException {
        setup();
        UUID enemy = t.spawn(3, 1, 0);
        t.engine.loadouts().activate(p, "ability_3");
        t.time.advance(20);
        assertTrue(t.engine.tags().has(enemy, Tags.ROOTED));
        assertTrue(t.engine.statuses().has(enemy, "hunters_mark"));
        assertEquals(0, t.engine.constructs().activeCount(), "no trap left behind");
    }

    @Test
    void atMostFiveTrapsAThrowingASixthRemovesTheOldest() throws IOException {
        setup();
        var first = throwSnare();
        for (int i = 0; i < 4; i++) throwSnare();
        assertEquals(5, t.engine.constructs().activeCount());
        assertTrue(first.isAlive());
        t.engine.cooldowns().clear(p, "hunters_snare");
        t.engine.loadouts().activate(p, "ability_3");
        t.time.advance(40);
        assertEquals(5, t.engine.constructs().activeCount(), "still 5");
        assertFalse(first.isAlive(), "the oldest went");
        assertTrue(t.render.cues.contains("fizzle"));
    }

    @Test
    void trapsStayArmedWhenYouDieButNotWhenYouLeave() throws IOException {
        setup();
        var trap = throwSnare();
        t.engine.resetOnDeath(p);
        t.time.advance(5);
        assertTrue(trap.isAlive(), "still out after death");
        UUID enemy = t.spawn(30, 1, 0);
        t.world.move(enemy, trap.position().add(0, 0.9, 0));
        t.time.advance(1);
        assertTrue(t.engine.tags().has(enemy, Tags.ROOTED), "and still springs");
        t.world.kill(enemy); // out of the way of the next trap

        var second = throwSnare();
        t.engine.resetEntity(p, "quit");
        t.time.advance(2);
        assertFalse(second.isAlive(), "logging out clears them");
    }

    @Test
    void projectilesAndPunchesPassThroughTheSnare() throws IOException {
        setup();
        var trap = throwSnare();
        assertFalse(trap.solid());
        // A shot straight through its centre doesn't touch it (a solid construct would absorb it)...
        Vec3 c = trap.position();
        assertTrue(t.engine.constructs().sweep(trap.world(), c.add(-2, 0, 0), c.add(2, 0, 0), 0.2).isEmpty());
        // ...and nothing striking it (your bolt, a punch) sets it off.
        assertFalse(t.engine.constructs().strike(trap,
                new me.mephisto.ability_engine.engine.construct.Strike(p, "infused_bolt", "none")));
        assertEquals(1, t.engine.constructs().activeCount(), "still waiting");
    }

    @Test
    void slidingCarriesAProjectileFurtherThanLandingFlat() {
        java.util.function.DoubleUnaryOperator landsAt = slide -> {
            TestEngine e = new TestEngine();
            e.world.floor(0);
            UUID thrower = e.spawn(0, 1, 0);
            e.load(me.mephisto.ability_engine.engine.testkit.Yml.abilities("toss", map("nodes", map(
                    "throw", map("type", "projectile", "speed", 0.65, "bounces", 1, "restitution", 0.3,
                            "friction", 0.2, "slide", slide, "motion", list(map("type", "gravity", "amount", 0.06)),
                            "on", map("hit_block", "mark")),
                    "mark", map("type", "construct", "at", "hit", "fuse", 100, "height", 0, "solid", false)))));
            e.engine.activator().activate(thrower, "toss");
            e.time.advance(60);
            return e.engine.constructs().all().get(0).position().x();
        };
        double flat = landsAt.applyAsDouble(0);
        double slid = landsAt.applyAsDouble(0.6);
        assertTrue(slid > flat + 0.2, "slid on: " + flat + " -> " + slid);
        assertTrue(slid < flat + 2, "only a bit: " + flat + " -> " + slid);
    }

    @Test
    void aMarkedEnemyTakesExtraDamageFromTheNextBoltOnly() throws IOException {
        setup();
        UUID enemy = t.spawn(10, 1, 0);
        t.engine.statuses().apply(enemy, "hunters_mark", p);
        t.engine.quivers().tryLoad(p);
        shoot();
        assertEquals(45 + 40, t.damage(enemy), 1e-9);
        assertFalse(t.engine.statuses().has(enemy, "hunters_mark"), "used up");
        t.engine.quivers().tryLoad(p);
        shoot();
        assertEquals(45 + 40 + 45, t.damage(enemy), 1e-9);
    }

    // ---- Overdrive: piercing -------------------------------------------------------------------------

    @Test
    void duringOverdriveBoltsPierceThreeEnemies() throws IOException {
        setup();
        UUID[] line = {t.spawn(4, 1, 0), t.spawn(6, 1, 0), t.spawn(8, 1, 0), t.spawn(10, 1, 0), t.spawn(12, 1, 0)};
        t.engine.loadouts().activate(p, "ultimate");
        t.engine.quivers().tryLoad(p);
        shoot();
        for (int i = 0; i < 4; i++) assertEquals(45, t.damage(line[i]), 1e-9, "enemy " + i);
        assertEquals(0, t.damage(line[4]), 1e-9, "stopped at the 4th");
        assertEquals(3, t.engine.statuses().find(p, "hunters_rhythm").orElseThrow().stacks(), "each hit counts");
    }

    @Test
    void withoutOverdriveBoltsStopAtTheFirstEnemy() throws IOException {
        setup();
        UUID first = t.spawn(4, 1, 0);
        UUID second = t.spawn(6, 1, 0);
        t.engine.quivers().tryLoad(p);
        shoot();
        assertEquals(45, t.damage(first), 1e-9);
        assertEquals(0, t.damage(second), 1e-9);
    }

    // ---- loading ----------------------------------------------------------------------------------

    @Test
    void quiverMistakesAreReportedAtLoad() {
        TestEngine t = new TestEngine();
        LoadReport report = new me.mephisto.ability_engine.engine.data.AbilityLoader(t.engine).load(map(
                "infusions", map("bad_color", map("color", "green", "on_hit", list(map("id", "status", "status", "stun")))),
                "abilities", map("x", map("nodes", map("i", map("type", "infuse", "infusion", "nope")))),
                "characters", map("c", map("slots", map(), "quiver", map("size", 3, "hotbar", 8)))), "test");
        String errors = String.join("\n", report.errors());
        assertTrue(errors.contains("color: expected #RRGGBB"), errors);
        assertTrue(errors.contains("unknown infusion 'nope'"), errors);
        assertTrue(errors.contains("don't fit in the hotbar"), errors);
    }
}
