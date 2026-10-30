package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Fae Trickster (fixture snapshot). The Fae stands at x=0 on a floor at y=0, facing +x; enemies are on team red.
 * Base damage 34.
 */
class TricksterTest {

    private TestEngine t;
    private UUID p;
    private final List<String> told = new ArrayList<>();

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.world.players.add(p);
        t.engine.loadouts().assign(p, "trickster");
        t.engine.setNotifier((who, text) -> told.add(text));
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        t.world.players.add(e);
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    /** Time goes on until her casts are over (Trick Room's aside: it lasts as long as its room). */
    private void untilDone() {
        for (int i = 0; i < 200 && t.engine.instances().of(p).stream().anyMatch(x -> !x.ability().id().equals("trickster_ab2")); i++) {
            t.time.advance(1);
        }
    }

    /** How many times a looping cue was started (on anyone). */
    private long started(String id) { return t.render.started.stream().filter(l -> id.equals(l[0])).count(); }

    /** Time goes on until {@code target} has taken some damage (at most 40 ticks). */
    private void untilHurt(UUID target) {
        for (int i = 0; i < 40 && t.damage(target) == 0; i++) t.time.advance(1);
    }

    private boolean silenced(UUID id) { return t.engine.tags().has(id, Tags.SILENCED); }

    /** Controls inverted (the topsy-turvy Trick Room). */
    private boolean topsyTurvy(UUID id) { return t.engine.tags().has(id, Tags.INVERTED); }

    private void swipe() {
        t.time.advance(15); // (past the swipe's cooldown)
        t.engine.loadouts().activate(p, Slots.PRIMARY);
    }

    // ---- Passive: Sucker Punch ------------------------------------------------------------------

    @Test
    void theFirstSwipeSilencesForASecondThenNotAgain() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertTrue(silenced(e), "the first basic attack silences");
        assertFalse(t.engine.tags().has(e, Tags.NAUSEOUS), "no nausea any more");
        assertEquals(34, t.damage(e), 1e-6, "100% of 34");
        assertTrue(t.render.cues.contains("trickster_hush"));
        t.time.advance(20);
        assertFalse(silenced(e), "for 1s");
        swipe();
        assertFalse(silenced(e), "SPENT: the next one doesn't");
    }

    @Test
    void anEnemyWhoStrikesFirstIsNotSilenced() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.notifyDamaged(p, e); // they hit the Fae first
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertFalse(silenced(e));
        assertTrue(t.damage(e) > 0, "the swipe still lands");
    }

    @Test
    void readyAgainAfterEightSecondsWithoutDamageEitherWay() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.PRIMARY); // silenced, SPENT
        t.time.advance(100);
        t.engine.notifyDamaged(p, e);                   // a hit between them at 5s: the 8s start over
        t.time.advance(100);                            // 10s since the swipe, 5s since their hit
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertFalse(silenced(e), "still SPENT: their hit restarted the timer");
        t.time.advance(165);                            // 8s+ with nothing between them
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertTrue(silenced(e), "READY again");
    }

    @Test
    void theFaeDyingMakesEveryoneReadyAgain() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.notifyDamaged(p, e); // SPENT
        t.engine.resetOnDeath(p);
        t.engine.resetOnRespawn(p);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertTrue(silenced(e), "a fresh start for the pair");
    }

    @Test
    void readyEnemiesNearbyAreMarkedForTheFae() throws IOException {
        setup();
        UUID near = enemyAt(5, 0);
        UUID far = enemyAt(15, 0);
        UUID ally = t.spawn(3, 1, 0);
        t.world.team(ally, "blue");
        assertEquals(List.of(near), t.engine.marks().marked(p), "within 10 blocks, enemies only");
        t.engine.notifyDamaged(p, near); // SPENT
        assertTrue(t.engine.marks().marked(p).isEmpty());
        assertFalse(t.engine.marks().marked(p).contains(far));
    }

    @Test
    void playRoughNeverSilences() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilDone();
        assertTrue(t.damage(e) > 0);
        assertFalse(silenced(e));
        t.engine.loadouts().activate(p, Slots.PRIMARY); // still READY: the slash didn't use the passive
        assertTrue(silenced(e));
    }

    // ---- 1: Play Rough ---------------------------------------------------------------------------

    @Test
    void playRoughLungesAndSlashesTwice() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilHurt(e);
        assertEquals(25.5, t.damage(e), 1e-6, "the first slash: 75% of 34");
        assertEquals(1, started("trickster_play_rough"), "drawn on her");
        untilDone();
        assertEquals(51, t.damage(e), 1e-6, "the second: 150% of 34 in all");
        assertEquals(2, started("trickster_play_rough"));
        assertTrue(pos(p).x() > 0.3 && pos(p).x() < 1.6, "lunged forward, stopping in front of them: " + pos(p));
    }

    @Test
    void playRoughsSlashesHitEveryoneInTheCone() throws IOException {
        setup();
        UUID edge = enemyAt(3.2, 1.0);     // inside the 90-degree, 3-block cone from both lunges (x=1, then x=2)
        UUID aside = enemyAt(2.5, 2.2);    // too far round
        UUID far = enemyAt(5.5, 0);        // out of reach
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilDone();
        assertEquals(2, pos(p).x(), 0.05, "two 1-block lunges, nobody in the way");
        assertEquals(51, t.damage(edge), 1e-6);
        assertEquals(0, t.damage(aside), 1e-9);
        assertEquals(0, t.damage(far), 1e-9);
    }

    @Test
    void steppingOutOfTheConeAfterTheFirstSlashDodgesTheSecond() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilHurt(e);
        assertEquals(25.5, t.damage(e), 1e-6);
        t.world.move(e, new Vec3(2, 1, 6));            // out of the cone
        untilDone();
        assertEquals(25.5, t.damage(e), 1e-6, "only the first");
    }

    @Test
    void aStunOrSilenceAfterTheFirstSlashLosesTheSecond() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilHurt(e);
        t.engine.statuses().apply(p, "silence", 20, null);
        untilDone();
        assertEquals(25.5, t.damage(e), 1e-6);
    }

    @Test
    void playRoughsCooldownStartsWhenItLands() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "trickster_ab1"), "not while lunging and slashing");
        untilDone();
        assertEquals(100, t.engine.cooldowns().remainingTicks(p, "trickster_ab1"), 1);
    }

    @Test
    void outsideHerTrickRoomSwipesTakeNoTimeOffPlayRough() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilDone();
        long before = t.engine.cooldowns().remainingTicks(p, "trickster_ab1");
        UUID e = enemyAt(pos(p).x() + 2, 0);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertTrue(t.damage(e) > 0);
        assertEquals(before, t.engine.cooldowns().remainingTicks(p, "trickster_ab1"), "no room: no time off");
    }

    @Test
    void swipesInsideHerTrickRoomTakeOneSecondOffOnAnyEnemy() throws IOException {
        setup();
        UUID e = t.spawn(2, 1, 0);
        t.world.team(e, "red");                        // a mob, not a player: it counts too
        room();
        t.engine.cooldowns().start(p, "trickster_ab1", 100);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertTrue(t.damage(e) > 0);
        assertEquals(100 - 20, t.engine.cooldowns().remainingTicks(p, "trickster_ab1"), 1, "in her room: -1s");
        t.time.advance(15);
        t.engine.loadouts().activate(p, Slots.PRIMARY);  // (aimed straight on: it still lands)
        assertEquals(100 - 15 - 40, t.engine.cooldowns().remainingTicks(p, "trickster_ab1"), 1, "each swipe");
        t.world.move(p, new Vec3(20, 1, 0));
        t.world.move(e, new Vec3(22, 1, 0));
        t.time.advance(15);                            // (off the floor, and past the swipe's cooldown)
        long before = t.engine.cooldowns().remainingTicks(p, "trickster_ab1");
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertEquals(before, t.engine.cooldowns().remainingTicks(p, "trickster_ab1"), 1, "outside it: nothing");
    }

    // ---- 2: Trick Room ---------------------------------------------------------------------------

    /**
     * An 11x11 room right where she stands: looking straight down (a level dash has nowhere to go), and her put back
     * where she was. On the block grid: centred on (0.5, 0.5), so x and z run from -5 to 6.
     */
    private void room() {
        t.world.look(p, new Vec3(0, -1, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        t.time.advance(3);
        t.world.move(p, new Vec3(0, 1, 0));
        t.world.look(p, new Vec3(1, 0, 0));
        t.time.advance(2); // the floor's first check puts its status on her
    }

    @Test
    void trickRoomDashesAheadAndPutsTheRoomWhereSheArrives() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -0.25, 0));        // looking a little down: the dash stays level
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        assertFalse(t.engine.targeting().current(p).isPresent(), "no aiming: straight off");
        for (int i = 0; i < 10; i++) t.time.advance(1);
        assertEquals(7, pos(p).x(), 0.6, "7 blocks ahead (give or take a tick of it)");
        assertEquals(1, pos(p).y(), 1e-9, "level");
        var floor = t.engine.floors().of(p).get(0);
        assertEquals(Math.floor(pos(p).x()) + 0.5, floor.center().x(), 1e-9, "the room where she arrived");
        assertTrue(t.render.cues.contains("trickster_room"));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "free to act once there");
    }

    @Test
    void theRoomGoesOnTheGroundBelowWhereSheEndsUp() throws IOException {
        setup();
        t.world.move(p, new Vec3(0, 6, 0));            // up on a ledge, the ground 6 below
        t.engine.loadouts().activate(p, Slots.ABILITY_2); // 7 ahead, off the ledge
        for (int i = 0; i < 12; i++) t.time.advance(1);
        var floor = t.engine.floors().of(p).get(0);
        assertEquals(0, floor.center().y(), 1e-9, "down on the ground, not in the air where the dash ended");
    }

    @Test
    void trickRoomsDashStopsAtAWall() throws IOException {
        setup();
        t.world.box(4, 5, -2, 2, 5);                   // a wall 4 ahead
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 10; i++) t.time.advance(1);
        assertTrue(pos(p).x() < 4, "stopped at it: " + pos(p));
        assertEquals(3.5, t.engine.floors().of(p).get(0).center().x(), 1e-9, "the room where she stopped");
    }

    @Test
    void rootedNoTrickRoomDash() throws IOException {
        setup();
        t.engine.statuses().apply(p, "root", 40, null);
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
    }

    @Test
    void theRoomIsElevenByElevenOnTheBlockGrid() throws IOException {
        setup();
        room();
        var floor = t.engine.floors().of(p).get(0);
        assertTrue(floor.square());
        assertEquals(5.5, floor.radius(), 1e-9);
        assertEquals(new Vec3(0.5, 0, 0.5), floor.center(), "odd: centred on the block's middle");
    }

    @Test
    void inTheRoomShiftWhileMovingTeleportsSixBlocks() throws IOException {
        setup();
        room();
        assertTrue(t.engine.tags().has(p, "state.on_floor"));
        t.world.move(p, new Vec3(0, 1, -4.5));         // near one side, so there's room for all of it
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)));
        assertEquals(1.5, pos(p).z(), 0.05, "6 blocks that way, at once");
        assertEquals(0, pos(p).x(), 1e-9);
        assertTrue(t.render.cues.stream().filter("trickster_blink"::equals).count() >= 2, "a puff where she left and arrived");
    }

    @Test
    void theTeleportStopsAtTheRoomsEdgeAtMostEveryHalfSecond() throws IOException {
        setup();
        room();
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)));
        assertEquals(5.65, pos(p).z(), 0.11, "clamped inside the edge (at 6)...");
        assertEquals(5, Math.floor(pos(p).z()), "...on the room's last block, not the one outside it");
        t.time.advance(9);
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, -1)), "0.5s between teleports");
        t.time.advance(1);
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, -1)));
        t.time.advance(10);
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)));
        t.time.advance(10);
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "at the edge: nowhere to go");
    }

    @Test
    void theRoomReachesTwoBlocksUpAndTwoDown() throws IOException {
        setup();
        room();
        t.world.move(p, new Vec3(0, 2.9, 0));          // feet 2 above the floor (a ledge)
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "2 up: inside");
        t.time.advance(10);
        t.world.move(p, new Vec3(0, -1.1, 0));         // feet 2 below it (a pit)
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "2 down: inside");
        t.time.advance(10);
        t.world.move(p, new Vec3(0, 3.5, 0));          // 2.6 up
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "higher: outside");
        t.world.move(p, new Vec3(0, -1.7, 0));         // 2.6 down
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "lower: outside");
    }

    @Test
    void theTeleportNeverLandsInABlock() throws IOException {
        setup();
        t.world.box(-2, 4, 2, 3, 5);                   // a wall across the room, 2 blocks ahead (+z)
        room();
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)));
        assertTrue(pos(p).z() < 2 - 0.29, "stopped in front of the wall: " + pos(p));
    }

    @Test
    void standingStillOrOutsideTheRoomShiftIsJustSneaking() throws IOException {
        setup();
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "no room");
        room();
        assertFalse(t.engine.floors().glide(p, Vec3.ZERO), "not moving");
        t.world.move(p, new Vec3(20, 1, 0));
        t.time.advance(10);
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "outside the room");
        assertEquals(new Vec3(20, 1, 0), pos(p));
    }

    @Test
    void theRoomLastsTenSecondsThenItsCooldownStarts() throws IOException {
        setup();
        room();
        t.time.advance(180);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "trickster_ab2"));
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)));
        t.time.advance(30);
        assertTrue(t.engine.floors().of(p).isEmpty(), "gone after 10s");
        assertEquals(280, t.engine.cooldowns().remainingTicks(p, "trickster_ab2"), 15);
    }

    @Test
    void rootedNoTeleportsButSilencedTheyStillWork() throws IOException {
        setup();
        room();
        t.engine.statuses().apply(p, "silence", 40, null);
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "silence: still works");
        t.time.advance(11);
        t.engine.statuses().apply(p, "root", 40, null);
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, -1)), "rooted: no");
        assertTrue(t.engine.floors().of(p).size() == 1, "and the room stays");
    }

    @Test
    void theTeleportCantSlipPastAWallBesideHerMiddle() throws IOException {
        setup();
        room();
        t.world.box(1.5, 2, 0.2, 0.5, 5);              // a thin post 1.5 ahead, off to one side of her middle
        assertTrue(t.engine.floors().glide(p, new Vec3(1, 0, 0)));
        assertTrue(pos(p).x() < 1.5 - 0.29, "her shoulder would hit it: stopped short, " + pos(p));
    }

    // ---- 2 again: moving the room ----

    @Test
    void twoAgainInsideTheRoomDashesFourBlocksAndTheRoomMovesWithHer() throws IOException {
        setup();
        room();
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "the recast");
        for (int i = 0; i < 8; i++) t.time.advance(1);
        assertEquals(4, pos(p).x(), 0.6, "a 4-block dash where she faces: " + pos(p));
        var floors = t.engine.floors().of(p).stream().filter(f -> "trick_room".equals(f.name())).toList();
        assertEquals(1, floors.size(), "still one room...");
        assertEquals(Math.floor(pos(p).x()) + 0.5, floors.get(0).center().x(), 1e-9, "...centred where she landed");
        assertEquals(5.5, floors.get(0).radius(), 1e-9, "the same 11x11");
        assertTrue(t.render.cues.contains("trickster_room_shift"));
    }

    @Test
    void theRoomsTimeShowsOnTheBossBarPurpleThenBlueOnceMoved() throws IOException {
        setup();
        room();                                        // (5 ticks in)
        var bar = t.engine.instances().timer(p).orElseThrow();
        assertEquals("trickster_ab2", bar.ability().id());
        assertEquals(195, bar.ticksLeft().orElseThrow(), 1, "10s, counting down");
        assertEquals("purple", bar.color(), "hers: purple");
        assertEquals(List.of("#FF3DF2", "#FF9AD8"), bar.textColors(), "its title magenta and pink, like the tiles");
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 8; i++) t.time.advance(1);
        assertEquals("blue", t.engine.instances().timer(p).orElseThrow().color(), "moved, against her enemies: blue");
        assertEquals(List.of("#6FCBFF", "#FFFFFF"), t.engine.instances().timer(p).orElseThrow().textColors(),
                "light blue and white, like the lit tiles");
        t.time.advance(190);
        assertTrue(t.engine.instances().timer(p).isEmpty(), "gone with the room");
    }

    @Test
    void herRoomIsHersUntilSheMovesItThenItsAgainstHerEnemies() throws IOException {
        setup();
        UUID e = enemyAt(3, 0);
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "(no speed bonus yet)");
        room();
        assertEquals(1.15, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "magenta: +15% speed");
        assertTrue(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "and teleports");
        assertFalse(topsyTurvy(e), "enemies untouched");
        t.world.move(p, new Vec3(0, 1, 0));
        t.time.advance(10);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);  // moved: now it's against them
        for (int i = 0; i < 16; i++) t.time.advance(1);  // (and the magenta room's hold runs out)
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "no more speed");
        assertFalse(t.engine.floors().glide(p, new Vec3(0, 0, 1)), "no more teleports");
        assertTrue(t.engine.tags().has(p, "state.in_trick_room"), "(still in her room)");
        t.world.move(e, new Vec3(pos(p).x() + 2, 1, 0));
        t.time.advance(3);
        assertTrue(topsyTurvy(e), "enemies in it: topsy-turvy");
        assertFalse(t.engine.tags().has(e, Tags.FRIENDLY_FIRE), "no friendly fire from the room");
        t.engine.cooldowns().start(p, "trickster_ab1", 100);
        t.time.advance(15);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertEquals(100 - 15 - 20, t.engine.cooldowns().remainingTicks(p, "trickster_ab1"), 1,
                "swipes still take 1s off in the moved room");
    }

    @Test
    void aMannequinCastingItTwiceTurnsTheRoomTopsyTurvyToo() throws IOException {
        setup();
        UUID m = t.spawn(30, 1, 0);                    // not a player (a dummy), cast as with /ae castas
        t.engine.loadouts().assign(m, "trickster");
        t.world.look(m, new Vec3(0, -1, 0));
        assertTrue(t.engine.activator().activate(m, "trickster_ab2").success(), "the room");
        t.world.look(m, new Vec3(1, 0, 0));
        assertTrue(t.engine.tags().has(m, "state.in_trick_room"), "standing in it the moment it's down");
        assertTrue(t.engine.activator().activate(m, "trickster_ab2", true).success(), "again right away: the recast");
        for (int i = 0; i < 10; i++) t.time.advance(1);
        assertTrue(t.engine.floors().of(m).stream().anyMatch(f -> f.lit()), "turned topsy-turvy: " + t.engine.floors().of(m));
    }

    @Test
    void theTopsyTurvyRoomWorksWithHerOutsideItToo() throws IOException {
        setup();
        UUID e = enemyAt(7, 0);
        room();
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 10; i++) t.time.advance(1);
        t.time.advance(6);                             // (0.3s in it)
        assertTrue(topsyTurvy(e), "in her moved room");
        t.world.move(p, new Vec3(40, 1, 0));           // she leaves it
        t.time.advance(20);
        assertTrue(topsyTurvy(e), "still topsy-turvy: the room works without her in it");
    }

    @Test
    void movedTheRoomIsTopsyTurvyForTheRestOfItsTime() throws IOException {
        setup();
        UUID e = enemyAt(7, 0);                        // outside the room now, inside it once it's moved
        UUID ally = t.spawn(6, 1, 1);
        t.world.team(ally, "blue");
        room();
        t.time.advance(2);
        assertFalse(topsyTurvy(e), "not before it moves");
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 8; i++) t.time.advance(1);
        t.time.advance(2);
        assertFalse(topsyTurvy(e), "not the moment it turns");
        t.time.advance(6);                             // (0.3s in it)
        assertTrue(topsyTurvy(e), "in the moved room: topsy-turvy");
        assertFalse(topsyTurvy(ally), "not her allies");
        assertEquals(0, t.damage(e), 1e-9, "no damage");
        assertTrue(t.render.cues.contains("trickster_room_flip"));
        var room = t.engine.floors().of(p).stream().filter(f -> "trick_room".equals(f.name())).findFirst().orElseThrow();
        var lit = t.engine.floors().of(p).stream().filter(f -> f.lit()).findFirst().orElseThrow();
        assertEquals(room.center(), lit.center(), "lit right over the room");
        t.world.move(e, new Vec3(20, 1, 0));           // stepping out: it ends 0.3s later
        t.time.advance(2);
        assertTrue(topsyTurvy(e), "not at once");
        t.time.advance(6);
        assertFalse(topsyTurvy(e), "0.3s after leaving: gone");
        t.world.move(e, new Vec3(7, 1, 0));            // back in: it takes 0.3s again
        t.time.advance(3);
        assertFalse(topsyTurvy(e), "not the moment they step in");
        t.time.advance(6);
        assertTrue(topsyTurvy(e), "0.3s in it: inverted");
        t.time.advance(171);                           // 10s since the room went down
        assertTrue(t.engine.floors().of(p).isEmpty(), "both gone with the room");
        t.time.advance(8);
        assertFalse(topsyTurvy(e));
    }

    @Test
    void theRoomMovesOnlyOnceAndKeepsItsTenSeconds() throws IOException {
        setup();
        room();
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 8; i++) t.time.advance(1);
        Vec3 there = pos(p);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 8; i++) t.time.advance(1);
        assertEquals(there, pos(p), "once per room");
        t.time.advance(185);                           // 10s since the room went down
        assertTrue(t.engine.floors().of(p).isEmpty(), "gone when the first room would have gone");
        assertEquals(280, t.engine.cooldowns().remainingTicks(p, "trickster_ab2"), 15);
    }

    @Test
    void outsideTheRoomTwoAgainDoesNothingUntilSheIsBackIn() throws IOException {
        setup();
        room();
        t.world.move(p, new Vec3(20, 1, 0));
        t.time.advance(10);                            // (off the floor long enough to lose its status)
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 8; i++) t.time.advance(1);
        assertEquals(new Vec3(20, 1, 0), pos(p), "outside: no dash");
        assertEquals(0.5, t.engine.floors().of(p).get(0).center().x(), 1e-9, "and the room stays where it was");
        t.world.move(p, new Vec3(0, 1, 0));
        t.time.advance(3);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 8; i++) t.time.advance(1);
        assertEquals(4, pos(p).x(), 0.6, "back in: it works");
    }

    @Test
    void rootedTwoAgainDoesNothing() throws IOException {
        setup();
        room();
        t.engine.statuses().apply(p, "root", 40, null);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        for (int i = 0; i < 8; i++) t.time.advance(1);
        assertEquals(0.5, t.engine.floors().of(p).get(0).center().x(), 1e-9);
        assertEquals(0, pos(p).x(), 0.05);
    }

    // ---- 3: Confuse Ray ---------------------------------------------------------------------------

    private boolean confused(UUID id) { return t.engine.tags().has(id, Tags.CONFUSED); }

    @Test
    void theSlowBeamConfusesTheFirstEnemyItHits() throws IOException {
        setup();
        UUID e = enemyAt(10, 0);
        UUID behind = enemyAt(12, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertTrue(t.render.cues.contains("trickster_topsy_cast"));
        t.time.advance(8);
        assertEquals(0, t.damage(e), 1e-9, "slow: not there yet");
        untilHurt(e);
        assertEquals(6.8, t.damage(e), 1e-6, "20% of 34 (no spell power)");
        assertTrue(confused(e), "confused");
        assertTrue(t.engine.tags().has(e, Tags.FRIENDLY_FIRE), "their attacks can hit their own team");
        assertTrue(t.render.loops.contains("trickster_confused"), "for everyone to see");
        assertFalse(confused(behind), "only the first it hits");
        t.time.advance(65);
        assertFalse(confused(e), "for 3s");
    }

    @Test
    void theBeamReachesSixteenBlocks() throws IOException {
        setup();
        UUID near = enemyAt(15, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(40);
        assertTrue(confused(near));
        setup();
        UUID far = enemyAt(18, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(60);
        assertFalse(confused(far), "out of range");
        assertEquals(0, t.damage(far), 1e-9);
    }

    @Test
    void confusedTheyTurnOnTheirClosestTeammateRunAtThemAndAttackFaster() throws IOException {
        setup();
        UUID e = enemyAt(5, 0);
        t.engine.loadouts().assign(e, "trickster");    // (a swipe to hit with)
        UUID mate = enemyAt(9, 0);                     // their teammate, ahead of them
        UUID farMate = enemyAt(5, 20);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        untilHurt(e);
        assertEquals(1.5, t.engine.stats().attackSpeedMultiplier(e), 1e-9, "+50% attack speed");
        double before = pos(e).x();
        t.time.advance(30);
        assertTrue(t.world.facing.getOrDefault(e, new Vec3(1, 0, 0)).x() > 0.9, "facing the closest one (already were)");
        assertTrue(pos(e).x() > before + 1, "running at them: " + pos(e));
        assertTrue(t.damage(mate) > 0, "and attacking them");
        assertTrue(t.world.swings.contains(e), "(swinging)");
        assertEquals(0, t.damage(farMate), 1e-9, "the closest only");
        assertEquals(0, t.damage(p), 1e-9, "not her");
    }

    @Test
    void confusedTheirBasicAttacksAndOneDealHalfDamage() throws IOException {
        setup();
        UUID e = enemyAt(5, 0);
        t.engine.loadouts().assign(e, "trickster");    // swipes for 34
        UUID mate = enemyAt(7, 0);
        t.engine.statuses().apply(e, "trickster_confused", 60, p);
        t.time.advance(1);
        assertEquals(17, t.damage(mate), 1e-6, "a swipe at half damage");
        t.engine.loadouts().activate(e, Slots.ABILITY_1);
        for (int i = 0; i < 4 && t.damage(mate) < 17 + 1; i++) t.time.advance(1);
        assertEquals(17 + 12.75, t.damage(mate), 1e-6, "Play Rough's slash at half damage too");
    }

    @Test
    void theyGoAfterTeammatesThenTheNeutralThenEnemiesThenHerLast() throws IOException {
        setup();
        UUID e = enemyAt(10, 0);
        UUID neutral = t.spawn(10, 1, 6);              // a camp monster: no team
        UUID foe = t.spawn(10, 1, -8);                 // one of her allies: their enemy
        t.world.team(foe, "blue");
        t.engine.statuses().apply(e, "trickster_confused", 60, p);
        t.time.advance(1);
        assertTrue(t.world.facing.get(e).z() > 0.9, "no teammate: the neutral one first");
        t.world.move(neutral, new Vec3(200, 1, 0));
        t.time.advance(1);
        assertTrue(t.world.facing.get(e).z() < -0.9, "then their enemy (not her, though she's nearer)");
        t.world.move(foe, new Vec3(-200, 1, 0));
        t.time.advance(1);
        assertTrue(t.world.facing.get(e).x() < -0.9, "and only then her");
    }

    @Test
    void noTeammateNearTheyGoAfterTheirClosestEnemy() throws IOException {
        setup();
        UUID e = enemyAt(6, 0);                        // alone on their team
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        untilHurt(e);
        double before = pos(e).x();
        t.time.advance(10);
        assertTrue(t.world.facing.get(e).x() < -0.9, "facing her: " + t.world.facing.get(e));
        assertTrue(pos(e).x() < before - 1, "running at her: " + pos(e));
        assertFalse(t.engine.tags().has(e, Tags.STUNNED));
    }

    @Test
    void nobodyInRangeAtAllTheyreStunned() throws IOException {
        setup();
        UUID e = enemyAt(100, 0);                      // nobody within 24 blocks of them
        t.engine.statuses().apply(e, "trickster_confused", 60, p);
        t.time.advance(2);
        assertTrue(t.engine.tags().has(e, Tags.STUNNED), "stunned");
        assertTrue(t.engine.tags().has(e, Tags.BLOCK_MOVE) && t.engine.tags().has(e, Tags.BLOCK_ABILITY));
        t.world.move(p, new Vec3(95, 1, 0));           // someone comes near: they go after her
        t.time.advance(2);
        assertFalse(t.engine.tags().has(e, Tags.STUNNED), "not stunned any more");
        t.world.move(p, new Vec3(0, 1, 0));
        t.time.advance(2);
        assertTrue(t.engine.tags().has(e, Tags.STUNNED));
        t.time.advance(60);
        assertFalse(confused(e));
        assertFalse(t.engine.tags().has(e, Tags.STUNNED), "and the stun goes with the confusion");
    }

    @Test
    void aConfusedCrossbowIsDrawnAndFiredForThem() throws IOException {
        setup();
        UUID e = enemyAt(5, 0);
        t.engine.loadouts().assign(e, "alchemist");
        UUID mate = enemyAt(12, 0);                    // their teammate, down the line of their crossbow
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        untilHurt(e);
        assertFalse(t.engine.quivers().isLoaded(e), "(nothing loaded)");
        for (int i = 0; i < 60 && t.damage(mate) == 0; i++) t.time.advance(1);
        assertTrue(t.damage(mate) > 0, "drawn, loaded and fired at their teammate");
    }

    @Test
    void confusionIsCrowdControl() throws IOException {
        setup();
        UUID e = enemyAt(5, 0);
        t.engine.tags().grant(e, Tags.UNSTOPPABLE);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        untilHurt(e);
        assertTrue(t.damage(e) > 0, "it still hits");
        assertFalse(confused(e), "the unstoppable keep their heads");
    }

    @Test
    void confuseRayIsTwentyPercentBaseDamageAndNinetyPercentSpellPower() throws IOException {
        setup();
        t.load(java.util.Map.of("statuses", java.util.Map.of(
                "test_blade", java.util.Map.of("damage_dealt", 1.5),
                "test_tome", java.util.Map.of("spell_power", 30))));
        UUID e = enemyAt(5, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        untilHurt(e);
        assertEquals(6.8, t.damage(e), 1e-6, "no spell power: 20% of 34 only");
        t.time.advance(70);                            // (the confusion over: they stand still again)
        t.world.move(e, new Vec3(5, 1, 0));
        t.engine.statuses().apply(p, "test_tome", 0, p);        // 30 spell power
        t.engine.statuses().apply(p, "test_blade", 0, p);       // +50% damage: only the base part
        t.engine.cooldowns().clear(p, "trickster_ab3");
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        for (int i = 0; i < 40 && t.damage(e) < 7; i++) t.time.advance(1);
        assertEquals(6.8 + 6.8 * 1.5 + 27, t.damage(e), 1e-6, "20% of 34 (x1.5) + 90% of 30");
    }

    // ---- Ultimate: Switcheroo --------------------------------------------------------------------

    private void charged() {
        t.engine.ultCharge().configure(true, 5, 2, 0);
        t.engine.ultCharge().set(p, 100);
    }

    /** Time goes on until the swap is over (at most 80 ticks). */
    private void untilLanded() {
        for (int i = 0; i < 80 && t.engine.instances().isRunning(p, "trickster_ult"); i++) t.time.advance(1);
    }

    @Test
    void switcherooFliesThemBothToEachOthersSpot() throws IOException {
        setup();
        charged();
        UUID e = enemyAt(10, 0);
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.time.advance(14);                            // about halfway (10 blocks at 0.35 a tick: 29 ticks)
        assertTrue(pos(p).y() > 1.5 && pos(e).y() > 1.5, "both floating: " + pos(p) + " " + pos(e));
        assertTrue(pos(p).x() > 2 && pos(p).x() < 8, "on her way: " + pos(p));
        assertTrue(Math.abs(pos(p).z() - pos(e).z()) > 1, "passing each other, not through each other");
        untilLanded();
        assertEquals(new Vec3(10, 1, 0), pos(p), "she lands exactly where they stood...");
        assertEquals(new Vec3(0, 1, 0), pos(e), "...and they where she stood");
        assertEquals(0, t.engine.ultCharge().of(p), 1e-9, "spent");
        assertTrue(t.render.cues.contains("trickster_switch_land"));
    }

    @Test
    void itsSlowTheFartherTheLonger() throws IOException {
        setup();
        charged();
        enemyAt(14, 0);
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.time.advance(30);
        assertTrue(t.engine.instances().isRunning(p, "trickster_ult"), "14 blocks: still flying after 1.5s");
        untilLanded();
        assertEquals(14, pos(p).x(), 1e-9);
    }

    @Test
    void meanwhileNeitherCanActOrBeTargetedButDamageStillLands() throws IOException {
        setup();
        charged();
        UUID e = enemyAt(10, 0);
        t.engine.loadouts().assign(e, "trickster");
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.time.advance(5);
        for (UUID id : List.of(p, e)) {
            assertTrue(t.engine.tags().has(id, Tags.UNTARGETABLE), "untargetable");
            assertFalse(t.engine.loadouts().activate(id, Slots.ABILITY_1).success(), "can't act");
            assertFalse(t.engine.loadouts().activate(id, Slots.PRIMARY).success(), "not even a swipe");
        }
        t.engine.statuses().apply(e, "burn", 40, null);  // damage over time still ticks
        t.time.advance(20);
        assertTrue(t.damage(e) > 0, "damage over time still hurts");
        untilLanded();
        for (UUID id : List.of(p, e)) {
            assertFalse(t.engine.tags().has(id, Tags.UNTARGETABLE), "landed: back to normal");
            assertFalse(t.engine.tags().has(id, Tags.BLOCK_ABILITY));
        }
        t.time.advance(15);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
    }

    @Test
    void withNobodyInSightNothingHappensAndTheChargeIsKept() throws IOException {
        setup();
        charged();
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        assertEquals(100, t.engine.ultCharge().of(p), 1e-9);
        assertEquals(new Vec3(0, 1, 0), pos(p));
        assertTrue(t.render.privateCues.stream().anyMatch(c -> "trickster_switch_fail".equals(c[0])), "only she sees the fizzle");
    }

    @Test
    void someoneWhoCantBeMovedKeepsTheirSpotAndSheKeepsHerCharge() throws IOException {
        setup();
        charged();
        UUID e = enemyAt(10, 0);
        t.engine.tags().grant(e, Tags.BLOCK_DISPLACE);
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.time.advance(40);
        assertEquals(new Vec3(10, 1, 0), pos(e));
        assertEquals(new Vec3(0, 1, 0), pos(p));
        assertEquals(100, t.engine.ultCharge().of(p), 1e-9);
        assertTrue(told.contains("They can't be moved."));
    }

    @Test
    void rootedNoSwitcheroo() throws IOException {
        setup();
        charged();
        enemyAt(10, 0);
        t.engine.statuses().apply(p, "root", 40, null);
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertEquals(100, t.engine.ultCharge().of(p), 1e-9);
    }
}
