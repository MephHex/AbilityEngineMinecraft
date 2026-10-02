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
    void eachShardHangsEightSecondsFromWhenItGotThere() throws IOException {
        setup();
        use(Slots.PRIMARY);
        t.time.advance(40);
        use(Slots.PRIMARY); // 2s later
        t.time.advance(15);
        assertEquals(2, t.engine.constructs().activeCount(), "both hanging");
        t.time.advance(120); // t=175: the first got there at ~12 (its flight), so 8s later it's gone
        assertEquals(1, t.engine.constructs().activeCount(), "the first has faded");
        t.time.advance(40);
        assertEquals(0, t.engine.constructs().activeCount(), "t=215: the second too (it got there at ~52)");
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
        assertTrue(t.engine.tags().has(p, "state.slowed"), "slowed while she gathers");
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "a channel: nothing else meanwhile");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        assertEquals(0, t.damage(enemy), 1e-6, "not loosed yet");
        t.time.advance(60);
        assertFalse(t.engine.tags().has(p, "state.slowed"), "loosed: not slowed any more");
        t.time.advance(10);
        use(Slots.PRIMARY); // and free again
        assertTrue(has(enemy, "amethyst_volley_slow"), "loosed by itself: slowed");
        assertTrue(t.damage(enemy) >= BASE * 0.6, "and the burst behind them hurt: " + t.damage(enemy));
        assertFalse(has(p, "amethyst_gathering"), "over: the count's gone");
    }

    // ---- Crystal Ward -----------------------------------------------------------------------------------------

    @Test
    void crystalWardSendsTheShootersOwnShotBackAtThem() throws IOException {
        setup();
        t.load(map("abilities", map("test_bolt", map("nodes", map( // (ordered: the first node is where it starts)
                "shoot", map("type", "projectile", "speed", 1.0, "size", 0.4, "lifetime", 30,
                        "on", map("hit_entity", "hurt")),
                "hurt", map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                        "effects", List.of(map("id", "damage", "amount", 50))))))));
        UUID enemy = foe(8, 0);
        t.world.look(enemy, new Vec3(-1, 0, 0)); // at her
        use(Slots.ABILITY_2);
        assertTrue(has(p, "amethyst_warding"), "faster meanwhile");
        assertTrue(t.engine.activator().activate(enemy, "test_bolt").success());
        t.time.advance(20);
        assertEquals(0, t.damage(p), 1e-6, "the barrier caught it");
        assertEquals(50, t.damage(enemy), 1e-6, "and sent their own shot back at them: its own 50 damage");
    }

    @Test
    void crystalWardCatchesShotsFromBehindToo() throws IOException {
        setup();
        t.load(map("abilities", map("test_bolt", map("nodes", map(
                "shoot", map("type", "projectile", "speed", 1.0, "size", 0.4, "lifetime", 30,
                        "on", map("hit_entity", "hurt")),
                "hurt", map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                        "effects", List.of(map("id", "damage", "amount", 50))))))));
        UUID enemy = foe(-8, 0); // behind her (she looks +x)
        t.world.look(enemy, new Vec3(1, 0, 0));
        use(Slots.ABILITY_2);
        assertTrue(t.engine.activator().activate(enemy, "test_bolt").success());
        t.time.advance(20);
        assertEquals(0, t.damage(p), 1e-6, "all the way around: caught from behind");
        assertEquals(50, t.damage(enemy), 1e-6, "and sent back");
    }

    // ---- Crystallize ------------------------------------------------------------------------------------------

    @Test
    void crystallizeLmbEncasesTheEnemyThenItShatters() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        UUID near = foe(6, 2);
        use(Slots.ULTIMATE);
        t.time.advance(2);
        assertTrue(has(enemy, "amethyst_sighted"), "the enemy in her sights glows");
        use(Slots.PRIMARY); // LMB: them
        assertTrue(has(enemy, "amethyst_crystal"), "encased");
        assertTrue(t.engine.tags().has(enemy, "state.stunned"), "can't act");
        assertEquals(0, DamageModifiers.apply(t.engine, p, enemy, 500, 0).amount(), 1e-9, "can't be hurt");
        assertFalse(t.engine.tags().has(p, "state.choosing_crystal"), "chosen: over");

        t.time.advance(50);
        assertFalse(has(enemy, "amethyst_crystal"));
        assertEquals(BASE * 1.8, t.damage(enemy), 1e-6, "it shatters: 180%");
        assertEquals(BASE * 1.8, t.damage(near), 1e-6, "on whoever's near too");
        assertTrue(has(near, "amethyst_shattered"), "slowed");
    }

    @Test
    void crystallizeRmbEncasesHerselfAndSheHeals() throws IOException {
        setup();
        UUID enemy = foe(3, 0);
        use(Slots.ULTIMATE);
        use(Slots.SECONDARY); // RMB: herself
        assertTrue(has(p, "amethyst_crystal_self"));
        t.time.advance(50);
        assertEquals(5 * 180 * 0.05, t.healed.getOrDefault(p, 0.0), 1e-6, "25% of her max HP");
        assertEquals(BASE * 1.8, t.damage(enemy), 1e-6, "then it shatters around her");
    }
}
