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

/** The Essence Reaver kit (fixture snapshot). The Reaver stands at x=0 facing +x. */
class EssenceReaverTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "essence_reaver");
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private void swing() {
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(12); // the swing cooldown
    }

    private double x() { return t.world.positionOf(new EntityTarget(p)).orElseThrow().position().x(); }

    // ---- Vital Overflow -------------------------------------------------------------------

    @Test
    void everyThirdSwingIsAWideCleaveThatHeals() throws IOException {
        setup();
        UUID front = enemyAt(2, 0);
        UUID wide = enemyAt(1, 2);               // ~63 degrees off: only the cleave reaches
        swing();
        swing();
        assertEquals(70, t.damage(front), 1e-9, "two normal swings");
        assertEquals(0, t.damage(wide), 1e-9);
        assertFalse(t.lifesteal.containsKey(p), "normal swings don't heal");

        swing();                                  // third: cleave
        assertEquals(70 + 45, t.damage(front), 1e-9);
        assertEquals(45, t.damage(wide), 1e-9, "the cleave is wider");
        assertEquals(90, t.lifesteal.get(p), 1e-9, "heals for all the damage it dealt");

        swing();                                  // counter reset: normal again
        assertEquals(70 + 45 + 35, t.damage(front), 1e-9);
    }

    @Test
    void onlySwingsThatHitChargeTheCleave() throws IOException {
        setup();
        swing();                                  // air
        swing();                                  // air
        swing();                                  // air: would have been the 3rd swing
        UUID front = enemyAt(2, 0);
        UUID wide = enemyAt(1, 2);
        assertEquals(0, t.damage(front), 1e-9);
        swing();                                  // hit 1
        assertEquals(0, t.damage(wide), 1e-9, "still a normal swing: the misses didn't count");
        swing();                                  // hit 2
        swing();                                  // now the cleave
        assertEquals(35 + 35 + 45, t.damage(front), 1e-9);
        assertEquals(45, t.damage(wide), 1e-9, "the cleave");
    }

    @Test
    void aWhiffInBetweenDoesntResetTheCharge() throws IOException {
        setup();
        UUID front = enemyAt(2, 0);
        swing();                                  // hit 1
        t.world.look(p, new Vec3(-1, 0, 0));
        swing();                                  // air
        t.world.look(p, new Vec3(1, 0, 0));
        swing();                                  // hit 2
        swing();                                  // cleave
        assertEquals(35 + 35 + 45, t.damage(front), 1e-9);
        assertTrue(t.lifesteal.containsKey(p), "the cleave healed");
    }

    // ---- Essence Absorption ---------------------------------------------------------------

    @Test
    void attachesThenRecallDamagesPullsAndHeals() throws IOException {
        setup();
        UUID target = enemyAt(8, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        for (int i = 0; i < 20 && t.damage(target) == 0; i++) t.time.advance(1);
        assertEquals(30, t.damage(target), 1e-9, "attached");
        assertTrue(t.engine.instances().awaitingRecast(p, "essence_absorption"), "recast available (icon glints)");
        assertTrue(t.render.loops.contains("essence_mark"));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "essence_absorption"), "no cooldown while attached");

        t.time.advance(20);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "recall");
        assertEquals(70, t.damage(target), 1e-9, "damaged again");
        assertEquals(40, t.lifesteal.get(p), 1e-9, "healed for it");
        assertTrue(t.knockbackVec.get(target).x() < 0, "pulled toward the Reaver");
        assertEquals(200, t.engine.cooldowns().remainingTicks(p, "essence_absorption"), "cooldown starts on recall");
        assertFalse(t.render.loops.contains("essence_mark"), "mark gone");
    }

    @Test
    void theRecallWindowRunsOut() throws IOException {
        setup();
        UUID target = enemyAt(8, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        for (int i = 0; i < 20 && t.damage(target) == 0; i++) t.time.advance(1);
        t.time.advance(100);
        assertFalse(t.engine.instances().awaitingRecast(p, "essence_absorption"));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "essence_absorption") > 0);
        assertEquals(30, t.damage(target), 1e-9, "no free recall");
    }

    @Test
    void aMissGoesOnCooldownWithoutARecall() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(45);
        assertEquals(0, t.engine.instances().count());
        assertTrue(t.engine.cooldowns().remainingTicks(p, "essence_absorption") > 0);
    }

    // ---- Lifeline Step --------------------------------------------------------------------

    @Test
    void dashesThroughThenTheEchoStunsAlongThePath() throws IOException {
        setup();
        UUID onPath = enemyAt(4, 0);
        UUID aside = enemyAt(4, 3);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(8);
        assertTrue(x() > 6, "went straight through them, x=" + x());
        assertFalse(t.engine.tags().has(onPath, Tags.STUNNED), "the echo hasn't come yet");
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "free to act right after the dash");

        for (int i = 0; i < 30 && !t.engine.tags().has(onPath, Tags.STUNNED); i++) t.time.advance(1);
        assertTrue(t.engine.tags().has(onPath, Tags.STUNNED), "the echo stunned them");
        assertFalse(t.engine.tags().has(aside, Tags.STUNNED), "only along the path");
        assertEquals("echo_path", t.render.lines.get(0)[0]);
    }

    @Test
    void theEchoConnectsToWhereSheIsNowNotWhereTheDashEnded() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(8); // dash over, ~8 blocks along +x
        t.world.move(p, new Vec3(8, 1, 6)); // she walked off to the side before the echo
        UUID onNewLine = enemyAt(4, 3);    // halfway along start (0,0) -> her now (8,6)
        UUID onOldPath = enemyAt(6, 0);    // on the dash path, but not on the new line
        for (int i = 0; i < 30 && !t.engine.tags().has(onNewLine, Tags.STUNNED); i++) t.time.advance(1);
        assertTrue(t.engine.tags().has(onNewLine, Tags.STUNNED), "the line runs to where she is now");
        assertFalse(t.engine.tags().has(onOldPath, Tags.STUNNED), "not along the old dash path");
        Vec3 drawnTo = (Vec3) t.render.lines.get(0)[2];
        assertEquals(new Vec3(8, 1, 6), drawnTo, "the visual ends on her too");
    }

    // ---- Meditation -----------------------------------------------------------------------

    @Test
    void healsResistsAndLocksForThreeSeconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        for (String tag : new String[]{Tags.RESISTANT, Tags.BLOCK_MOVE, Tags.BLOCK_ABILITY}) {
            assertTrue(t.engine.tags().has(p, tag), tag);
        }
        assertTrue(t.render.loops.contains("meditation"), "aura on");
        assertEquals("blocked:" + Tags.BLOCK_ABILITY, t.engine.loadouts().activate(p, Slots.PRIMARY).reason(), "can't attack");

        t.time.advance(60);
        assertEquals(144, t.healed.get(p), 1e-9, "12 heals of 12");
        assertFalse(t.engine.tags().has(p, Tags.RESISTANT));
        assertFalse(t.render.loops.contains("meditation"), "aura off");
    }

    @Test
    void pressingItAgainStopsItEarly() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(10);                       // heals at 0, 5, 10
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE), "free again");
        t.time.advance(30);
        assertEquals(36, t.healed.get(p), 1e-9, "no more healing after stopping");
    }

    @Test
    void holdingTheKeyDoesntStopIt() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(3);
        t.engine.loadouts().activate(p, Slots.ABILITY_3, false); // key repeat
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE));
    }

    @Test
    void aStunBreaksIt() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(5);
        t.engine.statuses().apply(p, "stun", 20, null);
        t.time.advance(30);
        assertFalse(t.engine.tags().has(p, Tags.RESISTANT));
        assertEquals(24, t.healed.get(p), 1e-9);
    }
}
