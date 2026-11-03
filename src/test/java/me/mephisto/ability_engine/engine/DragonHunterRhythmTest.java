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
 * The reworked Dragon Hunter (Hunter's Rhythm, Clean Cut), from the shipped dragon_hunter.yml (the old kit stays as the
 * fixture, DragonHunterTest). He stands at x=0 on a floor at y=0, facing +x; enemies are on team red.
 */
class DragonHunterRhythmTest {

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
        t.engine.loadouts().assign(p, "dragon_hunter");
        t.time.advance(1);
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    /** A real target with health that can die (design HP). */
    private UUID mortalAt(double x, double z, double hp, double max) {
        UUID e = enemyAt(x, z);
        t.world.health.put(e, hp);
        t.world.maxHealth.put(e, max);
        t.world.healthFraction.put(e, hp / max);
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private int combo() { return t.engine.statuses().find(p, "dh_combo").map(s -> s.stacks()).orElse(0); }

    private void swing() {
        t.time.advance(25); // (past the swing's cooldown)
        t.engine.loadouts().activate(p, Slots.PRIMARY);
    }

    // ---- Hunter's Rhythm ----

    @Test
    void anAbilityThenASwingIsALevel() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertEquals(0, combo(), "the first ability only opens the window");
        t.time.advance(10);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertEquals(1, combo(), "a swing in the window: a level");
        swing();
        assertEquals(1, combo(), "the swing closed it: another swing is nothing");
    }

    @Test
    void abilityAfterAbilityIsALevelAndOpensAnother() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(5);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertEquals(1, combo());
        t.time.advance(5);
        t.engine.loadouts().activate(p, Slots.PRIMARY);   // (Rising Fang made it ready)
        assertEquals(2, combo(), "still open after an ability");
        assertEquals(1.04 * 1.04, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "+4% speed a level");
    }

    @Test
    void theWindowClosesAfterThreeSeconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(65);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertEquals(0, combo(), "too late");
    }

    @Test
    void levelsWearOffAfterSixSeconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(2);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertEquals(1, combo());
        t.time.advance(125);
        assertEquals(0, combo());
    }

    // ---- Rising Fang, the mark and the bleed ----

    @Test
    void risingFangKnocksUpMarksAndResetsCrescentCleave() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        assertTrue(t.engine.cooldowns().remainingTicks(p, "dh_ab1") > 0);
        t.time.advance(2);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "the swing is ready at once");
        assertTrue(pos(e).y() > 1.5, "knocked up: " + pos(e));
        assertTrue(t.engine.statuses().has(e, "dh_mark"), "marked");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "dh_ab1"), "Crescent Cleave is ready again");
        assertFalse(t.engine.statuses().has(e, "dh_bleed"), "not bleeding yet");
    }

    @Test
    void theNextHitOnAMarkedEnemyOpensABleed() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        t.world.move(e, new Vec3(2, 1, 0));
        t.time.advance(2);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());   // Crescent Cleave
        assertFalse(t.engine.statuses().has(e, "dh_mark"), "the mark is used up...");
        assertTrue(t.engine.statuses().has(e, "dh_bleed"), "...and they bleed");
    }

    @Test
    void aSwingSetsTheMarkOffToo() throws IOException {
        setup();
        UUID e = enemyAt(2, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        t.world.move(e, new Vec3(2, 1, 0));
        swing();
        assertTrue(t.engine.statuses().has(e, "dh_bleed"));
    }

    // ---- Clean Cut ----

    /** Five combo levels, with the abilities' cooldowns skipped (ab1 / ab3 / swing in turn). */
    private void fullCombo() {
        t.engine.loadouts().activate(p, Slots.ABILITY_1); // opens
        for (int i = 0; i < 5; i++) {
            t.time.advance(2);
            t.engine.cooldowns().clear(p, "dh_ab1");
            t.engine.loadouts().activate(p, Slots.ABILITY_1); // a level each, and a new window
        }
    }

    @Test
    void cleanCutStartsByItselfAtFiveLevels() throws IOException {
        setup();
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success(), "F alone: not ready");
        fullCombo();
        assertTrue(t.engine.instances().isRunning(p, "dh_ult"), "five: it started by itself");
        assertTrue(t.engine.targeting().keyConfirms(p), "picking a spot (F confirms too)");
    }

    @Test
    void cleanCutLeapsCutsAndExecutesTheLow() throws IOException {
        setup();
        UUID low = mortalAt(8, 0, 25, 200);            // 12.5%: below 15% after the cut too
        UUID healthy = mortalAt(8, 2, 2000, 2000);
        fullCombo();
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(12);
        assertTrue(t.damage(healthy) > 0, "the cut");
        assertFalse(t.world.isAlive(low), "executed");
        assertTrue(t.world.isAlive(healthy));
    }

    @Test
    void anExecuteBringsHimBackWithTheCooldownAndComboRefreshed() throws IOException {
        setup();
        mortalAt(8, 0, 25, 200);
        fullCombo();
        Vec3 from = pos(p);
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        t.engine.targeting().confirm(p);
        t.time.advance(25);
        assertEquals(from.x(), pos(p).x(), 0.5, "back where he cast it: " + pos(p));
        assertEquals(5, combo(), "his combo is full again");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "dh_ult"), "no cooldown");
        assertTrue(t.engine.instances().isRunning(p, "dh_ult"), "and he picks again");
        assertTrue(t.engine.targeting().keyConfirms(p));
    }

    @Test
    void noKillSpendsTheComboAndStartsTheCooldown() throws IOException {
        setup();
        mortalAt(8, 0, 2000, 2000);
        fullCombo();
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        t.engine.targeting().confirm(p);
        t.time.advance(25);
        assertFalse(t.engine.instances().isRunning(p, "dh_ult"));
        assertEquals(0, combo());
        assertTrue(t.engine.cooldowns().remainingTicks(p, "dh_ult") > 800, "45s");
        assertTrue(pos(p).x() > 5, "he stays where he cut: " + pos(p));
    }
}
