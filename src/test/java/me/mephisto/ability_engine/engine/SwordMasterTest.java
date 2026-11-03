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
    void aBladeFromTheSideStillHitsWhatHeAimsAt() throws IOException {
        setup();
        UUID e = enemyAt(4, 0);
        t.engine.loadouts().activate(p, Slots.PRIMARY);              // from his left
        t.time.advance(10);
        assertTrue(t.damage(e) > 0, "converges on the crosshair");
        t.time.advance(10);
        t.engine.loadouts().activate(p, Slots.PRIMARY);              // from his right
        t.time.advance(10);
        assertTrue(t.damage(e) > BASE, "both sides");
    }

    @Test
    void twinThrustStabsWithBothBlades() throws IOException {
        setup();
        UUID near = enemyAt(3, 0), far = enemyAt(7, 0);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "nothing else meanwhile");
        t.time.advance(20);
        assertEquals(BASE * 0.9 + BASE * 0.5, t.damage(near), 1e-6, "both blades");
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

    @Test
    void theFallingBladeIsTheOneUpNextAndTheOtherAttacksMeanwhile() throws IOException {
        setup();
        UUID there = enemyAt(8, 0), shot = enemyAt(2.5, 3);
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(22);
        assertTrue(t.engine.statuses().has(p, "sm_neth_planted"), "netherite was up next");
        assertEquals(BASE * 1.5, t.damage(there), 1e-6, "150% base damage around it");

        t.world.look(p, new Vec3(2.5, 0, 3).normalize());
        for (int i = 0; i < 2; i++) {                                // gold both times while netherite is planted
            assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
            t.time.advance(i == 0 ? 16 : 20);
        }
        assertEquals(BASE * 0.6 * 2, t.damage(shot), 1e-6, "gold, twice");
        assertEquals(16, healed(), 1e-6);
        assertFalse(t.engine.statuses().has(p, "sm_neth_planted"), "back after 2s");
    }

    @Test
    void theGoldBladeFallsWhenItsUpNext() throws IOException {
        setup();
        UUID there = enemyAt(8, 0);
        t.engine.statuses().apply(p, "sm_gold_next", 0, p);
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.engine.targeting().confirm(p);
        t.time.advance(22);
        assertTrue(t.engine.statuses().has(p, "sm_gold_planted"));
        assertEquals(BASE * 0.6, t.damage(there), 1e-6, "magic, 60% base damage");
        assertEquals(30, healed(), 1e-6, "it heals him");
    }

    @Test
    void aThousandCutsStabsEveryoneInsideAndHoldsHisAbilities() throws IOException {
        setup();
        UUID in = enemyAt(8, 2), out = enemyAt(8, 9);
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.world.look(p, new Vec3(1, 0, 0));
        t.time.advance(5);
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "no abilities meanwhile");
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "nor basic attacks");
        t.time.advance(130);
        double pair = BASE * 0.3 + BASE * 0.15;
        assertEquals(pair * 12, t.damage(in), 1e-6, "a pair every 0.5s for 6s");
        assertEquals(0, t.damage(out), 1e-9);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "over: abilities again");
    }
}
