package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Amethyst (fixtures/amethyst.yml): 180 HP, 5 armor, base damage 32. She stands at the origin on a floor, looking +x,
 * team blue. Enemies are team red (no sheet: 200 HP, no armor).
 */
class AmethystTest {

    private static final double BASE = 32;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "amethyst");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int stacks(UUID id, String status) {
        return t.engine.statuses().find(id, status).map(s -> s.stacks()).orElse(0);
    }

    // ---- the shards (and the passive) -----------------------------------------------------------------------

    @Test
    void aShardPiercesTheFirstTakesFullDamageThoseBehindHalf() throws IOException {
        setup();
        UUID front = foe(4, 0);
        UUID back = foe(7, 0);
        use(Slots.PRIMARY);
        t.time.advance(10);
        assertEquals(BASE * 0.4, t.damage(front), 1e-6, "the first: 40%");
        assertEquals(BASE * 0.2, t.damage(back), 1e-6, "behind it: 20%");
        assertEquals(5, t.engine.resources().get(p, "ammo"), "a shard of ammo");
    }

    @Test
    void aShardHangsAtItsFullRangeAndRecallBringsItBackThroughEnemies() throws IOException {
        setup();
        use(Slots.PRIMARY);
        t.time.advance(15); // 18 blocks out: it hangs there
        assertEquals(1, t.engine.constructs().activeCount(), "a lingering shard");
        t.time.advance(10);
        use(Slots.PRIMARY);
        t.time.advance(15);
        assertEquals(2, t.engine.constructs().activeCount(), "two");

        assertEquals(4, t.engine.resources().get(p, "ammo"), "two shards out");

        UUID enemy = foe(9, 0); // between them and her
        use(Slots.ABILITY_3); // Recall
        t.time.advance(20);
        assertEquals(0, t.engine.constructs().activeCount(), "both came back");
        assertEquals(6, t.engine.resources().get(p, "ammo"), "and back in the ammo");
        assertEquals(2 * BASE * 0.45, t.damage(enemy), 1e-6, "45% per shard that hit");
        assertEquals(2, stacks(enemy, "amethyst_shard_slow"), "slowed more per shard");
        assertTrue(has(enemy, "amethyst_bleed"), "and bleeding");
        assertFalse(t.knockbackVec.containsKey(enemy), "no knockback");
        double hit = t.damage(enemy);
        t.time.advance(80);
        assertEquals(4 * 200 * 0.015, t.damage(enemy) - hit, 0.2 * 200 * 0.015 + 1e-6,
                "the bleed hurts: 1.5% of their max HP a second for 4s");
    }

    @Test
    void aShardThatHitsTheGroundHangsThereToo() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0)); // at the floor, a couple of blocks ahead
        use(Slots.PRIMARY);
        t.time.advance(5);
        assertEquals(1, t.engine.constructs().activeCount(), "it stays where it hit");
        use(Slots.ABILITY_3);
        t.time.advance(10);
        assertEquals(0, t.engine.constructs().activeCount(), "and comes back with Recall");
        assertEquals(6, t.engine.resources().get(p, "ammo"));
    }

    @Test
    void eachShardHangsFiveSecondsFromWhenItGotThere() throws IOException {
        setup();
        use(Slots.PRIMARY);
        t.time.advance(40);
        use(Slots.PRIMARY); // 2s later
        t.time.advance(15);
        assertEquals(2, t.engine.constructs().activeCount(), "both hanging");
        t.time.advance(60); // t=115: the first got there at ~12 (its flight), so 5s later it's gone
        assertEquals(1, t.engine.constructs().activeCount(), "the first has faded");
        t.time.advance(40);
        assertEquals(0, t.engine.constructs().activeCount(), "t=155: the second too (it got there at ~52)");
    }

    @Test
    void recallOnlyWithShardsHangingAndItsIconCountsThem() throws IOException {
        setup();
        var recall = t.engine.abilities().find("amethyst_recall").orElseThrow();
        assertTrue(t.engine.activator().isUnavailable(p, recall), "none hanging: greyed out");
        var none = t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertFalse(none.success());
        assertEquals(me.mephisto.ability_engine.engine.ability.activation.AbilityActivator.UNAVAILABLE, none.reason());
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "amethyst_recall"), "and it costs nothing");

        for (int i = 0; i < 3; i++) {
            use(Slots.PRIMARY);
            t.time.advance(4);
        }
        t.time.advance(15);
        assertEquals(3, t.engine.activator().constructsFor(p, recall), "3 to recall (the icon's count)");
        assertFalse(t.engine.activator().isUnavailable(p, recall));
        use(Slots.ABILITY_3);
    }

    @Test
    void recallStopsShardsMidFlightAndBringsThemBack() throws IOException {
        setup();
        var recall = t.engine.abilities().find("amethyst_recall").orElseThrow();
        use(Slots.PRIMARY);
        t.time.advance(3); // still flying (it takes ~12 ticks to reach its range)
        assertEquals(0, t.engine.constructs().activeCount(), "not hanging yet");
        assertEquals(1, t.engine.activator().constructsFor(p, recall), "but it counts: Recall can be used");
        UUID enemy = foe(2, 0); // between where it is and her
        use(Slots.ABILITY_3);
        t.time.advance(15);
        assertEquals(0, t.engine.constructs().activeCount(), "stopped where it was, and came back");
        assertEquals(BASE * 0.45, t.damage(enemy), 1e-6, "through the enemy on the way");
        assertEquals(6, t.engine.resources().get(p, "ammo"), "back in the ammo");
    }

    @Test
    void aShardThatFadesOnItsOwnIsGoneTheAmmoOnlyRefillsSlowly() throws IOException {
        setup();
        use(Slots.PRIMARY);
        t.time.advance(14); // hanging
        double before = t.engine.resources().value(p, "ammo");
        t.time.advance(105); // faded (5s after it got there)
        assertEquals(0, t.engine.constructs().activeCount(), "faded");
        assertEquals(Math.min(6, before + 105 / 60.0), t.engine.resources().value(p, "ammo"), 1e-6,
                "no shard back from fading: only the refill, a shard every 3s");
    }

    @Test
    void eachRecalledShardGivesExactlyOneAmmo() throws IOException {
        setup();
        use(Slots.PRIMARY);
        t.time.advance(4);
        use(Slots.PRIMARY);
        t.time.advance(15); // 2 hanging
        t.engine.resources().set(p, "ammo", 0);
        use(Slots.ABILITY_3);
        t.time.advance(60); // caught (and past the end of their 2s flight, had they flown on)
        assertEquals(2 + 60 / 60.0, t.engine.resources().value(p, "ammo"), 1e-6,
                "2 shards caught: 2 ammo (plus the 3s of slow refill), not 2 each");
    }

    @Test
    void theAmmoItemCountsDownToTheNextShard() throws IOException {
        setup();
        assertEquals(0, t.engine.resources().nextUnitTicks(p, "ammo"), "full: nothing coming");
        use(Slots.PRIMARY); // 5 left
        assertEquals(60, t.engine.resources().nextUnitTicks(p, "ammo"), "the next shard in 3s");
        t.time.advance(20);
        assertEquals(40, t.engine.resources().nextUnitTicks(p, "ammo"));
        t.time.advance(40);
        assertEquals(6, t.engine.resources().get(p, "ammo"));
        assertEquals(0, t.engine.resources().nextUnitTicks(p, "ammo"));
    }

    @Test
    void atMostSixShardsHang() throws IOException {
        setup();
        for (int i = 0; i < 6; i++) {
            use(Slots.PRIMARY);
            t.time.advance(4);
        }
        t.time.advance(15);
        assertEquals(6, t.engine.constructs().activeCount(), "the whole ammo hanging");
        for (int i = 0; i < 2; i++) { // out of ammo: plain shards, they don't hang
            use(Slots.PRIMARY);
            t.time.advance(14);
        }
        assertEquals(6, t.engine.constructs().activeCount());
    }

    // ---- Shard Volley -----------------------------------------------------------------------------------------

    @Test
    void outOfAmmoShardsDontPierceAndComeSlower() throws IOException {
        setup();
        int fast = t.engine.stats().cooldownTicks(p, "amethyst_primary", 4);
        for (int i = 0; i < 6; i++) {
            use(Slots.PRIMARY);
            t.time.advance(4);
        }
        assertEquals(0, t.engine.resources().get(p, "ammo"));
        assertTrue(has(p, "amethyst_drained"), "out of ammo: slower");
        assertTrue(t.engine.stats().cooldownTicks(p, "amethyst_primary", 4) > fast);

        t.time.advance(20); // (the last full shards are hanging by now)
        UUID front = foe(4, 0);
        UUID back = foe(7, 0);
        use(Slots.PRIMARY);
        t.time.advance(10);
        assertEquals(BASE * 0.4, t.damage(front), 1e-6, "a plain shard");
        assertEquals(0, t.damage(back), 1e-6, "that doesn't pierce");
    }

    // ---- Shard Volley -----------------------------------------------------------------------------------------

    @Test
    void shardVolleyGathersSixThenLoosesThemByItself() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.ABILITY_1);
        t.time.advance(25);
        assertTrue(stacks(p, "amethyst_gathering") >= 3, "the XP bar counts them: " + stacks(p, "amethyst_gathering"));
        assertTrue(t.engine.stats().moveSpeedMultiplier(p) < 0.8, "slower with every shard gathered");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "a channel: nothing else meanwhile");
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertEquals(0, t.damage(enemy), 1e-6, "not loosed yet");
        t.time.advance(60);
        assertEquals(1, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "loosed: not slowed any more");
        t.time.advance(10);
        use(Slots.PRIMARY); // and free again
        assertTrue(has(enemy, "amethyst_volley_slow"), "loosed by itself: slowed");
        assertTrue(t.damage(enemy) >= BASE * 0.6, "and the burst behind them hurt: " + t.damage(enemy));
        assertFalse(has(p, "amethyst_gathering"), "over: the count's gone");
    }

    @Test
    void lmbLoosesTheVolleyEarlyWithWhatSheHas() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.ABILITY_1);
        t.time.advance(20); // 3 gathered (at 0, 9, 18)
        assertEquals(3, stacks(p, "amethyst_gathering"));
        assertEquals("amethyst_volley", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "LMB looses it");
        use(Slots.PRIMARY);
        assertEquals(1, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "loosed: no more slow");
        t.time.advance(14); // the 3 circling shards fly, 0.2s apart
        assertFalse(t.engine.tags().has(p, "state.channeling"), "then the channel's over");
        assertEquals(0, t.engine.resources().get(p, "gathered"), "no more gathered meanwhile");
        t.time.advance(10);
        assertTrue(has(enemy, "amethyst_volley_slow"), "the 3 flew");
        assertTrue(t.damage(enemy) >= BASE * 0.6);
        assertTrue(t.damage(enemy) <= 3 * BASE * 0.6 + 1e-6, "3 shards at most: " + t.damage(enemy));
    }

    @Test
    void aStunBreaksTheVolleysChannel() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.ABILITY_1);
        t.time.advance(20);
        t.engine.statuses().apply(p, "stun", 20, enemy);
        assertFalse(t.engine.tags().has(p, "state.channeling"), "broken");
        t.time.advance(80);
        assertEquals(0, t.damage(enemy), 1e-6, "nothing was loosed");
        assertFalse(has(p, "amethyst_gathering"), "the XP bar's count is gone");
    }

    // ---- Crystal Ward (ultimate) ------------------------------------------------------------------------------

    private void loadTestBolt() {
        t.load(map("abilities", map("test_bolt", map("nodes", map( // (ordered: the first node is where it starts)
                "shoot", map("type", "projectile", "speed", 1.0, "size", 0.4, "lifetime", 30,
                        "on", map("hit_entity", "hurt")),
                "hurt", map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                        "effects", List.of(map("id", "damage", "amount", 50))))))));
    }

    @Test
    void crystalWardSendsEveryShotBackAndLasts5s() throws IOException {
        setup();
        loadTestBolt();
        UUID enemy = foe(8, 0);
        t.world.look(enemy, new Vec3(-1, 0, 0)); // at her
        use(Slots.ULTIMATE);
        assertTrue(has(p, "amethyst_warding"), "faster meanwhile");
        assertTrue(t.engine.activator().activate(enemy, "test_bolt").success());
        t.time.advance(20);
        assertEquals(0, t.damage(p), 1e-6, "the shell caught it");
        assertEquals(50, t.damage(enemy), 1e-6, "and sent their own shot back at them: its own 50 damage");
        assertTrue(t.engine.barriers().has(p), "one caught: it stands on");

        assertTrue(t.engine.activator().activate(enemy, "test_bolt").success());
        t.time.advance(20);
        assertEquals(0, t.damage(p), 1e-6, "the second one too");
        assertEquals(100, t.damage(enemy), 1e-6, "sent back as well");

        t.time.advance(61);
        assertFalse(t.engine.barriers().has(p), "5s: over");
        assertFalse(has(p, "amethyst_warding"));
    }

    @Test
    void crystalWardCatchesShotsFromBehindToo() throws IOException {
        setup();
        loadTestBolt();
        UUID enemy = foe(-8, 0); // behind her (she looks +x)
        t.world.look(enemy, new Vec3(1, 0, 0));
        use(Slots.ULTIMATE);
        assertTrue(t.engine.activator().activate(enemy, "test_bolt").success());
        t.time.advance(20);
        assertEquals(0, t.damage(p), 1e-6, "all the way around: caught from behind");
        assertEquals(50, t.damage(enemy), 1e-6, "and sent back");
    }

    // ---- Gem Rush ---------------------------------------------------------------------------------------------

    @Test
    void gemRushDashesTheWaySheMovesReloadsThreeAndShieldsHer() throws IOException {
        setup();
        for (int i = 0; i < 5; i++) { // 5 of her 6 shards out
            use(Slots.PRIMARY);
            t.time.advance(5);
        }
        double ammo = t.engine.resources().get(p, "ammo");
        t.world.walk(p, new Vec3(0, 0, 1)); // strafing to her right (+z) while looking +x
        use(Slots.ABILITY_2);
        t.time.advance(8);
        Vec3 at = t.world.positionOf(new me.mephisto.ability_engine.engine.target.EntityTarget(p)).orElseThrow().position();
        assertTrue(at.z() > 4 && Math.abs(at.x()) < 1, "dashed the way she was moving, ~6 blocks: " + at);
        assertEquals(Math.min(6, ammo + 3), t.engine.resources().get(p, "ammo"), 0.01, "3 shards back");
        assertEquals(40, t.shields.getOrDefault(p, 0.0), 1e-6, "a 40 HP shield");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "amethyst_ab2") > 0);
    }
}
