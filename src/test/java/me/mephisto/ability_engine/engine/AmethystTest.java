package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.floor.Floors;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Amethyst (fixtures/amethyst.yml): 180 HP, 15 armor, base damage 35. She stands at the origin on a floor (y 0, her
 * middle at y 1), looking +x, team blue. Enemies are team red (no sheet: 200 HP, no armor). Her ammo is her piercing
 * shards (none to start with; Allegro +4, Arpeggio +2, Geode Heart +6), lost 8s after the last gain:
 * through everyone, they hang where they end; without ammo, regular shards hit one. Recall brings them back, 10% less per
 * shard on the same enemy. Geode Bloom's geode banks off walls and crystallizes a floor (enemies slower, her faster, she
 * glides on it); Arpeggio fires 6 basic attacks in a run; Geode Heart encases her, then shatters into a ring of shards.
 * Geode Bloom's floor is the real ground turned into amethyst blocks (the fake world records them, see TestEngine).
 */
class AmethystTest {

    private static final double BASE = 35;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        setup(1);
    }

    /** She stands with her middle at {@code y} (the floor is at 0). */
    private void setup(double y) throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        // an enemy's plain hit on whoever's within 10 (to hurt her while she's encased)
        t.load(map("abilities", map("punch", map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "radius", "radius", 10), "effects", list(map("id", "damage", "amount", 10))))))));
        t.world.floor(0);
        p = t.spawn(0, y, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "amethyst");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    /** A shot of the primary, then enough time for the next (5 a second). */
    private void shoot() {
        use(Slots.PRIMARY);
        t.time.advance(5);
    }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int stacks(UUID id, String status) {
        return t.engine.statuses().find(id, status).map(s -> s.stacks()).orElse(0);
    }

    private Vec3 pos(UUID id) {
        return t.world.positionOf(new me.mephisto.ability_engine.engine.target.EntityTarget(id)).orElseThrow().position();
    }

    private int ammo() { return t.engine.resources().get(p, "ammo"); }

    private boolean running(String ability) { return t.engine.instances().isRunning(p, ability); }

    /** A floor of hers right where she stands: 4 blocks round, on the ground, for as long as the test needs it. */
    private void floorUnderHer(double radius) {
        t.engine.floors().add(new Floors.Patch(p, "test", "world", new Vec3(pos(p).x(), 0, pos(p).z()), radius,
                "amethyst_floor_slow", "amethyst_on_floor"));
        t.time.advance(2); // (Floors looks at who stands where every 2 ticks)
    }

    // ---- regular and piercing shards -------------------------------------------------------------------------

    @Test
    void aRegularShardHitsTheFirstEnemyOnlyAndDoesntHang() throws IOException {
        setup();
        UUID front = foe(4, 0);
        UUID back = foe(7, 0);
        shoot();
        t.time.advance(15);
        assertEquals(BASE * 0.8, t.damage(front), 1e-6, "80%");
        assertEquals(0, t.damage(back), 1e-9, "it stopped in the first");
        assertEquals(0, t.engine.constructs().activeCount(), "nothing hangs");
        assertEquals(0, ammo(), "none to start with (a regular shot costs none)");
        assertTrue(t.engine.activator().isUnavailable(p, t.engine.abilities().find("amethyst_recall").orElseThrow()),
                "nothing to recall");
    }

    @Test
    void aPiercingShardGoesThroughEveryoneAndHangsWhereItEnds() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        UUID front = foe(4, 0);
        UUID back = foe(7, 0);
        shoot();
        t.time.advance(15);
        assertEquals(BASE * 0.8, t.damage(front), 1e-6, "the first: 80%");
        assertEquals(BASE * 0.5, t.damage(back), 1e-6, "pierced: 50% behind it");
        assertEquals(3, ammo());
        assertEquals(1, t.engine.constructs().activeCount(), "it hangs where it ended");
        assertTrue(t.render.cues.contains("amethyst_shard_lodge"), "a knock as it lodges");
    }

    // ---- ammo expiry -------------------------------------------------------------------------------------------

    @Test
    void allTheAmmoExpiresEightSecondsAfterTheLastGain() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        assertEquals(4, ammo());
        assertTrue(has(p, "amethyst_ammo_timer"), "the XP bar shows the time left");
        t.time.advance(159);
        assertEquals(4, ammo(), "just under 8s: still there");
        t.time.advance(2);
        assertEquals(0, ammo(), "8s after the gain: all gone");
        assertFalse(has(p, "amethyst_ammo_timer"), "and its timer with it");
    }

    @Test
    void anyGainStartsTheEightSecondsOver() throws IOException {
        setup();
        use(Slots.ABILITY_1);                  // 4
        t.time.advance(120);
        t.engine.cooldowns().clearAll(p);
        use(Slots.ABILITY_1);                  // a gain: the 8s start over (capped at 6)
        assertEquals(6, ammo());
        t.time.advance(120);
        assertEquals(6, ammo(), "12s after the first gain, 6s after the last: still there");
        t.time.advance(41);
        assertEquals(0, ammo(), "8s after the last gain");
    }

    @Test
    void spendingTheLastShardTakesTheTimerOff() throws IOException {
        setup();
        t.engine.resources().add(p, "ammo", 1);
        assertTrue(has(p, "amethyst_ammo_timer"));
        shoot();
        assertEquals(0, ammo());
        assertFalse(has(p, "amethyst_ammo_timer"), "nothing left to expire");
    }

    // ---- 1: Allegro --------------------------------------------------------------------------------------------

    @Test
    void allegroGivesFourAmmoAndAnAttackSpeedBurstThatFadesNoMoveSpeed() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        assertEquals(4, ammo(), "+4 piercing shards");
        assertEquals(1.4, t.engine.stats().attackSpeedMultiplier(p), 1e-9, "+40% at the cast");
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "no movement speed");
        t.time.advance(40);
        double half = t.engine.stats().attackSpeedMultiplier(p);
        assertTrue(half > 1.16 && half < 1.24, "about +20% halfway through: " + half);
        t.time.advance(41);
        assertEquals(1.0, t.engine.stats().attackSpeedMultiplier(p), 1e-9, "nothing left after 4s");
        assertFalse(has(p, "amethyst_allegro"));
    }

    // ---- 3: Arpeggio -------------------------------------------------------------------------------------------

    @Test
    void arpeggioChargesHalfASecondThenFiresSixShotsOneEveryFifthOfASecond() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        assertEquals(2, ammo(), "+2 at the cast");
        t.time.advance(9);
        assertEquals(0, t.engine.projectiles().activeCount(), "still charging");
        assertEquals(2, ammo());
        t.time.advance(1);                     // t 10: the first shot
        assertEquals(1, ammo(), "a real shot: it costs a shard");
        t.time.advance(19);                    // t 29: the 6th is at t 30
        assertTrue(running("amethyst_ab3"));
        t.time.advance(1);
        assertFalse(running("amethyst_ab3"), "over the moment the 6th fires");
        assertEquals(0, ammo());
        long notes = t.render.cues.stream().filter(c -> c.startsWith("amethyst_arpeggio_note_")).count();
        assertEquals(6, notes, "six notes");
        assertTrue(t.render.cues.contains("amethyst_arpeggio_note_6"), "up the scale");
        t.time.advance(20);
        assertEquals(2, t.engine.constructs().activeCount(), "2 piercing shards hang; the other 4 were regular");
    }

    @Test
    void allegroThenArpeggioLeavesExactlySixPiercingShardsHangingAndComesFaster() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        use(Slots.ABILITY_3);
        assertEquals(6, ammo(), "4 + 2: full");
        t.time.advance(25);
        assertFalse(running("amethyst_ab3"), "faster with Allegro's attack speed (base: 30 ticks)");
        assertEquals(0, ammo());
        t.time.advance(20);
        assertEquals(6, t.engine.constructs().activeCount(), "all 6 piercing, all hanging");
    }

    @Test
    void nothingElseUntilTheLastShotThenRecallAtOnce() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        use(Slots.ABILITY_3);
        t.time.advance(12);
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "no LMB meanwhile");
        assertFalse(t.engine.loadouts().activate(p, Slots.SECONDARY).success(), "no Recall meanwhile");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "no other ability meanwhile");
        while (running("amethyst_ab3")) t.time.advance(1);
        use(Slots.SECONDARY);                  // Recall: free the moment the last shot fired
    }

    @Test
    void arpeggioSlowsHerOffTheFloorButNotOnIt() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        assertEquals(0.6, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "40% slower off her floor");
        floorUnderHer(4);
        assertTrue(t.engine.tags().has(p, "state.on_floor"));
        assertEquals(1.2, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "on it: no slow, and the floor's +20%");
    }

    // ---- 2: Geode Bloom ----------------------------------------------------------------------------------------

    @Test
    void geodeBloomFliesThroughEnemiesAndBurstsIntoAFloorWhereItLands() throws IOException {
        setup();
        UUID inTheWay = foe(3, 0);
        use(Slots.ABILITY_2);
        while (t.engine.projectiles().activeCount() > 0) t.time.advance(1);
        var floors = t.engine.floors().of(p);
        assertEquals(1, floors.size(), "a floor where it landed");
        Floors.Patch floor = floors.get(0);
        assertEquals(5, floor.radius(), 1e-9);
        assertEquals(0, floor.center().y(), 1e-6, "on the ground");
        assertTrue(floor.center().x() > 4, "it flew through the enemy in the way: " + floor.center());
        assertEquals(0, t.damage(inTheWay), 1e-9, "too far from the burst");
        assertEquals(0, ammo(), "no ammo from it");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "amethyst_ab2"), "no cooldown while the floor's up");
    }

    @Test
    void theBurstRootsWithoutDamageTheFloorSlowsEnemiesAndSpeedsHerUp() throws IOException {
        setup();                               // where does it land? (it flies through everyone: the same with them there)
        use(Slots.ABILITY_2);
        while (t.engine.projectiles().activeCount() > 0) t.time.advance(1);
        Vec3 c = t.engine.floors().of(p).get(0).center();
        setup();
        UUID there = foe(c.x(), c.z());
        UUID beside = foe(c.x() + 2, c.z());
        UUID far = foe(c.x() + 6, c.z());
        use(Slots.ABILITY_2);
        while (t.engine.projectiles().activeCount() > 0) t.time.advance(1);
        assertEquals(0, t.damage(there), 1e-9, "no damage");
        assertTrue(t.engine.tags().has(there, Tags.ROOTED), "rooted 1s");
        assertTrue(t.render.loops.contains("amethyst_feet_geode"), "feet stuck in a geode");
        assertTrue(t.engine.tags().has(beside, Tags.ROOTED), "2 blocks off: still in it (2.5)");
        assertFalse(t.engine.tags().has(far, Tags.ROOTED));
        t.time.advance(2);
        assertEquals(0.8, t.engine.stats().moveSpeedMultiplier(beside), 1e-9, "on her floor: 20% slower");
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(far), 1e-9, "off it: not slowed");
        t.time.advance(20);
        assertFalse(t.engine.tags().has(there, Tags.ROOTED), "only 1s");
        // her, on her own floor
        t.world.move(p, new Vec3(c.x(), 1, c.z() + 1));
        t.time.advance(2);
        assertEquals(1.2, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "she's 20% faster on it");
        t.world.move(p, new Vec3(c.x(), 1, c.z() + 8));
        t.time.advance(8);
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "off it: back to normal");
    }

    @Test
    void theFloorLastsSixSecondsThenTheCooldownStartsAndOnlyOneAtATime() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        var ab2 = t.engine.abilities().find("amethyst_ab2").orElseThrow();
        assertTrue(t.engine.activator().isPassive(p, ab2), "glinting while the geode is out");
        while (t.engine.projectiles().activeCount() > 0) t.time.advance(1);
        assertTrue(t.engine.activator().isPassive(p, ab2), "and while its floor is up");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "a second one: its key does nothing");
        t.time.advance(1);
        assertEquals(0, t.engine.projectiles().activeCount(), "no second geode");
        assertEquals(1, t.engine.floors().of(p).size());
        t.time.advance(119);
        assertEquals(0, t.engine.floors().of(p).size(), "6s: gone");
        assertEquals(240, t.engine.cooldowns().remainingTicks(p, "amethyst_ab2"), 2, "12s from when the floor ended");
        assertFalse(t.engine.activator().isPassive(p, ab2), "no longer glinting");
    }

    @Test
    void itsFloorIsTheRealGroundTurnedToAmethystNotDrawn() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        while (t.engine.projectiles().activeCount() > 0) t.time.advance(1);
        assertEquals(1, t.placed.size(), "real blocks");
        assertEquals("floor", t.placed.get(0).shape());
        assertEquals(120, t.placed.get(0).duration(), "for the floor's 6s");
        var floors = t.engine.floors().of(p);
        assertEquals(1, floors.size(), "the same floor (slow, speed, glide)...");
        assertFalse(floors.get(0).drawn(), "...not drawn: the blocks show it");
        assertTrue(floors.get(0).sight(), "and, like the blocks, not behind walls");
    }

    @Test
    void aFloorWithSightDoesntReachBehindAWall() throws IOException {
        setup();
        t.world.box(1.5, 2, -5, 5, 2);         // a wall 1.5 blocks out (+x), 2 tall
        t.world.box(-2, -1.5, -5, 5, 1);       // and a 1-block one behind her (-x): it hides nothing
        t.engine.floors().add(new Floors.Patch(p, "test", "world", new Vec3(0, 0, 0), 4, "amethyst_floor_slow",
                "amethyst_on_floor", false, true));
        UUID before = foe(1, 0.5);
        UUID behind = foe(3, 0);
        UUID overLow = foe(-3, 0);
        t.time.advance(2);
        assertEquals(0.8, t.engine.stats().moveSpeedMultiplier(before), 1e-9, "in sight: on it");
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(behind), 1e-9, "behind the 2-block wall: not");
        assertEquals(0.8, t.engine.stats().moveSpeedMultiplier(overLow), 1e-9, "behind the 1-block wall: still on it");
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "she still glides the open way");
    }

    @Test
    void theGeodeBanksOffWallsThreeTimesThenDropsStraightDown() throws IOException {
        setup(10);                             // high up: time for every bounce before it lands
        t.world.box(1, 3, -5, 5, 20);          // a wall just ahead (x 1)...
        t.world.box(-3, -1, -5, 5, 20);        // ...and one just behind (x -1)
        use(Slots.ABILITY_2);
        while (t.engine.projectiles().activeCount() > 0) t.time.advance(1);
        long bounces = t.render.cues.stream().filter("amethyst_geode_bounce"::equals).count();
        assertEquals(3, bounces, "3 wall bounces");
        // ahead (1), behind (2), ahead (3), then the 4th wall (behind) drops it straight down: it lands by that wall
        Vec3 c = t.engine.floors().of(p).get(0).center();
        assertTrue(c.x() < -0.5 && c.x() > -1.0, "dropped by the wall behind her: " + c);
    }

    // ---- the crystal floor: gliding ----------------------------------------------------------------------------

    @Test
    void sheGlidesThreeBlocksOnHerFloorAgainEveryHalfSecond() throws IOException {
        setup();
        floorUnderHer(6);
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)));
        t.time.advance(6);
        assertEquals(3, pos(p).z(), 0.1, "3 blocks that way");
        assertEquals(0, pos(p).x(), 1e-9);
        assertTrue(t.render.cues.contains("amethyst_glide"), "a chime");
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, -1)), "not again within 0.5s");
        t.time.advance(4);
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, -1)), "again after 0.5s");
    }

    @Test
    void aGlideNeverLeavesTheFloor() throws IOException {
        setup();
        floorUnderHer(4);
        t.world.move(p, new Vec3(2, 1, 0));
        assertTrue(t.engine.floors().glide(p, new Vec3(1, 0, 0)));
        t.time.advance(8);
        assertTrue(pos(p).x() <= 4 + 1e-9 && pos(p).x() > 3.5, "stopped at the edge: " + pos(p));
        assertTrue(t.engine.floors().onOwnFloor(p));
    }

    @Test
    void noGlidingOffHerFloorOrWhileRooted() throws IOException {
        setup();
        assertFalse(t.engine.floors().glide(p, new Vec3(1, 0, 0)), "no floor: no movement at all");
        floorUnderHer(4);
        t.engine.statuses().apply(p, "root", 20, null);
        assertFalse(t.engine.floors().glide(p, new Vec3(1, 0, 0)), "rooted");
    }

    @Test
    void touchingFloorsCountAsOneToGlideAcross() throws IOException {
        setup();
        floorUnderHer(2);                      // 0..2
        t.engine.floors().add(new Floors.Patch(p, "next", "world", new Vec3(3.5, 0, 0), 2, null, "amethyst_on_floor"));
        assertTrue(t.engine.floors().glide(p, new Vec3(1, 0, 0)));
        t.time.advance(8);
        assertEquals(3, pos(p).x(), 0.1, "across where they meet");
    }

    // ---- the slow cap ------------------------------------------------------------------------------------------

    @Test
    void recallsSlowAndTheFloorsTogetherNeverSlowMoreThanFortyPercent() throws IOException {
        setup();
        UUID enemy = foe(5, 0);
        for (int i = 0; i < 6; i++) t.engine.statuses().apply(enemy, "amethyst_shard_slow", p);
        assertEquals(0.6, t.engine.stats().moveSpeedMultiplier(enemy), 1e-9, "6 shards: capped at 40%");
        t.engine.statuses().apply(enemy, "amethyst_floor_slow", p);
        assertEquals(0.6, t.engine.stats().moveSpeedMultiplier(enemy), 1e-9, "and the floor's on top: still 40%");
        t.engine.statuses().clear(enemy);
        t.engine.statuses().apply(enemy, "amethyst_floor_slow", p);
        t.engine.statuses().apply(enemy, "amethyst_shard_slow", p);
        assertEquals(0.6, t.engine.stats().moveSpeedMultiplier(enemy), 1e-9, "0.8 x 0.75 = exactly the cap");
        t.engine.statuses().apply(enemy, "slow", p);
        assertEquals(0.6, t.engine.stats().moveSpeedMultiplier(enemy), 1e-9,
                "(a plain slow is a tag, not part of the cap: still multiplies on top on the platform)");
    }

    // ---- Recall ------------------------------------------------------------------------------------------------

    @Test
    void recallBringsThemBackEachDoingTenPercentLessToTheSameEnemy() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        for (int i = 0; i < 3; i++) shoot();
        t.time.advance(15);
        assertEquals(3, t.engine.constructs().activeCount(), "3 hanging at their full range");
        int before = ammo();

        UUID enemy = foe(10, 0); // between them and her
        use(Slots.SECONDARY);                 // RMB: Recall
        t.time.advance(25);
        assertEquals(0, t.engine.constructs().activeCount(), "all came back");
        assertEquals(before, ammo(), "to her, not into the ammo");
        assertEquals(BASE * (1 + 0.9 + 0.8), t.damage(enemy), 1e-6, "100%, 90%, 80%");
        assertEquals(3, stacks(enemy, "amethyst_shard_slow"));
        assertTrue(t.render.cues.contains("amethyst_recall_hit_3"), "each one note higher");
    }

    @Test
    void recallTakesTenSecondsOrFiveOnceAShardHitsAPlayer() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        shoot();
        t.time.advance(15);
        UUID mob = foe(10, 0);                 // not a player
        use(Slots.SECONDARY);
        t.time.advance(25);
        assertTrue(t.damage(mob) > 0);
        assertEquals(200 - 25, t.engine.cooldowns().remainingTicks(p, "amethyst_recall"), 1, "10s: no player hit");

        t.engine.cooldowns().clearAll(p);
        for (int i = 0; i < 3; i++) shoot();
        t.time.advance(15);
        UUID player = foe(12, 0.3);            // on the way back
        t.world.players.add(player);
        UUID other = foe(14, -0.3);
        t.world.players.add(other);            // two players hit: still only 5s off, once
        use(Slots.SECONDARY);
        t.time.advance(25);
        assertTrue(t.damage(player) > 0 || t.damage(other) > 0);
        assertEquals(100 - 25, t.engine.cooldowns().remainingTicks(p, "amethyst_recall"), 1, "5s once a player was hit");
    }

    // ---- ultimate: Geode Heart ---------------------------------------------------------------------------------

    @Test
    void geodeHeartEncasesHerTwoSecondsUntouchableAndHealing() throws IOException {
        setup();
        UUID enemy = foe(2, 0);
        use(Slots.ULTIMATE);
        assertTrue(has(p, "amethyst_geode_heart"));
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE), "untargetable");
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_WALK) && t.engine.tags().has(p, Tags.BLOCK_ABILITY), "can't move or act");
        t.engine.activator().activate(enemy, "punch");
        assertEquals(0, t.damage(p), 1e-9, "takes no damage");
        t.time.advance(39);
        assertTrue(has(p, "amethyst_geode_heart"));
        t.time.advance(1);
        assertFalse(has(p, "amethyst_geode_heart"), "shattered at 2s");
        assertEquals(180 * 0.25, t.healed.getOrDefault(p, 0.0), 1e-6, "25% of her max HP over it");
    }

    @Test
    void itShattersIntoABurstAFloorARingOfShardsAndSixAmmo() throws IOException {
        setup();
        UUID near = foe(2, 0);
        UUID far = foe(5, 0);
        use(Slots.ULTIMATE);
        t.time.advance(40);
        assertEquals(BASE, t.damage(near), 1e-6, "100% within 3 blocks");
        assertEquals(0, t.damage(far), 1e-9);
        assertTrue(t.engine.tags().has(near, Tags.ROOTED), "rooted 1s");
        assertEquals(0, t.engine.floors().of(p).size(), "no floor");
        assertEquals(6, ammo(), "+6 ammo");
        t.time.advance(15);
        assertEquals(6, t.engine.constructs().activeCount(), "6 shards hang");
        for (var c : t.engine.constructs().all()) {
            Vec3 at = c.position();
            assertEquals(7, Math.hypot(at.x(), at.z()), 0.8, "~7 blocks out: " + at);
        }
        assertFalse(t.engine.activator().isUnavailable(p, t.engine.abilities().find("amethyst_recall").orElseThrow()),
                "Recall can bring them back");
    }

    @Test
    void fAgainBreaksOutEarlyKeepingOnlyWhatsHealedSoFar() throws IOException {
        setup();
        use(Slots.ULTIMATE);
        t.time.advance(5);
        t.engine.loadouts().activate(p, Slots.ULTIMATE); // too soon (under 0.5s): nothing
        assertTrue(has(p, "amethyst_geode_heart"));
        t.time.advance(7);                     // t 12
        use(Slots.ULTIMATE);                   // F again: out now
        assertFalse(has(p, "amethyst_geode_heart"), "shattered early");
        assertEquals(6, ammo(), "and everything that comes with it");
        assertEquals(2 * 180 * 0.03125, t.healed.getOrDefault(p, 0.0), 1e-6, "only the 2 heals so far");
    }
}
