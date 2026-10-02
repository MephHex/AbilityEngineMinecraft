package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.PointTarget;
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
        assertEquals(BASE * 0.8, t.damage(front), 1e-6, "the first: 80%");
        assertEquals(BASE * 0.4, t.damage(back), 1e-6, "behind it: 40%");
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

        UUID enemy = foe(9, 0); // between them and her
        use(Slots.SECONDARY); // Recall
        t.time.advance(20);
        assertEquals(0, t.engine.constructs().activeCount(), "both came back");
        assertEquals(2 * BASE * 0.45, t.damage(enemy), 1e-6, "45% per shard that hit");
        assertEquals(2, stacks(enemy, "amethyst_shard_slow"), "slowed more per shard");
        assertTrue(has(enemy, "amethyst_bleed"), "and bleeding");
    }

    @Test
    void atMostSixShardsHang() throws IOException {
        setup();
        for (int i = 0; i < 8; i++) {
            use(Slots.PRIMARY);
            t.time.advance(15);
        }
        assertEquals(6, t.engine.constructs().activeCount());
    }

    // ---- Shard Volley -----------------------------------------------------------------------------------------

    @Test
    void shardVolleyGathersUpToSixThenLmbLoosesThem() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        assertEquals("amethyst_volley", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "LMB looses it");
        t.time.advance(40);
        assertEquals(6, t.engine.resources().get(p, "gathered"), "6 gathered");

        UUID enemy = foe(6, 0);
        use(Slots.PRIMARY);
        t.time.advance(20);
        assertTrue(has(enemy, "amethyst_volley_slow"), "slowed");
        assertTrue(t.damage(enemy) >= BASE * 0.6, "and the burst behind them hurt: " + t.damage(enemy));
        assertEquals("amethyst_primary", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "all loosed: over");
    }

    // ---- Crystal Ward -----------------------------------------------------------------------------------------

    @Test
    void crystalWardCatchesAShotAndThrowsItBackAtTheShooter() throws IOException {
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
        assertEquals(BASE, t.damage(enemy), 1e-6, "and threw it back: 100% base damage");
    }

    // ---- Shardfall --------------------------------------------------------------------------------------------

    @Test
    void shardfallRainsOnTheAreaSlowingAndHurtingWhoeverStaysInIt() throws IOException {
        setup();
        UUID enemy = foe(10, 0);
        UUID outside = foe(10, 8);
        var ability = t.engine.abilities().find("amethyst_ab3").orElseThrow();
        assertTrue(t.engine.activator().activateAt(p, ability, new PointTarget("world", new Vec3(10, 0, 0))).success());
        t.time.advance(40);
        assertTrue(has(enemy, "amethyst_shardfall"), "slowed and easier to hurt");
        t.time.advance(100);
        assertTrue(t.damage(enemy) >= 10 * BASE * 0.2 - 1e-6, "20% every 0.5s for 5s: " + t.damage(enemy));
        assertEquals(0, t.damage(outside), 1e-6);
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
