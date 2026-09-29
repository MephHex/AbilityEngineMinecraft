package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Copper Golem (fixtures/copper_golem.yml): 320 HP, 45 armor, base damage 34. He stands at the origin on
 * a floor, looking +x, team blue; the enemy stands 2.5 blocks in front, team red (no sheet: 200 HP, no armor).
 */
class CopperGolemTest {

    private static final double ARMOR = 100 / 145.0;

    private TestEngine t;
    private UUID p;
    private UUID enemy;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.load(abilities("punch", map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "amount", 100)))))));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "copper_golem");
        enemy = foe(2.5, 0);
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    /** The enemy hits him for 100, as a spell (an ability slot) or a basic attack (primary). */
    private void hit(boolean spell) {
        assertTrue(t.engine.activator().activateOnId(enemy, "punch", new EntityTarget(p),
                Map.of("slot", spell ? Slots.ABILITY_1 : Slots.PRIMARY)).success());
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private boolean barrierReady() { return t.engine.wards().state(p).orElseThrow().ready(); }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success()); }

    // ---- Passive: Cuprous Might --------------------------------------------------------------------

    @Test
    void heIsBigger() throws IOException {
        setup();
        assertEquals(1.2, t.engine.stats().of(p).scale(), 1e-9);
    }

    @Test
    void theBarrierTakesHalfOfTheFirstHitThenRechargesOutOfCombat() throws IOException {
        setup();
        assertTrue(barrierReady());
        hit(true);
        assertEquals(100 * ARMOR * 0.5, t.damage(p), 1e-9, "half of the first hit (after armor)");
        assertFalse(barrierReady(), "broken");
        assertTrue(t.render.cues.contains("barrier_break"));
        hit(true);
        assertEquals(100 * ARMOR * 1.5, t.damage(p), 1e-9, "the next one lands in full");

        t.time.advance(99);
        assertFalse(barrierReady());
        t.time.advance(2);
        assertTrue(barrierReady(), "5s out of combat");
    }

    @Test
    void theBarrierDoesntStopDebuffs() throws IOException {
        setup();
        t.time.advance(1);
        t.engine.statuses().apply(p, "stun", 20, enemy);
        assertTrue(t.engine.tags().has(p, Tags.STUNNED));
        assertFalse(t.engine.tags().has(p, Tags.DEBUFF_IMMUNE));
        assertTrue(barrierReady(), "still there for the next hit");
    }

    @Test
    void enemyBasicAttacksOnHimCutHisCooldowns() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        long before = t.engine.cooldowns().remainingTicks(p, "golem_ab3");
        hit(false);                                          // a basic attack
        assertEquals(before - 10, t.engine.cooldowns().remainingTicks(p, "golem_ab3"), "0.5s off");
        hit(true);                                           // a spell: no
        assertEquals(before - 10, t.engine.cooldowns().remainingTicks(p, "golem_ab3"));
    }

    // ---- Ability 1: Groundbreaker ------------------------------------------------------------------

    @Test
    void theHookStunsWhoItHitsAndDragsThemToHim() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(12, 1, 0));
        use(Slots.ABILITY_1);
        t.time.advance(3);
        assertTrue(t.render.lines.stream().anyMatch(l -> l[0].equals("anchor_chain")), "a chain follows the pick");
        t.time.advance(7);
        assertEquals(34 * 0.9, t.damage(enemy), 1e-9);
        assertTrue(t.engine.tags().has(enemy, Tags.STUNNED));
        t.time.advance(15);
        assertEquals(0, pos(p).x(), 1e-9, "he stays where he is");
        assertEquals(1.5, pos(enemy).x(), 0.8, "they're dragged to just in front of him: " + pos(enemy));
        assertTrue(t.render.cues.contains("anchor_land"));
    }

    @Test
    void terrainJustStopsIt() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(30, 1, 30));            // out of the way
        t.world.look(p, new Vec3(8, -1, 0));                 // at the ground ahead
        use(Slots.ABILITY_1);
        t.time.advance(30);
        assertEquals(0, pos(p).x(), 1e-9, "no grappling: he stays put");
        assertTrue(t.render.cues.contains("anchor_land"), "it clanks off the ground");
    }

    @Test
    void aRootDoesntStopTheHook() throws IOException {
        setup();
        t.engine.statuses().apply(p, "root", 40, enemy);
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "rooted");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "it moves them, not him");
    }

    // ---- Ability 2: Rustbreaker --------------------------------------------------------------------

    @Test
    void rustingBuildsAShieldAndSlowsMoreAndMore() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        double first = t.engine.stats().moveSpeedMultiplier(p);
        assertEquals(0.9, first, 1e-9, "one layer");
        t.time.advance(20);
        assertTrue(t.engine.stats().moveSpeedMultiplier(p) < first, "slower");
        t.time.advance(15);                                  // 8 layers at 1.75s
        assertEquals(Math.pow(0.9, 8), t.engine.stats().moveSpeedMultiplier(p), 1e-9);
        assertEquals(120, t.shields.get(p), 1e-9, "15 x 8");
    }

    @Test
    void atFullChargeHeSeizesUpThenBurstsOutDisarming() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        t.time.advance(41);                                  // full at 2s
        assertTrue(t.engine.tags().has(p, Tags.STUNNED), "seized up");
        assertEquals(0, t.damage(enemy), 1e-9, "not yet");
        t.time.advance(20);                                  // 1s later
        assertEquals(34 * 1.3, t.damage(enemy), 1e-9, "burst: 130%");
        assertTrue(t.engine.tags().has(enemy, Tags.DISARMED));
        assertFalse(t.engine.tags().has(p, Tags.STUNNED));
        assertEquals(1, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "the rust is gone");
    }

    @Test
    void duringLightningRodItRustsQuickerAndTheStunIsShorter() throws IOException {
        setup();
        t.engine.statuses().apply(p, "lightning_rod", p);
        use(Slots.ABILITY_2);
        t.time.advance(17);                                  // 8 layers at 0.8s
        assertTrue(t.engine.tags().has(p, Tags.STUNNED));
        t.time.advance(10);                                  // 0.5s
        assertEquals(34 * 1.3, t.damage(enemy), 1e-9);
        assertFalse(t.engine.tags().has(p, Tags.STUNNED));
    }

    // ---- Ability 3: Conduction Field ---------------------------------------------------------------

    @Test
    void theShockwaveRollsOutKnockingEveryoneUpOnce() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(1.5, 1, 0));
        UUID far = foe(0, 7);                                // behind him: a ring hits all around
        use(Slots.ABILITY_3);
        t.time.advance(4);
        assertEquals(0, t.damage(enemy), 1e-9, "winding up");
        t.time.advance(1);
        assertEquals(34 * 0.8, t.damage(enemy), 1e-9, "the first ring");
        assertEquals(0.45, t.knockbackVec.get(enemy).y(), 1e-9, "knocked up (a small hop)");
        assertEquals(0, t.damage(far), 1e-9, "not reached yet");
        t.time.advance(10);
        assertEquals(34 * 0.8, t.damage(far), 1e-9, "the last ring");
        assertEquals(34 * 0.8, t.damage(enemy), 1e-9, "each once");
    }

    // ---- Ultimate: Lightning Rod -------------------------------------------------------------------

    @Test
    void lightningStrikesAfterTheChannelThenTheFieldShocksAndParalyzes() throws IOException {
        setup();
        UUID outside = foe(8, 0);
        use(Slots.ULTIMATE);
        assertTrue(t.engine.tags().has(p, Tags.SLOWED), "channeling");
        assertTrue(t.engine.tags().has(p, Tags.SILENCED), "a self-silence");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_3).success(), "no abilities");
        t.time.advance(29);
        assertEquals(0, t.damage(enemy), 1e-9);
        t.time.advance(1);
        assertEquals(34 * 2.2 + 34 * 0.3, t.damage(enemy), 1e-9, "the bolt, and the field's first pulse");
        assertTrue(t.engine.tags().has(enemy, Tags.PARALYZED));
        assertEquals(0, t.damage(outside), 1e-9);
        assertTrue(t.engine.tags().has(p, Tags.HASTED), "faster");
        assertTrue(t.engine.tags().has(p, "state.lightning_rod"));
        assertTrue(t.engine.tags().has(p, Tags.SILENCED), "still silenced in the field");
        assertTrue(t.engine.loadouts().crowdControl(p, Slots.PRIMARY).isEmpty(), "basic attacks still work");

        t.time.advance(60);                                  // 3s in the field: 6 more pulses
        assertEquals(34 * 2.2 + 7 * 34 * 0.3, t.damage(enemy), 1e-6);
        assertTrue(t.engine.tags().has(enemy, Tags.PARALYZED), "kept paralyzed inside");
        t.time.advance(61);
        assertFalse(t.engine.instances().isRunning(p, "golem_ult1"), "6s: over");
        assertFalse(t.engine.tags().has(p, "state.lightning_rod"));
        assertFalse(t.engine.tags().has(p, Tags.SILENCED), "the silence ends with it");
        double after = t.damage(enemy);
        t.time.advance(20);
        assertEquals(after, t.damage(enemy), 1e-9, "no more pulses");
    }
}
