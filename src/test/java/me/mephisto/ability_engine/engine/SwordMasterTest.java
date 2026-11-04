package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SwordMaster, from the shipped swordmaster.yml (base damage 38, no spell power). He stands at x=0 on a floor at
 * y=0, facing +x; enemies are on team red.
 */
class SwordMasterTest {

    private static final double BASE = 38;

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
        t.engine.loadouts().assign(p, "swordmaster");
        t.time.advance(1);
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private double healed() { return t.healed.getOrDefault(p, 0.0); }

    private boolean goldNext() { return t.engine.tags().has(p, "swordmaster.gold_next"); }

    @Test
    void hisArmNeverSwings() throws IOException {
        setup();
        assertTrue(t.engine.tags().has(p, "state.no_swing"));
        assertTrue(t.engine.statuses().has(p, "sm_twin_blades"));
    }

    @Test
    void theBladesTakeTurnsOnOneCooldown() throws IOException {
        setup();
        UUID e = enemyAt(4, 0);
        assertFalse(goldNext(), "netherite first");
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(10);
        assertEquals(BASE, t.damage(e), 1e-6, "netherite: 100% base damage");
        assertEquals(0, healed(), 1e-9);
        assertTrue(goldNext(), "the gold blade next");
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "one cooldown for both blades");

        t.time.advance(10);
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(10);
        assertEquals(BASE + BASE * 0.6, t.damage(e), 1e-6, "gold: 60% base damage");
        assertEquals(8, healed(), 1e-6, "and a heal");
        assertFalse(goldNext(), "netherite again");
    }

    @Test
    void theBasicAttackReaches4AndAHalfBlocks() throws IOException {
        setup();
        UUID far = enemyAt(6, 0);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        t.time.advance(10);
        assertEquals(0, t.damage(far), 1e-9);
    }

    @Test
    void aThrustStrikesOnlyTheFirstInItsWay() throws IOException {
        setup();
        UUID first = enemyAt(2, 0), behind = enemyAt(4, 0);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        t.time.advance(10);
        assertEquals(BASE, t.damage(first), 1e-6);
        assertEquals(0, t.damage(behind), 1e-9, "a basic attack: one enemy");
    }

    @Test
    void twinThrustStabsWithBothBlades() throws IOException {
        setup();
        UUID near = enemyAt(3, 0), far = enemyAt(7, 0);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "nothing else meanwhile");
        t.time.advance(20);
        assertEquals(BASE * 0.5 + BASE * 0.4, t.damage(near), 1e-6, "both blades, true damage");
        assertEquals(0, t.damage(far), 1e-9, "4.5 blocks");
        assertEquals(8, healed(), 1e-6, "a heal per enemy hit");
    }

    @Test
    void bladeGuardHardensThenDashes() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        t.time.advance(2);
        assertTrue(t.engine.statuses().has(p, "sm_guarded"));
        t.time.advance(30);
        assertFalse(t.engine.statuses().has(p, "sm_guarded"));
        assertTrue(t.world.positionOf(new me.mephisto.ability_engine.engine.target.EntityTarget(p)).orElseThrow()
                .position().x() > 3, "dashed forward");
    }

    // ---- 3: Blade Stance ----

    private void waitForPrimary() {
        for (int i = 0; i < 40 && !t.engine.cooldowns().isReady(p, "sm_primary"); i++) t.time.advance(1);
    }

    @Test
    void bladeStanceSendsThreeDifferentWavesThenItsOver() throws IOException {
        setup();
        UUID foe = enemyAt(6, 0), beyond = enemyAt(9, 1);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertEquals(3, t.engine.statuses().find(p, "sm_stance").orElseThrow().stacks(), "three waves");
        assertTrue(t.engine.statuses().has(p, "sm_stance_haste"), "faster");

        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());       // 1: magic, and a heal
        t.time.advance(14);
        assertEquals(BASE * 0.6, t.damage(foe), 1e-6, "6 blocks: a wave reaches 7");
        assertEquals(0, t.damage(beyond), 1e-9, "9 blocks: out of its reach");
        assertEquals(12, healed(), 1e-6);
        assertEquals(2, t.engine.statuses().find(p, "sm_stance").orElseThrow().stacks());

        t.shields.put(foe, 40.0);
        waitForPrimary();
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());       // 2: shields break, physical
        t.time.advance(14);
        assertTrue(t.brokenShields.contains(foe), "its shield broken");
        assertEquals(BASE * 0.6 + BASE * 1.2, t.damage(foe), 1e-6);

        waitForPrimary();
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());       // 3: true damage
        t.time.advance(14);
        assertEquals(BASE * (0.6 + 1.2 + 0.7), t.damage(foe), 1e-6);
        assertFalse(t.engine.statuses().has(p, "sm_stance"), "three waves: over");

        waitForPrimary();
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());       // back to a thrust: 4.5 blocks
        t.time.advance(14);
        assertEquals(BASE * 2.5, t.damage(foe), 1e-6, "6 blocks away: out of a thrust's reach");
    }

    @Test
    void otherAbilitiesWorkInTheStance() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
    }

    // ---- ULTIMATE ----

    @Test
    void theUltimateReaches20BlocksWithBothBladesAndHoldsHisAbilities() throws IOException {
        setup();
        UUID foe = enemyAt(15, 0), beyond = enemyAt(25, 2);
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertTrue(t.engine.tags().has(p, "state.aim_highlight"), "who he'd reach glows for him");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "no abilities");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "basic attacks only");
        t.time.advance(12);
        assertEquals(BASE * 0.9 + BASE * 0.5, t.damage(foe), 1e-6, "15 blocks: both blades");
        assertEquals(8, healed(), 1e-6);
        assertEquals(0, t.damage(beyond), 1e-9);
        assertTrue(t.engine.statuses().has(p, "sm_cutting"), "20% faster attacks");
        t.time.advance(160);
        assertFalse(t.engine.tags().has(p, "swordmaster.cutting"), "8s: over");
        assertFalse(t.engine.tags().has(p, "state.aim_highlight"));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "abilities again");
    }
}
