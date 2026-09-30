package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Whisperer (fixtures/whisperer.yml): 200 HP, 10 armor, base damage 50. They stand at the origin on a
 * floor, looking +x, team blue; the enemy stands 2.5 blocks in front, team red (no sheet: 200 HP, no armor).
 * The fake world doesn't track health: tests set {@code world.healthFraction} to say how hurt someone is.
 */
class WhispererTest {

    private TestEngine t;
    private UUID p;
    private UUID enemy;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "whisperer");
        enemy = foe(2.5, 0);
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success()); }

    private void hurt(UUID id, double fraction) { t.world.healthFraction.put(id, fraction); }

    // ---- Primary: Sickle Rake ------------------------------------------------------------------------

    @Test
    void theRakeHitsTheEnemyInFrontWithinThreeBlocks() throws IOException {
        setup();
        use(Slots.PRIMARY);
        assertEquals(50, t.damage(enemy), 1e-9, "100% of 50 = 5 HP");
        assertEquals(12, t.engine.cooldowns().remainingTicks(p, "whisperer_primary"), "0.6s");
        t.world.move(enemy, new Vec3(5, 1, 0));
        t.time.advance(12);
        use(Slots.PRIMARY);
        assertEquals(50, t.damage(enemy), 1e-9, "out of reach");
    }

    // ---- Passive: Heard in Dreams --------------------------------------------------------------------

    @Test
    void woundedEnemiesNearbyAreHeard() throws IOException {
        setup();
        UUID healthy = foe(6, 0);
        UUID far = foe(40, 0);
        UUID friend = t.spawn(5, 1, 5);
        t.world.team(friend, "blue");
        hurt(enemy, 0.3);
        hurt(healthy, 0.5);
        hurt(far, 0.1);
        hurt(friend, 0.1);
        t.time.advance(5);
        assertEquals(List.of(enemy), t.engine.hearing().heard(p), "below 40%, within 30 blocks, an enemy");
        hurt(healthy, 0.2);
        t.time.advance(5);
        assertEquals(List.of(enemy, healthy), t.engine.hearing().heard(p), "nearest first");
        hurt(enemy, 1.0);
        hurt(healthy, 1.0);
        t.time.advance(5);
        assertTrue(t.engine.hearing().heard(p).isEmpty(), "healed up: not heard any more");
    }

    @Test
    void movingTowardAHeardEnemyIsFaster() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(10, 1, 0));
        hurt(enemy, 0.3);
        t.world.walk(p, new Vec3(1, 0, 0));
        t.time.advance(5);
        assertEquals(1.15, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "+15% toward them");
        t.world.walk(p, new Vec3(-1, 0, 0));
        t.time.advance(10);
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "away: no");
        t.world.move(enemy, new Vec3(20, 1, 0));
        t.world.walk(p, new Vec3(1, 0, 0));
        t.time.advance(10);
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "heard, but past 15 blocks: no");
    }

    // ---- Ability 1: Dream Step -------------------------------------------------------------------------

    @Test
    void dreamStepBlinksAheadAndBlindsEnemiesWhereYouComeOut() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(9, 1, 1));
        UUID back = foe(-3, 0);
        use(Slots.ABILITY_1);
        t.time.advance(4);
        assertEquals(8, pos(p).x(), 0.5, "8 blocks ahead");
        assertTrue(t.engine.tags().has(enemy, Tags.BLINDED), "near where you came out");
        assertFalse(t.engine.tags().has(back, Tags.BLINDED));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "whisperer_ab1"), "the rift is still open");
        assertTrue(t.engine.instances().awaitingRecast(p, "whisperer_ab1"), "the icon glints");
    }

    @Test
    void pressingAgainStepsBackIntoTheRift() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        t.time.advance(10);
        use(Slots.ABILITY_1);
        assertEquals(0, pos(p).x(), 0.5, "back where you left");
        assertEquals(200, t.engine.cooldowns().remainingTicks(p, "whisperer_ab1"), "the cooldown starts now");
    }

    @Test
    void theRiftClosesAfterThreeSeconds() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        t.time.advance(62);
        assertFalse(t.engine.instances().awaitingRecast(p, "whisperer_ab1"));
        assertTrue(t.render.cues.contains("rift_close"));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "whisperer_ab1") > 190);
    }

    // ---- Ability 2: Binding Whisper --------------------------------------------------------------------

    @Test
    void heldForTwoSecondsTheWhisperCursesThem() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(6, 1, 0));
        use(Slots.ABILITY_2);
        assertEquals(240, t.engine.cooldowns().remainingTicks(p, "whisperer_ab2"));
        assertEquals(0.8, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "20% slower while charging");
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "can still attack meanwhile");
        t.time.advance(39);
        assertFalse(t.engine.tags().has(enemy, "state.darkness"), "still charging");
        t.time.advance(2);
        assertTrue(t.engine.tags().has(enemy, "state.darkness"));
        assertTrue(t.engine.tags().has(enemy, Tags.BLINDED), "and blinded");
        assertTrue(t.engine.tags().has(enemy, "state.withered"));
        assertEquals(1.0, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "the slow is gone");
        t.time.advance(81);
        assertEquals(40, t.damage(enemy), 1e-9, "the wither: 4 HP over 4s");
        assertTrue(t.render.lines.stream().anyMatch(l -> l[0].equals("whisper_tether_4")), "brightest at the end");
    }

    @Test
    void runningPastNineBlocksBreaksItAndRefundsFortyPercent() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(6, 1, 0));
        use(Slots.ABILITY_2);
        t.time.advance(10);
        t.world.move(enemy, new Vec3(9.5, 1, 0));
        t.time.advance(1);
        assertFalse(t.engine.instances().isRunning(p, "whisperer_ab2"), "broken");
        t.time.advance(50);
        assertFalse(t.engine.tags().has(enemy, "state.darkness"));
        assertEquals(240 - 61 - 96, t.engine.cooldowns().remainingTicks(p, "whisperer_ab2"), 1, "40% back");
    }

    @Test
    void aWallBreaksItAfterHalfASecond() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(6, 1, 0));
        use(Slots.ABILITY_2);
        t.world.box(3.8, 4.2, -2, 2, 5);                  // a wall between them
        t.time.advance(10);
        assertTrue(t.engine.instances().isRunning(p, "whisperer_ab2"), "0.5s grace");
        t.time.advance(2);
        assertFalse(t.engine.instances().isRunning(p, "whisperer_ab2"), "out of sight too long");
    }

    @Test
    void beingSilencedBreaksIt() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        t.time.advance(5);
        t.engine.statuses().apply(p, "silence", 40, enemy);
        t.time.advance(1);
        assertFalse(t.engine.instances().isRunning(p, "whisperer_ab2"));
        assertTrue(t.render.cues.contains("whisper_snap"));
    }

    @Test
    void noOneToTetherSpendsNothing() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(8, 1, 0));             // past the 7-block reach
        use(Slots.ABILITY_2);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "whisperer_ab2"));
    }

    // ---- Ability 3: Chorus Shade -----------------------------------------------------------------------

    private me.mephisto.ability_engine.engine.testkit.FakeRenderer.FakeBody shade() {
        return t.render.bodies.get(t.render.bodies.size() - 1);
    }

    @Test
    void itFliesTwelveBlocksThenHoversThreeSecondsThenFades() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(40, 1, 40));           // far away, healthy: not a target
        use(Slots.ABILITY_3);
        t.time.advance(35);                                 // 12 blocks at 0.4 a tick: 30 ticks
        double x = shade().at.x();
        assertEquals(12, x, 0.8, "stopped at its max distance: " + shade().at);
        t.time.advance(40);
        assertEquals(x, shade().at.x(), 1e-9, "hovering");
        assertTrue(t.engine.instances().isRunning(p, "whisperer_ab3"));
        t.time.advance(25);                                 // 3s of hovering
        assertFalse(t.engine.instances().isRunning(p, "whisperer_ab3"), "gone");
        assertTrue(t.render.cues.contains("shade_dissolve"));
    }

    @Test
    void anEnemyWalkingUpToTheHoveringShadeGetsChased() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(40, 1, 40));
        use(Slots.ABILITY_3);
        t.time.advance(40);                                 // hovering at x=12
        t.world.move(enemy, new Vec3(12, 1, 5));            // within 6 blocks of it
        t.time.advance(20);
        assertEquals(60, t.damage(enemy), 1e-9, "it homed on them");
    }

    @Test
    void itHomesOnAHeardEnemyHoweverFar() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(10, 1, 20));           // way off to the side, far past 6 blocks
        hurt(enemy, 0.3);                                   // heard
        t.time.advance(5);
        use(Slots.ABILITY_3);
        boolean darkened = false;
        for (int i = 0; i < 150 && t.damage(enemy) == 0; i++) {
            t.time.advance(1);
            darkened |= t.engine.tags().has(enemy, "state.darkness") && t.engine.tags().has(enemy, Tags.BLINDED);
        }
        assertEquals(60, t.damage(enemy), 1e-9, "found them past its 12 blocks: homing doesn't count distance");
        assertTrue(darkened, "Darkness and Blindness around the shade as it came");
    }

    @Test
    void terrainDoesntStopIt() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(8, 1, 0));
        t.world.box(3.8, 4.2, -3, 3, 6);                    // a wall between them
        use(Slots.ABILITY_3);
        t.time.advance(25);
        assertEquals(60, t.damage(enemy), 1e-9, "through the wall");
    }

    @Test
    void enemiesCanKillIt() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(40, 1, 40));
        use(Slots.ABILITY_3);
        t.time.advance(5);
        shade().kill();
        t.time.advance(1);
        assertFalse(t.engine.instances().isRunning(p, "whisperer_ab3"), "slain");
        assertTrue(t.render.cues.contains("shade_dissolve"));
    }

    @Test
    void itExecutesBelowTenPercent() throws IOException {
        setup();
        hurt(enemy, 0.05);
        use(Slots.ABILITY_3);
        t.time.advance(10);
        assertEquals(60 + 200, t.damage(enemy), 1e-9, "6 HP, then all of their max HP");
        assertTrue(t.render.cues.contains("shade_execute"));
    }

    // ---- Ultimate: Into the Veil -------------------------------------------------------------------------

    /** An enemy ability: 100 damage to whoever it's aimed at (or 100 healing, for "mend"). */
    private void loadHelpers() {
        t.load(me.mephisto.ability_engine.engine.testkit.Yml.abilities(
                "punch", me.mephisto.ability_engine.engine.testkit.Yml.map("nodes", me.mephisto.ability_engine.engine.testkit.Yml.map(
                        "hit", me.mephisto.ability_engine.engine.testkit.Yml.map("type", "apply_effects",
                                "targets", me.mephisto.ability_engine.engine.testkit.Yml.map("type", "key", "key", "target"),
                                "affects", "all",
                                "effects", me.mephisto.ability_engine.engine.testkit.Yml.list(
                                        me.mephisto.ability_engine.engine.testkit.Yml.map("id", "damage", "amount", 100)))))));
    }

    private void punch(UUID from, UUID to) {
        t.engine.activator().activateOnId(from, "punch", new EntityTarget(to), java.util.Map.of());
    }

    @Test
    void intoTheVeilWarnsThenIsolatesTheTwo() throws IOException {
        setup();
        loadHelpers();
        UUID enemyFriend = foe(2, 3);                       // on the target's side
        UUID myFriend = t.spawn(-2, 1, 0);
        t.world.team(myFriend, "blue");
        use(Slots.ABILITY_2);                               // (a cooldown to see reset)
        use(Slots.ULTIMATE);
        assertTrue(t.engine.tags().has(enemy, "state.darkness"), "the warning pulse");
        assertFalse(t.engine.veils().isVeiled(p), "not yet: 0.5s warning");
        t.time.advance(10);
        assertTrue(t.engine.veils().isVeiled(p));
        assertEquals(enemy, t.engine.veils().partnerOf(p).orElseThrow());
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "whisperer_ab2"), "cooldowns reset");

        double mine = t.damage(p);
        punch(enemyFriend, p);
        assertEquals(mine, t.damage(p), 1e-9, "their friend can't reach you");
        double theirs = t.damage(enemy);
        punch(myFriend, enemy);
        assertEquals(theirs, t.damage(enemy), 1e-9, "nor can yours reach them");
        punch(enemy, p);
        assertTrue(t.damage(p) > mine, "but the two of you can hit each other");
        use(Slots.PRIMARY);
        assertEquals(theirs + 50 * 1.2, t.damage(enemy), 1e-9, "+20% damage");
        double friendBefore = t.damage(enemyFriend);
        t.world.move(enemyFriend, new Vec3(1.5, 1, 0));    // right in front of you
        t.time.advance(12);
        use(Slots.PRIMARY);
        assertEquals(friendBefore, t.damage(enemyFriend), 1e-9, "you can't touch anyone outside either");

        t.time.advance(140);
        assertFalse(t.engine.veils().isVeiled(p), "7s: over");
        assertFalse(t.engine.veils().isVeiled(enemy));
        punch(enemyFriend, p);
        assertTrue(t.damage(p) > mine + 100 * 100 / 110.0 - 1e-6, "back in the fight");
    }

    @Test
    void winningTheDuelEndsItAtOnceAndHeals() throws IOException {
        setup();
        use(Slots.ULTIMATE);
        t.time.advance(12);
        assertTrue(t.engine.veils().isVeiled(p));
        t.world.kill(enemy);
        t.time.advance(1);
        assertFalse(t.engine.veils().isVeiled(p), "they died: it ends");
        assertEquals(80, t.healed.get(p), 1e-9, "8 HP");
        assertFalse(t.engine.statuses().has(p, "veil_fury"));
    }

    @Test
    void nobodyInFrontSpendsNothing() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(0, 1, 25));
        use(Slots.ULTIMATE);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "whisperer_ult1"));
        t.time.advance(20);
        assertFalse(t.engine.veils().isVeiled(p));
    }
}
